package Services

import cats.effect.IO
import cats.implicits.*
import sttp.client3.*
import sttp.client3.circe.*
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.slf4j.LoggerFactory
import Config.ServerConfig
import Models.*
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}
import io.circe.parser.*
import java.time.Instant

class AuthService(config: ServerConfig) {
  private val logger = LoggerFactory.getLogger("AuthService")
  private val backend = AsyncHttpClientCatsBackend[IO]()
  private val authServiceUrl = config.authServiceUrl

  def validateToken(token: String): IO[Either[String, UserInfo]] = {
    val request = basicRequest
      .get(uri"$authServiceUrl/api/auth/validate")
      .header("Authorization", s"Bearer $token")
      .response(asJson[ApiResponse[UserInfo]])

    backend.flatMap { implicit b =>
      request.send(b).map(_.body match {
        case Right(response) if response.success =>
          response.data match {
            case Some(userInfo) => Right(userInfo)
            case None => Left("认证响应中缺少用户信息")
          }
        case Right(response) => Left(response.message.getOrElse("认证失败"))
        case Left(error) => Left(s"认证服务通信失败: ${error.getMessage}")
      })
    }.handleErrorWith { error =>
      logger.error("Token验证失败", error)
      IO.pure(Left(s"认证服务不可用: ${error.getMessage}"))
    }
  }

  def extractUserFromToken(token: String): IO[Either[String, UserInfo]] = {
    IO {
      // 先尝试本地解析JWT
      val cleanToken = token.replace("Bearer ", "")
      Jwt.decode(cleanToken, "GalPHOS_2025_SECRET_KEY", Seq(JwtAlgorithm.HS256)) match {
        case scala.util.Success(claim) =>
          parse(claim.content) match {
            case Right(json) =>
              json.as[TokenPayload] match {
                case Right(payload) =>
                  // 检查是否过期
                  if (payload.exp < Instant.now().getEpochSecond) {
                    Left("Token已过期")
                  } else {
                    Right(UserInfo(
                      username = payload.username,
                      role = payload.role
                    ))
                  }
                case Left(error) => Left(s"解析Token内容失败: $error")
              }
            case Left(error) => Left(s"解析Token失败: $error")
          }
        case scala.util.Failure(ex) => Left(s"Token无效: ${ex.getMessage}")
      }
    }.flatMap {
      case Right(userInfo) => IO.pure(Right(userInfo))
      case Left(_) => validateToken(token) // 本地解析失败时调用认证服务
    }
  }

  def validateStudentCoachRelation(studentUsername: String, coachUsername: String, token: String): IO[Either[String, UserInfo]] = {
    val request = basicRequest
      .get(uri"$authServiceUrl/api/coach/students/$studentUsername/validate")
      .header("Authorization", s"Bearer $token")
      .response(asJson[ApiResponse[UserInfo]])

    backend.flatMap { implicit b =>
      request.send(b).map(_.body match {
        case Right(response) if response.success =>
          response.data match {
            case Some(userInfo) => 
              if (userInfo.username == studentUsername) Right(userInfo)
              else Left("学生信息验证失败")
            case None => Left("验证响应中缺少学生信息")
          }
        case Right(response) => Left(response.message.getOrElse("学生-教练关系验证失败"))
        case Left(error) => Left(s"验证服务通信失败: ${error.getMessage}")
      })
    }.handleErrorWith { error =>
      logger.error("学生-教练关系验证失败", error)
      IO.pure(Left(s"验证服务不可用: ${error.getMessage}"))
    }
  }
}
