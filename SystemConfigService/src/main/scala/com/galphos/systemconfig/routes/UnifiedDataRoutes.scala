package com.galphos.systemconfig.routes

import cats.effect.IO
import cats.data.{Kleisli, OptionT}
import org.http4s.{AuthedRoutes, HttpRoutes, Header, Headers}
import org.http4s.dsl.io._
import org.http4s.server.{AuthMiddleware, Router}
import org.http4s.server.middleware.authentication.BasicAuth
import org.http4s.headers.Authorization
import com.galphos.systemconfig.models.{AllUsersResponse, StatisticsResponse, UsersByRoleResponse, ServiceInfoResponse, ErrorResponse, SuccessResponse}
import com.galphos.systemconfig.models.Models._
import com.galphos.systemconfig.services.{UnifiedDataProxyService, AuthService}
import io.circe.syntax._
import io.circe.generic.auto._
import org.http4s.circe.CirceEntityCodec._
import org.typelevel.log4cats.slf4j.Slf4jLogger
import cats.implicits._

import java.time.ZonedDateTime

class UnifiedDataRoutes(unifiedDataProxyService: UnifiedDataProxyService, authService: AuthService) {
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

  // 统一数据管理路由
  private val unifiedRoutes: AuthedRoutes[String, IO] = AuthedRoutes.of {
    // 获取所有用户（包括管理员和普通用户）
    case req @ GET -> Root / "data" / "users" / "all" as token =>
      val includeAdmins = req.req.params.get("includeAdmins").forall(_.toLowerCase != "false")
      for {
        allUsers <- unifiedDataProxyService.getAllUsers(token, includeAdmins)
        response <- Ok(allUsers.asJson)
      } yield response

    // 按角色获取用户
    case req @ GET -> Root / "data" / "users" / "by-role" / role as token =>
      for {
        users <- unifiedDataProxyService.getUsersByRole(role, token)
        response <- Ok(users.asJson)
      } yield response

    // 按状态获取用户
    case req @ GET -> Root / "data" / "users" / "by-status" / status as token =>
      for {
        users <- unifiedDataProxyService.getUsersByStatus(status, token)
        response <- Ok(users.asJson)
      } yield response

    // 搜索用户
    case req @ GET -> Root / "data" / "users" / "search" as token =>
      val query = req.req.params.getOrElse("q", "")
      val includeAdmins = req.req.params.get("includeAdmins").forall(_.toLowerCase != "false")
      
      if (query.trim.isEmpty) {
        BadRequest(ErrorResponse("搜索查询不能为空").asJson)
      } else {
        for {
          users <- unifiedDataProxyService.searchUsers(query, token, includeAdmins)
          response <- Ok(users.asJson)
        } yield response
      }

    // 数据统计接口
    case GET -> Root / "data" / "statistics" as token =>
      for {
        allUsers <- unifiedDataProxyService.getAllUsers(token, includeAdmins = true)
        studentCount = allUsers.users.count(_.role == "student")
        coachCount = allUsers.users.count(_.role == "coach")
        graderCount = allUsers.users.count(_.role == "grader")
        adminCount = allUsers.admins.count(_.role == "admin")
        superAdminCount = allUsers.admins.count(_.role == "super_admin")
        pendingUsers <- unifiedDataProxyService.getPendingUsers(token)
        
        statistics = StatisticsResponse(
          totalUsers = allUsers.totalUsers,
          totalAdmins = allUsers.totalAdmins,
          totalAll = allUsers.total,
          pendingUsers = pendingUsers.length,
          usersByRole = UsersByRoleResponse(
            student = studentCount,
            coach = coachCount,
            grader = graderCount,
            admin = adminCount,
            super_admin = superAdminCount
          ),
          lastUpdated = ZonedDateTime.now().toString
        )
        response <- Ok(statistics.asJson)
      } yield response
  }

  // 公共路由（用于健康检查等）
  private val publicRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "data" / "health" =>
      Ok("Unified data service is running")
      
    case GET -> Root / "data" / "info" =>
      val info = ServiceInfoResponse(
        service = "SystemConfigService - Unified Data Proxy",
        version = "1.3.0",
        description = "统一的用户和管理员数据访问代理服务",
        endpoints = List(
          "/admin/data/users/all - 获取所有用户",
          "/admin/data/users/by-role/{role} - 按角色获取用户",
          "/admin/data/users/by-status/{status} - 按状态获取用户",
          "/admin/data/users/search?q={query} - 搜索用户",
          "/admin/data/statistics - 获取数据统计"
        )
      )
      Ok(info.asJson)
  }

  // 合并所有路由
  val routes: HttpRoutes[IO] = {
    Router(
      "/admin" -> adminAuthMiddleware(unifiedRoutes),
      "/" -> publicRoutes
    )
  }
}
