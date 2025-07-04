package com.galphos.systemconfig.routes

import cats.effect.IO
import org.http4s.{AuthedRoutes, HttpRoutes, Request}
import org.http4s.dsl.io._
import org.http4s.server.{AuthMiddleware, Router}
import org.http4s.headers.Authorization
import com.galphos.systemconfig.models._
import com.galphos.systemconfig.models.Models._
import com.galphos.systemconfig.services.{SettingsService, AuthService}
import io.circe.syntax._
import io.circe.generic.auto._
import org.http4s.circe.CirceEntityCodec._
import org.typelevel.log4cats.slf4j.Slf4jLogger
import cats.implicits._

class SettingsRoutes(settingsService: SettingsService, authService: AuthService) {
  private val logger = Slf4jLogger.getLogger[IO]
  
  // 提取和验证Bearer令牌
  private def extractBearerToken(request: Request[IO]): IO[Option[String]] = IO {
    request.headers.get[Authorization].flatMap { auth =>
      auth.credentials match {
        case org.http4s.Credentials.Token(org.http4s.AuthScheme.Bearer, token) => Some(token)
        case _ => None
      }
    }
  }
  
  // 管理员认证中间件
  private val adminAuthMiddleware: AuthMiddleware[IO, String] = {
    import cats.data.{Kleisli, OptionT}
    
    val authUser: Kleisli[[x] =>> OptionT[IO, x], org.http4s.Request[IO], String] = 
      Kleisli { request =>
        OptionT(
          extractBearerToken(request).flatMap {
            case Some(token) =>
              authService.isAdminWithHealthCheck(token).map {
                case true => Some(token)
                case false => None
              }
            case None => IO.pure(None)
          }
        )
      }
      
    AuthMiddleware(authUser)
  }
  
  // 管理员路由
  private val adminRoutes: AuthedRoutes[String, IO] = AuthedRoutes.of[String, IO] {
    // 获取所有系统设置（包括非公开设置）
    case GET -> Root / "admin" / "system" / "settings" as token =>
      settingsService.getAllSettings(isPublic = false).flatMap { settings =>
        Ok(settings)
      }.handleErrorWith { error =>
        logger.error(error)("获取系统设置列表失败") *>
        InternalServerError(ErrorResponse(s"获取系统设置列表失败: ${error.getMessage}"))
      }
      
    // 更新系统设置 - 通过 configKey 路径参数
    case req @ PUT -> Root / "admin" / "system" / "settings" / configKey as token =>
      req.req.as[SystemConfig].flatMap { config =>
        settingsService.updateSetting(configKey, config.configValue, Some(config.isPublic)).flatMap {
          case Some(updated) => Ok(updated)
          case None => NotFound(ErrorResponse("设置不存在"))
        }
      }.handleErrorWith { error =>
        logger.error(error)("更新系统设置失败") *>
        InternalServerError(ErrorResponse(s"更新系统设置失败: ${error.getMessage}"))
      }
      
    // 更新系统设置 - 通过请求体
    case req @ PUT -> Root / "admin" / "system" / "settings" as token =>
      req.req.as[SystemConfig].flatMap { config =>
        settingsService.updateSetting(config.configKey, config.configValue, Some(config.isPublic)).flatMap {
          case Some(updated) => Ok(updated)
          case None => NotFound(ErrorResponse("设置不存在"))
        }
      }.handleErrorWith { error =>
        logger.error(error)("更新系统设置失败") *>
        InternalServerError(ErrorResponse(s"更新系统设置失败: ${error.getMessage}"))
      }
      
    // 创建系统设置
    case req @ POST -> Root / "admin" / "system" / "settings" as token =>
      req.req.as[SystemConfig].flatMap { config =>
        settingsService.createSetting(config).flatMap {
          case Some(created) => Created(created)
          case None => BadRequest(ErrorResponse("无法创建设置"))
        }
      }.handleErrorWith { error =>
        logger.error(error)("创建系统设置失败") *>
        InternalServerError(ErrorResponse(s"创建系统设置失败: ${error.getMessage}"))
      }
      
    // 删除系统设置
    case DELETE -> Root / "admin" / "system" / "settings" / configKey as token =>
      settingsService.deleteSetting(configKey).flatMap {
        case true => NoContent()
        case false => NotFound(ErrorResponse("设置不存在"))
      }.handleErrorWith { error =>
        logger.error(error)(s"删除系统设置失败，Key: $configKey") *>
        InternalServerError(ErrorResponse(s"删除系统设置失败: ${error.getMessage}"))
      }
  }
  
  // 公共路由
  private val publicRoutes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    // 获取公共系统设置
    case GET -> Root / "system" / "settings" =>
      settingsService.getAllSettings(isPublic = true).flatMap { settings =>
        Ok(settings)
      }.handleErrorWith { error =>
        logger.error(error)("获取公共系统设置失败") *>
        InternalServerError(ErrorResponse(s"获取公共系统设置失败: ${error.getMessage}"))
      }
  }
  
  // 合并路由
  val routes: HttpRoutes[IO] = adminAuthMiddleware(adminRoutes).combineK(publicRoutes)
}
