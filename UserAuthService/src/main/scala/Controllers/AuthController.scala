package Controllers

import cats.effect.IO
import cats.implicits.*
import io.circe.*
import io.circe.syntax.*
import io.circe.generic.auto.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.headers.Authorization
import org.slf4j.LoggerFactory
import Models.*
import Services.*
import java.util.UUID

class AuthController(
  authService: AuthService
) {
  private val logger = LoggerFactory.getLogger("AuthController")

  // CORS 支持
  private val corsHeaders = Headers(
    "Access-Control-Allow-Origin" -> "*",
    "Access-Control-Allow-Methods" -> "GET, POST, PUT, DELETE, OPTIONS",
    "Access-Control-Allow-Headers" -> "Content-Type, Authorization"
  )

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    // CORS 预检请求
    case req @ OPTIONS -> _ =>
      Ok().map(_.withHeaders(corsHeaders))

    // 用户登录
    case req @ POST -> Root / "api" / "auth" / "login" =>
      handleLogin(req).map(_.withHeaders(corsHeaders))

    // 用户注册
    case req @ POST -> Root / "api" / "auth" / "register" =>
      handleRegister(req).map(_.withHeaders(corsHeaders))

    // 管理员登录
    case req @ POST -> Root / "api" / "auth" / "admin-login" =>
      handleAdminLogin(req).map(_.withHeaders(corsHeaders))

    // Token验证
    case req @ GET -> Root / "api" / "auth" / "validate" =>
      handleValidate(req).map(_.withHeaders(corsHeaders))

    // 用户登出
    case req @ POST -> Root / "api" / "auth" / "logout" =>
      handleLogout(req).map(_.withHeaders(corsHeaders))

    // 健康检查
    case GET -> Root / "health" =>
      Ok("OK").map(_.withHeaders(corsHeaders))
  }

  // 处理用户登录
  private def handleLogin(req: Request[IO]): IO[Response[IO]] = {
    for {
      loginReq <- req.as[LoginRequest]
      result <- authService.login(loginReq)
      response <- result match {
        case Right((userInfo, token)) =>
          Ok(ApiResponse.successWithToken(userInfo, token, "登录成功").asJson)
        case Left(error) =>
          BadRequest(ApiResponse.error(error).asJson)
      }
    } yield response
  }.handleErrorWith { error =>
    logger.error("登录处理失败", error)
    BadRequest(ApiResponse.error(s"登录失败: ${error.getMessage}").asJson)
  }

  // 处理用户注册
  private def handleRegister(req: Request[IO]): IO[Response[IO]] = {
    for {
      registerReq <- req.as[RegisterRequest]
      result <- authService.register(registerReq)
      response <- result match {
        case Right(message) =>
          Ok(ApiResponse.success((), message).asJson)
        case Left(error) =>
          BadRequest(ApiResponse.error(error).asJson)
      }
    } yield response
  }.handleErrorWith { error =>
    logger.error("注册处理失败", error)
    BadRequest(ApiResponse.error(s"注册失败: ${error.getMessage}").asJson)
  }

  // 处理管理员登录
  private def handleAdminLogin(req: Request[IO]): IO[Response[IO]] = {
    for {
      adminLoginReq <- req.as[AdminLoginRequest]
      result <- authService.adminLogin(adminLoginReq)
      response <- result match {
        case Right((userInfo, token)) =>
          Ok(ApiResponse.successWithToken(userInfo, token, "管理员登录成功").asJson)
        case Left(error) =>
          BadRequest(ApiResponse.error(error).asJson)
      }
    } yield response
  }.handleErrorWith { error =>
    logger.error("管理员登录处理失败", error)
    BadRequest(ApiResponse.error(s"管理员登录失败: ${error.getMessage}").asJson)
  }

  // 处理Token验证
  private def handleValidate(req: Request[IO]): IO[Response[IO]] = {
    extractTokenFromHeader(req) match {
      case Some(token) =>
        for {
          result <- authService.validateToken(token)
          response <- result match {
            case Right(userInfo) =>
              Ok(ApiResponse.success(userInfo, "Token验证成功").asJson)
            case Left(error) =>
              IO.pure(Response[IO](Status.Unauthorized).withEntity(ApiResponse.error(error).asJson))
          }
        } yield response
      case None =>
        BadRequest(ApiResponse.error("缺少Authorization头").asJson)
    }
  }.handleErrorWith { error =>
    logger.error("Token验证处理失败", error)
    IO.pure(Response[IO](Status.Unauthorized).withEntity(ApiResponse.error(s"Token验证失败: ${error.getMessage}").asJson))
  }

  // 处理用户登出
  private def handleLogout(req: Request[IO]): IO[Response[IO]] = {
    extractTokenFromHeader(req) match {
      case Some(token) =>
        for {
          result <- authService.logout(token)
          response <- result match {
            case Right(_) =>
              Ok(ApiResponse.success((), "登出成功").asJson)
            case Left(error) =>
              BadRequest(ApiResponse.error(error).asJson)
          }
        } yield response
      case None =>
        BadRequest(ApiResponse.error("缺少Authorization头").asJson)
    }
  }.handleErrorWith { error =>
    logger.error("登出处理失败", error)
    BadRequest(ApiResponse.error(s"登出失败: ${error.getMessage}").asJson)
  }

  // 从请求头提取Token
  private def extractTokenFromHeader(req: Request[IO]): Option[String] = {
    req.headers.get[Authorization].flatMap {
      case Authorization(Credentials.Token(scheme, token)) if scheme.toString.toLowerCase == "bearer" =>
        Some(token)
      case _ => None
    }
  }
}
