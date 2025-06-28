package Services

import cats.effect.IO
import sttp.client3.*
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend
import io.circe.parser.*
import io.circe.syntax.*
import Config.ServerConfig
import java.util.UUID

case class AuthValidationResponse(
  valid: Boolean,
  userId: Option[UUID],
  role: Option[String],
  message: Option[String]
)

object AuthValidationResponse {
  import io.circe.{Decoder, Encoder}
  import io.circe.generic.semiauto.*
  
  given Decoder[AuthValidationResponse] = deriveDecoder[AuthValidationResponse]
  given Encoder[AuthValidationResponse] = deriveEncoder[AuthValidationResponse]
}

class AuthService(config: ServerConfig) {
  
  private val backend = AsyncHttpClientCatsBackend[IO]()
  
  def validateToken(token: String): IO[Either[String, AuthValidationResponse]] = {
    val url = s"${config.authServiceUrl}/api/auth/validate"
    
    val request = basicRequest
      .post(uri"$url")
      .header("Authorization", s"Bearer $token")
      .header("Content-Type", "application/json")
    
    backend.flatMap { implicit b =>
      request.send(b).map { response =>
        response.code.code match {
          case 200 =>
            response.body match {
              case Right(body) =>
                decode[AuthValidationResponse](body) match {
                  case Right(authResponse) => Right(authResponse)
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
      case Right(authResponse) if authResponse.valid =>
        authResponse.role match {
          case Some(role) if role == requiredRole =>
            authResponse.userId match {
              case Some(userId) => Right((userId, role))
              case None => Left("User ID not found in auth response")
            }
          case Some(role) => Left(s"Insufficient permissions. Required: $requiredRole, Got: $role")
          case None => Left("Role not found in auth response")
        }
      case Right(_) => Left("Token validation failed")
      case Left(error) => Left(error)
    }
  }
  
  def requireAnyRole(token: String, allowedRoles: List[String]): IO[Either[String, (UUID, String)]] = {
    validateToken(token).map {
      case Right(authResponse) if authResponse.valid =>
        authResponse.role match {
          case Some(role) if allowedRoles.contains(role) =>
            authResponse.userId match {
              case Some(userId) => Right((userId, role))
              case None => Left("User ID not found in auth response")
            }
          case Some(role) => Left(s"Insufficient permissions. Required one of: ${allowedRoles.mkString(", ")}, Got: $role")
          case None => Left("Role not found in auth response")
        }
      case Right(_) => Left("Token validation failed")
      case Left(error) => Left(error)
    }
  }
}
