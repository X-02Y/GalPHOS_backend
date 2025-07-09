package Controllers

import cats.effect.IO
import cats.implicits.*
import io.circe.*
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.headers.Authorization
import org.http4s.util.CaseInsensitiveString
import org.slf4j.LoggerFactory
import Models.*
import Services.*
import java.time.LocalDateTime
import java.util.Base64

class ExamController(
  examService: ExamService,
  questionService: QuestionService,
  submissionService: SubmissionService,
  authService: AuthService,
  fileStorageService: FileStorageService
) {
  private val logger = LoggerFactory.getLogger("ExamController")

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

    // ===================== 临时测试API（无认证） =====================
    
    // 测试获取考试列表（无认证）
    case req @ GET -> Root / "api" / "test" / "exams" =>
      handleGetExamList().map(_.withHeaders(corsHeaders))

    // ===================== 管理员考试管理 API =====================
    
    // 获取考试列表
    case req @ GET -> Root / "api" / "admin" / "exams" =>
      authenticateAdmin(req) { _ =>
        handleGetExamList()
      }.map(_.withHeaders(corsHeaders))

    // 创建考试
    case req @ POST -> Root / "api" / "admin" / "exams" =>
      authenticateAdmin(req) { user =>
        handleCreateExam(req, user.username)
      }.map(_.withHeaders(corsHeaders))

    // 获取特定考试
    case req @ GET -> Root / "api" / "admin" / "exams" / examId =>
      authenticateAdmin(req) { _ =>
        handleGetExamById(examId)
      }.map(_.withHeaders(corsHeaders))

    // 更新考试
    case req @ PUT -> Root / "api" / "admin" / "exams" / examId =>
      authenticateAdmin(req) { _ =>
        handleUpdateExam(req, examId)
      }.map(_.withHeaders(corsHeaders))

    // 删除考试
    case req @ DELETE -> Root / "api" / "admin" / "exams" / examId =>
      authenticateAdmin(req) { _ =>
        handleDeleteExam(examId)
      }.map(_.withHeaders(corsHeaders))

    // 设置问题分数
    case req @ POST -> Root / "api" / "admin" / "exams" / examId / "question-scores" =>
      authenticateAdmin(req) { _ =>
        handleSetQuestionScores(req, examId)
      }.map(_.withHeaders(corsHeaders))

    // 获取问题分数
    case req @ GET -> Root / "api" / "admin" / "exams" / examId / "question-scores" =>
      authenticateAdmin(req) { _ =>
        handleGetQuestionScores(examId)
      }.map(_.withHeaders(corsHeaders))

    // 更新单个问题分数
    case req @ PUT -> Root / "api" / "admin" / "exams" / examId / "question-scores" / IntVar(questionNumber) =>
      authenticateAdmin(req) { _ =>
        handleUpdateQuestionScore(req, examId, questionNumber)
      }.map(_.withHeaders(corsHeaders))

    // 发布考试
    case req @ POST -> Root / "api" / "admin" / "exams" / examId / "publish" =>
      authenticateAdmin(req) { _ =>
        handlePublishExam(req, examId)
      }.map(_.withHeaders(corsHeaders))

    // 取消发布考试
    case req @ POST -> Root / "api" / "admin" / "exams" / examId / "unpublish" =>
      authenticateAdmin(req) { _ =>
        handleUnpublishExam(examId)
      }.map(_.withHeaders(corsHeaders))

    // 更新考试状态
    case req @ POST -> Root / "api" / "admin" / "exams" / "update-statuses" =>
      authenticateAdmin(req) { _ =>
        handleUpdateExamStatuses()
      }.map(_.withHeaders(corsHeaders))

    // 预申请考试ID（用于文件上传）
    case req @ POST -> Root / "api" / "admin" / "exams" / "reserve" =>
      authenticateAdmin(req) { user =>
        handleReserveExamId(user.username)
      }.map(_.withHeaders(corsHeaders))

    // 删除预申请的考试ID
    case req @ DELETE -> Root / "api" / "admin" / "exams" / "reserve" / examId =>
      authenticateAdmin(req) { user =>
        handleDeleteReservedExamId(examId, user.username)
      }.map(_.withHeaders(corsHeaders))

    // 上传考试文件（特定考试ID）
    case req @ POST -> Root / "api" / "admin" / "exams" / examId / "upload" =>
      authenticateAdmin(req) { user =>
        handleUploadExamFileForExam(req, user, examId)
      }.map(_.withHeaders(corsHeaders))
      
    // 上传考试文件（新路径）
    case req @ POST -> Root / "api" / "admin" / "exams" / examId / "files" =>
      authenticateAdmin(req) { user =>
        handleUploadExamFileForExam(req, user, examId)
      }.map(_.withHeaders(corsHeaders))

    // 上传试题文件
    case req @ POST -> Root / "api" / "admin" / "exams" / examId / "upload" / "paper" =>
      authenticateAdmin(req) { user =>
        handleUploadSpecificFileType(req, user, examId, "question")
      }.map(_.withHeaders(corsHeaders))

    // 上传答案文件
    case req @ POST -> Root / "api" / "admin" / "exams" / examId / "upload" / "answer" =>
      authenticateAdmin(req) { user =>
        handleUploadSpecificFileType(req, user, examId, "answer")
      }.map(_.withHeaders(corsHeaders))

    // 上传答题卡文件
    case req @ POST -> Root / "api" / "admin" / "exams" / examId / "upload" / "answer-sheet" =>
      authenticateAdmin(req) { user =>
        handleUploadSpecificFileType(req, user, examId, "answerSheet")
      }.map(_.withHeaders(corsHeaders))

    // ===================== 学生考试管理 API =====================

    // 获取可用考试
    case req @ GET -> Root / "api" / "student" / "exams" =>
      authenticateStudent(req) { _ =>
        handleGetAvailableExams()
      }.map(_.withHeaders(corsHeaders))

    // 获取考试详情
    case req @ GET -> Root / "api" / "student" / "exams" / examId =>
      authenticateStudent(req) { _ =>
        handleGetExamDetails(examId)
      }.map(_.withHeaders(corsHeaders))

    // 提交考试答案
    case req @ POST -> Root / "api" / "student" / "exams" / examId / "submit" =>
      authenticateStudent(req) { user =>
        handleSubmitAnswers(req, examId, user.username)
      }.map(_.withHeaders(corsHeaders))

    // 获取提交状态
    case req @ GET -> Root / "api" / "student" / "exams" / examId / "submission" =>
      logger.info(s"Received GET request for submission status: examId = $examId")
      authenticateStudent(req) { user =>
        logger.info(s"Authentication successful for student: ${user.username}, proceeding to get submission status")
        handleGetSubmissionStatus(examId, user.username)
      }.map(_.withHeaders(corsHeaders))

    // 学生上传答题图片
    case req @ POST -> Root / "api" / "student" / "exams" / examId / "upload-answer" =>
      authenticateStudent(req) { user =>
        handleStudentUploadAnswerImage(req, user, examId)
      }.map(_.withHeaders(corsHeaders))

    // ===================== 评卷员考试管理 API =====================

    // 获取可阅卷考试
    case req @ GET -> Root / "api" / "grader" / "exams" =>
      authenticateGrader(req) { _ =>
        handleGetGradableExams(req)
      }.map(_.withHeaders(corsHeaders))

    // 获取考试详情（阅卷视角）
    case req @ GET -> Root / "api" / "grader" / "exams" / examId =>
      authenticateGrader(req) { _ =>
        handleGetExamForGrading(examId)
      }.map(_.withHeaders(corsHeaders))

    // 获取阅卷进度
    case req @ GET -> Root / "api" / "grader" / "exams" / examId / "progress" =>
      authenticateGrader(req) { _ =>
        handleGetGradingProgress(examId)
      }.map(_.withHeaders(corsHeaders))

    // ===================== 文件上传 API =====================

    // 上传考试文件
    case req @ POST -> Root / "api" / "upload" / "exam-files" =>
      authenticateAdmin(req) { user =>
        handleUploadExamFiles(req, user)
      }.map(_.withHeaders(corsHeaders))

    // 上传答案图片
    case req @ POST -> Root / "api" / "upload" / "answer-image" =>
      authenticateUser(req) { user =>
        handleUploadAnswerImage(req, user)
      }.map(_.withHeaders(corsHeaders))

    // ===================== 教练考试管理 API =====================

    // 获取教练可见的考试列表
    case req @ GET -> Root / "api" / "coach" / "exams" =>
      authenticateCoach(req) { _ =>
        handleGetCoachExams()
      }.map(_.withHeaders(corsHeaders))

    // 获取考试详情（教练视角）
    case req @ GET -> Root / "api" / "coach" / "exams" / examId =>
      authenticateCoach(req) { _ =>
        handleGetExamDetails(examId)
      }.map(_.withHeaders(corsHeaders))

    // 教练上传答案图片
    case req @ POST -> Root / "api" / "coach" / "exams" / examId / "upload-answer" =>
      authenticateCoach(req) { user =>
        handleCoachUploadAnswerImage(req, user)
      }.map(_.withHeaders(corsHeaders))

    // ===================== 内部 API =====================
    
    // 评卷员内部API - 获取考试信息
    case req @ GET -> Root / "api" / "internal" / "grader" / "exams" =>
      authenticateInternalApiKey(req) { _ =>
        handleGetGraderExamInfo()
      }.map(_.withHeaders(corsHeaders))

    // ===================== 测试 API =====================
    
    // 测试 API - 绕过认证（管理员端）
    case req @ GET -> Root / "api" / "test" / "exams" =>
      examService.getExamsForAdmin().flatMap { exams =>
        Ok(ApiResponse.success(exams, "考试列表获取成功"))
      }.handleErrorWith { error =>
        logger.error("获取考试列表失败", error)
        InternalServerError(ApiResponse.error("获取考试列表失败"))
      }.map(_.withHeaders(corsHeaders))

    // 测试学生端 API - 绕过认证
    case req @ GET -> Root / "api" / "test" / "student-exams" =>
      examService.getExamsByStatus(ExamStatus.Published).flatMap { exams =>
        Ok(ApiResponse.success(exams, "学生考试列表获取成功"))
      }.handleErrorWith { error =>
        logger.error("获取学生考试列表失败", error)
        InternalServerError(ApiResponse.error("获取学生考试列表失败"))
      }.map(_.withHeaders(corsHeaders))

    // 测试教练端 API - 绕过认证
    case req @ GET -> Root / "api" / "test" / "coach-exams" =>
      examService.getExamsByStatus(ExamStatus.Published).flatMap { exams =>
        Ok(ApiResponse.success(exams, "教练考试列表获取成功"))
      }.handleErrorWith { error =>
        logger.error("获取教练考试列表失败", error)
        InternalServerError(ApiResponse.error("获取教练考试列表失败"))
      }.map(_.withHeaders(corsHeaders))

    // ===================== 健康检查 API =====================
    
    // 健康检查
    case GET -> Root / "health" =>
      Ok(ApiResponse.success("OK")).map(_.withHeaders(corsHeaders))
      
    // 捕获所有未匹配的请求用于调试
    case req =>
      logger.warn(s"Unmatched request: ${req.method} ${req.uri}")
      NotFound(ApiResponse.error(s"路径不存在: ${req.method} ${req.uri}")).map(_.withHeaders(corsHeaders))
  }

  // 认证辅助方法
  private def authenticateAdmin(req: Request[IO])(handler: JwtPayload => IO[Response[IO]]): IO[Response[IO]] = {
    authenticate(req) { user =>
      if (user.role.equalsIgnoreCase("admin")) {
        handler(user)
      } else {
        Forbidden(ApiResponse.error("需要管理员权限"))
      }
    }
  }

  private def authenticateStudent(req: Request[IO])(handler: JwtPayload => IO[Response[IO]]): IO[Response[IO]] = {
    authenticate(req) { user =>
      if (user.role.equalsIgnoreCase("student")) {
        handler(user)
      } else {
        Forbidden(ApiResponse.error("需要学生权限"))
      }
    }
  }

  private def authenticateCoach(req: Request[IO])(handler: JwtPayload => IO[Response[IO]]): IO[Response[IO]] = {
    authenticate(req) { user =>
      if (user.role.equalsIgnoreCase("coach")) {
        handler(user)
      } else {
        Forbidden(ApiResponse.error("需要教练权限"))
      }
    }
  }

  private def authenticateGrader(req: Request[IO])(handler: JwtPayload => IO[Response[IO]]): IO[Response[IO]] = {
    authenticate(req) { user =>
      if (user.role.equalsIgnoreCase("grader")) {
        handler(user)
      } else {
        Forbidden(ApiResponse.error("需要阅卷者权限"))
      }
    }
  }

  private def authenticateUser(req: Request[IO])(handler: JwtPayload => IO[Response[IO]]): IO[Response[IO]] = {
    authenticate(req)(handler)
  }

  private def authenticate(req: Request[IO])(handler: JwtPayload => IO[Response[IO]]): IO[Response[IO]] = {
    req.headers.get[Authorization] match {
      case Some(Authorization(credentials)) =>
        credentials match {
          case org.http4s.Credentials.Token(scheme, token) if scheme.toString == "Bearer" =>
            if (token == null || token.trim.isEmpty) {
              logger.warn("Received null or empty token in Bearer authentication")
              BadRequest(ApiResponse.error("Token不能为空"))
            } else {
              logger.debug(s"Authenticating with token: ${token.take(20)}...")
              authService.validateToken(token).flatMap {
                case Some(user) => 
                  logger.debug(s"Token validation successful for user: ${user.username}")
                  handler(user)
                case None => 
                  logger.warn(s"Token validation failed for token: ${token.take(20)}...")
                  BadRequest(ApiResponse.error("无效的令牌"))
              }
            }
          case _ => 
            logger.warn(s"Invalid authentication credentials: ${credentials.toString}")
            BadRequest(ApiResponse.error("无效的认证头"))
        }
      case None => 
        logger.warn("Missing Authorization header")
        BadRequest(ApiResponse.error("缺少认证头"))
    }
  }

  // 处理方法实现
  private def handleGetExamList(): IO[Response[IO]] = {
    examService.getExamsForAdmin().flatMap { exams =>
      Ok(ApiResponse.success(exams, "考试列表获取成功"))
    }.handleErrorWith { error =>
      logger.error("获取考试列表失败", error)
      InternalServerError(ApiResponse.error("获取考试列表失败"))
    }
  }

  private def handleCreateExam(req: Request[IO], createdBy: String): IO[Response[IO]] = {
    for {
      request <- req.as[CreateExamRequest]
      _ <- IO {
        logger.info(s"创建考试请求: title=${request.title}, startTime=${request.startTime}, endTime=${request.endTime}")
      }
      exam <- examService.createExam(request, createdBy)
      response <- Created(ApiResponse.success(exam, "考试创建成功"))
    } yield response
  }.handleErrorWith { error =>
    logger.error("创建考试失败", error)
    error match {
      case _: io.circe.DecodingFailure =>
        BadRequest(ApiResponse.error("请求数据格式错误，请检查日期时间格式"))
      case _ =>
        BadRequest(ApiResponse.error("创建考试失败"))
    }
  }

  private def handleUpdateExam(req: Request[IO], examId: String): IO[Response[IO]] = {
    for {
      request <- req.as[UpdateExamRequest]
      examOpt <- examService.updateExam(examId, request)
      response <- examOpt match {
        case Some(exam) => Ok(ApiResponse.success(exam, "考试更新成功"))
        case None => NotFound(ApiResponse.error("考试不存在"))
      }
    } yield response
  }.handleErrorWith { error =>
    logger.error("更新考试失败", error)
    BadRequest(ApiResponse.error("更新考试失败"))
  }

  private def handleGetExamById(examId: String): IO[Response[IO]] = {
    examService.getExamById(examId).flatMap {
      case Some(exam) => Ok(ApiResponse.success(exam, "考试获取成功"))
      case None => NotFound(ApiResponse.error("考试不存在"))
    }.handleErrorWith { error =>
      logger.error(s"获取考试失败: examId=$examId", error)
      InternalServerError(ApiResponse.error("获取考试失败"))
    }
  }

  private def handleDeleteExam(examId: String): IO[Response[IO]] = {
    examService.deleteExam(examId).flatMap { deleted =>
      if (deleted) {
        Ok(ApiResponse.success("", "考试删除成功"))
      } else {
        NotFound(ApiResponse.error("考试不存在"))
      }
    }.handleErrorWith { error =>
      logger.error("删除考试失败", error)
      InternalServerError(ApiResponse.error("删除考试失败"))
    }
  }

  private def handleSetQuestionScores(req: Request[IO], examId: String): IO[Response[IO]] = {
    for {
      request <- req.as[SetQuestionScoresRequest]
      response <- questionService.setQuestionScores(examId, request)
      result <- Ok(ApiResponse.success(response, "问题分数设置成功"))
    } yield result
  }.handleErrorWith { error =>
    logger.error("设置问题分数失败", error)
    BadRequest(ApiResponse.error("设置问题分数失败"))
  }

  private def handleGetQuestionScores(examId: String): IO[Response[IO]] = {
    questionService.getQuestionScores(examId).flatMap {
      case Some(scores) => Ok(ApiResponse.success(scores, "问题分数获取成功"))
      case None => NotFound(ApiResponse.error("考试不存在或未设置问题分数"))
    }.handleErrorWith { error =>
      logger.error("获取问题分数失败", error)
      InternalServerError(ApiResponse.error("获取问题分数失败"))
    }
  }

  private def handleUpdateQuestionScore(req: Request[IO], examId: String, questionNumber: Int): IO[Response[IO]] = {
    for {
      request <- req.as[UpdateQuestionScoreRequest]
      updated <- questionService.updateQuestionScore(examId, questionNumber, request.score)
      response <- if (updated) {
        Ok(ApiResponse.success("", "问题分数更新成功"))
      } else {
        NotFound(ApiResponse.error("问题不存在"))
      }
    } yield response
  }.handleErrorWith { error =>
    logger.error("更新问题分数失败", error)
    BadRequest(ApiResponse.error("更新问题分数失败"))
  }

  private def handlePublishExam(req: Request[IO], examId: String): IO[Response[IO]] = {
    for {
      request <- req.attemptAs[PublishExamRequest].value.map {
        case Right(r) => r
        case Left(_) => PublishExamRequest() // Use default values when body is empty or invalid
      }
      published <- examService.publishExam(examId, request)
      response <- if (published) {
        Ok(ApiResponse.success("", "考试发布成功"))
      } else {
        NotFound(ApiResponse.error("考试不存在"))
      }
    } yield response
  }.handleErrorWith { error =>
    logger.error("发布考试失败", error)
    BadRequest(ApiResponse.error("发布考试失败"))
  }

  private def handleUnpublishExam(examId: String): IO[Response[IO]] = {
    examService.unpublishExam(examId).flatMap { unpublished =>
      if (unpublished) {
        Ok(ApiResponse.success("", "考试取消发布成功"))
      } else {
        NotFound(ApiResponse.error("考试不存在"))
      }
    }.handleErrorWith { error =>
      logger.error("取消发布考试失败", error)
      InternalServerError(ApiResponse.error("取消发布考试失败"))
    }
  }

  private def handleUpdateExamStatuses(): IO[Response[IO]] = {
    examService.updateExamStatuses().flatMap { updatedCount =>
      Ok(ApiResponse.success(updatedCount, s"成功更新 $updatedCount 个考试状态"))
    }.handleErrorWith { error =>
      logger.error("更新考试状态失败", error)
      InternalServerError(ApiResponse.error("更新考试状态失败"))
    }
  }

  private def handleReserveExamId(createdBy: String): IO[Response[IO]] = {
    examService.reserveExamId(createdBy).flatMap { examId =>
      val response = ReserveExamIdResponse(examId)
      Ok(ApiResponse.success(response, "考试ID预申请成功"))
    }.handleErrorWith { error =>
      logger.error("预申请考试ID失败", error)
      InternalServerError(ApiResponse.error("预申请考试ID失败"))
    }
  }

  private def handleDeleteReservedExamId(examId: String, username: String): IO[Response[IO]] = {
    examService.deleteReservedExamId(examId, username).flatMap { deleted =>
      if (deleted) {
        Ok(ApiResponse.success("", "预申请的考试ID删除成功"))
      } else {
        NotFound(ApiResponse.error("预申请的考试ID不存在"))
      }
    }.handleErrorWith { error =>
      logger.error("删除预申请考试ID失败", error)
      InternalServerError(ApiResponse.error("删除预申请考试ID失败"))
    }
  }

  private def handleGetAvailableExams(): IO[Response[IO]] = {
    examService.getExamsByStatus(ExamStatus.Published).flatMap { exams =>
      Ok(ApiResponse.success(exams, "可用考试列表获取成功"))
    }.handleErrorWith { error =>
      logger.error("获取可用考试失败", error)
      InternalServerError(ApiResponse.error("获取可用考试失败"))
    }
  }

  private def handleGetExamDetails(examId: String): IO[Response[IO]] = {
    examService.getExamById(examId).flatMap {
      case Some(exam) => Ok(ApiResponse.success(exam, "考试详情获取成功"))
      case None => NotFound(ApiResponse.error("考试不存在"))
    }.handleErrorWith { error =>
      logger.error("获取考试详情失败", error)
      InternalServerError(ApiResponse.error("获取考试详情失败"))
    }
  }

  private def handleSubmitAnswers(req: Request[IO], examId: String, studentUsername: String): IO[Response[IO]] = {
    for {
      request <- req.as[SubmitAnswersRequest]
      submission <- submissionService.submitAnswers(examId, studentUsername, request)
      response <- Ok(ApiResponse.success(submission, "答案提交成功"))
    } yield response
  }.handleErrorWith { error =>
    logger.error("提交答案失败", error)
    BadRequest(ApiResponse.error("提交答案失败"))
  }

  private def handleGetSubmissionStatus(examId: String, studentUsername: String): IO[Response[IO]] = {
    logger.info(s"handleGetSubmissionStatus called with examId: $examId, studentUsername: $studentUsername")
    submissionService.getSubmissionStatus(examId, studentUsername).flatMap {
      case Some(submission) => 
        logger.info(s"Submission found: $submission")
        Ok(ApiResponse.success(submission, "提交状态获取成功"))
      case None => 
        logger.info("No submission found, returning success with empty data")
        Ok(ApiResponse.success("", "提交状态获取成功"))
    }.handleErrorWith { error =>
      logger.error(s"获取提交状态失败 for examId: $examId, studentUsername: $studentUsername", error)
      InternalServerError(ApiResponse.error("获取提交状态失败"))
    }
  }

  private def handleGetCoachExams(): IO[Response[IO]] = {
    // 简化实现，返回所有已发布的考试
    examService.getExamsByStatus(ExamStatus.Published).flatMap { exams =>
      Ok(ApiResponse.success(exams, "教练考试列表获取成功"))
    }.handleErrorWith { error =>
      logger.error("获取教练考试失败", error)
      InternalServerError(ApiResponse.error("获取教练考试失败"))
    }
  }

  private def handleGetExamDetailsWithStats(examId: String): IO[Response[IO]] = {
    examService.getExamById(examId).flatMap {
      case Some(exam) => Ok(ApiResponse.success(exam, "考试详情获取成功"))
      case None => NotFound(ApiResponse.error("考试不存在"))
    }.handleErrorWith { error =>
      logger.error("获取考试详情失败", error)
      InternalServerError(ApiResponse.error("获取考试详情失败"))
    }
  }

  private def handleGetExamScoreStats(examId: String): IO[Response[IO]] = {
    submissionService.getSubmissionsByExam(examId).flatMap { submissions =>
      val stats = ScoreStats(
        totalStudents = submissions.length,
        submittedStudents = submissions.count(_.status == SubmissionStatus.Submitted),
        averageScore = submissions.flatMap(_.score).sum / submissions.length.max(1),
        scores = submissions.flatMap(s => s.score.map(score => 
          StudentScore(s.studentUsername, s.studentUsername, score, s.submittedAt)
        ))
      )
      Ok(ApiResponse.success(stats, "分数统计获取成功"))
    }.handleErrorWith { error =>
      logger.error("获取分数统计失败", error)
      InternalServerError(ApiResponse.error("获取分数统计失败"))
    }
  }

  private def handleCoachSubmitAnswers(req: Request[IO], examId: String, coachUsername: String): IO[Response[IO]] = {
    for {
      request <- req.as[CoachSubmitAnswersRequest]
      submission <- submissionService.submitAnswers(examId, request.studentUsername, 
        SubmitAnswersRequest(request.answers), Some(coachUsername))
      response <- Ok(ApiResponse.success(submission, "代学生提交答案成功"))
    } yield response
  }.handleErrorWith { error =>
    logger.error("代学生提交答案失败", error)
    BadRequest(ApiResponse.error("代学生提交答案失败"))
  }

  private def handleGetCoachSubmissions(examId: String, coachUsername: String): IO[Response[IO]] = {
    submissionService.getCoachSubmissions(examId, coachUsername).flatMap { submissions =>
      Ok(ApiResponse.success(submissions, "学生提交记录获取成功"))
    }.handleErrorWith { error =>
      logger.error("获取学生提交记录失败", error)
      InternalServerError(ApiResponse.error("获取学生提交记录失败"))
    }
  }

  private def handleGetGradableExams(req: Request[IO]): IO[Response[IO]] = {
    // 解析查询参数
    val queryParams = req.uri.query.params
    val status = queryParams.get("status").getOrElse("grading")
    val page = queryParams.get("page").flatMap(_.toIntOption).getOrElse(1)
    val limit = queryParams.get("limit").flatMap(_.toIntOption).getOrElse(50)
    
    // 计算偏移量
    val offset = (page - 1) * limit
    
    // 根据状态获取考试
    val examStatusFilter = status.toLowerCase match {
      case "grading" => ExamStatus.Grading
      case "published" => ExamStatus.Published
      case "ongoing" => ExamStatus.Ongoing
      case "completed" => ExamStatus.Completed
      case _ => ExamStatus.Grading // 默认为阅卷中
    }
    
    examService.getExamsByStatus(examStatusFilter).flatMap { allExams =>
      // 应用分页
      val totalCount = allExams.length
      val paginatedExams = allExams.drop(offset).take(limit)
      
      // 构造分页响应
      val paginatedResponse = Json.obj(
        "items" -> paginatedExams.asJson,
        "total" -> totalCount.asJson,
        "page" -> page.asJson,
        "limit" -> limit.asJson
      )
      
      Ok(ApiResponse.success(paginatedResponse, "可阅卷考试列表获取成功"))
    }.handleErrorWith { error =>
      logger.error("获取可阅卷考试失败", error)
      InternalServerError(ApiResponse.error("获取可阅卷考试失败"))
    }
  }

  private def handleGetExamForGrading(examId: String): IO[Response[IO]] = {
    examService.getExamById(examId).flatMap {
      case Some(exam) => Ok(ApiResponse.success(exam, "考试详情获取成功"))
      case None => NotFound(ApiResponse.error("考试不存在"))
    }.handleErrorWith { error =>
      logger.error("获取考试详情失败", error)
      InternalServerError(ApiResponse.error("获取考试详情失败"))
    }
  }

  private def handleGetGradingProgress(examId: String): IO[Response[IO]] = {
    submissionService.getSubmissionsByExam(examId).flatMap { submissions =>
      val progress = GradingProgress(
        examId = examId,
        examTitle = "考试标题", // 需要从考试信息中获取
        totalSubmissions = submissions.length,
        gradedSubmissions = submissions.count(_.status == SubmissionStatus.Graded),
        pendingSubmissions = submissions.count(_.status == SubmissionStatus.Submitted),
        myCompletedTasks = 0, // 需要实现具体逻辑
        progress = submissions.count(_.status == SubmissionStatus.Graded).toDouble / submissions.length.max(1) * 100
      )
      Ok(ApiResponse.success(progress, "阅卷进度获取成功"))
    }.handleErrorWith { error =>
      logger.error("获取阅卷进度失败", error)
      InternalServerError(ApiResponse.error("获取阅卷进度失败"))
    }
  }

  private def handleUploadExamFiles(req: Request[IO], user: JwtPayload): IO[Response[IO]] = {
    logger.info(s"Handling exam file upload for user: ${user.username}")
    
    req.as[JsonFileUploadRequest].flatMap { uploadReq =>
      for {
        // Decode base64 file content
        fileBytes <- IO {
          Base64.getDecoder.decode(uploadReq.fileContent)
        }.handleErrorWith { error =>
          IO.raiseError(new RuntimeException(s"Invalid base64 content: ${error.getMessage}"))
        }
        
        // Validate file upload
        _ <- validateFileUpload(uploadReq.originalName, fileBytes.length.toLong, uploadReq.fileType)
        
        // Prepare upload request for FileStorageService
        uploadRequest = FileStorageUploadRequest(
          originalName = uploadReq.originalName,
          fileContent = Base64.getEncoder.encodeToString(fileBytes), // Use Base64 to preserve byte encoding
          fileType = extractFileExtension(uploadReq.originalName),
          mimeType = detectMimeType(uploadReq.originalName),
          uploadUserId = user.username,
          uploadUserType = "admin",
          examId = Some(uploadReq.examId),
          submissionId = None,
          description = Some(s"考试${uploadReq.fileType}文件"),
          category = "exam"
        )
        
        // Call FileStorageService
        storageResponse <- fileStorageService.uploadFile(uploadRequest)
        
        response <- if (storageResponse.success) {
          val fileResponse = ExamFileUploadResponse(
            fileId = storageResponse.fileId.getOrElse("unknown"),
            originalName = uploadReq.originalName,
            url = storageResponse.url.getOrElse(""),
            size = fileBytes.length.toLong,
            uploadTime = java.time.LocalDateTime.now().toString
          )
          Ok(ApiResponse.success(fileResponse, "文件上传成功"))
        } else {
          BadRequest(ApiResponse.error(storageResponse.message.getOrElse("文件上传失败")))
        }
      } yield response
    }.handleErrorWith { error =>
      logger.error(s"File upload error: ${error.getMessage}", error)
      BadRequest(ApiResponse.error(s"文件上传失败: ${error.getMessage}"))
    }
  }

  private def handleUploadExamFileForExam(req: Request[IO], user: JwtPayload, examId: String): IO[Response[IO]] = {
    logger.info(s"Handling exam file upload for exam ID: $examId, user: ${user.username}")
    logger.info(s"Content-Type: ${req.contentType}")
    logger.info(s"Full Content-Type string: ${req.contentType.map(_.toString)}")
    
    req.contentType match {
      case Some(contentType) if contentType.mediaType.mainType == "multipart" && contentType.mediaType.subType == "form-data" =>
        // Extract boundary from content type - check both mediaType and full contentType
        val contentTypeStr = contentType.toString
        logger.info(s"Full Content-Type for boundary extraction: $contentTypeStr")
        logger.info(s"MediaType mainType: ${contentType.mediaType.mainType}, subType: ${contentType.mediaType.subType}")
        
        val boundary = if (contentTypeStr.contains("boundary=")) {
          contentTypeStr.split("boundary=\"").lastOption.flatMap(_.split("\"").headOption)
            .orElse(contentTypeStr.split("boundary=").lastOption.map(_.split(";")(0).trim.replaceAll("\"", "")))
        } else {
          contentType.mediaType.toString.split("boundary=").lastOption.map(_.replaceAll("\"", ""))
        }
        
        logger.info(s"Extracted boundary: $boundary")
        
        boundary match {
          case Some(boundaryStr) =>
            logger.info(s"Using boundary: $boundaryStr")
            handleMultipartFileUpload(req, user, examId, boundaryStr)
          case None =>
            logger.error(s"Failed to extract boundary from Content-Type: $contentTypeStr")
            BadRequest(ApiResponse.error("无法从Content-Type中提取boundary"))
        }
      case Some(contentType) =>
        logger.warn(s"Non-multipart content type: ${contentType.mediaType} (mainType: ${contentType.mediaType.mainType}, subType: ${contentType.mediaType.subType})")
        // For non-multipart requests, return the temporary response
        val fileResponse = ExamFileUploadResponse(
          fileId = java.util.UUID.randomUUID().toString,
          originalName = "temp_file.pdf",
          url = s"http://localhost:3008/files/${java.util.UUID.randomUUID()}",
          size = 1024L,
          uploadTime = java.time.LocalDateTime.now().toString
        )
        Ok(ApiResponse.success(fileResponse, "文件上传成功 (临时响应)"))
      case None =>
        logger.warn("No Content-Type header found")
        BadRequest(ApiResponse.error("缺少Content-Type头"))
    }
  }

  private def handleMultipartFileUpload(req: Request[IO], user: JwtPayload, examId: String, boundary: String): IO[Response[IO]] = {
    logger.info(s"Processing multipart upload with boundary: $boundary")
    
    // Read the raw body as bytes
    req.body.compile.toVector.flatMap { bodyBytes =>
      logger.info(s"Received multipart body length: ${bodyBytes.length}")
      
      // Parse multipart data as raw bytes instead of converting to string
      val result = try {
        logger.info(s"Starting multipart parsing with boundary: --$boundary")
        val boundaryBytes = s"--$boundary".getBytes("UTF-8")
        val bodyArray = bodyBytes.toArray
        
        var fileName: Option[String] = None
        var fileBytes: Option[Array[Byte]] = None
        var fileType: Option[String] = None
        
        // Split by boundary in byte array
        val parts = splitBytesByBoundary(bodyArray, boundaryBytes)
        logger.info(s"Split into ${parts.length} parts")
        
        for ((part, index) <- parts.zipWithIndex) {
          logger.debug(s"Processing part $index (length: ${part.length})")
          
          // Find the end of headers (first occurrence of \r\n\r\n)
          val headerEndPattern = "\r\n\r\n".getBytes("UTF-8")
          val headerEndIndex = indexOfByteSequence(part, headerEndPattern)
          
          if (headerEndIndex >= 0) {
            // Only convert headers to string, keep binary data as bytes
            val headersBytes = part.slice(0, headerEndIndex)
            val headersString = new String(headersBytes, "UTF-8")
            
            if (headersString.contains("Content-Disposition: form-data")) {
              logger.debug(s"Found form-data part $index")
              
              if (headersString.contains("name=\"file\"")) {
                logger.info(s"Found file part at index $index")
                // Extract filename from headers
                val lines = headersString.split("\r\n")
                for (line <- lines) {
                  if (line.contains("filename=")) {
                    val filenameMatch = """filename="([^"]+)"""".r
                    filenameMatch.findFirstMatchIn(line) match {
                      case Some(m) => 
                        fileName = Some(m.group(1))
                        logger.info(s"Extracted filename: ${fileName.get}")
                      case None => 
                        logger.warn(s"Could not extract filename from line: $line")
                    }
                  }
                }
                
                // Extract binary data (everything after \r\n\r\n)
                val binaryStartIndex = headerEndIndex + headerEndPattern.length
                val binaryEndIndex = findBoundaryInBytes(part, boundaryBytes, binaryStartIndex)
                
                if (binaryEndIndex > binaryStartIndex) {
                  fileBytes = Some(part.slice(binaryStartIndex, binaryEndIndex))
                  logger.info(s"Extracted file bytes: ${fileBytes.get.length} bytes")
                  logger.debug(s"File content preview (first 50 bytes): ${fileBytes.get.take(50).map(b => f"$b%02x").mkString(" ")}")
                } else {
                  fileBytes = Some(part.slice(binaryStartIndex, part.length))
                  logger.info(s"Extracted file bytes (no end boundary): ${fileBytes.get.length} bytes")
                }
              } else if (headersString.contains("name=\"fileType\"")) {
                logger.info(s"Found fileType part at index $index")
                // Extract file type value
                val binaryStartIndex = headerEndIndex + headerEndPattern.length
                val valueBytes = part.slice(binaryStartIndex, part.length)
                val value = new String(valueBytes, "UTF-8").trim.replaceAll(s"\\r\\n--$boundary.*", "")
                fileType = Some(value)
                logger.info(s"Extracted fileType: ${fileType.get}")
              }
            }
          }
        }
        
        logger.info(s"Multipart parsing complete - fileName: $fileName, fileBytes length: ${fileBytes.map(_.length)}, fileType: $fileType")
        
        Right((fileName, fileBytes, fileType))
      } catch {
        case ex: Exception =>
          logger.error(s"Multipart parsing error: ${ex.getMessage}", ex)
          Left(s"解析multipart数据失败: ${ex.getMessage}")
      }
      
      result match {
        case Right((Some(name), Some(bytes), typeOpt)) =>
          val finalFileType = typeOpt.getOrElse("question")
          logger.info(s"Successfully parsed multipart data - file: $name, size: ${bytes.length}, type: $finalFileType")
          
          // Validate file upload
          validateFileUpload(name, bytes.length.toLong, finalFileType).flatMap { _ =>
            logger.info(s"File validation passed")
            
            // Create upload request for FileStorageService
            val uploadRequest = FileStorageUploadRequest(
              originalName = name,
              fileContent = Base64.getEncoder.encodeToString(bytes), // Use Base64 to preserve byte encoding
              fileType = extractFileExtension(name),
              mimeType = detectMimeType(name),
              uploadUserId = user.username,
              uploadUserType = "admin",
              examId = Some(examId),
              submissionId = None,
              description = Some(s"考试${finalFileType}文件"),
              category = "exam"
            )
            
            logger.info(s"Created FileStorageUploadRequest for file: $name")
            
            // Call FileStorageService
            fileStorageService.uploadFile(uploadRequest).flatMap { storageResponse =>
              logger.info(s"FileStorageService response: success=${storageResponse.success}, fileId=${storageResponse.fileId}, message=${storageResponse.message}")
              
              if (storageResponse.success) {
                val fileId = storageResponse.fileId.getOrElse("unknown")
                val fileUrl = storageResponse.url.getOrElse("")
                
                logger.info(s"Saving exam file info to database: examId=$examId, fileId=$fileId, fileType=$finalFileType")
                
                // Save file information to local exam_files table
                val saveFileInfo = examService.saveExamFile(
                  examId = examId,
                  fileId = fileId,
                  fileName = name,
                  originalName = name,
                  fileUrl = fileUrl,
                  fileSize = bytes.length.toLong,
                  fileType = finalFileType,
                  mimeType = detectMimeType(name),
                  uploadedBy = user.username
                )
                
                saveFileInfo.flatMap { saved =>
                  logger.info(s"Exam file info saved to database: success=$saved")
                  
                  if (saved) {
                    val fileResponse = ExamFileUploadResponse(
                      fileId = fileId,
                      originalName = name,
                      url = fileUrl,
                      size = bytes.length.toLong,
                      uploadTime = java.time.LocalDateTime.now().toString
                    )
                    Ok(ApiResponse.success(fileResponse, "文件上传成功"))
                  } else {
                    logger.warn(s"File uploaded to storage but failed to save metadata for exam $examId")
                    // Still return success since the file was uploaded successfully
                    val fileResponse = ExamFileUploadResponse(
                      fileId = fileId,
                      originalName = name,
                      url = fileUrl,
                      size = bytes.length.toLong,
                      uploadTime = java.time.LocalDateTime.now().toString
                    )
                    Ok(ApiResponse.success(fileResponse, "文件上传成功"))
                  }
                }
              } else {
                logger.error(s"FileStorageService upload failed: ${storageResponse.message}")
                BadRequest(ApiResponse.error(storageResponse.message.getOrElse("文件上传失败")))
              }
            }
          }
        case Right(_) =>
          BadRequest(ApiResponse.error("无法从multipart请求中提取文件数据"))
        case Left(errorMsg) =>
          BadRequest(ApiResponse.error(errorMsg))
      }
    }.handleErrorWith { error =>
      logger.error(s"Error in handleMultipartFileUpload: ${error.getMessage}", error)
      InternalServerError(ApiResponse.error(s"文件上传处理失败: ${error.getMessage}"))
    }
  }

  private def handleUploadSpecificFileType(req: Request[IO], user: JwtPayload, examId: String, fileType: String): IO[Response[IO]] = {
    logger.info(s"Handling specific file upload for exam ID: $examId, fileType: $fileType, user: ${user.username}")
    logger.info(s"Content-Type: ${req.contentType}")
    logger.info(s"Headers: ${req.headers}")
    
    // Read the entire body first and then analyze it
    req.body.compile.toVector.flatMap { bodyBytes =>
      val bodyArray = bodyBytes.toArray
      val bodyPreviewString = new String(bodyArray.take(200), "UTF-8")
      logger.info(s"Body preview (first 200 bytes): $bodyPreviewString")
      
      // Check if the body actually contains multipart data regardless of Content-Type header
      val isActuallyMultipart = bodyPreviewString.contains("------") || bodyPreviewString.contains("Content-Disposition:")
      
      if (isActuallyMultipart) {
        logger.info("Detected multipart content in body, treating as multipart upload")
        // Extract boundary from the body itself
        val boundaryMatch = """--([a-zA-Z0-9\-_]+)""".r.findFirstMatchIn(bodyPreviewString)
        boundaryMatch match {
          case Some(m) =>
            val boundary = m.group(1)
            logger.info(s"Extracted boundary from body: $boundary")
            processMultipartWithBytes(bodyArray, user, examId, boundary, fileType)
          case None =>
            logger.error("Could not extract boundary from multipart body")
            BadRequest(ApiResponse.error("无法从multipart请求中提取boundary"))
        }
      } else {
        logger.info("Body does not appear to be multipart, attempting JSON parse")
        // Try to parse as JSON
        val bodyString = new String(bodyArray, "UTF-8")
        logger.info(s"Attempting to parse JSON body: ${bodyString.take(500)}")
        
        // Parse JSON manually to handle the case where content-type is wrong
        parseJsonFileUpload(bodyString) match {
          case Right((fileContent, originalName)) =>
            logger.info(s"Successfully parsed JSON upload: filename=$originalName")
            processJsonFileUpload(fileContent, originalName, user, examId, fileType)
          case Left(error) =>
            logger.error(s"Failed to parse as JSON: $error")
            BadRequest(ApiResponse.error(s"无法解析请求数据: $error"))
        }
      }
    }.handleErrorWith { error =>
      logger.error(s"Error in handleUploadSpecificFileType: ${error.getMessage}", error)
      InternalServerError(ApiResponse.error(s"文件上传处理失败: ${error.getMessage}"))
    }
  }

  private def handleMultipartFileUploadWithType(req: Request[IO], user: JwtPayload, examId: String, boundary: String, fileType: String): IO[Response[IO]] = {
    logger.info(s"Processing multipart upload with boundary: $boundary, fileType: $fileType")
    
    // Read the raw body as bytes
    req.body.compile.toVector.flatMap { bodyBytes =>
      logger.info(s"Received multipart body length: ${bodyBytes.length}")
      
      // Parse multipart data as raw bytes instead of converting to string
      val result = try {
        logger.info(s"Starting multipart parsing with boundary: --$boundary")
        val boundaryBytes = s"--$boundary".getBytes("UTF-8")
        val bodyArray = bodyBytes.toArray
        
        var fileName: Option[String] = None
        var fileBytes: Option[Array[Byte]] = None
        var fileTypeDetected: Option[String] = None
        
        // Split by boundary in byte array
        val parts = splitBytesByBoundary(bodyArray, boundaryBytes)
        logger.info(s"Split into ${parts.length} parts")
        
        for ((part, index) <- parts.zipWithIndex) {
          logger.debug(s"Processing part $index (length: ${part.length})")
          
          // Find the end of headers (first occurrence of \r\n\r\n)
          val headerEndPattern = "\r\n\r\n".getBytes("UTF-8")
          val headerEndIndex = indexOfByteSequence(part, headerEndPattern)
          
          if (headerEndIndex >= 0) {
            // Only convert headers to string, keep binary data as bytes
            val headersBytes = part.slice(0, headerEndIndex)
            val headersString = new String(headersBytes, "UTF-8")
            
            if (headersString.contains("Content-Disposition: form-data")) {
              logger.debug(s"Found form-data part $index")
              
              if (headersString.contains("name=\"file\"")) {
                logger.info(s"Found file part at index $index")
                // Extract filename from headers
                val lines = headersString.split("\r\n")
                for (line <- lines) {
                  if (line.contains("filename=")) {
                    val filenameMatch = """filename="([^"]+)"""".r
                    filenameMatch.findFirstMatchIn(line) match {
                      case Some(m) => 
                        fileName = Some(m.group(1))
                        logger.info(s"Extracted filename: ${fileName.get}")
                      case None => 
                        logger.warn(s"Could not extract filename from line: $line")
                    }
                  }
                }
                
                // Extract binary data (everything after \r\n\r\n)
                val binaryStartIndex = headerEndIndex + headerEndPattern.length
                val binaryEndIndex = findBoundaryInBytes(part, boundaryBytes, binaryStartIndex)
                
                if (binaryEndIndex > binaryStartIndex) {
                  fileBytes = Some(part.slice(binaryStartIndex, binaryEndIndex))
                  logger.info(s"Extracted file bytes: ${fileBytes.get.length} bytes")
                  logger.debug(s"File content preview (first 50 bytes): ${fileBytes.get.take(50).map(b => f"$b%02x").mkString(" ")}")
                } else {
                  fileBytes = Some(part.slice(binaryStartIndex, part.length))
                  logger.info(s"Extracted file bytes (no end boundary): ${fileBytes.get.length} bytes")
                }
              } else if (headersString.contains("name=\"fileType\"")) {
                logger.info(s"Found fileType part at index $index")
                // Extract file type value
                val binaryStartIndex = headerEndIndex + headerEndPattern.length
                val valueBytes = part.slice(binaryStartIndex, part.length)
                val value = new String(valueBytes, "UTF-8").trim.replaceAll(s"\\r\\n--$boundary.*", "")
                fileTypeDetected = Some(value)
                logger.info(s"Extracted fileType: ${fileTypeDetected.get}")
              }
            }
          }
        }
        
        logger.info(s"Multipart parsing complete - fileName: $fileName, fileBytes length: ${fileBytes.map(_.length)}, fileType: $fileTypeDetected")
        
        Right((fileName, fileBytes, fileTypeDetected))
      } catch {
        case ex: Exception =>
          logger.error(s"Multipart parsing error: ${ex.getMessage}", ex)
          Left(s"解析multipart数据失败: ${ex.getMessage}")
      }
      
      result match {
        case Right((Some(name), Some(bytes), typeOpt)) =>
          val finalFileType = fileType // Use the specific file type from the endpoint
          logger.info(s"Successfully parsed multipart data - file: $name, size: ${bytes.length}, type: $finalFileType")
          
          // Validate file upload
          validateFileUpload(name, bytes.length.toLong, finalFileType).flatMap { _ =>
            logger.info(s"File validation passed")
            
            // Create upload request for FileStorageService
            val uploadRequest = FileStorageUploadRequest(
              originalName = name,
              fileContent = Base64.getEncoder.encodeToString(bytes), // Use Base64 to preserve byte encoding
              fileType = extractFileExtension(name),
              mimeType = detectMimeType(name),
              uploadUserId = user.username,
              uploadUserType = "admin",
              examId = Some(examId),
              submissionId = None,
              description = Some(s"考试${finalFileType}文件"),
              category = "exam"
            )
            
            logger.info(s"Created FileStorageUploadRequest for file: $name")
            
            // Call FileStorageService
            fileStorageService.uploadFile(uploadRequest).flatMap { storageResponse =>
              logger.info(s"FileStorageService response: success=${storageResponse.success}, fileId=${storageResponse.fileId}, message=${storageResponse.message}")
              
              if (storageResponse.success) {
                val fileId = storageResponse.fileId.getOrElse("unknown")
                val fileUrl = storageResponse.url.getOrElse("")
                
                logger.info(s"Saving exam file info to database: examId=$examId, fileId=$fileId, fileType=$finalFileType")
                
                // Save file information to local exam_files table
                val saveFileInfo = examService.saveExamFile(
                  examId = examId,
                  fileId = fileId,
                  fileName = name,
                  originalName = name,
                  fileUrl = fileUrl,
                  fileSize = bytes.length.toLong,
                  fileType = finalFileType,
                  mimeType = detectMimeType(name),
                  uploadedBy = user.username
                )
                
                saveFileInfo.flatMap { saved =>
                  logger.info(s"Exam file info saved to database: success=$saved")
                  
                  if (saved) {
                    val fileResponse = ExamFileUploadResponse(
                      fileId = fileId,
                      originalName = name,
                      url = fileUrl,
                      size = bytes.length.toLong,
                      uploadTime = java.time.LocalDateTime.now().toString
                    )
                    Ok(ApiResponse.success(fileResponse, "文件上传成功"))
                  } else {
                    logger.warn(s"File uploaded to storage but failed to save metadata for exam $examId")
                    // Still return success since the file was uploaded successfully
                    val fileResponse = ExamFileUploadResponse(
                      fileId = fileId,
                      originalName = name,
                      url = fileUrl,
                      size = bytes.length.toLong,
                      uploadTime = java.time.LocalDateTime.now().toString
                    )
                    Ok(ApiResponse.success(fileResponse, "文件上传成功"))
                  }
                }
              } else {
                logger.error(s"FileStorageService upload failed: ${storageResponse.message}")
                BadRequest(ApiResponse.error(storageResponse.message.getOrElse("文件上传失败")))
              }
            }
          }
        case Right(_) =>
          BadRequest(ApiResponse.error("无法从multipart请求中提取文件数据"))
        case Left(errorMsg) =>
          BadRequest(ApiResponse.error(errorMsg))
      }
    }.handleErrorWith { error =>
      logger.error(s"Error in handleMultipartFileUpload: ${error.getMessage}", error)
      InternalServerError(ApiResponse.error(s"文件上传处理失败: ${error.getMessage}"))
    }
  }

  // Utility functions for multipart parsing
  private def splitBytesByBoundary(data: Array[Byte], boundary: Array[Byte]): Array[Array[Byte]] = {
    val parts = scala.collection.mutable.ListBuffer[Array[Byte]]()
    var start = 0
    var index = indexOfByteSequence(data, boundary, start)
    
    while (index >= 0) {
      if (index > start) {
        parts += data.slice(start, index)
      }
      start = index + boundary.length
      index = indexOfByteSequence(data, boundary, start)
    }
    
    if (start < data.length) {
      parts += data.slice(start, data.length)
    }
    
    parts.toArray
  }
  
  private def indexOfByteSequence(data: Array[Byte], pattern: Array[Byte], startIndex: Int = 0): Int = {
    for (i <- startIndex to data.length - pattern.length) {
      if (data.slice(i, i + pattern.length).sameElements(pattern)) {
        return i
      }
    }
    -1
  }
  
  private def findBoundaryInBytes(data: Array[Byte], boundary: Array[Byte], startIndex: Int): Int = {
    val crlfBoundary = ("\r\n--" + new String(boundary, "UTF-8")).getBytes("UTF-8")
    val index = indexOfByteSequence(data, crlfBoundary, startIndex)
    if (index >= 0) index else data.length
  }

  private def validateFileUpload(fileName: String, fileSize: Long, fileType: String): IO[Unit] = {
    val maxSize = 100 * 1024 * 1024 // 100MB
    val allowedExtensions = Set("pdf", "doc", "docx", "txt", "jpg", "jpeg", "png")
    val extension = extractFileExtension(fileName).toLowerCase
    
    if (fileSize > maxSize) {
      IO.raiseError(new RuntimeException(s"文件大小超过限制 (${maxSize / 1024 / 1024}MB)"))
    } else if (!allowedExtensions.contains(extension)) {
      IO.raiseError(new RuntimeException(s"不支持的文件类型: $extension"))
    } else {
      IO.unit
    }
  }

  private def extractFileExtension(fileName: String): String = {
    val lastDotIndex = fileName.lastIndexOf('.')
    if (lastDotIndex > 0) fileName.substring(lastDotIndex + 1) else ""
  }

  private def detectMimeType(fileName: String): String = {
    extractFileExtension(fileName).toLowerCase match {
      case "pdf" => "application/pdf"
      case "doc" => "application/msword"
      case "docx" => "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
      case "txt" => "text/plain"
      case "jpg" | "jpeg" => "image/jpeg"
      case "png" => "image/png"
      case _ => "application/octet-stream"
    }
  }

  private def handleJsonFileUploadWithType(req: Request[IO], user: JwtPayload, examId: String, fileType: String): IO[Response[IO]] = {
    logger.info(s"Processing JSON file upload with fileType: $fileType")
    
    // Try to parse as SimpleJsonFileUploadRequest first, then fall back to JsonFileUploadRequest
    req.attemptAs[SimpleJsonFileUploadRequest].value.flatMap {
      case Right(uploadReq) =>
        logger.info(s"Successfully parsed as SimpleJsonFileUploadRequest: ${uploadReq.originalName}")
        processJsonFileUpload(uploadReq.fileContent, uploadReq.originalName, user, examId, fileType)
      case Left(_) =>
        logger.info("Failed to parse as SimpleJsonFileUploadRequest, trying JsonFileUploadRequest")
        req.as[JsonFileUploadRequest].flatMap { uploadReq =>
          logger.info(s"Successfully parsed as JsonFileUploadRequest: ${uploadReq.originalName}")
          processJsonFileUpload(uploadReq.fileContent, uploadReq.originalName, user, examId, fileType)
        }
    }.handleErrorWith { error =>
      logger.error(s"JSON file upload error: ${error.getMessage}", error)
      BadRequest(ApiResponse.error(s"文件上传失败: ${error.getMessage}"))
    }
  }

  private def processJsonFileUpload(fileContent: String, originalName: String, user: JwtPayload, examId: String, fileType: String): IO[Response[IO]] = {
    for {
      // Decode base64 file content
      fileBytes <- IO {
        Base64.getDecoder.decode(fileContent)
      }.handleErrorWith { error =>
        IO.raiseError(new RuntimeException(s"Invalid base64 content: ${error.getMessage}"))
      }
      
      // Validate file upload
      _ <- validateFileUpload(originalName, fileBytes.length.toLong, fileType)
      
      // Prepare upload request for FileStorageService
      uploadRequest = FileStorageUploadRequest(
        originalName = originalName,
        fileContent = Base64.getEncoder.encodeToString(fileBytes), // Use Base64 to preserve byte encoding
        fileType = extractFileExtension(originalName),
        mimeType = detectMimeType(originalName),
        uploadUserId = user.username,
        uploadUserType = "admin",
        examId = Some(examId),
        submissionId = None,
        description = Some(s"考试${fileType}文件"),
        category = "exam"
      )
      
      // Call FileStorageService
      storageResponse <- fileStorageService.uploadFile(uploadRequest)
      
      response <- if (storageResponse.success) {
        val fileId = storageResponse.fileId.getOrElse("unknown")
        val fileUrl = storageResponse.url.getOrElse("")
        
        logger.info(s"Saving exam file info to database: examId=$examId, fileId=$fileId, fileType=$fileType")
        
        // Save file information to local exam_files table
        val saveFileInfo = examService.saveExamFile(
          examId = examId,
          fileId = fileId,
          fileName = originalName,
          originalName = originalName,
          fileUrl = fileUrl,
          fileSize = fileBytes.length.toLong,
          fileType = fileType,
          mimeType = detectMimeType(originalName),
          uploadedBy = user.username
        )
        
        saveFileInfo.flatMap { saved =>
          logger.info(s"Exam file info saved to database: success=$saved")
          
          if (saved) {
            val fileResponse = ExamFileUploadResponse(
              fileId = fileId,
              originalName = originalName,
              url = fileUrl,
              size = fileBytes.length.toLong,
              uploadTime = java.time.LocalDateTime.now().toString
            )
            Ok(ApiResponse.success(fileResponse, "文件上传成功"))
          } else {
            logger.warn(s"File uploaded to storage but failed to save metadata for exam $examId")
            // Still return success since the file was uploaded successfully
            val fileResponse = ExamFileUploadResponse(
              fileId = fileId,
              originalName = originalName,
              url = fileUrl,
              size = fileBytes.length.toLong,
              uploadTime = java.time.LocalDateTime.now().toString
            )
            Ok(ApiResponse.success(fileResponse, "文件上传成功"))
          }
        }
      } else {
        BadRequest(ApiResponse.error(storageResponse.message.getOrElse("文件上传失败")))
      }
    } yield response
  }

  private def handleUploadAnswerImage(req: Request[IO], user: JwtPayload): IO[Response[IO]] = {
    // TODO: Implement proper multipart/form-data handling with FileStorageService
    val imageResponse = AnswerImageUploadResponse(
      imageUrl = s"http://localhost:3008/images/${java.util.UUID.randomUUID()}",
      fileName = "answer_image.jpg",
      fileSize = 512,
      uploadTime = java.time.LocalDateTime.now().toString
    )
    Ok(ApiResponse.success(imageResponse, "答案图片上传成功"))
  }

  private def handleCoachUploadAnswerImage(req: Request[IO], user: JwtPayload): IO[Response[IO]] = {
    // TODO: Implement proper multipart/form-data handling with FileStorageService
    val imageResponse = AnswerImageUploadResponse(
      imageUrl = s"http://localhost:3008/images/${java.util.UUID.randomUUID()}",
      fileName = "coach_answer_image.jpg",
      fileSize = 512,
      uploadTime = java.time.LocalDateTime.now().toString
    )
    Ok(ApiResponse.success(imageResponse, "教练答案图片上传成功"))
  }

  private def handleStudentUploadAnswerImage(req: Request[IO], user: JwtPayload, examId: String): IO[Response[IO]] = {
    logger.info(s"Handling student answer image upload for exam ID: $examId, user: ${user.username}")
    logger.info(s"Content-Type: ${req.contentType}")
    
    req.contentType match {
      case Some(contentType) if contentType.mediaType.mainType == "multipart" && contentType.mediaType.subType == "form-data" =>
        // Extract boundary from content type
        val contentTypeStr = contentType.toString
        logger.info(s"Full Content-Type for boundary extraction: $contentTypeStr")
        
        val boundary = if (contentTypeStr.contains("boundary=")) {
          contentTypeStr.split("boundary=\"").lastOption.flatMap(_.split("\"").headOption)
            .orElse(contentTypeStr.split("boundary=").lastOption.map(_.split(";")(0).trim.replaceAll("\"", "")))
        } else {
          contentType.mediaType.toString.split("boundary=").lastOption.map(_.replaceAll("\"", ""))
        }
        
        logger.info(s"Extracted boundary: $boundary")
        
        boundary match {
          case Some(boundaryStr) =>
            logger.info(s"Using boundary: $boundaryStr")
            handleStudentAnswerMultipartUpload(req, user, examId, boundaryStr)
          case None =>
            logger.error(s"Failed to extract boundary from Content-Type: $contentTypeStr")
            BadRequest(ApiResponse.error("无法从Content-Type中提取boundary"))
        }
      case Some(contentType) =>
        logger.warn(s"Non-multipart content type: ${contentType.mediaType}")
        // For non-multipart requests, return a temporary response
        val imageResponse = AnswerImageUploadResponse(
          imageUrl = s"http://localhost:3008/images/${java.util.UUID.randomUUID()}",
          fileName = "student_answer.jpg",
          fileSize = 512L,
          uploadTime = java.time.LocalDateTime.now().toString
        )
        Ok(ApiResponse.success(imageResponse, "答题图片上传成功 (临时响应)"))
      case None =>
        logger.warn("No Content-Type header found")
        BadRequest(ApiResponse.error("缺少Content-Type头"))
    }
  }

  private def handleStudentAnswerMultipartUpload(req: Request[IO], user: JwtPayload, examId: String, boundary: String): IO[Response[IO]] = {
    logger.info(s"Processing student answer multipart upload with boundary: $boundary")
    
    // Read the raw body as bytes
    req.body.compile.toVector.flatMap { bodyBytes =>
      logger.info(s"Received multipart body length: ${bodyBytes.length}")
      
      // Parse multipart data
      val result = try {
        logger.info(s"Starting multipart parsing with boundary: --$boundary")
        val boundaryBytes = s"--$boundary".getBytes("UTF-8")
        val bodyArray = bodyBytes.toArray
        
        var fileName: Option[String] = None
        var fileBytes: Option[Array[Byte]] = None
        var questionNumber: Option[String] = None
        
        // Split by boundary in byte array
        val parts = splitBytesByBoundary(bodyArray, boundaryBytes)
        logger.info(s"Split into ${parts.length} parts")
        
        for ((part, index) <- parts.zipWithIndex) {
          logger.debug(s"Processing part $index (length: ${part.length})")
          
          // Find the end of headers (first occurrence of \r\n\r\n)
          val headerEndPattern = "\r\n\r\n".getBytes("UTF-8")
          val headerEndIndex = indexOfByteSequence(part, headerEndPattern)
          
          if (headerEndIndex >= 0) {
            val headersBytes = part.slice(0, headerEndIndex)
            val headersString = new String(headersBytes, "UTF-8")
            
            if (headersString.contains("Content-Disposition: form-data")) {
              logger.debug(s"Found form-data part $index")
              
              if (headersString.contains("name=\"file\"")) {
                logger.info(s"Found file part at index $index")
                // Extract filename from headers
                val lines = headersString.split("\r\n")
                for (line <- lines) {
                  if (line.contains("filename=")) {
                    val filenameMatch = """filename="([^"]+)"""".r
                    filenameMatch.findFirstMatchIn(line) match {
                      case Some(m) => 
                        fileName = Some(m.group(1))
                        logger.info(s"Extracted filename: ${fileName.get}")
                      case None => 
                        logger.warn(s"Could not extract filename from line: $line")
                    }
                  }
                }
                
                // Extract binary data
                val binaryStartIndex = headerEndIndex + headerEndPattern.length
                val binaryEndIndex = findBoundaryInBytes(part, boundaryBytes, binaryStartIndex)
                
                if (binaryEndIndex > binaryStartIndex) {
                  fileBytes = Some(part.slice(binaryStartIndex, binaryEndIndex))
                  logger.info(s"Extracted file bytes: ${fileBytes.get.length} bytes")
                } else {
                  fileBytes = Some(part.slice(binaryStartIndex, part.length))
                  logger.info(s"Extracted file bytes (no end boundary): ${fileBytes.get.length} bytes")
                }
              } else if (headersString.contains("name=\"questionNumber\"")) {
                logger.info(s"Found questionNumber part at index $index")
                // Extract question number value
                val binaryStartIndex = headerEndIndex + headerEndPattern.length
                val valueBytes = part.slice(binaryStartIndex, part.length)
                val value = new String(valueBytes, "UTF-8").trim.replaceAll(s"\\r\\n--$boundary.*", "")
                questionNumber = Some(value)
                logger.info(s"Extracted questionNumber: ${questionNumber.get}")
              }
            }
          }
        }
        
        logger.info(s"Student answer multipart parsing complete - fileName: $fileName, fileBytes length: ${fileBytes.map(_.length)}, questionNumber: $questionNumber")
        
        Right((fileName, fileBytes, questionNumber))
      } catch {
        case ex: Exception =>
          logger.error(s"Student answer multipart parsing error: ${ex.getMessage}", ex)
          Left(s"解析multipart数据失败: ${ex.getMessage}")
      }
      
      result match {
        case Right((Some(name), Some(bytes), questionNumOpt)) =>
          logger.info(s"Successfully parsed student answer multipart data - file: $name, size: ${bytes.length}, questionNumber: $questionNumOpt")
          
          // Validate image file upload (only allow images for answer uploads)
          if (!isImageFile(name)) {
            BadRequest(ApiResponse.error("只允许上传图片文件"))
          } else if (bytes.length > 10 * 1024 * 1024) { // 10MB limit for answer images
            BadRequest(ApiResponse.error("文件大小不能超过10MB"))
          } else {
            logger.info(s"Student answer image validation passed")
            
            // Create upload request for FileStorageService
            val uploadRequest = FileStorageUploadRequest(
              originalName = name,
              fileContent = Base64.getEncoder.encodeToString(bytes),
              fileType = extractFileExtension(name),
              mimeType = detectMimeType(name),
              uploadUserId = user.username,
              uploadUserType = "student",
              examId = Some(examId),
              submissionId = None,
              description = Some(s"学生考试${examId}答题图片${questionNumOpt.map(q => s" - 题目$q").getOrElse("")}"),
              category = "student-answer"
            )
            
            logger.info(s"Created FileStorageUploadRequest for student answer image: $name")
            
            // Call FileStorageService
            fileStorageService.uploadFile(uploadRequest).flatMap { storageResponse =>
              logger.info(s"FileStorageService response for student answer: success=${storageResponse.success}, fileId=${storageResponse.fileId}, message=${storageResponse.message}")
              
              if (storageResponse.success) {
                val fileId = storageResponse.fileId.getOrElse("unknown")
                val fileUrl = storageResponse.url.getOrElse("")
                
                logger.info(s"Student answer image upload successful: fileId=$fileId, url=$fileUrl")
                
                val imageResponse = AnswerImageUploadResponse(
                  imageUrl = fileUrl,
                  fileName = name,
                  fileSize = bytes.length.toLong,
                  uploadTime = java.time.LocalDateTime.now().toString
                )
                Ok(ApiResponse.success(imageResponse, "学生答题图片上传成功"))
              } else {
                logger.error(s"FileStorageService upload failed for student answer: ${storageResponse.message}")
                BadRequest(ApiResponse.error(storageResponse.message.getOrElse("答题图片上传失败")))
              }
            }
          }
        case Right(_) =>
          BadRequest(ApiResponse.error("无法从multipart请求中提取图片文件数据"))
        case Left(errorMsg) =>
          BadRequest(ApiResponse.error(errorMsg))
      }
    }
  }

  // Helper method to check if file is an image
  private def isImageFile(fileName: String): Boolean = {
    val imageExtensions = Set("jpg", "jpeg", "png", "gif", "bmp", "webp")
    val extension = extractFileExtension(fileName).toLowerCase
    imageExtensions.contains(extension)
  }

  // 内部API处理方法
  private def handleGetGraderExamInfo(): IO[Response[IO]] = {
    examService.getGraderExamInfo().flatMap { graderExamInfoList =>
      Ok(ApiResponse.success(graderExamInfoList, "评卷员考试信息获取成功"))
    }.handleErrorWith { error =>
      logger.error("获取评卷员考试信息失败", error)
      InternalServerError(ApiResponse.error("获取评卷员考试信息失败"))
    }
  }

  // 内部API认证方法
  private def authenticateInternalApiKey(req: Request[IO])(handler: Unit => IO[Response[IO]]): IO[Response[IO]] = {
    req.headers.get(CaseInsensitiveString("X-Internal-API-Key")) match {
      case Some(apiKey) =>
        if (apiKey.head.value == "your-internal-api-key-here") {
          handler(())
        } else {
          logger.warn(s"Invalid internal API key: ${apiKey.head.value}")
          Forbidden(ApiResponse.error("无效的内部API密钥"))
        }
      case None =>
        logger.warn("Missing internal API key")
        Forbidden(ApiResponse.error("缺少内部API密钥"))
    }
  }

  // Missing helper methods for compilation
  private def parseJsonFileUpload(jsonString: String): Either[String, (String, String)] = {
    try {
      import io.circe.parser._
      parse(jsonString) match {
        case Right(json) =>
          val cursor = json.hcursor
          
          // Try to extract fileContent and originalName from different possible structures
          val fileContent = cursor.downField("fileContent").as[String]
            .orElse(cursor.downField("file").as[String])
            .orElse(cursor.downField("content").as[String])
          
          val originalName = cursor.downField("originalName").as[String]
            .orElse(cursor.downField("fileName").as[String])
            .orElse(cursor.downField("name").as[String])
          
          (fileContent, originalName) match {
            case (Right(content), Right(name)) => Right((content, name))
            case (Left(_), _) => Left("Missing fileContent field in JSON")
            case (_, Left(_)) => Left("Missing originalName field in JSON")
          }
        case Left(parseError) => Left(s"Invalid JSON: ${parseError.getMessage}")
      }
    } catch {
      case ex: Exception => Left(s"JSON parsing error: ${ex.getMessage}")
    }
  }

  private def processMultipartWithBytes(bodyArray: Array[Byte], user: JwtPayload, examId: String, boundary: String, fileType: String): IO[Response[IO]] = {
    logger.info(s"Processing multipart upload with boundary: $boundary, fileType: $fileType")
    
    // Parse multipart data
    val result = try {
      logger.info(s"Starting multipart parsing with boundary: --$boundary")
      val boundaryBytes = s"--$boundary".getBytes("UTF-8")
      
      var fileName: Option[String] = None
      var fileBytes: Option[Array[Byte]] = None
      
      // Split by boundary in byte array
      val parts = splitBytesByBoundary(bodyArray, boundaryBytes)
      logger.info(s"Split into ${parts.length} parts")
      
      for ((part, index) <- parts.zipWithIndex) {
        logger.debug(s"Processing part $index (length: ${part.length})")
        
        // Find the end of headers (first occurrence of \r\n\r\n)
        val headerEndPattern = "\r\n\r\n".getBytes("UTF-8")
        val headerEndIndex = indexOfByteSequence(part, headerEndPattern)
        
        if (headerEndIndex >= 0) {
          val headersBytes = part.slice(0, headerEndIndex)
          val headersString = new String(headersBytes, "UTF-8")
          
          if (headersString.contains("Content-Disposition: form-data")) {
            logger.debug(s"Found form-data part $index")
            
            if (headersString.contains("name=\"file\"")) {
              logger.info(s"Found file part at index $index")
              // Extract filename from headers
              val lines = headersString.split("\r\n")
              for (line <- lines) {
                if (line.contains("filename=")) {
                  val filenameMatch = """filename="([^"]+)"""".r
                  filenameMatch.findFirstMatchIn(line) match {
                    case Some(m) => 
                      fileName = Some(m.group(1))
                      logger.info(s"Extracted filename: ${fileName.get}")
                    case None => 
                      logger.warn(s"Could not extract filename from line: $line")
                  }
                }
              }
              
              // Extract binary data
              val binaryStartIndex = headerEndIndex + headerEndPattern.length
              val binaryEndIndex = findBoundaryInBytes(part, boundaryBytes, binaryStartIndex)
              
              if (binaryEndIndex > binaryStartIndex) {
                fileBytes = Some(part.slice(binaryStartIndex, binaryEndIndex))
                logger.info(s"Extracted file bytes: ${fileBytes.get.length} bytes")
              } else {
                fileBytes = Some(part.slice(binaryStartIndex, part.length))
                logger.info(s"Extracted file bytes (no end boundary): ${fileBytes.get.length} bytes")
              }
            }
          }
        }
      }
      
      logger.info(s"Multipart parsing complete - fileName: $fileName, fileBytes length: ${fileBytes.map(_.length)}")
      
      Right((fileName, fileBytes))
    } catch {
      case ex: Exception =>
        logger.error(s"Multipart parsing error: ${ex.getMessage}", ex)
        Left(s"解析multipart数据失败: ${ex.getMessage}")
    }
    
    result match {
      case Right((Some(name), Some(bytes))) =>
        logger.info(s"Successfully parsed multipart data - file: $name, size: ${bytes.length}")
        
        // Validate file upload
        validateFileUpload(name, bytes.length.toLong, fileType).flatMap { _ =>
          logger.info(s"File validation passed")
          
          // Create upload request for FileStorageService
          val uploadRequest = FileStorageUploadRequest(
            originalName = name,
            fileContent = Base64.getEncoder.encodeToString(bytes),
            fileType = extractFileExtension(name),
            mimeType = detectMimeType(name),
            uploadUserId = user.username,
            uploadUserType = "admin",
            examId = Some(examId),
            submissionId = None,
            description = Some(s"考试${fileType}文件"),
            category = "exam"
          )
          
          logger.info(s"Created FileStorageUploadRequest for file: $name")
          
          // Call FileStorageService
          fileStorageService.uploadFile(uploadRequest).flatMap { storageResponse =>
            logger.info(s"FileStorageService response: success=${storageResponse.success}, fileId=${storageResponse.fileId}, message=${storageResponse.message}")
            
            if (storageResponse.success) {
              val fileId = storageResponse.fileId.getOrElse("unknown")
              val fileUrl = storageResponse.url.getOrElse("")
              
              logger.info(s"Saving exam file info to database: examId=$examId, fileId=$fileId, fileType=$fileType")
              
              // Save file information to local exam_files table
              val saveFileInfo = examService.saveExamFile(
                examId = examId,
                fileId = fileId,
                fileName = name,
                originalName = name,
                fileUrl = fileUrl,
                fileSize = bytes.length.toLong,
                fileType = fileType,
                mimeType = detectMimeType(name),
                uploadedBy = user.username
              )
              
              saveFileInfo.flatMap { saved =>
                logger.info(s"Exam file info saved to database: success=$saved")
                
                val fileResponse = ExamFileUploadResponse(
                  fileId = fileId,
                  originalName = name,
                  url = fileUrl,
                  size = bytes.length.toLong,
                  uploadTime = java.time.LocalDateTime.now().toString
                )
                Ok(ApiResponse.success(fileResponse, "文件上传成功"))
              }
            } else {
              logger.error(s"FileStorageService upload failed: ${storageResponse.message}")
              BadRequest(ApiResponse.error(storageResponse.message.getOrElse("文件上传失败")))
            }
          }
        }
      case Right(_) =>
        BadRequest(ApiResponse.error("无法从multipart请求中提取文件数据"))
      case Left(errorMsg) =>
        BadRequest(ApiResponse.error(errorMsg))
    }
  }
}