package Controllers

import cats.effect.IO
import cats.implicits.*
import io.circe.*
import io.circe.generic.auto.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.headers.Authorization
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

    // ===================== 教练考试管理 API =====================

    // 获取考试列表（教练视角）
    case req @ GET -> Root / "api" / "coach" / "exams" =>
      authenticateCoach(req) { _ =>
        handleGetCoachExams()
      }.map(_.withHeaders(corsHeaders))

    // 获取考试详情与统计
    case req @ GET -> Root / "api" / "coach" / "exams" / examId =>
      authenticateCoach(req) { _ =>
        handleGetExamDetailsWithStats(examId)
      }.map(_.withHeaders(corsHeaders))

    // 获取考试分数统计
    case req @ GET -> Root / "api" / "coach" / "exams" / examId / "score-stats" =>
      authenticateCoach(req) { _ =>
        handleGetExamScoreStats(examId)
      }.map(_.withHeaders(corsHeaders))

    // 代学生提交答案
    case req @ POST -> Root / "api" / "coach" / "exams" / examId / "submissions" =>
      authenticateCoach(req) { user =>
        handleCoachSubmitAnswers(req, examId, user.username)
      }.map(_.withHeaders(corsHeaders))

    // 获取学生提交记录
    case req @ GET -> Root / "api" / "coach" / "exams" / examId / "submissions" =>
      authenticateCoach(req) { user =>
        handleGetCoachSubmissions(examId, user.username)
      }.map(_.withHeaders(corsHeaders))

    // ===================== 阅卷者考试管理 API =====================

    // 获取可阅卷考试
    case req @ GET -> Root / "api" / "grader" / "exams" =>
      authenticateGrader(req) { _ =>
        handleGetGradableExams()
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

    // 教练上传答案图片
    case req @ POST -> Root / "api" / "coach" / "exams" / examId / "upload-answer" =>
      authenticateCoach(req) { user =>
        handleCoachUploadAnswerImage(req, user)
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
            authService.validateToken(token).flatMap {
              case Some(user) => handler(user)
              case None => BadRequest(ApiResponse.error("无效的令牌"))
            }
          case _ => BadRequest(ApiResponse.error("无效的认证头"))
        }
      case None => BadRequest(ApiResponse.error("缺少认证头"))
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

  private def handleGetGradableExams(): IO[Response[IO]] = {
    examService.getExamsByStatus(ExamStatus.Grading).flatMap { exams =>
      Ok(ApiResponse.success(exams, "可阅卷考试列表获取成功"))
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
          fileContent = fileBytes.map(_.toInt).toList, // Convert bytes to List[Int] for JSON serialization
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
    
    req.contentType match {
      case Some(contentType) if contentType.mediaType.toString.startsWith("multipart/form-data") =>
        // Extract boundary from content type
        val boundary = contentType.mediaType.toString.split("boundary=").lastOption.map(_.replaceAll("\"", ""))
        boundary match {
          case Some(boundaryStr) =>
            handleMultipartFileUpload(req, user, examId, boundaryStr)
          case None =>
            BadRequest(ApiResponse.error("无法从Content-Type中提取boundary"))
        }
      case _ =>
        // For non-multipart requests, return the temporary response
        val fileResponse = ExamFileUploadResponse(
          fileId = java.util.UUID.randomUUID().toString,
          originalName = "temp_file.pdf",
          url = s"http://localhost:3008/files/${java.util.UUID.randomUUID()}",
          size = 1024L,
          uploadTime = java.time.LocalDateTime.now().toString
        )
        Ok(ApiResponse.success(fileResponse, "文件上传成功 (临时响应)"))
    }
  }

  private def handleMultipartFileUpload(req: Request[IO], user: JwtPayload, examId: String, boundary: String): IO[Response[IO]] = {
    logger.info(s"Processing multipart upload with boundary: $boundary")
    
    // Read the raw body as bytes
    req.body.compile.toVector.flatMap { bodyBytes =>
      val bodyString = new String(bodyBytes.toArray, "UTF-8")
      logger.info(s"Received multipart body length: ${bodyBytes.length}")
      
      // Simple multipart parsing - look for file content
      try {
        val parts = bodyString.split(s"--$boundary")
        var fileName: Option[String] = None
        var fileBytes: Option[Array[Byte]] = None
        var fileType: Option[String] = None
        
        for (part <- parts) {
          if (part.contains("Content-Disposition: form-data")) {
            if (part.contains("name=\"file\"")) {
              // This is the file part
              val lines = part.split("\r\n")
              for (line <- lines) {
                if (line.contains("filename=")) {
                  val filenameMatch = """filename="([^"]+)"""".r
                  filenameMatch.findFirstMatchIn(line) match {
                    case Some(m) => fileName = Some(m.group(1))
                    case None => // no match
                  }
                }
              }
              
              // Extract binary data (everything after the headers)
              val headerEndIndex = part.indexOf("\r\n\r\n")
              if (headerEndIndex > 0) {
                val binaryPart = part.substring(headerEndIndex + 4)
                // Remove trailing boundary markers
                val cleanBinary = binaryPart.replaceAll(s"\\r\\n--$boundary.*", "")
                fileBytes = Some(cleanBinary.getBytes("ISO-8859-1"))
              }
            } else if (part.contains("name=\"fileType\"")) {
              // Extract file type
              val headerEndIndex = part.indexOf("\r\n\r\n")
              if (headerEndIndex > 0) {
                val value = part.substring(headerEndIndex + 4).trim.replaceAll(s"\\r\\n--$boundary.*", "")
                fileType = Some(value)
              }
            }
          }
        }
        
        (fileName, fileBytes, fileType) match {
          case (Some(name), Some(bytes), typeOpt) =>
            val finalFileType = typeOpt.getOrElse("question")
            logger.info(s"Extracted file: $name, size: ${bytes.length}, type: $finalFileType")
            
            // Validate file upload
            validateFileUpload(name, bytes.length.toLong, finalFileType).flatMap { _ =>
              // Create upload request for FileStorageService
              val uploadRequest = FileStorageUploadRequest(
                originalName = name,
                fileContent = bytes.map(_.toInt).toList,
                fileType = extractFileExtension(name),
                mimeType = detectMimeType(name),
                uploadUserId = user.username,
                uploadUserType = "admin",
                examId = Some(examId),
                submissionId = None,
                description = Some(s"考试${finalFileType}文件"),
                category = "exam"
              )
              
              // Call FileStorageService
              fileStorageService.uploadFile(uploadRequest).flatMap { storageResponse =>
                if (storageResponse.success) {
                  val fileResponse = ExamFileUploadResponse(
                    fileId = storageResponse.fileId.getOrElse("unknown"),
                    originalName = name,
                    url = storageResponse.url.getOrElse(""),
                    size = bytes.length.toLong,
                    uploadTime = java.time.LocalDateTime.now().toString
                  )
                  Ok(ApiResponse.success(fileResponse, "文件上传成功"))
                } else {
                  BadRequest(ApiResponse.error(storageResponse.message.getOrElse("文件上传失败")))
                }
              }
            }
          case _ =>
            BadRequest(ApiResponse.error("无法从multipart请求中提取文件数据"))
        }
      } catch {
        case ex: Exception =>
          logger.error(s"Multipart parsing error: ${ex.getMessage}", ex)
          BadRequest(ApiResponse.error(s"解析multipart数据失败: ${ex.getMessage}"))
      }
    }
  }

  private def validateFileUpload(fileName: String, fileSize: Long, fileType: String): IO[Unit] = {
    val maxSize = 100 * 1024 * 1024 // 100MB
    val allowedExtensions = Set("pdf", "doc", "docx", "jpg", "jpeg", "png")
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
      case "jpg" | "jpeg" => "image/jpeg"
      case "png" => "image/png"
      case _ => "application/octet-stream"
    }
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
}
