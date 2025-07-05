package com.galphos.systemconfig.routes

import cats.effect.IO
import org.http4s.{AuthedRoutes, HttpRoutes, Header, Headers}
import org.http4s.dsl.io._
import org.http4s.server.{AuthMiddleware, Router}
import org.http4s.server.middleware.authentication.BasicAuth
import org.http4s.headers.Authorization
import com.galphos.systemconfig.models.{Admin, CreateAdminRequest, UpdateAdminRequest, ResetPasswordRequest, AdminResponse, ErrorResponse, SuccessResponse, ApiResponse}
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
        Ok(ApiResponse.success(admins, "获取管理员列表成功"))
      }.handleErrorWith { error =>
        logger.error(error)("获取管理员列表失败") *>
        InternalServerError(ApiResponse.error(s"获取管理员列表失败: ${error.getMessage}"))
      }
      
    // 创建新管理员
    case req @ POST -> Root / "admin" / "system" / "admins" as token =>
      req.req.as[CreateAdminRequest].flatMap { adminRequest =>
        logger.info(s"收到创建管理员请求: username=${adminRequest.username}, role=${adminRequest.role}") *>
        adminProxyService.createAdmin(adminRequest, token).flatMap {
          case Some(admin) => 
            logger.info(s"成功创建管理员: ${admin.username}") *>
            Created(ApiResponse.success(admin, "创建管理员成功"))
          case None => 
            logger.warn(s"创建管理员失败: ${adminRequest.username}") *>
            BadRequest(ApiResponse.error("无法创建管理员"))
        }
      }.handleErrorWith { error =>
        logger.error(error)("创建管理员失败") *>
        InternalServerError(ApiResponse.error(s"创建管理员失败: ${error.getMessage}"))
      }
      
    // 获取单个管理员（已不再需要，直接通过列表获取）
    case GET -> Root / "admin" / "system" / "admins" / LongVar(adminId) as token =>
      adminProxyService.getAllAdmins(token).flatMap { admins =>
        admins.find(_.adminId.contains(adminId)) match {
          case Some(admin) => Ok(ApiResponse.success(admin, "获取管理员成功"))
          case None => NotFound(ApiResponse.error("管理员不存在"))
        }
      }.handleErrorWith { error =>
        logger.error(error)(s"获取管理员失败，ID: $adminId") *>
        InternalServerError(ApiResponse.error(s"获取管理员失败: ${error.getMessage}"))
      }
      
    // 更新管理员
    case req @ PUT -> Root / "admin" / "system" / "admins" / LongVar(adminId) as token =>
      req.req.as[UpdateAdminRequest].flatMap { updateRequest =>
        adminProxyService.updateAdmin(adminId, updateRequest, token).flatMap {
          case Some(admin) => Ok(ApiResponse.success(admin, "更新管理员成功"))
          case None => NotFound(ApiResponse.error("管理员不存在或无变更"))
        }
      }.handleErrorWith { error =>
        logger.error(error)(s"更新管理员失败，ID: $adminId") *>
        InternalServerError(ApiResponse.error(s"更新管理员失败: ${error.getMessage}"))
      }
      
    // 删除管理员
    case DELETE -> Root / "admin" / "system" / "admins" / LongVar(adminId) as token =>
      adminProxyService.deleteAdmin(adminId, token).flatMap {
        case true => Ok(ApiResponse.success("删除成功", "管理员删除成功"))
        case false => NotFound(ApiResponse.error("管理员不存在"))
      }.handleErrorWith { error =>
        logger.error(error)(s"删除管理员失败，ID: $adminId") *>
        InternalServerError(ApiResponse.error(s"删除管理员失败: ${error.getMessage}"))
      }
      
    // 重置管理员密码
    case req @ PUT -> Root / "admin" / "system" / "admins" / LongVar(adminId) / "password" as token =>
      req.req.as[ResetPasswordRequest].flatMap { resetRequest =>
        adminProxyService.resetPassword(adminId, resetRequest, token).flatMap {
          case true => Ok(ApiResponse.success("密码重置成功", "密码已重置"))
          case false => NotFound(ApiResponse.error("管理员不存在"))
        }
      }.handleErrorWith { error =>
        logger.error(error)(s"重置密码失败，ID: $adminId") *>
        InternalServerError(ApiResponse.error(s"重置密码失败: ${error.getMessage}"))
      }
  }
  
  // 公开路由
  val routes: HttpRoutes[IO] = superAdminAuthMiddleware(superAdminRoutes)
}
