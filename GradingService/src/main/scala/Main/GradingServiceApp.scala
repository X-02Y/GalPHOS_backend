package Main

import cats.effect.{IO, IOApp, ExitCode}
import org.http4s.ember.server.EmberServerBuilder
import org.slf4j.LoggerFactory
import Config.{ConfigLoader, ServiceConfig}
import Database.DatabaseManager
import Services.*
import Controllers.GradingController
import Process.Init
import com.comcast.ip4s.*

object GradingServiceApp extends IOApp {
  private val logger = LoggerFactory.getLogger("GradingServiceApp")

  override def run(args: List[String]): IO[ExitCode] = {
    logger.info("启动阅卷管理服务...")

    for {
      // 加载配置
      config <- IO(ConfigLoader.loadConfig())
      _ = logger.info(s"配置加载完成: ${config.serverIP}:${config.serverPort}")

      // 初始化数据库连接池
      _ <- DatabaseManager.initializeDataSource(config.toDatabaseConfig)
      
      // 初始化数据库表
      _ <- Init.initializeDatabase()

      // 创建服务实例
      externalServiceClient = new ExternalServiceClient()
      graderService = new GraderService()
      gradingTaskService = new GradingTaskService()
      questionScoreService = new QuestionScoreService()
      coachStudentService = new CoachStudentService()
      gradingImageService = new GradingImageService(externalServiceClient)

      // 创建控制器
      gradingController = new GradingController(
        graderService,
        gradingTaskService,
        questionScoreService,
        coachStudentService,
        gradingImageService
      )

      // 创建HTTP应用
      httpApp = gradingController.allRoutes.orNotFound

      // 启动服务器
      _ <- EmberServerBuilder.default[IO]
        .withHost(ipv4"0.0.0.0")
        .withPort(Port.fromInt(config.serverPort).getOrElse(port"3005"))
        .withHttpApp(httpApp)
        .build
        .use { server =>
          logger.info(s"阅卷管理服务已启动: http://${config.serverIP}:${config.serverPort}")
          logger.info("服务支持的API接口:")
          logger.info("- 管理员阅卷员管理: GET /api/admin/graders")
          logger.info("- 管理员阅卷任务分配: POST /api/admin/grading/assign")
          logger.info("- 管理员阅卷进度监控: GET /api/admin/grading/progress/{examId}")
          logger.info("- 管理员阅卷任务管理: GET /api/admin/grading/tasks")
          logger.info("- 管理员题目分数管理: GET/POST /api/admin/exams/{examId}/question-scores")
          logger.info("- 管理员单题分数更新: PUT /api/admin/exams/{examId}/question-scores/{questionNumber}")
          logger.info("- 阅卷员任务列表: GET /api/grader/tasks")
          logger.info("- 阅卷员任务详情: GET /api/grader/tasks/{taskId}")
          logger.info("- 开始阅卷任务: POST /api/grader/tasks/{taskId}/start")
          logger.info("- 提交阅卷结果: POST /api/grader/tasks/{taskId}/submit")
          logger.info("- 放弃阅卷任务: POST /api/grader/tasks/{taskId}/abandon")
          logger.info("- 保存阅卷进度: POST /api/grader/tasks/{taskId}/save-progress")
          logger.info("- 阅卷过程管理: POST /api/grader/tasks/{taskId}/questions/{questionNumber}/score")
          logger.info("- 评分历史: GET /api/grader/tasks/{taskId}/questions/{questionNumber}/history")
          logger.info("- 阅卷员查看考试题目分数: GET /api/grader/exams/{examId}/questions/scores")
          logger.info("- 教练非独立学生列表: GET /api/coach/students")
          logger.info("- 教练非独立学生详情: GET /api/coach/students/{studentId}")
          logger.info("- 创建教练非独立学生: POST /api/coach/students")
          logger.info("- 更新教练非独立学生: PUT /api/coach/students/{studentId}")
          logger.info("- 删除教练非独立学生: DELETE /api/coach/students/{studentId}")
          logger.info("按 Ctrl+C 停止服务")
          IO.never
        }
    } yield ExitCode.Success
  }.handleErrorWith { error =>
    IO {
      logger.error("阅卷管理服务启动失败", error)
      ExitCode.Error
    }
  }
}
