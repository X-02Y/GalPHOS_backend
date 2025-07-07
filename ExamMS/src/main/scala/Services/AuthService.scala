package Services

import cats.effect.IO
import pdi.jwt.JwtAlgorithm
import io.circe.generic.auto.*
import io.circe.parser.*
import Models.*
import Config.ServiceConfig
import Database.DatabaseUtils
import org.slf4j.LoggerFactory
import java.time.Instant

trait AuthService {
  def validateToken(token: String): IO[Option[JwtPayload]]
  def extractUserFromToken(token: String): IO[Option[JwtPayload]]
  def hasRole(token: String, requiredRole: String): IO[Boolean]
  def isAdmin(token: String): IO[Boolean]
  def isStudent(token: String): IO[Boolean]
  def isCoach(token: String): IO[Boolean]
  def isGrader(token: String): IO[Boolean]
}

class AuthServiceImpl(config: ServiceConfig) extends AuthService {
  private val logger = LoggerFactory.getLogger("AuthService")
  private val jwtSecret = config.jwtSecret
  private val algorithm = JwtAlgorithm.HS256

  override def validateToken(token: String): IO[Option[JwtPayload]] = {
    for {
      tokenResult <- IO {
        try {
          // Use the same JWT decoding logic as UserAuthService
          pdi.jwt.Jwt.decode(token, jwtSecret, Seq(algorithm)) match {
            case scala.util.Success(decoded) =>
              logger.info(s"JWT decoded successfully: ${decoded.content}")
              
              // Extract subject (userId) and expiration from standard JWT fields
              decoded.subject match {
                case Some(userIdStr) =>
                  // Parse the JSON content to get the isAdmin field
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
                            Some((userIdStr, isAdmin, tokenExpiry))
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

      // Get user role from database if token is valid
      payload <- tokenResult match {
        case Some((userIdStr, isAdmin, expiry)) =>
          if (isAdmin) {
            // For admin tokens, always use admin role
            val payload = JwtPayload(
              userId = userIdStr,
              username = userIdStr,
              role = "admin",
              exp = expiry
            )
            logger.info(s"Token valid for admin: ${payload.userId}")
            IO.pure(Some(payload))
          } else {
            // For non-admin tokens, look up the actual role from database with fallback
            getUserRoleWithFallback(userIdStr).map { role =>
              val payload = JwtPayload(
                userId = userIdStr,
                username = userIdStr,
                role = role,
                exp = expiry
              )
              logger.info(s"Token valid for user: ${payload.userId}, role: ${payload.role}")
              Some(payload)
            }
          }
        case None =>
          IO.pure(None)
      }
    } yield payload
  }

  // Get user role with fallback to prevent authentication failures
  private def getUserRoleWithFallback(userId: String): IO[String] = {
    getUserRole(userId).map {
      case Some(role) => role
      case None => 
        logger.warn(s"No role found for user: $userId, defaulting to student role")
        "student" // Default to student role if lookup fails
    }.handleErrorWith { error =>
      logger.error(s"Database error while looking up role for user $userId, defaulting to student role", error)
      IO.pure("student") // Default to student role if database lookup fails completely
    }
  }

  // Get user role from database and map to English
  private def getUserRole(userId: String): IO[Option[String]] = {
    logger.info(s"Looking up role for user: $userId")
    
    // First check in user_table
    val userQuery = "SELECT role FROM authservice.user_table WHERE user_id = ?"
    for {
      userResult <- DatabaseUtils.executeQuery(userQuery, List(userId))(_.getString("role"))
        .handleErrorWith { error =>
          logger.error(s"Database query failed for user_table: ${error.getMessage}", error)
          IO.pure(List.empty[String])
        }
      role <- userResult.headOption match {
        case Some(chineseRole) => 
          logger.info(s"Found user role: $chineseRole for user: $userId")
          val englishRole = mapChineseRoleToEnglish(chineseRole)
          logger.info(s"Mapped role: $chineseRole -> $englishRole")
          IO.pure(Some(englishRole))
        case None =>
          logger.info(s"User not found in user_table, checking admin_table for user: $userId")
          // Check in admin_table
          val adminQuery = "SELECT role FROM authservice.admin_table WHERE admin_id = ?"
          for {
            adminResult <- DatabaseUtils.executeQuery(adminQuery, List(userId))(_.getString("role"))
              .handleErrorWith { error =>
                logger.error(s"Database query failed for admin_table: ${error.getMessage}", error)
                IO.pure(List.empty[String])
              }
            adminRole <- adminResult.headOption match {
              case Some(role) => 
                logger.info(s"Found admin role: $role for user: $userId")
                IO.pure(Some("admin")) // Always return "admin" for any admin role
              case None =>
                logger.warn(s"No role found in either user_table or admin_table for user: $userId")
                // Instead of returning None, we could try to extract role from JWT payload
                // or provide a more graceful fallback
                IO.pure(None)
            }
          } yield adminRole
      }
    } yield role
  }

  // Map Chinese roles to English roles
  private def mapChineseRoleToEnglish(chineseRole: String): String = {
    chineseRole match {
      case r if r.contains("学生") => "student"
      case r if r.contains("教练") => "coach"
      case r if r.contains("评卷") || r.contains("阅卷") => "grader"
      case r if r.contains("admin") => "admin"
      case _ => 
        logger.warn(s"Unknown Chinese role: $chineseRole, defaulting to 'student'")
        "student"
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

// 权限验证工具类
object AuthUtils {
  def hasAnyRole(payload: JwtPayload, roles: List[String]): Boolean = {
    roles.exists(role => payload.role.equalsIgnoreCase(role))
  }
  
  def requireRole(payload: JwtPayload, requiredRole: String): Boolean = {
    payload.role.equalsIgnoreCase(requiredRole)
  }
  
  def requireAnyRole(payload: JwtPayload, requiredRoles: List[String]): Boolean = {
    requiredRoles.exists(role => payload.role.equalsIgnoreCase(role))
  }
}
