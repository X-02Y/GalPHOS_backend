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

class GraderController(
  examService: ExamService,
  authService: AuthService
) {
  private val logger = LoggerFactory.getLogger("GraderController")

  private def unauthorizedResponse: IO[Response[IO]] = {
    Response[IO](Status.Unauthorized).withEntity(ErrorResponse(
      error = true,
      message = "Unauthorized access",
      code = "UNAUTHORIZED"
    ).asJson).pure[IO]
  }

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

    // Get exams available for grading
    case req @ GET -> Root / "api" / "grader" / "exams" =>
      handleGetAvailableExams(req).map(_.withHeaders(corsHeaders))

    // Get exam details with grading information
    case req @ GET -> Root / "api" / "grader" / "exams" / examId =>
      handleGetExamDetail(req, examId).map(_.withHeaders(corsHeaders))

    // Get exam grading progress and statistics
    case req @ GET -> Root / "api" / "grader" / "exams" / examId / "progress" =>
      handleGetExamGradingProgress(req, examId).map(_.withHeaders(corsHeaders))

    // Get question score configuration for exam
    case req @ GET -> Root / "api" / "grader" / "exams" / examId / "questions" / "scores" =>
      handleGetExamQuestionScores(req, examId).map(_.withHeaders(corsHeaders))
  }

  private def handleGetAvailableExams(req: Request[IO]): IO[Response[IO]] = {
    authenticateGrader(req).flatMap {
      case Some(userInfo) =>
        val status = req.uri.query.params.get("status")
        val pageStr = req.uri.query.params.get("page")
        val limitStr = req.uri.query.params.get("limit")
        val subject = req.uri.query.params.get("subject")
        
        val page = pageStr.flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(1)
        val limit = limitStr.flatMap(s => scala.util.Try(s.toInt).toOption).getOrElse(10)
        
        examService.getAvailableExamsForGrader(status, page, limit, subject).flatMap { response =>
          Ok(response.asJson)
        }.handleErrorWith { error =>
          logger.error("Failed to fetch gradeable exams list", error)
          InternalServerError(ErrorResponse(
            error = true,
            message = "Failed to fetch gradeable exams list",
            code = "FETCH_GRADER_EXAMS_ERROR"
          ).asJson)
        }
      case None =>
        unauthorizedResponse
    }
  }

  private def handleGetExamDetail(req: Request[IO], examId: String): IO[Response[IO]] = {
    authenticateGrader(req).flatMap {
      case Some(userInfo) =>
        examService.getExamDetailForGrader(examId).flatMap {
          case Some(examDetailResponse) =>
            Ok(examDetailResponse.asJson)
          case None =>
            NotFound(ErrorResponse(
              error = true,
              message = "考试不存在或不可访问",
              code = "EXAM_NOT_FOUND"
            ).asJson)
        }.handleErrorWith { error =>
          logger.error(s"Failed to fetch exam details: $examId", error)
          InternalServerError(ErrorResponse(
            error = true,
            message = "Failed to fetch exam details",
            code = "FETCH_EXAM_DETAIL_ERROR"
          ).asJson)
        }
      case None =>
        unauthorizedResponse
    }
  }

  private def handleGetExamGradingProgress(req: Request[IO], examId: String): IO[Response[IO]] = {
    authenticateGrader(req).flatMap {
      case Some(userInfo) =>
        examService.getExamGradingProgress(examId).flatMap {
          case Some(progressResponse) =>
            Ok(progressResponse.asJson)
          case None =>
            NotFound(ErrorResponse(
              error = true,
              message = "考试不存在",
              code = "EXAM_NOT_FOUND"
            ).asJson)
        }.handleErrorWith { error =>
          logger.error(s"获取考试评分进度失败: $examId", error)
          InternalServerError(ErrorResponse(
            error = true,
            message = "获取考试评分进度失败",
            code = "FETCH_GRADING_PROGRESS_ERROR"
          ).asJson)
        }
      case None =>
        unauthorizedResponse
    }
  }

  private def handleGetExamQuestionScores(req: Request[IO], examId: String): IO[Response[IO]] = {
    authenticateGrader(req).flatMap {
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

  private def authenticateGrader(req: Request[IO]): IO[Option[UserInfo]] = {
    req.headers.get[Authorization] match {
      case Some(Authorization(credentials)) =>
        authService.extractTokenFromHeader(credentials.toString) match {
          case Some(token) =>
            authService.validateToken(token).flatMap {
              case Some(userInfo) if authService.hasAnyRole(userInfo, List("grader", "admin")) =>
                IO.pure(Some(userInfo))
              case _ => IO.pure(None)
            }
          case None => IO.pure(None)
        }
      case None => IO.pure(None)
    }
  }
}
