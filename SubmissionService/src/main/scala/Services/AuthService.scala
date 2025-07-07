package Services

import cats.effect.IO
import cats.implicits.*
import org.http4s.*
import org.http4s.client.Client
import org.http4s.circe.*
import org.http4s.headers.*
import io.circe.generic.auto.*
import io.circe.parser.*
import io.circe.syntax.*
import pdi.jwt.{JwtAlgorithm, JwtClaim, JwtCirce}
import Models.*
import Config.ServiceConfig
import org.slf4j.LoggerFactory

trait AuthService {
  def validateToken(token: String): IO[Either[ServiceError, JwtClaims]]
  def getUserInfo(userId: String): IO[Either[ServiceError, UserInfo]]
  def checkCoachStudentRelation(coachId: String, studentUsername: String): IO[Boolean]
}

class HttpAuthService(config: ServiceConfig, client: Client[IO]) extends AuthService {
  private val logger = LoggerFactory.getLogger("HttpAuthService")

  override def validateToken(token: String): IO[Either[ServiceError, JwtClaims]] = {
    IO {
      try {
        val cleanToken = if (token.startsWith("Bearer ")) token.substring(7) else token
        
        JwtCirce.decode(cleanToken, config.jwtSecret, Seq(JwtAlgorithm.HS256)) match {
          case scala.util.Success(claims) =>
            decode[JwtClaims](claims.content) match {
              case Right(jwtClaims) =>
                // Check if token is expired
                val currentTime = System.currentTimeMillis() / 1000
                if (jwtClaims.exp < currentTime) {
                  Left(ServiceError.unauthorized("Token expired"))
                } else {
                  Right(jwtClaims)
                }
              case Left(error) =>
                logger.error(s"Failed to decode JWT claims: $error")
                Left(ServiceError.unauthorized("Invalid token format"))
            }
          case scala.util.Failure(error) =>
            logger.error(s"JWT validation failed: $error")
            Left(ServiceError.unauthorized("Invalid token"))
        }
      } catch {
        case ex: Exception =>
          logger.error(s"Token validation error: ${ex.getMessage}")
          Left(ServiceError.unauthorized("Token validation failed"))
      }
    }
  }

  override def getUserInfo(userId: String): IO[Either[ServiceError, UserInfo]] = {
    val uri = Uri.unsafeFromString(s"http://${config.externalServices.userManagementService.host}:${config.externalServices.userManagementService.port}/api/internal/users/$userId")
    
    val request = Request[IO](
      method = Method.GET,
      uri = uri,
      headers = Headers(
        Authorization(Credentials.Token(AuthScheme.Bearer, config.jwtSecret)),
        `Content-Type`(MediaType.application.json)
      )
    )

    client.expect[String](request).attempt.flatMap {
      case Right(response) =>
        IO {
          decode[ApiResponse[UserInfo]](response) match {
            case Right(apiResponse) if apiResponse.success =>
              apiResponse.data match {
                case Some(userInfo) => Right(userInfo)
                case None => Left(ServiceError.notFound("User not found"))
              }
            case Right(apiResponse) =>
              Left(ServiceError.badRequest(apiResponse.message.getOrElse("Failed to get user info")))
            case Left(error) =>
              logger.error(s"Failed to parse user info response: $error")
              Left(ServiceError.internalError("Failed to parse user info"))
          }
        }
      case Left(error) =>
        logger.error(s"Failed to get user info: $error")
        IO.pure(Left(ServiceError.internalError("Failed to connect to user management service")))
    }
  }

  override def checkCoachStudentRelation(coachId: String, studentUsername: String): IO[Boolean] = {
    val uri = Uri.unsafeFromString(s"http://${config.externalServices.userManagementService.host}:${config.externalServices.userManagementService.port}/api/internal/coach/$coachId/students/$studentUsername")
    
    val request = Request[IO](
      method = Method.GET,
      uri = uri,
      headers = Headers(
        Authorization(Credentials.Token(AuthScheme.Bearer, config.jwtSecret)),
        `Content-Type`(MediaType.application.json)
      )
    )

    client.expect[String](request).attempt.flatMap {
      case Right(response) =>
        IO {
          decode[ApiResponse[CoachStudentRelation]](response) match {
            case Right(apiResponse) => apiResponse.success
            case Left(_) => false
          }
        }
      case Left(error) =>
        logger.warn(s"Failed to check coach-student relation: $error")
        IO.pure(false)
    }
  }
}

// Mock implementation for testing
class MockAuthService extends AuthService {
  private val logger = LoggerFactory.getLogger("MockAuthService")

  override def validateToken(token: String): IO[Either[ServiceError, JwtClaims]] = {
    IO.pure(Right(JwtClaims("user123", "testuser", "student", System.currentTimeMillis() / 1000 + 3600)))
  }

  override def getUserInfo(userId: String): IO[Either[ServiceError, UserInfo]] = {
    IO.pure(Right(UserInfo(userId, "testuser", Some("Test User"), "student", Some(true))))
  }

  override def checkCoachStudentRelation(coachId: String, studentUsername: String): IO[Boolean] = {
    IO.pure(true)
  }
}
