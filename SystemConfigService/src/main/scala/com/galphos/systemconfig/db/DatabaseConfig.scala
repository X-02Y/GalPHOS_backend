package com.galphos.systemconfig.db

import cats.effect.{IO, Resource}
import com.galphos.systemconfig.config.AppConfig
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts
import doobie.implicits._
import doobie._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

import scala.concurrent.ExecutionContext

class DatabaseConfig(appConfig: AppConfig, connectEC: ExecutionContext) {
  private val logger = Slf4jLogger.getLogger[IO]
  
  // 创建数据库连接池 - Hikari
  def transactor: Resource[IO, HikariTransactor[IO]] = {
    for {
      ce <- ExecutionContexts.fixedThreadPool[IO](appConfig.maximumPoolSize) // 连接执行上下文
      xa <- HikariTransactor.newHikariTransactor[IO](
        "org.postgresql.Driver",
        appConfig.jdbcUrl, 
        appConfig.username,
        appConfig.password,
        ce
      )
      _ <- Resource.eval(logger.info(s"数据库连接已初始化: ${appConfig.jdbcUrl}"))
    } yield xa
  }
  
  // 测试数据库连接
  def testConnection(xa: Transactor[IO]): IO[Unit] = {
    sql"SELECT 1".query[Int].unique.transact(xa).flatMap { result =>
      logger.info(s"数据库连接测试成功: $result")
    }.handleErrorWith { error =>
      logger.error(error)("数据库连接测试失败") *> 
      IO.raiseError(new RuntimeException("无法连接到数据库，请检查配置", error))
    }
  }
}
