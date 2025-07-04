package com.galphos.systemconfig

import cats.effect.{ExitCode, IO, IOApp, Resource}
import cats.syntax.all._
import com.galphos.systemconfig.config.AppConfig
import com.galphos.systemconfig.db.DatabaseConfig
import com.galphos.systemconfig.routes.{AdminRoutes, HealthRoutes, SettingsRoutes, VersionRoutes}
import com.galphos.systemconfig.services.{AdminProxyService, AuthService, SettingsService, VersionService}
import doobie.hikari.HikariTransactor
import doobie.util.transactor.Transactor
import fs2.io.net.Network
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.{Logger, CORS}
import org.http4s.server.middleware.CORS.policy
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.{Header, Headers, HttpRoutes}
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.typelevel.log4cats.Logger as Log4CatsLogger
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._

object SystemConfigServiceMain extends IOApp {
  private val logger: Log4CatsLogger[IO] = Slf4jLogger.getLogger[IO]

  override def run(args: List[String]): IO[ExitCode] = {
    for {
      config <- IO(AppConfig.load())
      _ <- logger.info(s"启动系统配置服务，监听端口：${config.port}")
      
      // 移除强制等待认证服务启动的逻辑，避免启动时的权限验证冲突
      _ <- logger.info("启动系统配置服务...")
      
      // 初始化数据库配置
      dbConfig = new DatabaseConfig(config, global)
      
      exitCode <- dbConfig.transactor.use { transactor =>
        for {
          // 检查数据库连接
          _ <- dbConfig.testConnection(transactor)
          
          // 初始化服务
          versionService = new VersionService(Resource.pure[IO, Transactor[IO]](transactor))
          adminProxyService = new AdminProxyService(config.userManagementServiceUrl)
          settingsService = new SettingsService(Resource.pure[IO, Transactor[IO]](transactor))
          authService = new AuthService(config)
          
          // 移除启动时的认证服务检查，避免干扰正常的管理员会话
          _ <- logger.info("使用管理员代理服务，所有管理员操作将代理到UserManagementService")
          
          // 初始化路由
          adminRoutes = new AdminRoutes(adminProxyService, authService)
          settingsRoutes = new SettingsRoutes(settingsService, authService)
          versionRoutes = new VersionRoutes(versionService)
          healthRoutes = new HealthRoutes()
          
          // 组合所有API路由
          apiRoutes = adminRoutes.routes <+> settingsRoutes.routes <+> versionRoutes.routes
          
          // 创建完整路由
          httpApp = Router(
            "/api" -> apiRoutes,
            "/" -> healthRoutes.routes
          ).orNotFound
          
          // 添加CORS中间件
          corsApp = CORS.policy
            .withAllowOriginAll
            .withAllowMethodsAll
            .withAllowHeadersAll
            .withAllowCredentials(false)
            .apply(httpApp)
          
          // 添加请求日志中间件
          finalHttpApp = Logger.httpApp(logHeaders = true, logBody = true)(corsApp)
          
          // 启动HTTP服务器
          result <- EmberServerBuilder
            .default[IO]
            .withHost(config.host)
            .withPort(config.port)
            .withHttpApp(finalHttpApp)
            .build
            .use { server => 
              for {
                _ <- logger.info(s"服务器已启动，访问地址: http://${config.host}:${config.port}")
                _ <- logger.info(s"按 CTRL+C 停止服务器")
                
                // 保持服务器运行，直到收到终止信号
                _ <- IO.never
              } yield ExitCode.Success
            }
            .handleErrorWith { error =>
              logger.error(error)("服务器启动或运行时出现错误") *> 
              IO.pure(ExitCode.Error)
            }
        } yield result
      }
    } yield exitCode
  }
}
