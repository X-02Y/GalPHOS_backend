package com.galphos.systemconfig.services

import cats.effect.{IO, Resource}
import com.galphos.systemconfig.models._
import com.galphos.systemconfig.models.Models._
import com.galphos.systemconfig.db.DoobieMeta._
import com.galphos.systemconfig.db.DatabaseSupport._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import java.time.ZonedDateTime
import org.mindrot.jbcrypt.BCrypt
import cats.implicits._

class AdminService(xa: Resource[IO, Transactor[IO]]) {
  private val logger = Slf4jLogger.getLogger[IO]
  
  // 获取所有管理员
  def getAllAdmins: IO[List[Admin]] = {
    val query = sql"""
      SELECT admin_id, username, password_hash, role, 
             (role = 'super_admin') as is_super_admin, status
      FROM authservice.admin_table
      ORDER BY username
    """.query[(String, String, String, String, Boolean, String)]
      .map { case (id, username, passwordHash, role, isSuperAdmin, status) =>
        Admin(Some(id), username, Some(passwordHash), role, 
              isSuperAdmin, Some(status), None, None, None)
      }
      .to[List]
      
    xa.use(query.transact(_))
      .handleErrorWith { error =>
        logger.error(error)("获取管理员列表失败") *> IO.pure(List.empty)
      }
  }
  
  // 获取单个管理员
  def getAdmin(adminId: String): IO[Option[Admin]] = {
    val query = sql"""
      SELECT admin_id, username, password_hash, role, 
             (role = 'super_admin') as is_super_admin, status
      FROM authservice.admin_table
      WHERE admin_id = $adminId
    """.query[(String, String, String, String, Boolean, String)]
      .map { case (id, username, passwordHash, role, isSuperAdmin, status) =>
        Admin(Some(id), username, Some(passwordHash), role, 
              isSuperAdmin, Some(status), None, None, None)
      }
      .option
      
    xa.use(query.transact(_))
      .handleErrorWith { error =>
        logger.error(error)(s"获取管理员失败，ID: $adminId") *> IO.pure(None)
      }
  }
  
  // 创建管理员
  def createAdmin(adminRequest: CreateAdminRequest): IO[Option[Admin]] = {
    // 前端已经进行了密码哈希，不需要再次哈希
    val passwordHash = adminRequest.password
    val now = ZonedDateTime.now()
    val newAdminId = java.util.UUID.randomUUID().toString
    val fixedSalt = "GalPHOS_2025_SALT" // 使用固定盐值
    
    // 根据role字段确定是否为超级管理员
    val role = adminRequest.role.getOrElse("admin") // 默认为admin角色
    
    val insertQuery = sql"""
      INSERT INTO authservice.admin_table 
        (admin_id, username, password_hash, salt, role, status, created_at)
      VALUES 
        ($newAdminId, ${adminRequest.username}, $passwordHash, $fixedSalt, $role, 
         'active', $now)
    """.update.run
    
    val transaction = for {
      _ <- insertQuery
      admin <- getAdminFromDb(newAdminId)
    } yield admin
    
    xa.use(transaction.transact(_))
      .handleErrorWith { error =>
        logger.error(error)(s"创建管理员失败: ${adminRequest.username}") *> IO.pure(None)
      }
  }
  
  // 从数据库获取管理员（内部使用）
  private def getAdminFromDb(adminId: String): ConnectionIO[Option[Admin]] = {
    sql"""
      SELECT admin_id, username, password_hash, role, 
             (role = 'super_admin') as is_super_admin, status
      FROM authservice.admin_table
      WHERE admin_id = $adminId
    """.query[(String, String, String, String, Boolean, String)]
      .map { case (id, username, passwordHash, role, isSuperAdmin, status) =>
        Admin(Some(id), username, Some(passwordHash), role, 
              isSuperAdmin, Some(status), None, None, None)
      }
      .option
  }
  
  // 更新管理员
  def updateAdmin(adminId: String, updateRequest: UpdateAdminRequest): IO[Option[Admin]] = {
    val now = ZonedDateTime.now()
    
    // 构建更新片段
    val updateFragments = List(
      updateRequest.role.map(r => fr"role = $r"),
      updateRequest.status.map(s => fr"status = $s"),
      updateRequest.username.map(u => fr"username = $u"),
      updateRequest.name.map(n => fr"name = $n"),
      updateRequest.avatarUrl.map(a => fr"avatar_url = $a")
    ).flatten
    
    if (updateFragments.isEmpty) {
      return IO.pure(None)
    }
    
    val setClause = updateFragments.intercalate(fr", ")
    val updateQuery = (fr"UPDATE authservice.admin_table SET" ++ setClause ++ fr"WHERE admin_id = $adminId").update.run
    
    val transaction = for {
      updated <- updateQuery
      admin <- if (updated > 0) getAdminFromDb(adminId) else doobie.free.connection.pure[Option[Admin]](None)
    } yield admin
    
    xa.use(transaction.transact(_))
      .handleErrorWith { error =>
        logger.error(error)(s"更新管理员失败，ID: $adminId") *> IO.pure(None)
      }
  }
  
  // 删除管理员
  def deleteAdmin(adminId: String): IO[Boolean] = {
    val deleteQuery = sql"DELETE FROM authservice.admin_table WHERE admin_id = $adminId".update.run
    
    xa.use(deleteQuery.transact(_))
      .map(_ > 0)
      .handleErrorWith { error =>
        logger.error(error)(s"删除管理员失败，ID: $adminId") *> IO.pure(false)
      }
  }
  
  // 重置管理员密码
  def resetPassword(adminId: String, resetRequest: ResetPasswordRequest): IO[Boolean] = {
    val fixedSalt = "GalPHOS_2025_SALT"
    // 对于重置密码，我们使用 newPassword 字段，前端已经进行了哈希处理
    val passwordHash = resetRequest.newPassword
    
    val updateQuery = sql"""
      UPDATE authservice.admin_table 
      SET password_hash = $passwordHash, salt = $fixedSalt
      WHERE admin_id = $adminId
    """.update.run
    
    xa.use(updateQuery.transact(_))
      .map(_ > 0)
      .handleErrorWith { error =>
        logger.error(error)(s"重置密码失败，ID: $adminId") *> IO.pure(false)
      }
  }
}
