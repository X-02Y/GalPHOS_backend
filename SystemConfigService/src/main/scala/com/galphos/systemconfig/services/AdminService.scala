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
             is_super_admin, created_at, updated_at, last_login
      FROM system_admins
      ORDER BY username
    """.query[(Long, String, String, String, 
              Boolean, ZonedDateTime, ZonedDateTime, Option[ZonedDateTime])]
      .map { case (id, username, passwordHash, role, 
                   isSuperAdmin, createdAt, updatedAt, lastLogin) =>
        Admin(Some(id), username, Some(passwordHash), role, 
              isSuperAdmin, Some(createdAt), Some(updatedAt), lastLogin)
      }
      .to[List]
      
    xa.use(query.transact(_))
      .handleErrorWith { error =>
        logger.error(error)("获取管理员列表失败") *> IO.pure(List.empty)
      }
  }
  
  // 获取单个管理员
  def getAdmin(adminId: Long): IO[Option[Admin]] = {
    val query = sql"""
      SELECT admin_id, username, password_hash, role, 
             is_super_admin, created_at, updated_at, last_login
      FROM system_admins
      WHERE admin_id = $adminId
    """.query[(Long, String, String, String, 
              Boolean, ZonedDateTime, ZonedDateTime, Option[ZonedDateTime])]
      .map { case (id, username, passwordHash, role, 
                   isSuperAdmin, createdAt, updatedAt, lastLogin) =>
        Admin(Some(id), username, Some(passwordHash), role, 
              isSuperAdmin, Some(createdAt), Some(updatedAt), lastLogin)
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
    
    // 根据role字段确定是否为超级管理员
    val isSuperAdmin = adminRequest.role.exists(_.contains("super_admin"))
    val role = adminRequest.role.getOrElse("admin") // 默认为admin角色
    
    val insertQuery = sql"""
      INSERT INTO system_admins 
        (username, password_hash, role, is_super_admin, created_at, updated_at)
      VALUES 
        (${adminRequest.username}, $passwordHash, $role, 
         $isSuperAdmin, $now, $now)
      RETURNING admin_id
    """.query[Long].unique
    
    val transaction = for {
      id <- insertQuery
      admin <- getAdminFromDb(id)
    } yield admin
    
    xa.use(transaction.transact(_))
      .handleErrorWith { error =>
        logger.error(error)(s"创建管理员失败: ${adminRequest.username}") *> IO.pure(None)
      }
  }
  
  // 从数据库获取管理员（内部使用）
  private def getAdminFromDb(adminId: Long): ConnectionIO[Option[Admin]] = {
    sql"""
      SELECT admin_id, username, password_hash, role, 
             is_super_admin, created_at, updated_at, last_login
      FROM system_admins
      WHERE admin_id = $adminId
    """.query[(Long, String, String, String, 
              Boolean, ZonedDateTime, ZonedDateTime, Option[ZonedDateTime])]
      .map { case (id, username, passwordHash, role, 
                   isSuperAdmin, createdAt, updatedAt, lastLogin) =>
        Admin(Some(id), username, Some(passwordHash), role, 
              isSuperAdmin, Some(createdAt), Some(updatedAt), lastLogin)
      }
      .option
  }
  
  // 更新管理员
  def updateAdmin(adminId: Long, updateRequest: UpdateAdminRequest): IO[Option[Admin]] = {
    val now = ZonedDateTime.now()
    
    // 构建更新片段
    val updateFragments = List(
      updateRequest.role.map(r => fr"role = $r"),
      updateRequest.isSuperAdmin.map(sa => fr"is_super_admin = $sa"),
      Some(fr"updated_at = $now")
    ).flatten
    
    if (updateFragments.isEmpty) {
      return IO.pure(None)
    }
    
    val setClause = updateFragments.intercalate(fr", ")
    val updateQuery = (fr"UPDATE system_admins SET" ++ setClause ++ fr"WHERE admin_id = $adminId").update.run
    
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
  def deleteAdmin(adminId: Long): IO[Boolean] = {
    val deleteQuery = sql"DELETE FROM system_admins WHERE admin_id = $adminId".update.run
    
    xa.use(deleteQuery.transact(_))
      .map(_ > 0)
      .handleErrorWith { error =>
        logger.error(error)(s"删除管理员失败，ID: $adminId") *> IO.pure(false)
      }
  }
  
  // 重置管理员密码
  def resetPassword(adminId: Long, resetRequest: ResetPasswordRequest): IO[Boolean] = {
    val passwordHash = BCrypt.hashpw(resetRequest.password, BCrypt.gensalt())
    val now = ZonedDateTime.now()
    
    val updateQuery = sql"""
      UPDATE system_admins 
      SET password_hash = $passwordHash, updated_at = $now
      WHERE admin_id = $adminId
    """.update.run
    
    xa.use(updateQuery.transact(_))
      .map(_ > 0)
      .handleErrorWith { error =>
        logger.error(error)(s"重置密码失败，ID: $adminId") *> IO.pure(false)
      }
  }
}
