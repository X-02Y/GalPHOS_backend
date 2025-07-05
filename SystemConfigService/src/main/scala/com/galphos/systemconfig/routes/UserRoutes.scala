package com.galphos.systemconfig.routes

import cats.effect.IO
import cats.data.{Kleisli, OptionT}
import org.http4s.{AuthedRoutes, HttpRoutes, Header, Headers}
import org.http4s.dsl.io._
import org.http4s.server.{AuthMiddleware, Router}
import org.http4s.server.middleware.authentication.BasicAuth
import org.http4s.headers.Authorization
import com.galphos.systemconfig.models.{User, CreateUserRequest, UpdateUserRequest, UserApprovalRequest, UserResponse, ErrorResponse, SuccessResponse}
import com.galphos.systemconfig.models.Models._
import com.galphos.systemconfig.services.{UserProxyService, AuthService}
import io.circe.syntax._
import io.circe.generic.auto._
import org.http4s.circe.CirceEntityCodec._
import org.typelevel.log4cats.slf4j.Slf4jLogger
import cats.implicits._

import java.time.ZonedDateTime

class UserRoutes(userProxyService: UserProxyService, authService: AuthService) {
  private val logger = Slf4jLogger.getLogger[IO]

  // 管理员认证中间件
  private val adminAuthMiddleware: AuthMiddleware[IO, String] = AuthMiddleware.withFallThrough(
    cats.data.Kleisli { (req: org.http4s.Request[IO]) =>
      cats.data.OptionT {
        req.headers.get[Authorization] match {
          case Some(Authorization(org.http4s.Credentials.Token(scheme, token))) if scheme.toString.toLowerCase == "bearer" =>
            authService.isAdmin(token).map {
              case true => Some(token)
              case false => None
            }
          case _ => IO.pure(None)
        }
      }
    }
  )

  // 超级管理员认证中间件
  private val superAdminAuthMiddleware: AuthMiddleware[IO, String] = AuthMiddleware.withFallThrough(
    cats.data.Kleisli { (req: org.http4s.Request[IO]) =>
      cats.data.OptionT {
        req.headers.get[Authorization] match {
          case Some(Authorization(org.http4s.Credentials.Token(scheme, token))) if scheme.toString.toLowerCase == "bearer" =>
            authService.isSuperAdmin(token).map {
              case true => Some(token)
              case false => None
            }
          case _ => IO.pure(None)
        }
      }
    }
  )

  // 管理员路由
  private val adminRoutes: AuthedRoutes[String, IO] = AuthedRoutes.of {
    // 获取待审核用户列表
    case GET -> Root / "users" / "pending" as token =>
      for {
        users <- userProxyService.getPendingUsers(token)
        response <- Ok(users.asJson)
      } yield response

    // 获取已审核用户列表
    case req @ GET -> Root / "users" / "approved" as token =>
      val page = req.req.params.get("page").flatMap(_.toIntOption)
      val limit = req.req.params.get("limit").flatMap(_.toIntOption)
      val role = req.req.params.get("role")
      val status = req.req.params.get("status")
      
      for {
        users <- userProxyService.getApprovedUsers(token, page, limit, role, status)
        response <- Ok(users.asJson)
      } yield response

    // 获取单个用户信息
    case GET -> Root / "users" / userId as token =>
      for {
        userOpt <- userProxyService.getUserById(userId, token)
        response <- userOpt match {
          case Some(user) => Ok(user.asJson)
          case None => NotFound(ErrorResponse("用户不存在").asJson)
        }
      } yield response

    // 审核用户申请
    case req @ POST -> Root / "users" / "approve" as token =>
      for {
        approvalReq <- req.req.as[UserApprovalRequest]
        success <- userProxyService.approveUser(approvalReq, token)
        response <- if (success) {
          Ok(SuccessResponse("用户审核成功").asJson)
        } else {
          BadRequest(ErrorResponse("用户审核失败").asJson)
        }
      } yield response

    // 更新用户状态
    case req @ PUT -> Root / "users" / userId / "status" as token =>
      for {
        json <- req.req.as[io.circe.Json]
        status <- IO.fromEither(json.hcursor.downField("status").as[String])
        success <- userProxyService.updateUserStatus(userId, status, token)
        response <- if (success) {
          Ok(SuccessResponse("用户状态更新成功").asJson)
        } else {
          BadRequest(ErrorResponse("用户状态更新失败").asJson)
        }
      } yield response

    // 更新用户信息
    case req @ PUT -> Root / "users" / userId as token =>
      for {
        updateReq <- req.req.as[UpdateUserRequest]
        userOpt <- userProxyService.updateUser(userId, updateReq, token)
        response <- userOpt match {
          case Some(user) => Ok(user.asJson)
          case None => BadRequest(ErrorResponse("用户信息更新失败").asJson)
        }
      } yield response

    // 删除用户
    case DELETE -> Root / "users" / userId as token =>
      for {
        success <- userProxyService.deleteUser(userId, token)
        response <- if (success) {
          Ok(SuccessResponse("用户删除成功").asJson)
        } else {
          BadRequest(ErrorResponse("用户删除失败").asJson)
        }
      } yield response
  }

  // 公共路由（用于健康检查等）
  private val publicRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "users" / "health" =>
      Ok("User service is running")
  }

  // 合并所有路由
  val routes: HttpRoutes[IO] = {
    Router(
      "/admin" -> adminAuthMiddleware(adminRoutes),
      "/" -> publicRoutes
    )
  }
}
