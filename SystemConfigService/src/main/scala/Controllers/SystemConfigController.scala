package Controllers

import Models.*
import Models.Encoders.given
import Services.*
import cats.effect.IO
import cats.implicits.*
import io.circe.*
import io.circe.syntax.*
import io.circe.Json
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.io.*
import org.slf4j.LoggerFactory

class SystemConfigController(
  configService: SystemConfigService
) {
  private val logger = LoggerFactory.getLogger("SystemConfigController")

  // CORS 支持
  private val corsHeaders = Headers(
    ("Access-Control-Allow-Origin", "*"),
    ("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS"),
    ("Access-Control-Allow-Headers", "Content-Type, Authorization"),
    ("Access-Control-Max-Age", "3600")
  )

  implicit val systemSettingsDecoder: EntityDecoder[IO, UpdateSystemSettingsRequest] = jsonOf[IO, UpdateSystemSettingsRequest]
  implicit val createAdminDecoder: EntityDecoder[IO, CreateAdminRequest] = jsonOf[IO, CreateAdminRequest]
  implicit val updateAdminDecoder: EntityDecoder[IO, UpdateAdminRequest] = jsonOf[IO, UpdateAdminRequest]
  implicit val changePasswordDecoder: EntityDecoder[IO, ChangeAdminPasswordRequest] = jsonOf[IO, ChangeAdminPasswordRequest]
  implicit val resetPasswordDecoder: EntityDecoder[IO, ResetAdminPasswordRequest] = jsonOf[IO, ResetAdminPasswordRequest]

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {

    // CORS 预检请求
    case OPTIONS -> _ =>
      Ok().map(_.withHeaders(corsHeaders))

    // ===== 系统管理员管理 API =====
    
    // GET /api/admin/system/admins - 获取系统管理员列表
    case GET -> Root / "api" / "admin" / "system" / "admins" :? OptionalPageQueryParamMatcher(page) 
          +& OptionalLimitQueryParamMatcher(limit) 
          +& OptionalSearchQueryParamMatcher(search) =>
      val params = QueryParams(page = page, limit = limit, search = search)
      configService.getSystemAdmins(params).flatMap { result =>
        Ok(ApiResponse.success(result).asJson)
      }.handleErrorWith { error =>
        logger.error("获取系统管理员列表失败", error)
        InternalServerError(ApiResponse.error(s"获取系统管理员列表失败: ${error.getMessage}").asJson)
      }.map(_.withHeaders(corsHeaders))

    // POST /api/admin/system/admins - 创建系统管理员
    case req @ POST -> Root / "api" / "admin" / "system" / "admins" =>
      req.as[CreateAdminRequest].flatMap { request =>
        configService.createSystemAdmin(request).flatMap { adminId =>
          Created(ApiResponse.success(Map("adminId" -> adminId), "系统管理员创建成功").asJson)
        }
      }.handleErrorWith { error =>
        logger.error("创建系统管理员失败", error)
        BadRequest(ApiResponse.error(s"创建系统管理员失败: ${error.getMessage}").asJson)
      }

    // GET /api/admin/system/admins/{adminId} - 获取系统管理员详情
    case GET -> Root / "api" / "admin" / "system" / "admins" / adminId =>
      configService.getSystemAdminById(adminId).flatMap {
        case Some(admin) => Ok(ApiResponse.success(admin).asJson)
        case None => NotFound(ApiResponse.error("系统管理员不存在").asJson)
      }.handleErrorWith { error =>
        logger.error(s"获取系统管理员详情失败: $adminId", error)
        InternalServerError(ApiResponse.error(s"获取系统管理员详情失败: ${error.getMessage}").asJson)
      }

    // PUT /api/admin/system/admins/{adminId} - 更新系统管理员
    case req @ PUT -> Root / "api" / "admin" / "system" / "admins" / adminId =>
      req.as[UpdateAdminRequest].flatMap { request =>
        configService.updateSystemAdmin(adminId, request).flatMap { _ =>
          Ok(ApiResponse.successMessage("系统管理员更新成功").asJson)
        }
      }.handleErrorWith { error =>
        logger.error(s"更新系统管理员失败: $adminId", error)
        BadRequest(ApiResponse.error(s"更新系统管理员失败: ${error.getMessage}").asJson)
      }

    // DELETE /api/admin/system/admins/{adminId} - 删除系统管理员
    case DELETE -> Root / "api" / "admin" / "system" / "admins" / adminId =>
      configService.deleteSystemAdmin(adminId).flatMap { _ =>
        Ok(ApiResponse.successMessage("系统管理员删除成功").asJson)
      }.handleErrorWith { error =>
        logger.error(s"删除系统管理员失败: $adminId", error)
        BadRequest(ApiResponse.error(s"删除系统管理员失败: ${error.getMessage}").asJson)
      }

    // PUT /api/admin/system/admins/{adminId}/password - 修改系统管理员密码
    case req @ PUT -> Root / "api" / "admin" / "system" / "admins" / adminId / "password" =>
      req.as[ResetAdminPasswordRequest].flatMap { request =>
        configService.resetAdminPassword(adminId, request.password).flatMap { _ =>
          Ok(ApiResponse.successMessage("密码修改成功").asJson)
        }
      }.handleErrorWith { error =>
        logger.error(s"修改系统管理员密码失败: $adminId", error)
        BadRequest(ApiResponse.error(s"修改密码失败: ${error.getMessage}").asJson)
      }

    // ===== 系统配置管理 API =====

    // GET /api/admin/system/settings - 获取系统配置（管理员视图）
    case GET -> Root / "api" / "admin" / "system" / "settings" =>
      configService.getSystemSettings().flatMap { settings =>
        val filteredSettings = SystemSettings(
          announcementEnabled = settings.announcementEnabled,
          systemName = settings.systemName,
          version = settings.version,
          buildTime = settings.buildTime
        )
        Ok(ApiResponse.success(filteredSettings).asJson)
      }.handleErrorWith { error =>
        logger.error("获取系统配置失败", error)
        InternalServerError(ApiResponse.error(s"获取系统配置失败: ${error.getMessage}").asJson)
      }.map(_.withHeaders(corsHeaders))

    // PUT /api/admin/system/settings - 更新系统配置
    case req @ PUT -> Root / "api" / "admin" / "system" / "settings" =>
      req.as[UpdateSystemSettingsRequest].flatMap { request =>
        val filteredRequest = UpdateSystemSettingsRequest(
          announcementEnabled = request.announcementEnabled,
          systemName = request.systemName,
          version = request.version,
          buildTime = request.buildTime
        )
        configService.updateSystemSettings(filteredRequest).flatMap { _ =>
          Ok(ApiResponse.successMessage("系统配置更新成功").asJson)
        }
      }.handleErrorWith { error =>
        logger.error("更新系统配置失败", error)
        BadRequest(ApiResponse.error(s"更新系统配置失败: ${error.getMessage}").asJson)
      }.map(_.withHeaders(corsHeaders))

    // GET /api/public/settings - 获取公开系统配置（前端可访问）
    case GET -> Root / "api" / "public" / "settings" =>
      configService.getPublicSettings().flatMap { settings =>
        Ok(ApiResponse.success(settings).asJson)
      }.handleErrorWith { error =>
        logger.error("获取公开系统配置失败", error)
        InternalServerError(ApiResponse.error(s"获取公开系统配置失败: ${error.getMessage}").asJson)
      }.map(_.withHeaders(corsHeaders))

    // ===== 健康检查 API =====

    // GET /health - 健康检查
    case GET -> Root / "health" =>
      import Database.DatabaseManager
      DatabaseManager.healthCheck().flatMap { isHealthy =>
        if (isHealthy) {
          Ok(Map(
            "status" -> "healthy",
            "service" -> "SystemConfigService",
            "timestamp" -> java.time.LocalDateTime.now().toString
          ).asJson)
        } else {
          ServiceUnavailable(Map(
            "status" -> "unhealthy",
            "service" -> "SystemConfigService",
            "timestamp" -> java.time.LocalDateTime.now().toString
          ).asJson)
        }
      }

    // 404 处理
    case _ =>
      NotFound(ApiResponse.error("API端点不存在").asJson).map(_.withHeaders(corsHeaders))
  }

  // 查询参数匹配器
  object OptionalPageQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Int]("page")
  object OptionalLimitQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Int]("limit")
  object OptionalSearchQueryParamMatcher extends OptionalQueryParamDecoderMatcher[String]("search")
}
