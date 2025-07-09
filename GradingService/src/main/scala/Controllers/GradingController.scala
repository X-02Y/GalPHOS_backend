package Controllers

import Models.*
import Models.Implicits.given
import Services.*
import Utils.AuthUtils
import cats.effect.IO
import cats.implicits.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.*
import io.circe.*
import io.circe.syntax.*
import io.circe.generic.auto.*
import org.slf4j.LoggerFactory

class GradingController(
  graderService: GraderService,
  gradingTaskService: GradingTaskService,
  questionScoreService: QuestionScoreService,
  coachStudentService: CoachStudentService,
  gradingImageService: GradingImageService
) {
  private val logger = LoggerFactory.getLogger("GradingController")

  // CORS 支持
  private val corsHeaders = Headers(
    "Access-Control-Allow-Origin" -> "*",
    "Access-Control-Allow-Methods" -> "GET, POST, PUT, DELETE, OPTIONS",
    "Access-Control-Allow-Headers" -> "Content-Type, Authorization"
  )

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    
    // CORS 预检请求
    case req @ OPTIONS -> _ =>
      Ok().map(_.withHeaders(corsHeaders))
    
    // 管理员阅卷员管理
    case GET -> Root / "api" / "admin" / "graders" =>
      graderService.getAllGraders().flatMap { graders =>
        Ok(ApiResponse(
          success = true,
          message = "获取阅卷员列表成功",
          data = Some(graders)
        ).asJson)
      }.handleErrorWith { error =>
        logger.error("获取阅卷员列表失败", error)
        InternalServerError(ApiResponse[List[GraderInfo]](
          success = false,
          message = s"获取阅卷员列表失败: ${error.getMessage}"
        ).asJson)
      }.map(_.withHeaders(corsHeaders))

    // 管理员创建已结束考试的阅卷任务
    case req @ POST -> Root / "api" / "admin" / "grading" / "create-tasks" =>
      gradingTaskService.createGradingTasksForCompletedExams().flatMap { createdCount =>
        Ok(ApiResponse(
          success = true,
          message = s"成功为已结束考试创建 $createdCount 个阅卷任务",
          data = Some(createdCount)
        ).asJson)
      }.handleErrorWith { error =>
        logger.error("创建阅卷任务失败", error)
        InternalServerError(ApiResponse[Int](
          success = false,
          message = s"创建阅卷任务失败: ${error.getMessage}"
        ).asJson)
      }.map(_.withHeaders(corsHeaders))

    // 管理员阅卷任务分配
    case req @ POST -> Root / "api" / "admin" / "grading" / "assign" =>
      req.decode[TaskAssignmentRequest] { request =>
        gradingTaskService.assignTasks(request).flatMap { affected =>
          if (affected > 0) {
            Ok(ApiResponse(
              success = true,
              message = s"成功分配 $affected 个阅卷任务",
              data = Some(affected)
            ).asJson)
          } else {
            BadRequest(ApiResponse[Int](
              success = false,
              message = "没有找到可分配的阅卷任务"
            ).asJson)
          }
        }
      }.handleErrorWith { error =>
        logger.error("阅卷任务分配失败", error)
        InternalServerError(ApiResponse[Int](
          success = false,
          message = s"阅卷任务分配失败: ${error.getMessage}"
        ).asJson)
      }

    // 管理员阅卷进度监控
    case GET -> Root / "api" / "admin" / "grading" / "progress" / examId =>
      gradingTaskService.getGradingProgress(examId).flatMap { progress =>
        Ok(ApiResponse(
          success = true,
          message = "获取阅卷进度成功",
          data = Some(progress)
        ).asJson)
      }.handleErrorWith { error =>
        logger.error(s"获取考试 $examId 阅卷进度失败", error)
        InternalServerError(ApiResponse[GradingProgress](
          success = false,
          message = s"获取阅卷进度失败: ${error.getMessage}"
        ).asJson)
      }

    // 管理员阅卷任务管理
    case GET -> Root / "api" / "admin" / "grading" / "tasks" =>
      gradingTaskService.getAllTasksForAdmin().flatMap { tasks =>
        Ok(ApiResponse(
          success = true,
          message = "获取阅卷任务列表成功",
          data = Some(tasks)
        ).asJson)
      }.handleErrorWith { error =>
        logger.error("获取阅卷任务列表失败", error)
        InternalServerError(ApiResponse[List[AdminGradingTask]](
          success = false,
          message = s"获取阅卷任务列表失败: ${error.getMessage}"
        ).asJson)
      }

    // 管理员题目分数设置和查看
    case GET -> Root / "api" / "admin" / "exams" / LongVar(examId) / "question-scores" =>
      questionScoreService.getQuestionScores(examId).flatMap { scores =>
        Ok(ApiResponse(
          success = true,
          message = "获取题目分数配置成功",
          data = Some(scores)
        ).asJson)
      }.handleErrorWith { error =>
        logger.error(s"获取考试 $examId 题目分数配置失败", error)
        InternalServerError(ApiResponse[List[QuestionScore]](
          success = false,
          message = s"获取题目分数配置失败: ${error.getMessage}"
        ).asJson)
      }

    case req @ POST -> Root / "api" / "admin" / "exams" / LongVar(examId) / "question-scores" =>
      req.decode[List[QuestionScoreRequest]] { scores =>
        questionScoreService.createOrUpdateQuestionScores(examId, scores).flatMap { affected =>
          Ok(ApiResponse(
            success = true,
            message = s"成功设置 $affected 个题目分数",
            data = Some(affected)
          ).asJson)
        }
      }.handleErrorWith { error =>
        logger.error(s"设置考试 $examId 题目分数失败", error)
        InternalServerError(ApiResponse[Int](
          success = false,
          message = s"设置题目分数失败: ${error.getMessage}"
        ).asJson)
      }

    // 管理员单题分数更新
    case req @ PUT -> Root / "api" / "admin" / "exams" / LongVar(examId) / "question-scores" / IntVar(questionNumber) =>
      req.decode[UpdateQuestionScoreRequest] { request =>
        questionScoreService.updateQuestionScore(examId, questionNumber, request).flatMap { affected =>
          if (affected > 0) {
            Ok(ApiResponse(
              success = true,
              message = "题目分数更新成功",
              data = Some(affected)
            ).asJson)
          } else {
            NotFound(ApiResponse[Int](
              success = false,
              message = "未找到指定的题目分数配置"
            ).asJson)
          }
        }
      }.handleErrorWith { error =>
        logger.error(s"更新考试 $examId 题目 $questionNumber 分数失败", error)
        InternalServerError(ApiResponse[Int](
          success = false,
          message = s"更新题目分数失败: ${error.getMessage}"
        ).asJson)
      }

    // 阅卷员任务列表
    case GET -> Root / "api" / "grader" / "tasks" :? GraderIdQueryParam(graderIdOpt) =>
      graderIdOpt match {
        case Some(graderId) =>
          gradingTaskService.getTasksByGrader(graderId).flatMap { tasks =>
            Ok(ApiResponse(
              success = true,
              message = "获取阅卷任务列表成功",
              data = Some(tasks)
            ).asJson)
          }
        case None =>
          // 如果没有graderId参数，返回所有任务（管理员视图）
          gradingTaskService.getAllTasks().flatMap { tasks =>
            Ok(ApiResponse(
              success = true,
              message = "获取所有阅卷任务列表成功",
              data = Some(tasks)
            ).asJson)
          }
      }

    // 阅卷员任务详情
    case GET -> Root / "api" / "grader" / "tasks" / LongVar(taskId) =>
      gradingTaskService.getTaskById(taskId).flatMap {
        case Some(task) =>
          Ok(ApiResponse(
            success = true,
            message = "获取阅卷任务详情成功",
            data = Some(task)
          ).asJson)
        case None =>
          NotFound(ApiResponse[GradingTask](
            success = false,
            message = "未找到指定的阅卷任务"
          ).asJson)
      }

    // 开始阅卷任务
    case req @ POST -> Root / "api" / "grader" / "tasks" / LongVar(taskId) / "start" =>
      gradingTaskService.startTask(taskId).flatMap { affected =>
        if (affected > 0) {
          Ok(ApiResponse(
            success = true,
            message = "开始阅卷任务成功",
            data = Some(affected)
          ).asJson)
        } else {
          BadRequest(ApiResponse[Int](
            success = false,
            message = "任务状态不允许开始阅卷"
          ).asJson)
        }
      }.handleErrorWith { error =>
        logger.error(s"开始阅卷任务 $taskId 失败", error)
        InternalServerError(ApiResponse[Int](
          success = false,
          message = s"开始阅卷任务失败: ${error.getMessage}"
        ).asJson)
      }

    // 提交阅卷结果
    case req @ POST -> Root / "api" / "grader" / "tasks" / LongVar(taskId) / "submit" =>
      req.decode[SubmitGradingRequest] { request =>
        gradingTaskService.submitTask(request).flatMap { affected =>
          if (affected > 0) {
            Ok(ApiResponse(
              success = true,
              message = "提交阅卷结果成功",
              data = Some(affected)
            ).asJson)
          } else {
            BadRequest(ApiResponse[Int](
              success = false,
              message = "提交阅卷结果失败"
            ).asJson)
          }
        }
      }.handleErrorWith { error =>
        logger.error(s"提交阅卷任务 $taskId 结果失败", error)
        InternalServerError(ApiResponse[Int](
          success = false,
          message = s"提交阅卷结果失败: ${error.getMessage}"
        ).asJson)
      }

    // 放弃阅卷任务
    case req @ POST -> Root / "api" / "grader" / "tasks" / LongVar(taskId) / "abandon" =>
      gradingTaskService.abandonTask(taskId).flatMap { affected =>
        if (affected > 0) {
          Ok(ApiResponse(
            success = true,
            message = "放弃阅卷任务成功",
            data = Some(affected)
          ).asJson)
        } else {
          BadRequest(ApiResponse[Int](
            success = false,
            message = "任务状态不允许放弃"
          ).asJson)
        }
      }.handleErrorWith { error =>
        logger.error(s"放弃阅卷任务 $taskId 失败", error)
        InternalServerError(ApiResponse[Int](
          success = false,
          message = s"放弃阅卷任务失败: ${error.getMessage}"
        ).asJson)
      }

    // 保存阅卷进度
    case req @ POST -> Root / "api" / "grader" / "tasks" / LongVar(taskId) / "save-progress" =>
      req.decode[SaveProgressRequest] { request =>
        gradingTaskService.saveProgress(request).flatMap { affected =>
          Ok(ApiResponse(
            success = true,
            message = "保存阅卷进度成功",
            data = Some(affected)
          ).asJson)
        }
      }.handleErrorWith { error =>
        logger.error(s"保存阅卷任务 $taskId 进度失败", error)
        InternalServerError(ApiResponse[Int](
          success = false,
          message = s"保存阅卷进度失败: ${error.getMessage}"
        ).asJson)
      }

    // 阅卷员查看考试题目分数
    case GET -> Root / "api" / "grader" / "exams" / LongVar(examId) / "questions" / "scores" =>
      questionScoreService.getQuestionScores(examId).flatMap { scores =>
        Ok(ApiResponse(
          success = true,
          message = "获取考试题目分数成功",
          data = Some(scores)
        ).asJson)
      }.handleErrorWith { error =>
        logger.error(s"阅卷员获取考试 $examId 题目分数失败", error)
        InternalServerError(ApiResponse[List[QuestionScore]](
          success = false,
          message = s"获取考试题目分数失败: ${error.getMessage}"
        ).asJson)
      }

    // 题目评分
    case req @ POST -> Root / "api" / "grader" / "tasks" / LongVar(taskId) / "questions" / IntVar(questionNumber) / "score" =>
      req.decode[SaveProgressRequest] { request =>
        gradingTaskService.saveProgress(request).flatMap { affected =>
          Ok(ApiResponse(
            success = true,
            message = "题目评分保存成功",
            data = Some(affected)
          ).asJson)
        }
      }.handleErrorWith { error =>
        logger.error(s"保存题目评分失败 - 任务:$taskId, 题目:$questionNumber", error)
        InternalServerError(ApiResponse[Int](
          success = false,
          message = s"保存题目评分失败: ${error.getMessage}"
        ).asJson)
      }

    // 评分历史
    case GET -> Root / "api" / "grader" / "tasks" / LongVar(taskId) / "questions" / IntVar(questionNumber) / "history" =>
      gradingTaskService.getScoreHistory(taskId, questionNumber).flatMap { history =>
        Ok(ApiResponse(
          success = true,
          message = "获取评分历史成功",
          data = Some(history)
        ).asJson)
      }.handleErrorWith { error =>
        logger.error(s"获取评分历史失败 - 任务:$taskId, 题目:$questionNumber", error)
        InternalServerError(ApiResponse[List[ScoreHistory]](
          success = false,
          message = s"获取评分历史失败: ${error.getMessage}"
        ).asJson)
      }

    // 阅卷图片管理 - 获取阅卷图片
    case req @ GET -> Root / "api" / "grader" / "images" :? ExamIdQueryParam(examIdOpt) +& StudentIdQueryParam(studentIdOpt) +& QuestionNumberQueryParam(questionNumberOpt) =>
      examIdOpt match {
        case Some(examId) =>
          // 从请求头获取阅卷员ID（实际项目中应该从JWT token解析）
          val graderIdOpt = req.headers.get[org.http4s.headers.Authorization].flatMap { authHeader =>
            // 这里应该解析JWT token获取graderId，暂时使用模拟值
            Some(1L) // 模拟阅卷员ID
          }
          
          graderIdOpt match {
            case Some(graderId) =>
              // 验证阅卷员权限
              gradingImageService.validateGraderAccess(graderId, examId).flatMap { hasAccess =>
                if (hasAccess) {
                  // 获取图片数据（带内容）
                  gradingImageService.getGradingImagesWithContent(examId, studentIdOpt, questionNumberOpt).flatMap { images =>
                    logger.info(s"阅卷员 $graderId 获取了 ${images.length} 张阅卷图片")
                    Ok(ApiResponse(
                      success = true,
                      message = "获取阅卷图片成功",
                      data = Some(images.map { img =>
                        Map(
                          "imageUrl" -> img.imageUrl,
                          "fileName" -> img.fileName,
                          "examId" -> img.examId.toString,
                          "studentId" -> img.studentId.toString,
                          "questionNumber" -> img.questionNumber.toString,
                          "uploadTime" -> img.uploadTime.toString,
                          "base64Content" -> img.base64Content.getOrElse("")
                        )
                      })
                    ).asJson)
                  }.handleErrorWith { error =>
                    logger.error(s"获取阅卷图片失败 - examId:$examId", error)
                    InternalServerError(ApiResponse[List[Map[String, String]]](
                      success = false,
                      message = s"获取阅卷图片失败: ${error.getMessage}"
                    ).asJson)
                  }
                } else {
                  Forbidden(ApiResponse[List[Map[String, String]]](
                    success = false,
                    message = "无权限访问该考试的图片"
                  ).asJson)
                }
              }
            case None =>
              Forbidden(ApiResponse[List[Map[String, String]]](
                success = false,
                message = "缺少认证信息"
              ).asJson)
          }
        case None =>
          BadRequest(ApiResponse[List[Map[String, String]]](
            success = false,
            message = "缺少必需的examId参数"
          ).asJson)
      }
  }

  // 查询参数提取器
  object GraderIdQueryParam extends OptionalQueryParamDecoderMatcher[String]("graderId")
  object CoachIdQueryParam extends OptionalQueryParamDecoderMatcher[Long]("coachId")
  object ExamIdQueryParam extends OptionalQueryParamDecoderMatcher[Long]("examId")
  object StudentIdQueryParam extends OptionalQueryParamDecoderMatcher[Long]("studentId")
  object QuestionNumberQueryParam extends OptionalQueryParamDecoderMatcher[Int]("questionNumber")

  // 健康检查端点
  val healthRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "health" =>
      Ok(io.circe.Json.obj(
        "status" -> io.circe.Json.fromString("healthy"),
        "service" -> io.circe.Json.fromString("GradingService"),
        "timestamp" -> io.circe.Json.fromString(java.time.Instant.now().toString)
      ))
  }

  // 合并所有路由
  val allRoutes: HttpRoutes[IO] = routes <+> healthRoutes
  
  // 添加CORS支持
  def allRoutesWithCORS: HttpRoutes[IO] = {
    allRoutes.map { response =>
      response.copy(headers = response.headers ++ corsHeaders)
    }
  }
}
