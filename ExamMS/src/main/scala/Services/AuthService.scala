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
  private val jwtSecret = "GalPHOS_2025_SECRET_KEY" // Updated to match UserAuthService
  private val backend = AsyncHttpClientCatsBackend[IO]()

  def validateToken(token: String): IO[Option[UserInfo]] = {
    // Always validate with AuthService to get complete user information
    validateWithAuthService(token)
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
          // JWT from UserAuthService has 'sub' (subject) field with userId and 'isAdmin' field
          userId <- cursor.get[String]("sub").toOption
          isAdminStr <- cursor.get[String]("isAdmin").toOption
        } yield {
          val isAdmin = isAdminStr.toBoolean
          val role = if (isAdmin) "admin" else "user" // Default role, real role should come from validation
          UserInfo(userId, "unknown", role, None) // Placeholder values, full info comes from auth service validation
        }
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
                  val dataCursor = cursor.downField("data")
                  // Parse UserAuthService UserInfo structure
                  val userInfoResult = for {
                    username <- dataCursor.get[String]("username")
                    roleOpt = dataCursor.get[String]("role").toOption
                    typeOpt = dataCursor.get[String]("type").toOption
                  } yield {
                    // Determine role from either 'role' or 'type' field
                    val finalRole = typeOpt.getOrElse(roleOpt.getOrElse("user"))
                    // Generate a placeholder ID since UserAuthService doesn't return ID in validation
                    val id = java.util.UUID.nameUUIDFromBytes(username.getBytes).toString
                    UserInfo(id, username, finalRole, None)
                  }
                  
                  userInfoResult match {
                    case Right(userInfo) => IO.pure(Some(userInfo))
                    case Left(error) => 
                      logger.error(s"Failed to parse user info: $error")
                      IO.pure(None)
                  }
                case _ => 
                  logger.error("Auth service returned success=false")
                  IO.pure(None)
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
