package Utils

import cats.effect.IO
import org.http4s.{Request, Response, Status}
import org.http4s.headers.Authorization
import org.http4s.dsl.io.*
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}
import io.circe.parser.*
import io.circe.syntax.*
import Config.Constants
import org.slf4j.LoggerFactory

object AuthUtils {
  private val logger = LoggerFactory.getLogger("AuthUtils")
  private val SECRET_KEY = "your-secret-key-here" // 应该从配置文件读取
  
  case class UserInfo(
    userId: Long,
    username: String,
    role: String
  )
  
  def extractUserInfo(request: Request[IO]): IO[Option[UserInfo]] = {
    IO {
      request.headers.get[Authorization] match {
        case Some(auth) =>
          auth.credentials match {
            case org.http4s.Credentials.Token(_, token) =>
              validateToken(token)
            case _ =>
              logger.warn("Authorization格式不正确")
              None
          }
        case None =>
          logger.warn("请求中未找到Authorization头")
          None
      }
    }
  }
  
  def validateToken(token: String): Option[UserInfo] = {
    try {
      if (Jwt.isValid(token, SECRET_KEY, Seq(JwtAlgorithm.HS256))) {
        val claim = Jwt.decode(token, SECRET_KEY, Seq(JwtAlgorithm.HS256))
        claim.toOption.flatMap { decodedClaim =>
          parse(decodedClaim.content).toOption.flatMap { json =>
            for {
              userId <- json.hcursor.downField("userId").as[Long].toOption
              username <- json.hcursor.downField("username").as[String].toOption
              role <- json.hcursor.downField("role").as[String].toOption
            } yield UserInfo(userId, username, role)
          }
        }
      } else {
        logger.warn("JWT token验证失败")
        None
      }
    } catch {
      case e: Exception =>
        logger.error("JWT token解析失败", e)
        None
    }
  }
  
  def requireAuth(request: Request[IO]): IO[Either[Response[IO], UserInfo]] = {
    extractUserInfo(request).map {
      case Some(userInfo) => Right(userInfo)
      case None => Left(Response[IO](Status.Unauthorized))
    }
  }
  
  def requireRole(request: Request[IO], allowedRoles: Set[String]): IO[Either[Response[IO], UserInfo]] = {
    requireAuth(request).map {
      case Right(userInfo) if allowedRoles.contains(userInfo.role) => Right(userInfo)
      case Right(_) => Left(Response[IO](Status.Forbidden))
      case Left(response) => Left(response)
    }
  }
  
  def requireAdmin(request: Request[IO]): IO[Either[Response[IO], UserInfo]] = {
    requireRole(request, Set(Constants.USER_ROLE_ADMIN))
  }
  
  def requireGrader(request: Request[IO]): IO[Either[Response[IO], UserInfo]] = {
    requireRole(request, Set(Constants.USER_ROLE_GRADER))
  }
  
  def requireCoach(request: Request[IO]): IO[Either[Response[IO], UserInfo]] = {
    requireRole(request, Set(Constants.USER_ROLE_COACH))
  }
  
  def requireAdminOrGrader(request: Request[IO]): IO[Either[Response[IO], UserInfo]] = {
    requireRole(request, Set(Constants.USER_ROLE_ADMIN, Constants.USER_ROLE_GRADER))
  }
}
