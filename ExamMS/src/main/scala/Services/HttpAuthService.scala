package Services

import cats.effect.IO
import pdi.jwt.JwtAlgorithm
import io.circe.generic.auto.*
import io.circe.parser.*
import io.circe.Json
import Models.*
import Config.ServiceConfig
import org.slf4j.LoggerFactory
import java.time.Instant
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.net.URI
import java.time.Duration

// HTTP-based AuthService that calls UserAuthService for role validation
class HttpAuthService(config: ServiceConfig) extends AuthService {
  private val logger = LoggerFactory.getLogger("HttpAuthService")
  private val jwtSecret = config.jwtSecret
  private val algorithm = JwtAlgorithm.HS256
  private val userAuthServiceUrl = "http://localhost:3001" // UserAuthService URL
  
  private val httpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .build()

  override def validateToken(token: String): IO[Option[JwtPayload]] = {
    for {
      // First, try to validate token via UserAuthService HTTP API
      httpResult <- validateTokenViaHttp(token)
      
      // If HTTP validation fails, fall back to local JWT validation
      result <- httpResult match {
        case Some(payload) => IO.pure(Some(payload))
        case None => validateTokenLocally(token)
      }
    } yield result
  }

  private def validateTokenViaHttp(token: String): IO[Option[JwtPayload]] = {
    IO.blocking {
      try {
        val request = HttpRequest.newBuilder()
          .uri(URI.create(s"$userAuthServiceUrl/api/auth/validate"))
          .header("Authorization", s"Bearer $token")
          .header("Content-Type", "application/json")
          .GET()
          .build()

        logger.debug(s"Validating token via HTTP: ${token.take(20)}...")
        
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        
        if (response.statusCode() == 200) {
          parse(response.body()) match {
            case Right(json) =>
              val cursor = json.hcursor
              cursor.downField("success").as[Boolean] match {
                case Right(true) =>
                  cursor.downField("data").as[Json] match {
                    case Right(data) =>
                      val dataCursor = data.hcursor
                      val username = dataCursor.downField("username").as[String].getOrElse("")
                      val userType = dataCursor.downField("type").as[String].toOption
                      val role = dataCursor.downField("role").as[String].toOption
                      
                      // Extract userId from token for consistency
                      val userIdOpt = extractUserIdFromToken(token)
                      
                      userIdOpt match {
                        case Some(userId) =>
                          val finalRole = userType.getOrElse(role.getOrElse("student"))
                          logger.info(s"Token validation successful via HTTP: username=$username, role=$finalRole")
                          
                          Some(JwtPayload(
                            userId = userId,
                            username = username,
                            role = finalRole,
                            exp = extractExpirationFromToken(token).getOrElse(0L)
                          ))
                        case None =>
                          logger.error("Could not extract userId from token")
                          None
                      }
                    case Left(error) =>
                      logger.error(s"Failed to parse data field: $error")
                      None
                  }
                case Right(false) =>
                  logger.warn("Token validation failed according to auth service")
                  None
                case Left(error) =>
                  logger.error(s"Failed to parse success field: $error")
                  None
              }
            case Left(error) =>
              logger.error(s"Failed to parse validation response: ${error.message}")
              None
          }
        } else {
          logger.warn(s"Token validation failed with HTTP status: ${response.statusCode()}")
          None
        }
      } catch {
        case ex: Exception =>
          logger.error(s"HTTP token validation failed: ${ex.getMessage}", ex)
          None
      }
    }
  }

  private def validateTokenLocally(token: String): IO[Option[JwtPayload]] = {
    IO {
      try {
        // Check for null or "null" string
        if (token == null || token.trim.isEmpty || token.equalsIgnoreCase("null")) {
          logger.warn(s"Invalid token provided for local validation: [${Option(token).getOrElse("null")}]")
          return IO.pure(None)
        }
        
        pdi.jwt.Jwt.decode(token, jwtSecret, Seq(algorithm)) match {
          case scala.util.Success(decoded) =>
            logger.info(s"JWT decoded successfully: ${decoded.content}")
            
            decoded.subject match {
              case Some(userIdStr) =>
                io.circe.parser.parse(decoded.content) match {
                  case Right(json) =>
                    val cursor = json.hcursor
                    cursor.downField("isAdmin").as[String] match {
                      case Right(isAdminStr) =>
                        val isAdmin = isAdminStr.toBoolean
                        
                        // Check if token is expired
                        val currentTime = Instant.now().getEpochSecond
                        val tokenExpiry = decoded.expiration.getOrElse(0L)
                        
                        if (tokenExpiry > currentTime) {
                          val role = if (isAdmin) "admin" else "student" // Default to student if not admin
                          logger.warn(s"Local token validation fallback for user: $userIdStr, defaulting role to: $role")
                          
                          Some(JwtPayload(
                            userId = userIdStr,
                            username = userIdStr,
                            role = role,
                            exp = tokenExpiry
                          ))
                        } else {
                          logger.warn(s"Token expired for user: $userIdStr")
                          None
                        }
                      case Left(decodingError) =>
                        logger.error(s"Failed to extract isAdmin field: ${decodingError.getMessage}")
                        None
                    }
                  case Left(parsingError) =>
                    logger.error(s"Failed to parse JWT content: ${parsingError.message}")
                    None
                }
              case None =>
                logger.error("JWT token missing subject field")
                None
            }
          case scala.util.Failure(error) =>
            logger.error(s"Failed to decode JWT: ${error.getMessage}")
            None
        }
      } catch {
        case ex: Exception =>
          logger.error(s"JWT validation error: ${ex.getMessage}")
          None
      }
    }
  }

  private def extractUserIdFromToken(token: String): Option[String] = {
    try {
      // Check for null or "null" string
      if (token == null || token.trim.isEmpty || token.equalsIgnoreCase("null")) {
        logger.warn(s"Invalid token provided: [${Option(token).getOrElse("null")}]")
        return None
      }
      
      pdi.jwt.Jwt.decode(token, jwtSecret, Seq(algorithm)) match {
        case scala.util.Success(decoded) => decoded.subject
        case _ => None
      }
    } catch {
      case ex: Exception => 
        logger.debug(s"Token decoding failed: ${ex.getMessage}")
        None
    }
  }

  private def extractExpirationFromToken(token: String): Option[Long] = {
    try {
      pdi.jwt.Jwt.decode(token, jwtSecret, Seq(algorithm)) match {
        case scala.util.Success(decoded) => decoded.expiration
        case _ => None
      }
    } catch {
      case _: Exception => None
    }
  }

  override def extractUserFromToken(token: String): IO[Option[JwtPayload]] = {
    validateToken(token)
  }

  override def hasRole(token: String, requiredRole: String): IO[Boolean] = {
    validateToken(token).map {
      case Some(payload) => payload.role.equalsIgnoreCase(requiredRole)
      case None => false
    }
  }

  override def isAdmin(token: String): IO[Boolean] = {
    hasRole(token, "admin")
  }

  override def isStudent(token: String): IO[Boolean] = {
    hasRole(token, "student")
  }

  override def isCoach(token: String): IO[Boolean] = {
    hasRole(token, "coach")
  }

  override def isGrader(token: String): IO[Boolean] = {
    hasRole(token, "grader")
  }
}
