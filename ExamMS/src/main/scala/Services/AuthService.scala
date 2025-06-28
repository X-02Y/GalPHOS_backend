package Services

import cats.effect.IO
import cats.implicits.*
import io.circe.*
import io.circe.generic.auto.*
import io.circe.parser.*
import pdi.jwt.{JwtAlgorithm, JwtClaim, JwtCirce}
import org.slf4j.LoggerFactory
import sttp.client3.*
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend
import Models.*
import Config.ServerConfig

class AuthService(config: ServerConfig) {
  private val logger = LoggerFactory.getLogger("AuthService")
  private val jwtSecret = "your-secret-key" // Should be loaded from config
  private val backend = AsyncHttpClientCatsBackend[IO]()

  def validateToken(token: String): IO[Option[UserInfo]] = {
    // First try to decode JWT locally
    decodeJwtToken(token).flatMap {
      case Some(userInfo) => IO.pure(Some(userInfo))
      case None => 
        // If local validation fails, call auth service
        validateWithAuthService(token)
    }
  }

  def extractTokenFromHeader(authHeader: String): Option[String] = {
    if (authHeader.startsWith("Bearer ")) {
      Some(authHeader.substring(7))
    } else {
      None
    }
  }

  def hasRole(userInfo: UserInfo, requiredRole: String): Boolean = {
    userInfo.role == requiredRole || userInfo.role == "admin" // Admin has access to everything
  }

  def hasAnyRole(userInfo: UserInfo, roles: List[String]): Boolean = {
    roles.contains(userInfo.role) || userInfo.role == "admin"
  }

  private def decodeJwtToken(token: String): IO[Option[UserInfo]] = IO {
    try {
      JwtCirce.decodeJson(token, jwtSecret, Seq(JwtAlgorithm.HS256)).toOption.flatMap { json =>
        val cursor = json.hcursor
        for {
          id <- cursor.get[String]("userId").toOption
          username <- cursor.get[String]("username").toOption
          role <- cursor.get[String]("role").toOption
          email = cursor.get[String]("email").toOption
        } yield UserInfo(id, username, role, email)
      }
    } catch {
      case e: Exception =>
        logger.error(s"JWT解码失败: ${e.getMessage}")
        None
    }
  }

  private def validateWithAuthService(token: String): IO[Option[UserInfo]] = {
    val request = basicRequest
      .get(uri"${config.authServiceUrl}/api/auth/validate")
      .header("Authorization", s"Bearer $token")
      .response(asString)

    backend.flatMap { b =>
      b.send(request).flatMap { response =>
      response.body match {
        case Right(body) =>
          parse(body) match {
            case Right(json) =>
              val cursor = json.hcursor
              cursor.get[Boolean]("success").toOption match {
                case Some(true) =>
                  cursor.get[UserInfo]("user").toOption match {
                    case Some(userInfo) => IO.pure(Some(userInfo))
                    case None => IO.pure(None)
                  }
                case _ => IO.pure(None)
              }
            case Left(error) =>
              logger.error(s"Auth service response parsing failed: $error")
              IO.pure(None)
          }
        case Left(error) =>
          logger.error(s"Auth service validation failed: $error")
          IO.pure(None)
      }
    }.handleErrorWith { error =>
      logger.error(s"Auth service call failed: ${error.getMessage}")
      IO.pure(None)
    }
    }
  }
}
