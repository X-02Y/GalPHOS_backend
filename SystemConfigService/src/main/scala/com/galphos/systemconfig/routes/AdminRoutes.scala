package com.galphos.systemconfig.routes

import cats.effect.IO
import org.http4s.{AuthedRoutes, HttpRoutes, Header, Headers}
import org.http4s.dsl.io._
import org.http4s.server.{AuthMiddleware, Router}
import org.http4s.server.middleware.authentication.BasicAuth
import org.http4s.headers.Authorization
import com.galphos.systemconfig.models._
import com.galphos.systemconfig.models.Models._
import com.galphos.systemconfig.services.{AdminProxyService, AuthService}
import io.circe.syntax._
import io.circe.generic.auto._
import org.http4s.circe.CirceEntityCodec._
import org.typelevel.log4cats.slf4j.Slf4jLogger
import cats.implicits._

import java.time.ZonedDateTime

class AdminRoutes(adminProxyService: AdminProxyService, authService: AuthService) {
  private val logger = Slf4jLogger.getLogger[IO]
  
  // 提取和验证Bearer令牌
  private def extractBearerToken(request: org.http4s.Request[IO]): IO[Option[String]] = IO {
    request.headers.get[Authorization].flatMap { auth =>
      auth.credentials match {
        case org.http4s.Credentials.Token(org.http4s.AuthScheme.Bearer, token) => Some(token)
        case _ => None
      }
    }
  }
  
  // 超级管理员认证中间件
  private val superAdminAuthMiddleware: AuthMiddleware[IO, String] = {
    import cats.data.{Kleisli, OptionT}
    
    val authUser: Kleisli[[x] =>> OptionT[IO, x], org.http4s.Request[IO], String] = 
      Kleisli { request =>
        OptionT(
          extractBearerToken(request).flatMap {
            case Some(token) =>
              authService.isSuperAdminWithHealthCheck(token).map {
                case true => Some(token)
                case false => None
              }
            case None => IO.pure(None)
          }
        )
      }
      
    AuthMiddleware(authUser)
  }
  
  // 超级管理员路由
  private val superAdminRoutes: AuthedRoutes[String, IO] = AuthedRoutes.of[String, IO] {
    // 获取所有管理员
    case GET -> Root / "admin" / "system" / "admins" as token =>
      adminProxyService.getAllAdmins(token).flatMap { admins =>
        Ok(admins)
      }.handleErrorWith { error =>
        logger.error(error)("获取管理员列表失败") *>
        InternalServerError(ErrorResponse(s"获取管理员列表失败: ${error.getMessage}"))
      }
      
    // 创建新管理员
    case req @ POST -> Root / "admin" / "system" / "admins" as token =>
      req.req.as[CreateAdminRequest].flatMap { adminRequest =>
        adminProxyService.createAdmin(adminRequest, token).flatMap {
          case Some(admin) => Created(admin)
          case None => BadRequest(ErrorResponse("无法创建管理员"))
        }
      }.handleErrorWith { error =>
        logger.error(error)("创建管理员失败") *>
        InternalServerError(ErrorResponse(s"创建管理员失败: ${error.getMessage}"))
      }
      
    // 获取单个管理员（已不再需要，直接通过列表获取）
    case GET -> Root / "admin" / "system" / "admins" / LongVar(adminId) as token =>
      adminProxyService.getAllAdmins(token).flatMap { admins =>
        admins.find(_.adminId.contains(adminId)) match {
          case Some(admin) => Ok(admin)
          case None => NotFound(ErrorResponse("管理员不存在"))
        }
      }.handleErrorWith { error =>
        logger.error(error)(s"获取管理员失败，ID: $adminId") *>
        InternalServerError(ErrorResponse(s"获取管理员失败: ${error.getMessage}"))
      }
      
    // 更新管理员
    case req @ PUT -> Root / "admin" / "system" / "admins" / LongVar(adminId) as token =>
      req.req.as[UpdateAdminRequest].flatMap { updateRequest =>
        adminProxyService.updateAdmin(adminId, updateRequest, token).flatMap {
          case Some(admin) => Ok(admin)
          case None => NotFound(ErrorResponse("管理员不存在或无变更"))
        }
      }.handleErrorWith { error =>
        logger.error(error)(s"更新管理员失败，ID: $adminId") *>
        InternalServerError(ErrorResponse(s"更新管理员失败: ${error.getMessage}"))
      }
      
    // 删除管理员
    case DELETE -> Root / "admin" / "system" / "admins" / LongVar(adminId) as token =>
    // 删除管理员
    case DELETE -> Root / "admin" / "system" / "admins" / LongVar(adminId) as token =>
      adminProxyService.deleteAdmin(adminId, token).flatMap {
        case true => NoContent()
        case false => NotFound(ErrorResponse("管理员不存在"))
      }.handleErrorWith { error =>
        logger.error(error)(s"删除管理员失败，ID: $adminId") *>
        InternalServerError(ErrorResponse(s"删除管理员失败: ${error.getMessage}"))
      }
      
    // 重置管理员密码
    case req @ PUT -> Root / "admin" / "system" / "admins" / LongVar(adminId) / "password" as token =>
      req.req.as[ResetPasswordRequest].flatMap { resetRequest =>
        adminProxyService.resetPassword(adminId, resetRequest, token).flatMap {
          case true => Ok(SuccessResponse("密码已重置"))
          case false => NotFound(ErrorResponse("管理员不存在"))
        }
      }.handleErrorWith { error =>
        logger.error(error)(s"重置密码失败，ID: $adminId") *>
        InternalServerError(ErrorResponse(s"重置密码失败: ${error.getMessage}"))
      }
  }
  
  // 公开路由
  val routes: HttpRoutes[IO] = superAdminAuthMiddleware(superAdminRoutes)
}
