package Process

import cats.effect.{IO, IOApp, ExitCode}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router
import com.comcast.ip4s.*
import org.slf4j.LoggerFactory
import Controllers.SubmissionController
import Services.{AuthService, ExamService, FileStorageService, SubmissionService}
import Database.DatabaseManager
import Config.ServerConfig

object Server extends IOApp {
  private val logger = LoggerFactory.getLogger("SubmissionServer")

  def run(args: List[String]): IO[ExitCode] = {
    for {
      _ <- IO(logger.info("启动答题提交服务..."))
      config <- IO(ServerConfig.loadConfig())
      _ <- IO(logger.info(s"服务配置加载成功，端口: ${config.serverPort}"))
      
      // 初始化数据库
      _ <- DatabaseManager.initialize(config)
      _ <- IO(logger.info("数据库连接初始化成功"))
      
      // 初始化服务
      authService = new AuthService(config)
      examService = new ExamService(config)
      fileStorageService = new FileStorageService(config)
      submissionService = new SubmissionService(authService, examService, fileStorageService)
      
      // 初始化控制器
      submissionController = new SubmissionController(submissionService)
      
      // 配置路由
      httpApp = Router(
        "/" -> submissionController.routes
      ).orNotFound
      
      _ <- IO(logger.info(s"在端口 ${config.serverPort} 启动HTTP服务器"))
      
      // 启动服务器
      exitCode <- EmberServerBuilder
        .default[IO]
        .withHost(Host.fromString(config.serverIP).getOrElse(host"localhost"))
        .withPort(Port.fromInt(config.serverPort).getOrElse(port"3004"))
        .withHttpApp(httpApp)
        .build
        .use { server =>
          IO(logger.info(s"答题提交服务已启动: http://${config.serverIP}:${config.serverPort}")) *>
          IO.never
        }
        .as(ExitCode.Success)
    } yield exitCode
  }.handleErrorWith { error =>
    IO(logger.error("服务启动失败", error)) *>
    DatabaseManager.shutdown() *>
    IO.pure(ExitCode.Error)
  }
}
