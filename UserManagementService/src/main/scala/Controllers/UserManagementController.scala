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

class UserManagementController(
  userManagementService: UserManagementService,
  coachStudentService: CoachStudentService,
  authMiddleware: AuthMiddlewareService
) {
  private val logger = LoggerFactory.getLogger("UserManagementController")

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

    // ===================== 用户管理模块 =====================
    
    // 获取待审核用户列表
    case req @ GET -> Root / "api" / "admin" / "users" / "pending" =>
      authenticateAdmin(req) { _ =>
        handleGetPendingUsers()
      }.map(_.withHeaders(corsHeaders))

    // 审核用户申请
    case req @ POST -> Root / "api" / "admin" / "users" / "approve" =>
      authenticateAdmin(req) { _ =>
        handleApproveUser(req)
      }.map(_.withHeaders(corsHeaders))

    // 获取已审核用户列表
    case req @ GET -> Root / "api" / "admin" / "users" / "approved" =>
      authenticateAdmin(req) { _ =>
        handleGetApprovedUsers(req)
      }.map(_.withHeaders(corsHeaders))

    // 更新用户状态
    case req @ PUT -> Root / "api" / "admin" / "users" / "status" =>
      authenticateAdmin(req) { _ =>
        handleUpdateUserStatus(req)
      }.map(_.withHeaders(corsHeaders))

    // 删除用户
    case req @ DELETE -> Root / "api" / "admin" / "users" / userId =>
      authenticateAdmin(req) { _ =>
        handleDeleteUser(userId)
      }.map(_.withHeaders(corsHeaders))

    // ===================== 教练学生管理模块 =====================
    
    // 获取教练学生关系列表
    case req @ GET -> Root / "api" / "admin" / "coach-students" =>
      authenticateAdmin(req) { _ =>
        handleGetCoachStudents(req)
      }.map(_.withHeaders(corsHeaders))

    // 获取教练学生统计
    case req @ GET -> Root / "api" / "admin" / "coach-students" / "stats" =>
      authenticateAdmin(req) { _ =>
        handleGetCoachStudentStats()
      }.map(_.withHeaders(corsHeaders))

    // 创建教练学生关系
    case req @ POST -> Root / "api" / "admin" / "coach-students" =>
      authenticateAdmin(req) { _ =>
        handleCreateCoachStudentRelationship(req)
      }.map(_.withHeaders(corsHeaders))

    // 删除教练学生关系
    case req @ DELETE -> Root / "api" / "admin" / "coach-students" / relationshipId =>
      authenticateAdmin(req) { _ =>
        handleDeleteCoachStudentRelationship(relationshipId)
      }.map(_.withHeaders(corsHeaders))

    // ===================== 学生注册审核模块 =====================
    
    // 获取学生注册申请列表
    case req @ GET -> Root / "api" / "admin" / "student-registrations" =>
      authenticateAdmin(req) { _ =>
        handleGetStudentRegistrations(req)
      }.map(_.withHeaders(corsHeaders))
    
    // 审核学生注册申请
    case req @ POST -> Root / "api" / "admin" / "student-registrations" / "approve" =>
      authenticateAdmin(req) { _ =>
        handleApproveStudentRegistration(req)
      }.map(_.withHeaders(corsHeaders))

    // ===================== 个人资料管理模块 =====================
    
    // 管理员个人资料
    case req @ GET -> Root / "api" / "admin" / "profile" =>
      authenticateAdmin(req) { authResult =>
        handleGetAdminProfile(authResult.username.getOrElse(""))
      }.map(_.withHeaders(corsHeaders))

    case req @ PUT -> Root / "api" / "admin" / "profile" =>
      authenticateAdmin(req) { authResult =>
        handleUpdateAdminProfile(req, authResult.username.getOrElse(""))
      }.map(_.withHeaders(corsHeaders))

    // 学生个人资料
    case req @ GET -> Root / "api" / "student" / "profile" =>
      authenticateUser(req, "student") { authResult =>
        handleGetStudentProfile(authResult.username.getOrElse(""))
      }.map(_.withHeaders(corsHeaders))

    case req @ PUT -> Root / "api" / "student" / "profile" =>
      authenticateUser(req, "student") { authResult =>
        handleUpdateStudentProfile(req, authResult.username.getOrElse(""))
      }.map(_.withHeaders(corsHeaders))

    // 学生密码修改
    case req @ PUT -> Root / "api" / "student" / "password" =>
      authenticateUser(req, "student") { authResult =>
        handleChangeStudentPassword(req, authResult.username.getOrElse(""))
      }.map(_.withHeaders(corsHeaders))

    // 学生区域变更申请
    case req @ POST -> Root / "api" / "student" / "region-change" =>
      authenticateUser(req, "student") { authResult =>
        handleStudentRegionChangeRequest(req, authResult.username.getOrElse(""))
      }.map(_.withHeaders(corsHeaders))

    case req @ GET -> Root / "api" / "student" / "region-change" =>
      authenticateUser(req, "student") { authResult =>
        handleGetStudentRegionChangeRequests(authResult.username.getOrElse(""))
      }.map(_.withHeaders(corsHeaders))

    // 教练个人资料
    case req @ GET -> Root / "api" / "coach" / "profile" =>
      authenticateUser(req, "coach") { authResult =>
        handleGetCoachProfile(authResult.username.getOrElse(""))
      }.map(_.withHeaders(corsHeaders))

    case req @ PUT -> Root / "api" / "coach" / "profile" =>
      authenticateUser(req, "coach") { authResult =>
        handleUpdateCoachProfile(req, authResult.username.getOrElse(""))
      }.map(_.withHeaders(corsHeaders))

    // 教练管理的学生
    case req @ GET -> Root / "api" / "coach" / "students" =>
      authenticateUser(req, "coach") { authResult =>
        handleGetCoachManagedStudents(req, authResult.username.getOrElse(""))
      }.map(_.withHeaders(corsHeaders))

    case req @ POST -> Root / "api" / "coach" / "students" =>
      authenticateUser(req, "coach") { authResult =>
        handleAddCoachManagedStudent(req, authResult.username.getOrElse(""))
      }.map(_.withHeaders(corsHeaders))

    // 阅卷员个人资料
    case req @ GET -> Root / "api" / "grader" / "profile" =>
      authenticateUser(req, "grader") { authResult =>
        handleGetGraderProfile(authResult.username.getOrElse(""))
      }.map(_.withHeaders(corsHeaders))

    case req @ PUT -> Root / "api" / "grader" / "profile" =>
      authenticateUser(req, "grader") { authResult =>
        handleUpdateGraderProfile(req, authResult.username.getOrElse(""))
      }.map(_.withHeaders(corsHeaders))

    // 阅卷员密码修改
    case req @ PUT -> Root / "api" / "grader" / "change-password" =>
      authenticateUser(req, "grader") { authResult =>
        handleChangeGraderPassword(req, authResult.username.getOrElse(""))
      }.map(_.withHeaders(corsHeaders))

    // 健康检查
    case GET -> Root / "health" =>
      Ok("OK").map(_.withHeaders(corsHeaders))
  }

  // 管理员身份验证中间件
  private def authenticateAdmin(req: Request[IO])(handler: AuthResult => IO[Response[IO]]): IO[Response[IO]] = {
    extractTokenFromHeader(req) match {
      case Some(token) =>
        authMiddleware.validateAdminToken(token).flatMap { authResult =>
          if (authResult.success) {
            handler(authResult)
          } else {
            IO.pure(Response[IO](Status.Unauthorized).withEntity(ApiResponse.error(authResult.message.getOrElse("身份验证失败")).asJson))
          }
        }.handleErrorWith { error =>
          logger.error("身份验证过程出错", error)
          InternalServerError(ApiResponse.error(s"身份验证失败: ${error.getMessage}").asJson)
        }
      case None =>
        BadRequest(ApiResponse.error("缺少Authorization头").asJson)
    }
  }

  // 用户身份验证中间件（非管理员用户）
  private def authenticateUser(req: Request[IO], requiredRole: String)(handler: AuthResult => IO[Response[IO]]): IO[Response[IO]] = {
    extractTokenFromHeader(req) match {
      case Some(token) =>
        // 这里应该调用认证服务验证普通用户token
        // 暂时简化实现，实际应该有专门的用户token验证方法
        authMiddleware.validateAdminToken(token).flatMap { authResult =>
          if (authResult.success && authResult.userType.contains(requiredRole)) {
            handler(authResult)
          } else {
            IO.pure(Response[IO](Status.Unauthorized).withEntity(ApiResponse.error(s"权限不足：需要${requiredRole}身份").asJson))
          }
        }.handleErrorWith { error =>
          logger.error("用户身份验证过程出错", error)
          InternalServerError(ApiResponse.error(s"身份验证失败: ${error.getMessage}").asJson)
        }
      case None =>
        BadRequest(ApiResponse.error("缺少Authorization头").asJson)
    }
  }

  // ===================== 用户管理处理方法 =====================
  
  private def handleGetPendingUsers(): IO[Response[IO]] = {
    for {
      users <- userManagementService.getPendingUsers()
      response <- Ok(ApiResponse.success(users, "获取待审核用户列表成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("获取待审核用户列表失败", error)
    InternalServerError(ApiResponse.error(s"获取失败: ${error.getMessage}").asJson)
  }

  private def handleApproveUser(req: Request[IO]): IO[Response[IO]] = {
    for {
      approvalReq <- req.as[UserApprovalRequest]
      _ <- userManagementService.approveUser(approvalReq)
      response <- Ok(ApiResponse.success((), "用户审核成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("审核用户失败", error)
    BadRequest(ApiResponse.error(s"审核失败: ${error.getMessage}").asJson)
  }

  private def handleGetApprovedUsers(req: Request[IO]): IO[Response[IO]] = {
    val queryParams = extractQueryParams(req)
    for {
      result <- userManagementService.getApprovedUsers(queryParams)
      response <- Ok(ApiResponse.success(result, "获取已审核用户列表成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("获取已审核用户列表失败", error)
    InternalServerError(ApiResponse.error(s"获取失败: ${error.getMessage}").asJson)
  }

  private def handleUpdateUserStatus(req: Request[IO]): IO[Response[IO]] = {
    for {
      updateReq <- req.as[UserStatusUpdateRequest]
      _ <- userManagementService.updateUserStatus(updateReq)
      response <- Ok(ApiResponse.success((), "用户状态更新成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("更新用户状态失败", error)
    BadRequest(ApiResponse.error(s"更新失败: ${error.getMessage}").asJson)
  }

  private def handleDeleteUser(userId: String): IO[Response[IO]] = {
    for {
      _ <- userManagementService.deleteUser(userId)
      response <- Ok(ApiResponse.success((), "用户删除成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("删除用户失败", error)
    BadRequest(ApiResponse.error(s"删除失败: ${error.getMessage}").asJson)
  }

  // ===================== 教练学生管理处理方法 =====================
  
  private def handleGetCoachStudents(req: Request[IO]): IO[Response[IO]] = {
    val queryParams = extractQueryParams(req)
    for {
      result <- coachStudentService.getCoachStudentRelationships(queryParams)
      response <- Ok(ApiResponse.success(result, "获取教练学生关系列表成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("获取教练学生关系列表失败", error)
    InternalServerError(ApiResponse.error(s"获取失败: ${error.getMessage}").asJson)
  }

  private def handleGetCoachStudentStats(): IO[Response[IO]] = {
    for {
      stats <- coachStudentService.getCoachStudentStats()
      response <- Ok(ApiResponse.success(stats, "获取教练学生统计成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("获取教练学生统计失败", error)
    InternalServerError(ApiResponse.error(s"获取失败: ${error.getMessage}").asJson)
  }

  private def handleCreateCoachStudentRelationship(req: Request[IO]): IO[Response[IO]] = {
    for {
      createReq <- req.as[CreateCoachStudentRequest]
      relationshipId <- coachStudentService.createCoachStudentRelationship(createReq)
      response <- Ok(ApiResponse.success(Map("id" -> relationshipId), "教练学生关系创建成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("创建教练学生关系失败", error)
    BadRequest(ApiResponse.error(s"创建失败: ${error.getMessage}").asJson)
  }

  private def handleDeleteCoachStudentRelationship(relationshipId: String): IO[Response[IO]] = {
    for {
      _ <- coachStudentService.deleteCoachStudentRelationship(relationshipId)
      response <- Ok(ApiResponse.success((), "教练学生关系删除成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("删除教练学生关系失败", error)
    BadRequest(ApiResponse.error(s"删除失败: ${error.getMessage}").asJson)
  }

  // ===================== 辅助方法 =====================
  
  // 从请求头提取Token
  private def extractTokenFromHeader(req: Request[IO]): Option[String] = {
    req.headers.get[Authorization].flatMap {
      case Authorization(Credentials.Token(scheme, token)) if scheme.toString.toLowerCase == "bearer" =>
        Some(token)
      case _ => None
    }
  }

  // ===================== 新增API处理方法 =====================

  // 学生注册审核相关
  private def handleGetStudentRegistrations(req: Request[IO]): IO[Response[IO]] = {
    val queryParams = extractQueryParams(req)
    for {
      result <- userManagementService.getPendingUsers() // 复用现有方法
      response <- Ok(ApiResponse.success(result, "获取学生注册申请列表成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("获取学生注册申请列表失败", error)
    InternalServerError(ApiResponse.error(s"获取失败: ${error.getMessage}").asJson)
  }

  private def handleApproveStudentRegistration(req: Request[IO]): IO[Response[IO]] = {
    for {
      approvalReq <- req.as[UserApprovalRequest]
      _ <- userManagementService.approveUser(approvalReq)
      response <- Ok(ApiResponse.success((), "学生注册申请审核成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("审核学生注册申请失败", error)
    BadRequest(ApiResponse.error(s"审核失败: ${error.getMessage}").asJson)
  }

  // 个人资料管理相关
  private def handleGetAdminProfile(username: String): IO[Response[IO]] = {
    // TODO: 实现管理员个人资料获取
    Ok(ApiResponse.success(Map("username" -> username, "role" -> "admin"), "获取管理员资料成功").asJson)
  }

  private def handleUpdateAdminProfile(req: Request[IO], username: String): IO[Response[IO]] = {
    // TODO: 实现管理员个人资料更新
    Ok(ApiResponse.success((), "管理员资料更新成功").asJson)
  }

  private def handleGetStudentProfile(username: String): IO[Response[IO]] = {
    // TODO: 实现学生个人资料获取
    Ok(ApiResponse.success(Map("username" -> username, "role" -> "student"), "获取学生资料成功").asJson)
  }

  private def handleUpdateStudentProfile(req: Request[IO], username: String): IO[Response[IO]] = {
    // TODO: 实现学生个人资料更新
    Ok(ApiResponse.success((), "学生资料更新成功").asJson)
  }

  private def handleChangeStudentPassword(req: Request[IO], username: String): IO[Response[IO]] = {
    // TODO: 实现学生密码修改
    Ok(ApiResponse.success((), "学生密码修改成功").asJson)
  }

  private def handleStudentRegionChangeRequest(req: Request[IO], username: String): IO[Response[IO]] = {
    // TODO: 实现学生区域变更申请
    Ok(ApiResponse.success((), "区域变更申请提交成功").asJson)
  }

  private def handleGetStudentRegionChangeRequests(username: String): IO[Response[IO]] = {
    // TODO: 实现获取学生区域变更申请列表
    val emptyList: List[String] = List.empty
    Ok(ApiResponse.success(emptyList, "获取区域变更申请列表成功").asJson)
  }

  private def handleGetCoachProfile(username: String): IO[Response[IO]] = {
    // TODO: 实现教练个人资料获取
    Ok(ApiResponse.success(Map("username" -> username, "role" -> "coach"), "获取教练资料成功").asJson)
  }

  private def handleUpdateCoachProfile(req: Request[IO], username: String): IO[Response[IO]] = {
    // TODO: 实现教练个人资料更新
    Ok(ApiResponse.success((), "教练资料更新成功").asJson)
  }

  private def handleGetCoachManagedStudents(req: Request[IO], coachUsername: String): IO[Response[IO]] = {
    val queryParams = extractQueryParams(req)
    for {
      result <- coachStudentService.getCoachStudentRelationships(queryParams)
      response <- Ok(ApiResponse.success(result, "获取教练管理的学生列表成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("获取教练管理的学生列表失败", error)
    InternalServerError(ApiResponse.error(s"获取失败: ${error.getMessage}").asJson)
  }

  private def handleAddCoachManagedStudent(req: Request[IO], coachUsername: String): IO[Response[IO]] = {
    for {
      createReq <- req.as[CreateCoachStudentRequest]
      relationshipId <- coachStudentService.createCoachStudentRelationship(createReq)
      response <- Ok(ApiResponse.success(Map("id" -> relationshipId), "添加学生成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("添加学生失败", error)
    BadRequest(ApiResponse.error(s"添加失败: ${error.getMessage}").asJson)
  }

  private def handleGetGraderProfile(username: String): IO[Response[IO]] = {
    // TODO: 实现阅卷员个人资料获取
    Ok(ApiResponse.success(Map("username" -> username, "role" -> "grader"), "获取阅卷员资料成功").asJson)
  }

  private def handleUpdateGraderProfile(req: Request[IO], username: String): IO[Response[IO]] = {
    // TODO: 实现阅卷员个人资料更新
    Ok(ApiResponse.success((), "阅卷员资料更新成功").asJson)
  }

  private def handleChangeGraderPassword(req: Request[IO], username: String): IO[Response[IO]] = {
    // TODO: 实现阅卷员密码修改
    Ok(ApiResponse.success((), "阅卷员密码修改成功").asJson)
  }

  // 提取查询参数
  private def extractQueryParams(req: Request[IO]): QueryParams = {
    val params = req.params
    QueryParams(
      page = params.get("page").flatMap(_.toIntOption),
      limit = params.get("limit").flatMap(_.toIntOption),
      role = params.get("role"),
      status = params.get("status"),
      search = params.get("search"),
      provinceId = params.get("provinceId"),
      coachId = params.get("coachId")
    )
  }
}
