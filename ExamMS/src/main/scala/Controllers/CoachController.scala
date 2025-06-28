package Controllers

import cats.effect.IO
import cats.implicits.*
import io.circe.*
import io.circe.syntax.*
import io.circe.generic.auto.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.headers.{Authorization, `Content-Type`}
import org.typelevel.ci.CIStringSyntax
import org.slf4j.LoggerFactory
import Models.*
import Services.*

class CoachController(
  examService: ExamService,
  authService: AuthService,
  fileService: FileService
) {
  private val logger = LoggerFactory.getLogger("CoachController")

  // CORS support
  private val corsHeaders = Headers(
    "Access-Control-Allow-Origin" -> "*",
    "Access-Control-Allow-Methods" -> "GET, POST, PUT, DELETE, OPTIONS",
    "Access-Control-Allow-Headers" -> "Content-Type, Authorization"
  )

  private def unauthorizedResponse: IO[Response[IO]] = {
    Response[IO](Status.Unauthorized).withEntity(ErrorResponse(
      error = true,
      message = "Unauthorized access",
      code = "UNAUTHORIZED"
    ).asJson).pure[IO]
  }

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    // CORS 预检请求
    case req @ OPTIONS -> _ =>
      Ok().map(_.withHeaders(corsHeaders))

    // Get exam list with filtering options
    case req @ GET -> Root / "api" / "coach" / "exams" =>
      handleGetExams(req).map(_.withHeaders(corsHeaders))

    // Get comprehensive exam details with statistics
    case req @ GET -> Root / "api" / "coach" / "exams" / examId =>
      handleGetExamDetails(req, examId).map(_.withHeaders(corsHeaders))

    // Download exam-related files
    case req @ GET -> Root / "api" / "coach" / "exams" / examId / "download" =>
      handleDownloadExamFile(req, examId).map(_.withHeaders(corsHeaders))

    // Get comprehensive exam score statistics
    case req @ GET -> Root / "api" / "coach" / "exams" / examId / "scores" / "statistics" =>
      handleGetExamScoreStatistics(req, examId).map(_.withHeaders(corsHeaders))

    // Get student ranking for specific exam
    case req @ GET -> Root / "api" / "coach" / "exams" / examId / "ranking" =>
      handleGetStudentRanking(req, examId).map(_.withHeaders(corsHeaders))

    // Export exam score report
    case req @ POST -> Root / "api" / "coach" / "exams" / examId / "scores" / "export" =>
      handleExportScoreReport(req, examId).map(_.withHeaders(corsHeaders))
  }

  private def handleGetExams(req: Request[IO]): IO[Response[IO]] = {
    authenticateCoach(req).flatMap {
      case Some(userInfo) =>
        val status = req.uri.query.params.get("status")
        val timeRange = req.uri.query.params.get("timeRange")
        
        examService.getExamsForCoach(status, timeRange).flatMap { exams =>
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

  private def handleGetExamDetails(req: Request[IO], examId: String): IO[Response[IO]] = {
    authenticateCoach(req).flatMap {
      case Some(userInfo) =>
        examService.getExamDetailsForCoach(examId).flatMap {
          case Some(examDetailResponse) =>
            Ok(examDetailResponse.asJson)
          case None =>
            NotFound(ErrorResponse(
              error = true,
              message = "Exam not found",
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

  private def handleDownloadExamFile(req: Request[IO], examId: String): IO[Response[IO]] = {
    authenticateCoach(req).flatMap {
      case Some(userInfo) =>
        val fileType = req.uri.query.params.getOrElse("fileType", "question_paper")
        
        fileService.downloadExamFile(examId, fileType).flatMap {
          case Some(fileContent) =>
            Ok(fileContent)
              .map(_.withContentType(`Content-Type`(MediaType.application.`octet-stream`)))
              .map(_.withHeaders(Header.Raw(ci"Content-Disposition", s"attachment; filename=exam_$examId.$fileType")))
          case None =>
            NotFound(ErrorResponse(
              error = true,
              message = "File not found",
              code = "FILE_NOT_FOUND"
            ).asJson)
        }.handleErrorWith { error =>
          logger.error(s"Failed to download file: $examId, $fileType", error)
          InternalServerError(ErrorResponse(
            error = true,
            message = "Failed to download file",
            code = "DOWNLOAD_FILE_ERROR"
          ).asJson)
        }
      case None =>
        unauthorizedResponse
    }
  }

  private def handleGetExamScoreStatistics(req: Request[IO], examId: String): IO[Response[IO]] = {
    authenticateCoach(req).flatMap {
      case Some(userInfo) =>
        // Mock statistics data - would typically come from score statistics service
        val mockStats = ExamScoreStats(
          totalParticipants = 50,
          averageScore = BigDecimal(78.5),
          median = BigDecimal(80.0),
          standardDeviation = BigDecimal(12.3),
          highestScore = BigDecimal(98.5),
          lowestScore = BigDecimal(45.0),
          passRate = BigDecimal(85.0)
        )
        
        val mockDistribution = ScoreDistribution(
          ranges = List(
            ScoreRange(BigDecimal(90), BigDecimal(100), 8, BigDecimal(16.0)),
            ScoreRange(BigDecimal(80), BigDecimal(89), 15, BigDecimal(30.0)),
            ScoreRange(BigDecimal(70), BigDecimal(79), 12, BigDecimal(24.0)),
            ScoreRange(BigDecimal(60), BigDecimal(69), 10, BigDecimal(20.0)),
            ScoreRange(BigDecimal(0), BigDecimal(59), 5, BigDecimal(10.0))
          )
        )
        
        Ok(ExamScoreStatisticsResponse(mockStats, mockDistribution).asJson)
      case None =>
        unauthorizedResponse
    }
  }

  private def handleGetStudentRanking(req: Request[IO], examId: String): IO[Response[IO]] = {
    authenticateCoach(req).flatMap {
      case Some(userInfo) =>
        val studentId = req.uri.query.params.get("studentId")
        
        // Mock ranking data - would typically come from score statistics service
        val mockRankings = List(
          StudentRanking(1, "student1", "Alice Johnson", BigDecimal(95.5), BigDecimal(95.5), java.time.Instant.now()),
          StudentRanking(2, "student2", "Bob Smith", BigDecimal(92.0), BigDecimal(92.0), java.time.Instant.now()),
          StudentRanking(3, "student3", "Carol Davis", BigDecimal(88.5), BigDecimal(88.5), java.time.Instant.now())
        )
        
        val mockMyStudents = List(
          StudentRank("student1", "Alice Johnson", 1, BigDecimal(95.5), BigDecimal(95.5)),
          StudentRank("student3", "Carol Davis", 3, BigDecimal(88.5), BigDecimal(88.5))
        )
        
        Ok(StudentRankingResponse(mockRankings, mockMyStudents).asJson)
      case None =>
        unauthorizedResponse
    }
  }

  private def handleExportScoreReport(req: Request[IO], examId: String): IO[Response[IO]] = {
    authenticateCoach(req).flatMap {
      case Some(userInfo) =>
        req.as[Json].flatMap { json =>
          val format = json.hcursor.get[String]("format").getOrElse("csv")
          
          // Generate report content based on format
          val reportContent = generateScoreReport(examId, format)
          val fileName = s"exam_${examId}_scores.$format"
          
          Ok(reportContent)
            .map(_.withContentType(getContentTypeForFormat(format)))
            .map(_.withHeaders(Header.Raw(ci"Content-Disposition", s"attachment; filename=$fileName")))
        }.handleErrorWith { error =>
          logger.error(s"Failed to export score report: $examId", error)
          InternalServerError(ErrorResponse(
            error = true,
            message = "Failed to export score report",
            code = "EXPORT_REPORT_ERROR"
          ).asJson)
        }
      case None =>
        unauthorizedResponse
    }
  }

  private def generateScoreReport(examId: String, format: String): String = {
    format.toLowerCase match {
      case "csv" =>
        "Student Name,Student ID,Score,Percentage,Rank\n" +
        "Alice Johnson,student1,95.5,95.5%,1\n" +
        "Bob Smith,student2,92.0,92.0%,2\n" +
        "Carol Davis,student3,88.5,88.5%,3\n"
      case "json" =>
        """[
          {"studentName": "Alice Johnson", "studentId": "student1", "score": 95.5, "percentage": 95.5, "rank": 1},
          {"studentName": "Bob Smith", "studentId": "student2", "score": 92.0, "percentage": 92.0, "rank": 2},
          {"studentName": "Carol Davis", "studentId": "student3", "score": 88.5, "percentage": 88.5, "rank": 3}
        ]"""
      case _ =>
        "Format not supported"
    }
  }

  private def getContentTypeForFormat(format: String): `Content-Type` = {
    format.toLowerCase match {
      case "csv" => `Content-Type`(MediaType.text.csv)
      case "json" => `Content-Type`(MediaType.application.json)
      case "pdf" => `Content-Type`(MediaType.application.pdf)
      case _ => `Content-Type`(MediaType.text.plain)
    }
  }

  private def authenticateCoach(req: Request[IO]): IO[Option[UserInfo]] = {
    req.headers.get[Authorization] match {
      case Some(Authorization(credentials)) =>
        authService.extractTokenFromHeader(credentials.toString) match {
          case Some(token) =>
            authService.validateToken(token).flatMap {
              case Some(userInfo) if authService.hasAnyRole(userInfo, List("coach", "admin")) =>
                IO.pure(Some(userInfo))
              case _ => IO.pure(None)
            }
          case None => IO.pure(None)
        }
      case None => IO.pure(None)
    }
  }
}
