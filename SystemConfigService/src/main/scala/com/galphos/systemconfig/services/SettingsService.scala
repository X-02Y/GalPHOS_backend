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

class SettingsService(xa: Resource[IO, Transactor[IO]]) {
  private val logger = Slf4jLogger.getLogger[IO]
  
  // 获取所有系统设置
  def getAllSettings(isPublic: Boolean = false): IO[List[SystemConfig]] = {
    val query = if (isPublic) {
      sql"""
        SELECT id, config_key, config_value, description, is_public, created_at, updated_at
        FROM system_config
        WHERE is_public = true
        ORDER BY config_key
      """
    } else {
      sql"""
        SELECT id, config_key, config_value, description, is_public, created_at, updated_at
        FROM system_config
        ORDER BY config_key
      """
    }
    
    val result = query
      .query[(Long, String, String, Option[String], Boolean, ZonedDateTime, ZonedDateTime)]
      .map { case (id, key, value, description, isPublic, createdAt, updatedAt) =>
        SystemConfig(Some(id), key, value, description, isPublic, Some(createdAt), Some(updatedAt))
      }
      .to[List]
      
    xa.use(result.transact(_))
      .handleErrorWith { error =>
        logger.error(error)("获取系统设置列表失败") *> IO.pure(List.empty)
      }
  }
  
  // 获取单个设置
  def getSetting(configKey: String): IO[Option[SystemConfig]] = {
    val query = sql"""
      SELECT id, config_key, config_value, description, is_public, created_at, updated_at
      FROM system_config
      WHERE config_key = $configKey
    """.query[(Long, String, String, Option[String], Boolean, ZonedDateTime, ZonedDateTime)]
      .map { case (id, key, value, description, isPublic, createdAt, updatedAt) =>
        SystemConfig(Some(id), key, value, description, isPublic, Some(createdAt), Some(updatedAt))
      }
      .option
      
    xa.use(query.transact(_))
      .handleErrorWith { error =>
        logger.error(error)(s"获取系统设置失败，Key: $configKey") *> IO.pure(None)
      }
  }
  
  // 从数据库获取设置（内部使用）
  private def getSettingFromDb(configKey: String): ConnectionIO[Option[SystemConfig]] = {
    sql"""
      SELECT id, config_key, config_value, description, is_public, created_at, updated_at
      FROM system_config
      WHERE config_key = $configKey
    """.query[(Long, String, String, Option[String], Boolean, ZonedDateTime, ZonedDateTime)]
      .map { case (id, key, value, description, isPublic, createdAt, updatedAt) =>
        SystemConfig(Some(id), key, value, description, isPublic, Some(createdAt), Some(updatedAt))
      }
      .option
  }
  
  // 更新设置
  def updateSetting(configKey: String, configValue: String, isPublic: Option[Boolean] = None): IO[Option[SystemConfig]] = {
    val now = ZonedDateTime.now()
    
    // 事务处理
    val transaction = for {
      // 检索旧值用于历史记录
      oldConfigOpt <- getSettingFromDb(configKey)
      oldValue = oldConfigOpt.map(_.configValue).getOrElse("")
      
      // 构建更新SQL
      updateSql = if (isPublic.isDefined) {
        val isPublicValue = isPublic.get
        sql"""
          UPDATE system_config 
          SET config_value = $configValue, updated_at = $now, is_public = $isPublicValue
          WHERE config_key = $configKey
        """.update.run
      } else {
        sql"""
          UPDATE system_config 
          SET config_value = $configValue, updated_at = $now
          WHERE config_key = $configKey
        """.update.run
      }
      
      // 执行更新
      updated <- updateSql
      
      // 记录变更历史（管理员ID为1，实际应用应该从上下文获取）
      _ <- {
        val adminId = 1L
        sql"""
          INSERT INTO config_history (config_key, old_value, new_value, changed_by, changed_at)
          VALUES ($configKey, $oldValue, $configValue, $adminId, $now)
        """.update.run
      }
      
      // 获取更新后的值
      config <- if (updated > 0) getSettingFromDb(configKey) else doobie.free.connection.pure(None)
    } yield config
    
    xa.use(transaction.transact(_))
      .handleErrorWith { error =>
        logger.error(error)(s"更新系统设置失败，Key: $configKey") *> IO.pure(None)
      }
  }
  
  // 创建设置
  def createSetting(config: SystemConfig): IO[Option[SystemConfig]] = {
    val now = ZonedDateTime.now()
    
    val transaction = for {
      id <- {
        val configKey = config.configKey
        val configValue = config.configValue
        val description = config.description
        val isPublic = config.isPublic
        sql"""
          INSERT INTO system_config 
            (config_key, config_value, description, is_public, created_at, updated_at)
          VALUES 
            ($configKey, $configValue, $description, $isPublic, $now, $now)
          RETURNING id
        """.query[Long].unique
      }
      
      newConfig <- getSettingFromDb(config.configKey)
    } yield newConfig
    
    xa.use(transaction.transact(_))
      .handleErrorWith { error =>
        logger.error(error)(s"创建系统设置失败，Key: ${config.configKey}") *> IO.pure(None)
      }
  }
  
  // 删除设置
  def deleteSetting(configKey: String): IO[Boolean] = {
    val transaction = for {
      // 查询是否存在
      exists <- sql"SELECT COUNT(*) FROM system_config WHERE config_key = $configKey"
                 .query[Int].unique.map(_ > 0)
                 
      // 如果存在，执行删除
      deleted <- if (exists) {
        sql"DELETE FROM system_config WHERE config_key = $configKey".update.run.map(_ > 0)
      } else {
        doobie.free.connection.pure(false)
      }
    } yield deleted
    
    xa.use(transaction.transact(_))
      .handleErrorWith { error =>
        logger.error(error)(s"删除系统设置失败，Key: $configKey") *> IO.pure(false)
      }
  }
}
