package Main

import cats.effect.{IO, IOApp, ExitCode}
import org.http4s.ember.server.EmberServerBuilder
import org.slf4j.LoggerFactory
import Config.{ConfigLoader, ServiceConfig}
import Database.DatabaseManager
import Services.*
import Controllers.UserManagementController
import Process.Init
import com.comcast.ip4s.*

object UserManagementServiceApp extends IOApp {
  private val logger = LoggerFactory.getLogger("UserManagementServiceApp")

  override def run(args: List[String]): IO[ExitCode] = {
    logger.info("启动用户管理服务...")

    for {
      // 加载配置
      config <- IO(ConfigLoader.loadConfig())
      _ = logger.info(s"配置加载完成: ${config.serverIP}:${config.serverPort}")

      // 初始化数据库连接池
      _ <- DatabaseManager.initializeDataSource(config.toDatabaseConfig)
      
      // 初始化数据库表
      _ <- Init.initializeDatabase()

      // 创建服务实例
      userManagementService = new UserManagementServiceImpl()
      coachStudentService = new CoachStudentServiceImpl()
      authMiddleware = new AuthMiddlewareServiceImpl(config)

      // 创建控制器
      controller = new UserManagementController(
        userManagementService,
        coachStudentService,
        authMiddleware
      )

      // 启动HTTP服务器
      _ <- EmberServerBuilder.default[IO]
        .withHost(Host.fromString(config.serverIP).getOrElse(ipv4"0.0.0.0"))
        .withPort(Port.fromInt(config.serverPort).getOrElse(port"3002"))
        .withHttpApp(controller.routes.orNotFound)
        .build
        .use(_ => IO.never)
        .onError { error =>
          IO {
            logger.error("服务器启动失败", error)
          }
        }
    } yield ExitCode.Success
  }.handleErrorWith { error =>
    IO {
      logger.error("用户管理服务启动失败", error)
      ExitCode.Error
    }
  }
}
