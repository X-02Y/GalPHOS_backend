package Services

import cats.effect.IO
import Models.*
import pdi.jwt.{JwtCirce, JwtAlgorithm, JwtClaim}
import io.circe.parser.decode
import io.circe.generic.auto.*
import org.slf4j.LoggerFactory
import Config.ServiceConfig
import scala.util

trait AuthService {
  def validateToken(token: String): IO[Option[UserClaims]]
  def extractUserFromToken(token: String): IO[Option[UserClaims]]
}

class JwtAuthService(config: ServiceConfig) extends AuthService {
  private val logger = LoggerFactory.getLogger("JwtAuthService")
  private val jwtSecret = config.jwtSecret

  override def validateToken(token: String): IO[Option[UserClaims]] = {
    IO {
      try {
        val cleanToken = if (token.startsWith("Bearer ")) token.substring(7) else token
        
        JwtCirce.decode(cleanToken, jwtSecret, Seq(JwtAlgorithm.HS256)) match {
          case util.Success(claims) =>
            decode[UserClaims](claims.content) match {
              case Right(userClaims) =>
                val currentTime = System.currentTimeMillis() / 1000
                if (userClaims.exp > currentTime) {
                  logger.debug(s"Token validated for user: ${userClaims.username}")
                  Some(userClaims)
                } else {
                  logger.warn("Token has expired")
                  None
                }
              case Left(error) =>
                logger.error(s"Failed to decode user claims: $error")
                None
            }
          case util.Failure(error) =>
            logger.error(s"JWT validation failed: ${error.getMessage}")
            None
        }
      } catch {
        case e: Exception =>
          logger.error(s"Error validating token: ${e.getMessage}")
          None
      }
    }
  }

  override def extractUserFromToken(token: String): IO[Option[UserClaims]] = {
    validateToken(token)
  }
}

object AuthService {
  def extractBearerToken(authHeader: Option[String]): Option[String] = {
    authHeader.flatMap { header =>
      if (header.startsWith("Bearer ")) {
        Some(header.substring(7))
      } else {
        None
      }
    }
  }
}
