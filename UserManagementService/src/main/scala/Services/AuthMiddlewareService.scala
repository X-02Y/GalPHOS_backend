package Services

import cats.effect.IO
import io.circe.*
import io.circe.parser.*
import org.slf4j.LoggerFactory
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.net.URI
import java.time.Duration

case class AuthResult(
  success: Boolean,
  username: Option[String] = None,
  userType: Option[String] = None,
  message: Option[String] = None
)

trait AuthMiddlewareService {
  def validateAdminToken(token: String): IO[AuthResult]
  def validateUserToken(token: String, requiredRole: Option[String] = None): IO[AuthResult]
}

class AuthMiddlewareServiceImpl(config: Config.ServiceConfig) extends AuthMiddlewareService {
  private val logger = LoggerFactory.getLogger("AuthMiddleware")
  private val authServiceUrl = s"${Config.Constants.AUTH_SERVICE_BASE_URL}/validate"
  private val httpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .build()

  override def validateAdminToken(token: String): IO[AuthResult] = {
    IO.blocking {
      try {
        val request = HttpRequest.newBuilder()
          .uri(URI.create(authServiceUrl))
          .header("Authorization", s"Bearer $token")
          .header("Content-Type", "application/json")
          .GET()
          .build()

        logger.debug(s"验证管理员Token: ${token.take(20)}...")
        
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        
        if (response.statusCode() == 200) {
          parse(response.body()) match {
            case Right(json) =>
              val cursor = json.hcursor
              cursor.downField("success").as[Boolean] match {
                case Right(true) =>
                  val dataOpt = cursor.downField("data").as[Json].toOption
                  dataOpt match {
                    case Some(data) =>
                      val dataCursor = data.hcursor
                      val username = dataCursor.downField("username").as[String].toOption
                      val userType = dataCursor.downField("type").as[String].toOption
                      
                      if (userType.contains("admin")) {
                        logger.info(s"管理员Token验证成功: username=${username.getOrElse("unknown")}")
                        AuthResult(success = true, username = username, userType = userType)
                      } else {
                        logger.warn(s"Token验证失败：用户类型不是管理员: userType=${userType.getOrElse("unknown")}")
                        AuthResult(success = false, message = Some("权限不足：需要管理员身份"))
                      }
                    case None =>
                      logger.warn("Token验证失败：响应数据格式错误")
                      AuthResult(success = false, message = Some("Token验证失败：数据格式错误"))
                  }
                case Right(false) =>
                  val message = cursor.downField("message").as[String].getOrElse("Token验证失败")
                  logger.warn(s"Token验证失败: $message")
                  AuthResult(success = false, message = Some(message))
                case Left(error) =>
                  logger.error(s"Token验证响应解析失败: ${error.getMessage}")
                  AuthResult(success = false, message = Some("Token验证响应解析失败"))
              }
            case Left(parseError) =>
              logger.error(s"Token验证响应JSON解析失败: ${parseError.getMessage}")
              AuthResult(success = false, message = Some("响应格式错误"))
          }
        } else {
          logger.warn(s"Token验证失败，HTTP状态码: ${response.statusCode()}")
          AuthResult(success = false, message = Some(s"Token验证失败，状态码: ${response.statusCode()}"))
        }
      } catch {
        case e: Exception =>
          logger.error(s"Token验证网络请求失败: ${e.getMessage}", e)
          AuthResult(success = false, message = Some(s"Token验证网络错误: ${e.getMessage}"))
      }
    }
  }

  override def validateUserToken(token: String, requiredRole: Option[String] = None): IO[AuthResult] = {
    IO.blocking {
      try {
        val request = HttpRequest.newBuilder()
          .uri(URI.create(authServiceUrl))
          .header("Authorization", s"Bearer $token")
          .header("Content-Type", "application/json")
          .GET()
          .build()

        logger.debug(s"验证用户Token: ${token.take(20)}... 要求角色: ${requiredRole.getOrElse("任意")}")
        
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        
        if (response.statusCode() == 200) {
          parse(response.body()) match {
            case Right(json) =>
              val cursor = json.hcursor
              cursor.downField("success").as[Boolean] match {
                case Right(true) =>
                  val dataOpt = cursor.downField("data").as[Json].toOption
                  dataOpt match {
                    case Some(data) =>
                      val dataCursor = data.hcursor
                      val username = dataCursor.downField("username").as[String].toOption
                      // 尝试从type字段(管理员)或role字段(普通用户)读取角色信息
                      val userType = dataCursor.downField("type").as[String].toOption
                        .orElse(dataCursor.downField("role").as[String].toOption)
                      
                      requiredRole match {
                        case Some(role) if !userType.contains(role) =>
                          logger.warn(s"Token验证失败：用户角色不匹配: userType=${userType.getOrElse("unknown")}, required=$role")
                          AuthResult(success = false, message = Some(s"权限不足：需要${role}身份"))
                        case _ =>
                          logger.info(s"用户Token验证成功: username=${username.getOrElse("unknown")}, type=${userType.getOrElse("unknown")}")
                          AuthResult(success = true, username = username, userType = userType)
                      }
                    case None =>
                      logger.warn("Token验证失败：响应数据格式错误")
                      AuthResult(success = false, message = Some("Token验证失败：数据格式错误"))
                  }
                case Right(false) =>
                  val message = cursor.downField("message").as[String].getOrElse("Token验证失败")
                  logger.warn(s"Token验证失败: $message")
                  AuthResult(success = false, message = Some(message))
                case Left(error) =>
                  logger.error(s"Token验证响应解析失败: ${error.getMessage}")
                  AuthResult(success = false, message = Some("Token验证响应解析失败"))
              }
            case Left(parseError) =>
              logger.error(s"Token验证响应JSON解析失败: ${parseError.getMessage}")
              AuthResult(success = false, message = Some("响应格式错误"))
          }
        } else {
          logger.warn(s"Token验证失败，HTTP状态码: ${response.statusCode()}")
          AuthResult(success = false, message = Some(s"Token验证失败，状态码: ${response.statusCode()}"))
        }
      } catch {
        case e: Exception =>
          logger.error(s"Token验证网络请求失败: ${e.getMessage}", e)
          AuthResult(success = false, message = Some(s"Token验证网络错误: ${e.getMessage}"))
      }
    }
  }
}
