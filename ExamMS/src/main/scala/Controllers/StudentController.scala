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

class StudentController(
  examService: ExamService,
  authService: AuthService
) {
  private val logger = LoggerFactory.getLogger("StudentController")

  private def unauthorizedResponse: IO[Response[IO]] = {
    Response[IO](Status.Unauthorized).withEntity(ErrorResponse(
      error = true,
      message = "Unauthorized access",
      code = "UNAUTHORIZED"
    ).asJson).pure[IO]
  }

  // CORS support
  private val corsHeaders = Headers(
    "Access-Control-Allow-Origin" -> "*",
    "Access-Control-Allow-Methods" -> "GET, POST, PUT, DELETE, OPTIONS",
    "Access-Control-Allow-Headers" -> "Content-Type, Authorization"
  )

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    // CORS preflight request
    case req @ OPTIONS -> _ =>
      Ok().map(_.withHeaders(corsHeaders))

    // Get exam list for students (view only)
    case req @ GET -> Root / "api" / "student" / "exams" =>
      handleGetExams(req).map(_.withHeaders(corsHeaders))

    // Get exam details for students
    case req @ GET -> Root / "api" / "student" / "exams" / examId =>
      handleGetExamDetail(req, examId).map(_.withHeaders(corsHeaders))

    // Get detailed score for specific exam
    case req @ GET -> Root / "api" / "student" / "exams" / examId / "score" =>
      handleGetScoreDetail(req, examId).map(_.withHeaders(corsHeaders))

    // Get score ranking for specific exam
    case req @ GET -> Root / "api" / "student" / "exams" / examId / "ranking" =>
      handleGetScoreRanking(req, examId).map(_.withHeaders(corsHeaders))
  }

  private def handleGetExams(req: Request[IO]): IO[Response[IO]] = {
    authenticateStudent(req).flatMap {
      case Some(userInfo) =>
        examService.getExamsForStudent().flatMap { exams =>
          val response = ExamsResponse(exams, exams.length)
          Ok(response.asJson)
        }.handleErrorWith { error =>
          logger.error("Failed to fetch exam list", error)
          InternalServerError(ErrorResponse(
            error = true,
            message = "Failed to fetch exam list",
            code = "FETCH_EXAMS_ERROR"
          ).asJson)
        }
      case None =>
        unauthorizedResponse
    }
  }

  private def handleGetExamDetail(req: Request[IO], examId: String): IO[Response[IO]] = {
    authenticateStudent(req).flatMap {
      case Some(userInfo) =>
        examService.getExamDetailForStudent(examId).flatMap {
          case Some(examDetail) =>
            Ok(ExamDetailResponse(examDetail).asJson)
          case None =>
            NotFound(ErrorResponse(
              error = true,
              message = "Exam not found or not accessible",
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

  private def handleGetScoreDetail(req: Request[IO], examId: String): IO[Response[IO]] = {
    authenticateStudent(req).flatMap {
      case Some(userInfo) =>
        // This would typically call a score service to get the detailed score
        // For now, return a mock response
        val mockScore = DetailedScore(
          examId = examId,
          studentId = userInfo.id,
          totalScore = BigDecimal(85.5),
          maxScore = BigDecimal(100),
          percentage = BigDecimal(85.5),
          rank = Some(15),
          questionScores = List(
            QuestionDetailScore(1, BigDecimal(8.5), BigDecimal(10), Some("Good answer")),
            QuestionDetailScore(2, BigDecimal(15), BigDecimal(15), Some("Perfect!"))
          )
        )
        
        val mockBreakdown = ScoreBreakdown(
          byQuestion = List(
            QuestionBreakdown(1, BigDecimal(8.5), BigDecimal(10), false),
            QuestionBreakdown(2, BigDecimal(15), BigDecimal(15), true)
          ),
          byCategory = None
        )
        
        Ok(ScoreDetailResponse(mockScore, mockBreakdown).asJson)
      case None =>
        unauthorizedResponse
    }
  }

  private def handleGetScoreRanking(req: Request[IO], examId: String): IO[Response[IO]] = {
    authenticateStudent(req).flatMap {
      case Some(userInfo) =>
        // This would typically call a score service to get the ranking
        // For now, return a mock response
        val mockRanking = List(
          Ranking(1, "student1", "Alice Johnson", BigDecimal(95.5), BigDecimal(95.5)),
          Ranking(2, "student2", "Bob Smith", BigDecimal(92.0), BigDecimal(92.0)),
          Ranking(3, "student3", "Carol Davis", BigDecimal(88.5), BigDecimal(88.5))
        )
        
        Ok(ScoreRankingResponse(
          ranking = mockRanking,
          myRank = 15,
          totalParticipants = 50
        ).asJson)
      case None =>
        unauthorizedResponse
    }
  }

  private def authenticateStudent(req: Request[IO]): IO[Option[UserInfo]] = {
    req.headers.get[Authorization] match {
      case Some(Authorization(credentials)) =>
        authService.extractTokenFromHeader(credentials.toString) match {
          case Some(token) =>
            authService.validateToken(token).flatMap {
              case Some(userInfo) if authService.hasAnyRole(userInfo, List("student", "coach", "grader", "admin")) =>
                IO.pure(Some(userInfo))
              case _ => IO.pure(None)
            }
          case None => IO.pure(None)
        }
      case None => IO.pure(None)
    }
  }
}
