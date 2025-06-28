package Process

import cats.effect.*
import cats.implicits.*
import io.circe.*
import io.circe.parser.*
import io.circe.generic.auto.*
import Config.ServerConfig
import Database.DatabaseManager
import Services.*
import Controllers.*
import org.slf4j.LoggerFactory
import java.nio.file.{Files, Paths}

object Init {
  private val logger = LoggerFactory.getLogger("Init")

  def init(config: ServerConfig): IO[ExamManagementController] = {
    for {
      _ <- IO(logger.info("初始化考试管理服务..."))
      
      // 初始化数据库连接
      _ <- DatabaseManager.initialize(config.toDatabaseConfig)
      _ <- IO(logger.info("数据库连接池初始化完成"))
      
      // 初始化服务
      authService = new AuthService(config)
      examService = new ExamService()
      fileService = new FileService(config)
      _ <- IO(logger.info("服务初始化完成"))
      
      // 初始化控制器
      studentController = new StudentController(examService, authService)
      coachController = new CoachController(examService, authService, fileService)
      graderController = new GraderController(examService, authService)
      adminController = new AdminController(examService, authService, fileService)
      
      examController = new ExamManagementController(
        studentController,
        coachController,
        graderController,
        adminController
      )
      _ <- IO(logger.info("控制器初始化完成"))
      
      _ <- IO(logger.info("考试管理服务初始化完成"))
    } yield examController
  }
}

object ProcessUtils {
  private val logger = LoggerFactory.getLogger("ProcessUtils")

  def readConfig(filename: String): IO[ServerConfig] = {
    for {
      configPath <- IO(Paths.get(filename))
      exists <- IO(Files.exists(configPath))
      _ <- if (!exists) {
        IO.raiseError(new RuntimeException(s"配置文件不存在: $filename"))
      } else IO.unit
      
      content <- IO(Files.readString(configPath))
      config <- IO.fromEither(parse(content).flatMap(_.as[ServerConfig]))
        .adaptError { case error =>
          new RuntimeException(s"解析配置文件失败: ${error.getMessage}", error)
        }
      
      _ <- IO(logger.info(s"配置文件加载成功: $filename"))
    } yield config
  }
}
