package Controllers

import cats.effect.IO
import cats.implicits.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.headers.Authorization
import io.circe.generic.auto.*
import io.circe.syntax.*
import Models.*
import Services.*
import org.slf4j.LoggerFactory

class SubmissionController(
  submissionService: SubmissionService,
  authService: AuthService,
  fileStorageService: FileStorageService
) {
  private val logger = LoggerFactory.getLogger("SubmissionController")

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
    
    // Health check endpoint
    case GET -> Root / "health" =>
      Ok(ApiResponse.success("OK")).map(_.withHeaders(corsHeaders))
    
    // Student APIs
    case req @ POST -> Root / "api" / "student" / "exams" / examId / "submit" =>
      handleStudentSubmission(req, examId)

    case GET -> Root / "api" / "student" / "exams" / examId / "submission" :? AuthTokenQueryParam(token) =>
      handleGetStudentSubmission(examId, token)

    case req @ POST -> Root / "api" / "student" / "upload" / "answer-image" =>
      handleStudentAnswerImageUpload(req)

    // Coach APIs
    case req @ POST -> Root / "api" / "coach" / "exams" / examId / "upload-answer" =>
      handleCoachAnswerImageUpload(req, examId)

    case req @ POST -> Root / "api" / "coach" / "exams" / examId / "submissions" =>
      handleCoachProxySubmission(req, examId)

    case GET -> Root / "api" / "coach" / "exams" / examId / "submissions" :? AuthTokenQueryParam(token) +& StudentUsernameQueryParam(studentUsername) =>
      handleGetCoachManagedSubmissions(examId, token, studentUsername)

    // Grader APIs
    case GET -> Root / "api" / "grader" / "submissions" / submissionId :? AuthTokenQueryParam(token) =>
      handleGetSubmissionForGrader(submissionId, token)

    case GET -> Root / "api" / "grader" / "exams" / examId / "progress" :? AuthTokenQueryParam(token) =>
      handleGetGradingProgress(examId, token)
  }

  // Query parameter extractors
  object AuthTokenQueryParam extends QueryParamDecoderMatcher[String]("token")
  object StudentUsernameQueryParam extends OptionalQueryParamDecoderMatcher[String]("studentUsername")

  // Extract auth token from request
  private def extractAuthToken(req: Request[IO]): Option[String] = {
    req.headers.get[Authorization].map(_.credentials.toString).orElse(
      req.uri.query.params.get("token")
    )
  }

  // Validate authentication and get user claims
  private def validateAuth(req: Request[IO]): IO[Either[Response[IO], JwtClaims]] = {
    extractAuthToken(req) match {
      case Some(token) =>
        authService.validateToken(token).flatMap {
          case Right(claims) => IO.pure(Right(claims))
          case Left(error) => 
            logger.warn(s"Authentication failed: ${error.message}")
            Forbidden(ApiResponse.error("Unauthorized").asJson).map(_.withHeaders(corsHeaders)).map(Left(_))
        }
      case None =>
        Forbidden(ApiResponse.error("Missing authentication token").asJson).map(_.withHeaders(corsHeaders)).map(Left(_))
    }
  }

  private def validateAuth(token: String): IO[Either[Response[IO], JwtClaims]] = {
    authService.validateToken(token).flatMap {
      case Right(claims) => IO.pure(Right(claims))
      case Left(error) => 
        logger.warn(s"Authentication failed: ${error.message}")
        Forbidden(ApiResponse.error("Unauthorized").asJson).map(_.withHeaders(corsHeaders)).map(Left(_))
    }
  }

  // Student submission endpoint
  private def handleStudentSubmission(req: Request[IO], examId: String): IO[Response[IO]] = {
    validateAuth(req).flatMap {
      case Right(claims) =>
        for {
          request <- req.as[StudentSubmissionRequest]
          result <- submissionService.submitExamAnswers(examId, claims.userId, request)
          response <- result match {
            case Right(submission) =>
              Ok(ApiResponse.success(submission).asJson).map(_.withHeaders(corsHeaders))
            case Left(error) =>
              error.code match {
                case "UNAUTHORIZED" => Forbidden(ApiResponse.error(error.message).asJson).map(_.withHeaders(corsHeaders))
                case "FORBIDDEN" => Forbidden(ApiResponse.error(error.message).asJson).map(_.withHeaders(corsHeaders))
                case "NOT_FOUND" => NotFound(ApiResponse.error(error.message).asJson).map(_.withHeaders(corsHeaders))
                case "BAD_REQUEST" => BadRequest(ApiResponse.error(error.message).asJson).map(_.withHeaders(corsHeaders))
                case _ => InternalServerError(ApiResponse.error(error.message).asJson).map(_.withHeaders(corsHeaders))
              }
          }
        } yield response
      case Left(response) => IO.pure(response)
    }.handleErrorWith { error =>
      logger.error(s"Error in student submission: ${error.getMessage}")
      InternalServerError(ApiResponse.error("Internal server error").asJson).map(_.withHeaders(corsHeaders))
    }
  }

  // Get student submission endpoint
  private def handleGetStudentSubmission(examId: String, token: String): IO[Response[IO]] = {
    validateAuth(token).flatMap {
      case Right(claims) =>
        for {
          result <- submissionService.getStudentSubmission(examId, claims.username)
          response <- result match {
            case Right(submission) =>
              Ok(ApiResponse.success(submission).asJson).map(_.withHeaders(corsHeaders))
            case Left(error) =>
              error.code match {
                case "NOT_FOUND" => NotFound(ApiResponse.error(error.message).asJson).map(_.withHeaders(corsHeaders))
                case _ => InternalServerError(ApiResponse.error(error.message).asJson).map(_.withHeaders(corsHeaders))
              }
          }
        } yield response
      case Left(response) => IO.pure(response)
    }.handleErrorWith { error =>
      logger.error(s"Error getting student submission: ${error.getMessage}")
      InternalServerError(ApiResponse.error("Internal server error").asJson).map(_.withHeaders(corsHeaders))
    }
  }

  // Student answer image upload endpoint
  private def handleStudentAnswerImageUpload(req: Request[IO]): IO[Response[IO]] = {
    validateAuth(req).flatMap {
      case Right(claims) =>
        for {
          // Try to parse as JSON first (base64 format)
          uploadRequest <- req.as[FileUploadRequest].handleErrorWith { _ =>
            // If JSON parsing fails, try the legacy format
            req.as[LegacyFileUploadRequest].map(legacy => FileUploadRequest(
              fileName = legacy.fileName,
              fileData = legacy.fileData,
              relatedId = legacy.relatedId,
              questionNumber = legacy.questionNumber,
              category = legacy.category,
              timestamp = legacy.timestamp,
              token = None
            )).handleErrorWith { _ =>
              // If both fail, try to handle as form data by reading raw body
              req.bodyText.compile.string.flatMap { bodyString =>
                logger.info(s"Raw request body: $bodyString")
                parseFormDataToJson(bodyString).flatMap { parsedRequest =>
                  IO.pure(parsedRequest)
                }
              }
            }
          }
          
          _ = logger.info(s"Processing upload request: fileName=${uploadRequest.fileName}, relatedId=${uploadRequest.relatedId}")
          
          // Decode base64 file data
          fileBytes <- IO.fromTry(scala.util.Try(java.util.Base64.getDecoder.decode(uploadRequest.fileData)))
          
          result <- fileStorageService.uploadAnswerImage(
            fileBytes, 
            uploadRequest.fileName, 
            uploadRequest.relatedId, 
            uploadRequest.questionNumber
          )
          response <- result match {
            case Right(fileResponse) =>
              Ok(ApiResponse.success(fileResponse).asJson).map(_.withHeaders(corsHeaders))
            case Left(error) =>
              BadRequest(ApiResponse.error(error.message).asJson).map(_.withHeaders(corsHeaders))
          }
        } yield response
      case Left(response) => IO.pure(response)
    }.handleErrorWith { error =>
      logger.error(s"Error uploading answer image: ${error.getMessage}")
      error.getMessage match {
        case msg if msg.contains("Base64") =>
          BadRequest(ApiResponse.error("Invalid base64 encoded file data").asJson).map(_.withHeaders(corsHeaders))
        case _ =>
          InternalServerError(ApiResponse.error("Internal server error").asJson).map(_.withHeaders(corsHeaders))
      }
    }
  }

  // Helper method to parse form data and convert to FileUploadRequest
  private def parseFormDataToJson(bodyString: String): IO[FileUploadRequest] = {
    IO.raiseError(new RuntimeException("Form data parsing not yet implemented - please use JSON format"))
  }

  // Coach answer image upload endpoint
  private def handleCoachAnswerImageUpload(req: Request[IO], examId: String): IO[Response[IO]] = {
    validateAuth(req).flatMap {
      case Right(claims) =>
        validateRole(claims, "coach").flatMap {
          case Right(_) =>
            for {
              uploadRequest <- req.as[CoachFileUploadRequest]
              
              // Decode base64 file data
              fileBytes <- IO.fromTry(scala.util.Try(java.util.Base64.getDecoder.decode(uploadRequest.fileData)))
              
              result <- fileStorageService.uploadAnswerImage(
                fileBytes, 
                uploadRequest.fileName, 
                examId, 
                uploadRequest.questionNumber, 
                Some(uploadRequest.studentUsername)
              )
              response <- result match {
                case Right(fileResponse) =>
                  Ok(ApiResponse.success(fileResponse).asJson).map(_.withHeaders(corsHeaders))
                case Left(error) =>
                  BadRequest(ApiResponse.error(error.message).asJson).map(_.withHeaders(corsHeaders))
              }
            } yield response
          case Left(response) => IO.pure(response)
        }
      case Left(response) => IO.pure(response)
    }.handleErrorWith { error =>
      logger.error(s"Error uploading coach answer image: ${error.getMessage}")
      InternalServerError(ApiResponse.error("Internal server error").asJson).map(_.withHeaders(corsHeaders))
    }
  }

  // Coach proxy submission endpoint
  private def handleCoachProxySubmission(req: Request[IO], examId: String): IO[Response[IO]] = {
    validateAuth(req).flatMap {
      case Right(claims) =>
        validateRole(claims, "coach").flatMap {
          case Right(_) =>
            for {
              request <- req.as[CoachSubmissionRequest]
              result <- submissionService.coachProxySubmission(examId, claims.userId, request)
              response <- result match {
                case Right(submission) =>
                  Ok(ApiResponse.success(submission).asJson).map(_.withHeaders(corsHeaders))
                case Left(error) =>
                  error.code match {
                    case "FORBIDDEN" => Forbidden(ApiResponse.error(error.message).asJson).map(_.withHeaders(corsHeaders))
                    case "NOT_FOUND" => NotFound(ApiResponse.error(error.message).asJson).map(_.withHeaders(corsHeaders))
                    case _ => InternalServerError(ApiResponse.error(error.message).asJson).map(_.withHeaders(corsHeaders))
                  }
              }
            } yield response
          case Left(response) => IO.pure(response)
        }
      case Left(response) => IO.pure(response)
    }.handleErrorWith { error =>
      logger.error(s"Error in coach proxy submission: ${error.getMessage}")
      InternalServerError(ApiResponse.error("Internal server error").asJson).map(_.withHeaders(corsHeaders))
    }
  }

  // Get coach managed submissions endpoint
  private def handleGetCoachManagedSubmissions(examId: String, token: String, studentUsername: Option[String]): IO[Response[IO]] = {
    validateAuth(token).flatMap {
      case Right(claims) =>
        validateRole(claims, "coach").flatMap {
          case Right(_) =>
            for {
              result <- submissionService.getCoachManagedSubmissions(examId, claims.userId, studentUsername)
              response <- result match {
                case Right(submissions) =>
                  Ok(ApiResponse.success(submissions).asJson).map(_.withHeaders(corsHeaders))
                case Left(error) =>
                  InternalServerError(ApiResponse.error(error.message).asJson).map(_.withHeaders(corsHeaders))
              }
            } yield response
          case Left(response) => IO.pure(response)
        }
      case Left(response) => IO.pure(response)
    }.handleErrorWith { error =>
      logger.error(s"Error getting coach managed submissions: ${error.getMessage}")
      InternalServerError(ApiResponse.error("Internal server error").asJson).map(_.withHeaders(corsHeaders))
    }
  }

  // Get submission for grader endpoint
  private def handleGetSubmissionForGrader(submissionId: String, token: String): IO[Response[IO]] = {
    validateAuth(token).flatMap {
      case Right(claims) =>
        validateRole(claims, "grader").flatMap {
          case Right(_) =>
            for {
              result <- submissionService.getSubmissionForGrader(submissionId)
              response <- result match {
                case Right(submission) =>
                  Ok(ApiResponse.success(submission).asJson).map(_.withHeaders(corsHeaders))
                case Left(error) =>
                  error.code match {
                    case "NOT_FOUND" => NotFound(ApiResponse.error(error.message).asJson).map(_.withHeaders(corsHeaders))
                    case _ => InternalServerError(ApiResponse.error(error.message).asJson).map(_.withHeaders(corsHeaders))
                  }
              }
            } yield response
          case Left(response) => IO.pure(response)
        }
      case Left(response) => IO.pure(response)
    }.handleErrorWith { error =>
      logger.error(s"Error getting submission for grader: ${error.getMessage}")
      InternalServerError(ApiResponse.error("Internal server error").asJson).map(_.withHeaders(corsHeaders))
    }
  }

  // Get grading progress endpoint
  private def handleGetGradingProgress(examId: String, token: String): IO[Response[IO]] = {
    validateAuth(token).flatMap {
      case Right(claims) =>
        validateRole(claims, "grader").flatMap {
          case Right(_) =>
            for {
              result <- submissionService.getGradingProgress(examId, claims.userId)
              response <- result match {
                case Right(progress) =>
                  Ok(ApiResponse.success(progress).asJson).map(_.withHeaders(corsHeaders))
                case Left(error) =>
                  InternalServerError(ApiResponse.error(error.message).asJson).map(_.withHeaders(corsHeaders))
              }
            } yield response
          case Left(response) => IO.pure(response)
        }
      case Left(response) => IO.pure(response)
    }.handleErrorWith { error =>
      logger.error(s"Error getting grading progress: ${error.getMessage}")
      InternalServerError(ApiResponse.error("Internal server error").asJson).map(_.withHeaders(corsHeaders))
    }
  }

  // Helper methods
  private def validateRole(claims: JwtClaims, expectedRole: String): IO[Either[Response[IO], Unit]] = {
    if (claims.role == expectedRole) {
      IO.pure(Right(()))
    } else {
      Forbidden(ApiResponse.error(s"Access denied. Required role: $expectedRole").asJson).map(_.withHeaders(corsHeaders)).map(Left(_))
    }
  }
}
