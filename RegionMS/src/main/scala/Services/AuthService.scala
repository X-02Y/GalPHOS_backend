package Services

import cats.effect.IO
import sttp.client3.*
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend
import io.circe.parser.*
import io.circe.syntax.*
import Config.ServerConfig
import java.util.UUID

case class AuthValidationResponse(
  success: Boolean,
  data: Option[UserData],
  message: Option[String]
)

case class UserData(
  username: String,
  role: Option[String] = None,
  `type`: Option[String] = None,
  province: Option[String] = None,
  school: Option[String] = None,
  avatar: Option[String] = None
)

object AuthValidationResponse {
  import io.circe.{Decoder, Encoder}
  import io.circe.generic.semiauto.*
  
  given Decoder[UserData] = deriveDecoder[UserData]
  given Encoder[UserData] = deriveEncoder[UserData]
  given Decoder[AuthValidationResponse] = deriveDecoder[AuthValidationResponse]
  given Encoder[AuthValidationResponse] = deriveEncoder[AuthValidationResponse]
}

class AuthService(config: ServerConfig) {
  
  private val backend = AsyncHttpClientCatsBackend[IO]()
  
  def validateToken(token: String): IO[Either[String, AuthValidationResponse]] = {
    val url = s"${config.authServiceUrl}/api/auth/validate"
    
    val request = basicRequest
      .get(uri"$url")
      .header("Authorization", s"Bearer $token")
      .header("Content-Type", "application/json")
    
    backend.flatMap { implicit b =>
      request.send(b).map { response =>
        response.code.code match {
          case 200 =>
            response.body match {
              case Right(body) =>
                decode[AuthValidationResponse](body) match {
                  case Right(authResponse) if authResponse.success => Right(authResponse)
                  case Right(authResponse) => Left(authResponse.message.getOrElse("Token validation failed"))
                  case Left(error) => Left(s"Failed to parse auth response: ${error.getMessage}")
                }
              case Left(error) => Left(s"Auth service error: $error")
            }
          case 401 => Left("Invalid or expired token")
          case 403 => Left("Insufficient permissions")
          case _ => Left(s"Auth service returned status: ${response.code.code}")
        }
      }
    }.handleErrorWith { throwable =>
      IO.pure(Left(s"Failed to connect to auth service: ${throwable.getMessage}"))
    }
  }
  
  def extractTokenFromHeader(authHeader: Option[String]): Option[String] = {
    authHeader.flatMap { header =>
      if (header.startsWith("Bearer ")) {
        Some(header.substring(7))
      } else {
        None
      }
    }
  }
  
  def requireRole(token: String, requiredRole: String): IO[Either[String, (UUID, String)]] = {
    validateToken(token).map {
      case Right(authResponse) =>
        authResponse.data match {
          case Some(userData) =>
            // Check if user is admin (based on type field) or has the required role
            val userRole = userData.`type`.getOrElse(userData.role.getOrElse(""))
            if (userRole == requiredRole || (requiredRole == "admin" && userData.`type`.contains("admin"))) {
              // For admin users, we need to generate a UUID since they don't have userId in response
              val userId = if (userData.`type`.contains("admin")) {
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000") // Use the admin UUID from database
              } else {
                UUID.randomUUID() // Generate UUID for regular users - this should ideally come from response
              }
              Right((userId, userRole))
            } else {
              Left(s"Insufficient permissions. Required: $requiredRole, Got: $userRole")
            }
          case None => Left("User data not found in auth response")
        }
      case Left(error) => Left(error)
    }
  }
  
  def requireAnyRole(token: String, allowedRoles: List[String]): IO[Either[String, (UUID, String)]] = {
    validateToken(token).map {
      case Right(authResponse) =>
        authResponse.data match {
          case Some(userData) =>
            val userRole = userData.`type`.getOrElse(userData.role.getOrElse(""))
            if (allowedRoles.contains(userRole) || (allowedRoles.contains("admin") && userData.`type`.contains("admin"))) {
              val userId = if (userData.`type`.contains("admin")) {
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
              } else {
                UUID.randomUUID()
              }
              Right((userId, userRole))
            } else {
              Left(s"Insufficient permissions. Required one of: ${allowedRoles.mkString(", ")}, Got: $userRole")
            }
          case None => Left("User data not found in auth response")
        }
      case Left(error) => Left(error)
    }
  }
}
