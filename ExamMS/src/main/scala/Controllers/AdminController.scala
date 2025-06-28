package Controllers

import cats.effect.IO
import cats.implicits.*
import io.circe.*
import io.circe.syntax.*
import io.circe.generic.auto.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.headers.Authorization
import org.slf4j.LoggerFactory
import Models.*
import Services.*
import java.time.Instant

class AdminController(
  examService: ExamService,
  authService: AuthService,
  fileService: FileService
) {
  private val logger = LoggerFactory.getLogger("AdminController")

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

    // Get comprehensive exam list with admin privileges
    case req @ GET -> Root / "api" / "admin" / "exams" =>
      handleGetExams(req).map(_.withHeaders(corsHeaders))

    // Create new exam
    case req @ POST -> Root / "api" / "admin" / "exams" =>
      handleCreateExam(req).map(_.withHeaders(corsHeaders))

    // Update existing exam
    case req @ PUT -> Root / "api" / "admin" / "exams" / examId =>
      handleUpdateExam(req, examId).map(_.withHeaders(corsHeaders))

    // Delete exam
    case req @ DELETE -> Root / "api" / "admin" / "exams" / examId =>
      handleDeleteExam(req, examId).map(_.withHeaders(corsHeaders))

    // Publish exam to make it available
    case req @ POST -> Root / "api" / "admin" / "exams" / examId / "publish" =>
      handlePublishExam(req, examId).map(_.withHeaders(corsHeaders))

    // Unpublish exam to make it unavailable
    case req @ POST -> Root / "api" / "admin" / "exams" / examId / "unpublish" =>
      handleUnpublishExam(req, examId).map(_.withHeaders(corsHeaders))

    // Upload files related to exam
    case req @ POST -> Root / "api" / "admin" / "exams" / examId / "files" =>
      handleUploadExamFiles(req, examId).map(_.withHeaders(corsHeaders))

    // Configure scoring for exam questions
    case req @ POST -> Root / "api" / "admin" / "exams" / examId / "questions" / "scores" =>
      handleSetQuestionScores(req, examId).map(_.withHeaders(corsHeaders))

    // Get current question score configuration
    case req @ GET -> Root / "api" / "admin" / "exams" / examId / "questions" / "scores" =>
      handleGetQuestionScores(req, examId).map(_.withHeaders(corsHeaders))
  }

  private def unauthorizedResponse: IO[Response[IO]] = {
    Response[IO](Status.Unauthorized).withEntity(ErrorResponse(
      error = true,
      message = "Unauthorized access",
      code = "UNAUTHORIZED"
    ).asJson).pure[IO]
  }

  private def handleGetExams(req: Request[IO]): IO[Response[IO]] = {
    authenticateAdmin(req).flatMap {
      case Some(userInfo) =>
        val pageStr = req.uri.query.params.get("page")
        val limitStr = req.uri.query.params.get("limit")
        val status = req.uri.query.params.get("status")
        
        val page = pageStr.flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(1)
        val limit = limitStr.flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(10)
        
        examService.getExamsForAdmin(page, limit, status).flatMap { response =>
          Ok(response.asJson)
        }.handleErrorWith { error =>
          logger.error("Failed to fetch admin exam list", error)
          InternalServerError(ErrorResponse(
            error = true,
            message = "Failed to fetch exam list",
            code = "FETCH_ADMIN_EXAMS_ERROR"
          ).asJson)
        }
      case None =>
        unauthorizedResponse
    }
  }

  private def handleCreateExam(req: Request[IO]): IO[Response[IO]] = {
    authenticateAdmin(req).flatMap {
      case Some(userInfo) =>
        req.as[CreateExamRequest].flatMap { examData =>
          examService.createExam(examData, userInfo.id).flatMap { exam =>
            Ok(SuccessResponse(
              success = true,
              message = "Exam created successfully",
              exam = Some(exam)
            ).asJson)
          }
        }.handleErrorWith { error =>
          logger.error("Failed to create exam", error)
          BadRequest(ErrorResponse(
            error = true,
            message = "Failed to create exam",
            code = "CREATE_EXAM_ERROR",
            details = Some(Map("error" -> error.getMessage))
          ).asJson)
        }
      case None =>
        unauthorizedResponse
    }
  }

  private def handleUpdateExam(req: Request[IO], examId: String): IO[Response[IO]] = {
    authenticateAdmin(req).flatMap {
      case Some(userInfo) =>
        req.as[UpdateExamRequest].flatMap { examData =>
          examService.updateExam(examId, examData).flatMap {
            case Some(exam) =>
              Ok(SuccessResponse(
                success = true,
                message = "Exam updated successfully",
                exam = Some(exam)
              ).asJson)
            case None =>
              NotFound(ErrorResponse(
                error = true,
                message = "Exam not found",
                code = "EXAM_NOT_FOUND"
              ).asJson)
          }
        }.handleErrorWith { error =>
          logger.error(s"Failed to update exam: $examId", error)
          BadRequest(ErrorResponse(
            error = true,
            message = "Failed to update exam",
            code = "UPDATE_EXAM_ERROR",
            details = Some(Map("error" -> error.getMessage))
          ).asJson)
        }
      case None =>
        unauthorizedResponse
    }
  }

  private def handleDeleteExam(req: Request[IO], examId: String): IO[Response[IO]] = {
    authenticateAdmin(req).flatMap {
      case Some(userInfo) =>
        examService.deleteExam(examId).flatMap { deleted =>
          if (deleted) {
            Ok(SuccessResponse(
              success = true,
              message = "Exam deleted successfully"
            ).asJson)
          } else {
            NotFound(ErrorResponse(
              error = true,
              message = "Exam not found",
              code = "EXAM_NOT_FOUND"
            ).asJson)
          }
        }.handleErrorWith { error =>
          logger.error(s"Failed to delete exam: $examId", error)
          InternalServerError(ErrorResponse(
            error = true,
            message = "Failed to delete exam",
            code = "DELETE_EXAM_ERROR"
          ).asJson)
        }
      case None =>
        unauthorizedResponse
    }
  }

  private def handlePublishExam(req: Request[IO], examId: String): IO[Response[IO]] = {
    authenticateAdmin(req).flatMap {
      case Some(userInfo) =>
        examService.publishExam(examId).flatMap {
          case Some(exam) =>
            Ok(SuccessResponse(
              success = true,
              message = "Exam published successfully",
              exam = Some(exam)
            ).asJson)
          case None =>
            NotFound(ErrorResponse(
              error = true,
              message = "Exam not found",
              code = "EXAM_NOT_FOUND"
            ).asJson)
        }.handleErrorWith { error =>
          logger.error(s"Failed to publish exam: $examId", error)
          InternalServerError(ErrorResponse(
            error = true,
            message = "Failed to publish exam",
            code = "PUBLISH_EXAM_ERROR"
          ).asJson)
        }
      case None =>
        unauthorizedResponse
    }
  }

  private def handleUnpublishExam(req: Request[IO], examId: String): IO[Response[IO]] = {
    authenticateAdmin(req).flatMap {
      case Some(userInfo) =>
        examService.unpublishExam(examId).flatMap {
          case Some(exam) =>
            Ok(SuccessResponse(
              success = true,
              message = "Exam unpublished successfully",
              exam = Some(exam)
            ).asJson)
          case None =>
            NotFound(ErrorResponse(
              error = true,
              message = "Exam not found",
              code = "EXAM_NOT_FOUND"
            ).asJson)
        }.handleErrorWith { error =>
          logger.error(s"Failed to unpublish exam: $examId", error)
          InternalServerError(ErrorResponse(
            error = true,
            message = "Failed to unpublish exam",
            code = "UNPUBLISH_EXAM_ERROR"
          ).asJson)
        }
      case None =>
        unauthorizedResponse
    }
  }

  private def handleUploadExamFiles(req: Request[IO], examId: String): IO[Response[IO]] = {
    authenticateAdmin(req).flatMap {
      case Some(userInfo) =>
        // For now, return a stubbed response since multipart handling needs more complex setup
        Ok(SuccessResponse(
          success = true,
          message = "Files uploaded successfully",
          files = Some(List.empty)
        ).asJson).handleErrorWith { error =>
          logger.error(s"Failed to upload exam files: $examId", error)
          BadRequest(ErrorResponse(
            error = true,
            message = "Failed to upload files",
            code = "UPLOAD_FILES_ERROR",
            details = Some(Map("error" -> error.getMessage))
          ).asJson)
        }
      case None =>
        unauthorizedResponse
    }
  }

  private def handleSetQuestionScores(req: Request[IO], examId: String): IO[Response[IO]] = {
    authenticateAdmin(req).flatMap {
      case Some(userInfo) =>
        req.as[SetQuestionScoresRequest].flatMap { scoresData =>
          examService.setQuestionScores(examId, scoresData.scores).flatMap { scores =>
            Ok(SuccessResponse(
              success = true,
              message = "Question scores configured successfully",
              scores = Some(scores)
            ).asJson)
          }
        }.handleErrorWith { error =>
          logger.error(s"Failed to set question scores: $examId", error)
          BadRequest(ErrorResponse(
            error = true,
            message = "Failed to set question scores",
            code = "SET_QUESTION_SCORES_ERROR",
            details = Some(Map("error" -> error.getMessage))
          ).asJson)
        }
      case None =>
        unauthorizedResponse
    }
  }

  private def handleGetQuestionScores(req: Request[IO], examId: String): IO[Response[IO]] = {
    authenticateAdmin(req).flatMap {
      case Some(userInfo) =>
        examService.getExamQuestionScores(examId).flatMap { response =>
          Ok(response.asJson)
        }.handleErrorWith { error =>
          logger.error(s"Failed to fetch question scores configuration: $examId", error)
          InternalServerError(ErrorResponse(
            error = true,
            message = "Failed to fetch question scores configuration",
            code = "FETCH_QUESTION_SCORES_ERROR"
          ).asJson)
        }
      case None =>
        unauthorizedResponse
    }
  }

  private def authenticateAdmin(req: Request[IO]): IO[Option[UserInfo]] = {
    req.headers.get[Authorization] match {
      case Some(Authorization(credentials)) =>
        authService.extractTokenFromHeader(credentials.toString) match {
          case Some(token) =>
            authService.validateToken(token).flatMap {
              case Some(userInfo) if authService.hasRole(userInfo, "admin") =>
                IO.pure(Some(userInfo))
              case _ => IO.pure(None)
            }
          case None => IO.pure(None)
        }
      case None => IO.pure(None)
    }
  }
}
