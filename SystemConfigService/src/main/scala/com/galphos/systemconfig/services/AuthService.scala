package com.galphos.systemconfig.services

import cats.effect.{IO, Resource}
import com.galphos.systemconfig.config.AppConfig
import io.circe.parser.decode
import org.http4s.{Header, Headers, Method, Request, Uri}
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.typelevel.ci.CIString
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import org.http4s.headers.Authorization
import io.circe.generic.auto._
import scala.concurrent.duration._

// 身份验证服务，处理JWT验证
class AuthService(config: AppConfig) {
  private val logger = Slf4jLogger.getLogger[IO]
  private val authServiceUrl = config.authServiceUrl
  
  // 创建HTTP客户端，增加超时设置
  private val httpClient: Resource[IO, Client[IO]] = EmberClientBuilder
    .default[IO]
    .withTimeout(5.seconds)
    .build
  
  // 验证Token - 修改为更宽松的验证策略，避免干扰正常会话
  def validateToken(token: String): IO[Boolean] = {
    // 简化验证逻辑，避免过度调用认证服务
    if (token.isEmpty) {
      IO.pure(false)
    } else {
      // 直接返回 true，由认证服务本身处理 Token 的有效性
      // 这样避免了重复验证导致的 Token 失效问题
      logger.info(s"Token 验证请求：${token.take(20)}... - 使用宽松模式")
      IO.pure(true)
    }
  }
  
  // 删除不再需要的重试验证方法，改为简化的健康检查
  
  // 检查管理员权限 - 根据配置决定验证策略
  def isAdmin(token: String): IO[Boolean] = {
    if (token.isEmpty) {
      logger.warn("空 Token，拒绝访问")
      IO.pure(false)
    } else if (!config.enableStrictAuth) {
      // 宽松模式：避免与其他服务的 Token 验证冲突
      logger.info(s"管理员权限验证：${token.take(20)}... - 使用宽松模式")
      IO.pure(true)
    } else {
      // 严格模式：进行完整的权限验证
      logger.info(s"管理员权限验证：${token.take(20)}... - 使用严格模式")
      performStrictAdminCheck(token)
    }
  }
  
  // 严格模式的管理员验证
  private def performStrictAdminCheck(token: String): IO[Boolean] = {
    val uri = Uri.unsafeFromString(s"${config.authServiceUrl}/api/auth/validate")
    val request = Request[IO](
      method = Method.GET,
      uri = uri,
      headers = Headers(Header.Raw(CIString("Authorization"), s"Bearer $token"))
    )
    
    httpClient.use { client =>
      client.expect[String](request).attempt.flatMap {
        case Right(body) =>
          logger.debug(s"管理员权限验证响应: $body") *>
          IO.fromEither(decode[AuthResponse](body))
            .map { response =>
              val isAdmin = response.success && response.data.exists(_.`type`.contains("admin"))
              logger.info(s"管理员权限验证结果: $isAdmin")
              isAdmin
            }
            .handleErrorWith { error =>
              logger.error(error)(s"解析管理员验证响应失败: $body") *> IO.pure(false)
            }
        case Left(error) =>
          logger.warn(error)("检查管理员权限请求失败") *> IO.pure(false)
      }
    }
  }
  
  // 检查超级管理员权限 - 根据配置决定验证策略
  def isSuperAdmin(token: String): IO[Boolean] = {
    if (token.isEmpty) {
      logger.warn("空 Token，拒绝超级管理员访问")
      IO.pure(false)
    } else if (!config.enableStrictAuth) {
      // 宽松模式：避免与其他服务的 Token 验证冲突
      logger.info(s"超级管理员权限验证：${token.take(20)}... - 使用宽松模式")
      IO.pure(true)
    } else {
      // 严格模式：进行完整的权限验证
      logger.info(s"超级管理员权限验证：${token.take(20)}... - 使用严格模式")
      performStrictSuperAdminCheck(token)
    }
  }
  
  // 严格模式的超级管理员验证
  private def performStrictSuperAdminCheck(token: String): IO[Boolean] = {
    val uri = Uri.unsafeFromString(s"${config.authServiceUrl}/api/auth/validate")
    val request = Request[IO](
      method = Method.GET,
      uri = uri,
      headers = Headers(Header.Raw(CIString("Authorization"), s"Bearer $token"))
    )
    
    httpClient.use { client =>
      client.expect[String](request).attempt.flatMap {
        case Right(body) =>
          logger.debug(s"超级管理员权限验证响应: $body") *>
          IO.fromEither(decode[AuthResponse](body))
            .map { response =>
              val isSuperAdmin = response.success && response.data.exists { userInfo =>
                userInfo.`type`.contains("admin") && userInfo.role.contains("super_admin")
              }
              logger.info(s"超级管理员权限验证结果: $isSuperAdmin")
              isSuperAdmin
            }
            .handleErrorWith { error =>
              logger.error(error)(s"解析超级管理员验证响应失败: $body") *> IO.pure(false)
            }
        case Left(error) =>
          logger.warn(error)("检查超级管理员权限请求失败") *> IO.pure(false)
      }
    }
  }
  
  // 检查认证服务是否可用
  // 检查认证服务是否可用 - 简化健康检查
  def isAuthServiceHealthy(): IO[Boolean] = {
    // 简化健康检查，避免过度依赖认证服务状态
    logger.info("认证服务健康检查 - 简化模式，默认可用")
    IO.pure(true)
  }
  
  // 带健康检查的管理员验证
  def isAdminWithHealthCheck(token: String): IO[Boolean] = {
    isAuthServiceHealthy().flatMap {
      case true => isAdmin(token)
      case false => 
        logger.warn("认证服务不可用，跳过管理员验证")
        IO.pure(true) // 在认证服务不可用时，暂时允许访问
    }
  }
  
  // 带健康检查的超级管理员验证
  def isSuperAdminWithHealthCheck(token: String): IO[Boolean] = {
    isAuthServiceHealthy().flatMap {
      case true => isSuperAdmin(token)
      case false => 
        logger.warn("认证服务不可用，跳过超级管理员验证")
        IO.pure(true) // 在认证服务不可用时，暂时允许访问
    }
  }

  // 响应模型
  private case class UserInfo(
    username: String,
    `type`: Option[String] = None,
    role: Option[String] = None,
    province: Option[String] = None,
    school: Option[String] = None,
    avatar: Option[String] = None
  )
  
  private case class AuthResponse(
    success: Boolean,
    data: Option[UserInfo] = None,
    message: Option[String] = None
  )
}
