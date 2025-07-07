package Main

import cats.effect.{IO, IOApp, ExitCode}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.ember.client.EmberClientBuilder
import org.slf4j.LoggerFactory
import Config.{ConfigLoader, ServiceConfig}
import Database.DatabaseManager
import Services.*
import Controllers.ExamController
import Process.Init
import com.comcast.ip4s.*

object ExamManagementServiceApp extends IOApp {
  private val logger = LoggerFactory.getLogger("ExamManagementServiceApp")

  override def run(args: List[String]): IO[ExitCode] = {
    logger.info("启动考试管理服务...")

    val app = for {
      // 加载配置
      config <- IO(ConfigLoader.loadConfig())
      _ = logger.info(s"配置加载完成: ${config.serverIP}:${config.serverPort}")

      // 初始化数据库连接池
      _ <- DatabaseManager.initializeDataSource(config.toDatabaseConfig)
      
      // 初始化数据库表和数据
      _ <- Init.performStartupTasks()

      // 创建HTTP客户端
      httpClient <- EmberClientBuilder.default[IO].build.use { client =>
        
        // 创建服务实例
        val examService = new ExamServiceImpl()
        val questionService = new QuestionServiceImpl()
        val submissionService = new SubmissionServiceImpl()
        val authService = new HttpAuthService(config) // Use HTTP-based auth service
        val fileStorageService = new FileStorageServiceImpl(config, client)

        // 创建控制器
        val examController = new ExamController(
          examService,
          questionService,
          submissionService,
          authService,
          fileStorageService
        )

        // 启动HTTP服务器
        EmberServerBuilder.default[IO]
          .withHost(Host.fromString(config.serverIP).getOrElse(ipv4"0.0.0.0"))
          .withPort(Port.fromInt(config.serverPort).getOrElse(port"3003"))
          .withHttpApp(examController.routes.orNotFound)
          .build
          .use { server =>
            logger.info(s"考试管理服务已启动，监听端口: ${server.address}")
            logger.info("服务详情:")
            logger.info(s"  - 服务名称: ExamManagementService")
            logger.info(s"  - 服务端口: ${config.serverPort}")
            logger.info(s"  - 数据库: ${config.jdbcUrl}")
            logger.info(s"  - 文件存储服务: ${config.fileStorageService.host}:${config.fileStorageService.port}")
            logger.info("  - 可用API端点:")
            logger.info("    * Admin APIs: /api/admin/exams")
            logger.info("    * Student APIs: /api/student/exams")
            logger.info("    * Coach APIs: /api/coach/exams")
            logger.info("    * Grader APIs: /api/grader/exams")
            logger.info("    * Upload APIs: /api/upload/")
            logger.info("服务已准备就绪，等待请求...")
            IO.never
          }
      }
    } yield ExitCode.Success

    app.handleErrorWith { error =>
      logger.error("考试管理服务启动失败", error)
      DatabaseManager.shutdown().flatMap(_ => IO.pure(ExitCode.Error))
    }
  }
}
