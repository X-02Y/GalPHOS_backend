package Controllers

import cats.effect.IO
import cats.implicits.*
import io.circe.*
import io.circe.syntax.*
import io.circe.generic.auto.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.headers.{Authorization, `Content-Type`}
import org.http4s.Credentials
import org.slf4j.LoggerFactory
import Models.*
import Services.*
import Database.{DatabaseManager, SqlParameter}

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

    // 更新用户状态 - 支持 PUT 和 POST 请求
    case req @ PUT -> Root / "api" / "admin" / "users" / "status" =>
      authenticateAdmin(req) { _ =>
        handleUpdateUserStatus(req)
      }.map(_.withHeaders(corsHeaders))
      
    // 更新用户状态 - 支持前端 POST 请求
    case req @ POST -> Root / "api" / "admin" / "users" / "status" =>
      authenticateAdmin(req) { _ =>
        handleUpdateUserStatus(req)
      }.map(_.withHeaders(corsHeaders))

    // 删除用户
    case req @ DELETE -> Root / "api" / "admin" / "users" / userId =>
      authenticateAdmin(req) { _ =>
        handleDeleteUser(userId)
      }.map(_.withHeaders(corsHeaders))

    // 单用户操作 - 获取用户信息
    case req @ GET -> Root / "api" / "admin" / "users" / userId =>
      authenticateAdmin(req) { _ =>
        handleGetUserById(userId)
      }.map(_.withHeaders(corsHeaders))

    // 单用户操作 - 更新用户信息
    case req @ PUT -> Root / "api" / "admin" / "users" / userId =>
      authenticateAdmin(req) { _ =>
        handleUpdateUserById(req, userId)
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

    // 审核学生注册申请 - review 接口
    case req @ POST -> Root / "api" / "admin" / "student-registrations" / requestId / "review" =>
      authenticateAdmin(req) { _ =>
        handleReviewStudentRegistration(req, requestId)
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
      
    // 管理员密码修改 - 统一为 PUT 方法
    case req @ PUT -> Root / "api" / "admin" / "password" =>
      authenticateAdmin(req) { authResult =>
        handleChangeAdminPassword(req, authResult.username.getOrElse(""))
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

    // 学生密码修改 - 统一为 PUT 方法
    case req @ PUT -> Root / "api" / "student" / "password" =>
      authenticateUser(req, "student") { authResult =>
        handleChangeStudentPassword(req, authResult.username.getOrElse(""))
      }.map(_.withHeaders(corsHeaders))

    // 学生区域变更申请 - 统一为 PUT 方法
    case req @ PUT -> Root / "api" / "student" / "region-change" =>
      authenticateUser(req, "student") { authResult =>
        handleStudentRegionChangeRequest(req, authResult.username.getOrElse(""))
      }.map(_.withHeaders(corsHeaders))

    // 获取学生区域变更申请状态
    case req @ GET -> Root / "api" / "student" / "region-change" / "status" =>
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

    // 教练密码修改 - 统一为 PUT 方法
    case req @ PUT -> Root / "api" / "coach" / "password" =>
      authenticateUser(req, "coach") { authResult =>
        handleChangeCoachPassword(req, authResult.username.getOrElse(""))
      }.map(_.withHeaders(corsHeaders))

    // 教练区域变更申请 - 统一为 PUT 方法
    case req @ PUT -> Root / "api" / "coach" / "region-change" =>
      authenticateUser(req, "coach") { authResult =>
        handleCoachRegionChangeRequest(req, authResult.username.getOrElse(""))
      }.map(_.withHeaders(corsHeaders))

    // 获取教练区域变更申请记录
    case req @ GET -> Root / "api" / "coach" / "region-change" / "status" =>
      authenticateUser(req, "coach") { authResult =>
        handleGetCoachRegionChangeRequests(authResult.username.getOrElse(""))
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

    case req @ PUT -> Root / "api" / "coach" / "students" / studentId =>
      authenticateUser(req, "coach") { authResult =>
        handleUpdateCoachManagedStudent(req, authResult.username.getOrElse(""), studentId)
      }.map(_.withHeaders(corsHeaders))

    case req @ DELETE -> Root / "api" / "coach" / "students" / studentId =>
      authenticateUser(req, "coach") { authResult =>
        handleDeleteCoachManagedStudent(studentId, authResult.username.getOrElse(""))
      }.map(_.withHeaders(corsHeaders))

    // ===================== 阅卷员路由 =====================
    
    // 阅卷员个人资料
    case req @ GET -> Root / "api" / "grader" / "profile" =>
      authenticateUser(req, "grader") { authResult =>
        handleGetGraderProfile(authResult.username.getOrElse(""))
      }.map(_.withHeaders(corsHeaders))

    case req @ PUT -> Root / "api" / "grader" / "profile" =>
      authenticateUser(req, "grader") { authResult =>
        handleUpdateGraderProfile(req, authResult.username.getOrElse(""))
      }.map(_.withHeaders(corsHeaders))

    // 阅卷员密码修改 - 统一为 PUT 方法
    case req @ PUT -> Root / "api" / "grader" / "password" =>
      authenticateUser(req, "grader") { authResult =>
        handleChangeGraderPassword(req, authResult.username.getOrElse(""))
      }.map(_.withHeaders(corsHeaders))

    // ===================== 系统管理员管理模块（SystemConfigService代理） =====================
    
    // 获取管理员列表
    case req @ GET -> Root / "api" / "admin" / "system" / "admins" =>
      authenticateAdmin(req) { _ =>
        handleGetSystemAdmins()
      }.map(_.withHeaders(corsHeaders))
    
    // 创建管理员
    case req @ POST -> Root / "api" / "admin" / "system" / "admins" =>
      authenticateAdmin(req) { _ =>
        handleCreateSystemAdmin(req)
      }.map(_.withHeaders(corsHeaders))
    
    // 更新管理员
    case req @ PUT -> Root / "api" / "admin" / "system" / "admins" / adminId =>
      authenticateAdmin(req) { _ =>
        handleUpdateSystemAdmin(req, adminId)
      }.map(_.withHeaders(corsHeaders))
    
    // 删除管理员
    case req @ DELETE -> Root / "api" / "admin" / "system" / "admins" / adminId =>
      authenticateAdmin(req) { _ =>
        handleDeleteSystemAdmin(adminId)
      }.map(_.withHeaders(corsHeaders))
    
    // 重置管理员密码
    case req @ PUT -> Root / "api" / "admin" / "system" / "admins" / adminId / "password" =>
      authenticateAdmin(req) { _ =>
        handleResetSystemAdminPassword(req, adminId)
      }.map(_.withHeaders(corsHeaders))

    // 测试数据库连接和管理员表
    case req @ GET -> Root / "api" / "admin" / "system" / "test" =>
      authenticateAdmin(req) { _ =>
        handleTestDatabase()
      }.map(_.withHeaders(corsHeaders))

    // 健康检查
    case GET -> Root / "health" =>
      Ok("OK").map(_.withHeaders(corsHeaders))

    // 健康检查API（不需要认证）
    case req @ GET -> Root / "api" / "health" =>
      handleHealthCheck().map(_.withHeaders(corsHeaders))
  }

  // 管理员身份验证中间件
  private def authenticateAdmin(req: Request[IO])(handler: AuthResult => IO[Response[IO]]): IO[Response[IO]] = {
    extractTokenFromHeader(req) match {
      case Some(token: String) =>
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
      case Some(token: String) =>
        authMiddleware.validateUserToken(token, Some(requiredRole)).flatMap { authResult =>
          if (authResult.success) {
            handler(authResult)
          } else {
            IO.pure(Response[IO](Status.Unauthorized).withEntity(ApiResponse.error(authResult.message.getOrElse(s"权限不足：需要${requiredRole}身份")).asJson))
          }
        }.handleErrorWith { error =>
          logger.error("用户身份验证过程出错", error)
          InternalServerError(ApiResponse.error(s"身份验证失败: ${error.getMessage}").asJson)
        }
      case None =>
        BadRequest(ApiResponse.error("缺少Authorization头").asJson)
    }
  }

  // 从请求头提取Token
  private def extractTokenFromHeader(req: Request[IO]): Option[String] = {
    req.headers.get[Authorization].flatMap {
      case Authorization(Credentials.Token(scheme, token)) if scheme.toString.toLowerCase == "bearer" =>
        Some(token)
      case _ => None
    }
  }

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
      // 转换为API响应格式，严格适配前端ApprovedUser类型
      userDtos = result.items.map { user =>
        val apiStatus = user.status.value.toLowerCase match {
          case "active" | "approved" => "approved"
          case "disabled" => "disabled"
          case _ => user.status.value.toLowerCase
        }
        ApprovedUserDto(
          id = user.id,
          username = user.username,
          phone = user.phone,
          role = user.role.value,
          province = user.province.getOrElse(""),
          school = user.school.getOrElse(""),
          status = apiStatus,
          approvedAt = user.approvedAt.getOrElse(""),
          lastLoginAt = user.lastLoginAt.getOrElse(""),
          avatarUrl = user.avatarUrl
        )
      }
      responseData = ApprovedUsersResponse(
        users = userDtos,
        total = result.total,
        page = result.page,
        limit = result.limit
      )
      response <- Ok(ApiResponse.success(responseData, "获取已审核用户列表成功").asJson)
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

  // ===================== 用户管理模块 - stub 补充 =====================
  def handleDeleteUser(userId: String): IO[Response[IO]] = {
    // TODO: 实现删除用户逻辑
    Ok(ApiResponse.success(s"删除用户: $userId").asJson)
  }

  def handleGetUserById(userId: String): IO[Response[IO]] = {
    // TODO: 实现获取用户信息逻辑
    Ok(ApiResponse.success(s"获取用户: $userId").asJson)
  }

  def handleUpdateUserById(req: Request[IO], userId: String): IO[Response[IO]] = {
    // TODO: 实现更新用户信息逻辑
    Ok(ApiResponse.success(s"更新用户: $userId").asJson)
  }

  // ===================== 教练学生管理处理方法 =====================
  
  private def handleGetCoachStudents(req: Request[IO]): IO[Response[IO]] = {
    val queryParams = extractQueryParams(req)
    for {
      result <- coachStudentService.getCoachStudentRelationships(queryParams)
      response <- Ok(ApiResponse.success(result, "获取教练学生关系成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("获取教练学生关系失败", error)
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
      response <- Ok(ApiResponse.success(Map("id" -> relationshipId), "创建教练学生关系成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("创建教练学生关系失败", error)
    BadRequest(ApiResponse.error(s"创建失败: ${error.getMessage}").asJson)
  }

  private def handleDeleteCoachStudentRelationship(relationshipId: String): IO[Response[IO]] = {
    for {
      _ <- coachStudentService.deleteCoachStudentRelationship(relationshipId)
      response <- Ok(ApiResponse.success((), "删除教练学生关系成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("删除教练学生关系失败", error)
    BadRequest(ApiResponse.error(s"删除失败: ${error.getMessage}").asJson)
  }

  // ===================== 学生注册审核处理方法 =====================
  
  private def handleGetStudentRegistrations(req: Request[IO]): IO[Response[IO]] = {
    for {
      registrations <- userManagementService.getStudentRegistrationRequests()
      response <- Ok(ApiResponse.success(registrations, "获取学生注册申请成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("获取学生注册申请失败", error)
    InternalServerError(ApiResponse.error(s"获取失败: ${error.getMessage}").asJson)
  }

  private def handleApproveStudentRegistration(req: Request[IO]): IO[Response[IO]] = {
    for {
      approvalReq <- req.as[StudentRegistrationApprovalRequest]
      _ <- userManagementService.approveStudentRegistration(approvalReq)
      response <- Ok(ApiResponse.success((), "学生注册申请处理成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("学生注册申请处理失败", error)
    BadRequest(ApiResponse.error(s"处理失败: ${error.getMessage}").asJson)
  }

  private def handleReviewStudentRegistration(req: Request[IO], requestId: String): IO[Response[IO]] = {
    for {
      reviewReq <- req.as[ReviewStudentRegistrationRequest]
      _ <- userManagementService.reviewStudentRegistration(requestId, ReviewRegistrationRequest(reviewReq.action, reviewReq.note))
      response <- Ok(ApiResponse.success((), "学生注册申请审核成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("学生注册申请审核失败", error)
    BadRequest(ApiResponse.error(s"审核失败: ${error.getMessage}").asJson)
  }

  // ===================== 个人资料管理处理方法 =====================
  
  private def handleGetAdminProfile(username: String): IO[Response[IO]] = {
    for {
      profileOpt <- userManagementService.getAdminProfile(username)
      response <- profileOpt match {
        case Some(profile) => Ok(ApiResponse.success(profile, "获取管理员资料成功").asJson)
        case None => NotFound(ApiResponse.error("管理员不存在").asJson)
      }
    } yield response
  }.handleErrorWith { error =>
    logger.error("获取管理员资料失败", error)
    InternalServerError(ApiResponse.error(s"获取失败: ${error.getMessage}").asJson)
  }

  private def handleUpdateAdminProfile(req: Request[IO], username: String): IO[Response[IO]] = {
    for {
      updateReq <- req.as[UpdateAdminProfileRequest]
      _ <- userManagementService.updateAdminProfile(username, updateReq)
      response <- Ok(ApiResponse.success((), "管理员资料更新成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("管理员资料更新失败", error)
    BadRequest(ApiResponse.error(s"更新失败: ${error.getMessage}").asJson)
  }

  private def handleChangeAdminPassword(req: Request[IO], username: String): IO[Response[IO]] = {
    for {
      changeReq <- req.as[ChangeAdminPasswordRequest]
      _ <- userManagementService.changeAdminPassword(username, changeReq.currentPassword, changeReq.newPassword)
      response <- Ok(ApiResponse.success((), "管理员密码修改成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("管理员密码修改失败", error)
    BadRequest(ApiResponse.error(s"修改失败: ${error.getMessage}").asJson)
  }

  private def handleGetStudentProfile(username: String): IO[Response[IO]] = {
    for {
      profileOpt <- userManagementService.getUserProfile(username)
      response <- profileOpt match {
        case Some(profile) => Ok(ApiResponse.success(profile, "获取学生资料成功").asJson)
        case None => NotFound(ApiResponse.error("学生不存在").asJson)
      }
    } yield response
  }.handleErrorWith { error =>
    logger.error("获取学生资料失败", error)
    InternalServerError(ApiResponse.error(s"获取失败: ${error.getMessage}").asJson)
  }

  private def handleUpdateStudentProfile(req: Request[IO], username: String): IO[Response[IO]] = {
    for {
      updateReq <- req.as[UpdateProfileRequest]
      _ <- userManagementService.updateUserProfile(username, updateReq)
      response <- Ok(ApiResponse.success((), "学生资料更新成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("学生资料更新失败", error)
    BadRequest(ApiResponse.error(s"更新失败: ${error.getMessage}").asJson)
  }

  private def handleChangeStudentPassword(req: Request[IO], username: String): IO[Response[IO]] = {
    for {
      changeReq <- req.as[ChangePasswordRequest]
      _ <- userManagementService.changeUserPassword(username, changeReq)
      response <- Ok(ApiResponse.success((), "学生密码修改成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("学生密码修改失败", error)
    BadRequest(ApiResponse.error(s"修改失败: ${error.getMessage}").asJson)
  }

  private def handleStudentRegionChangeRequest(req: Request[IO], username: String): IO[Response[IO]] = {
    for {
     
      changeReq <- req.as[RegionChangeRequest]
      requestId <- userManagementService.createRegionChangeRequest(username, changeReq)
      response <- Ok(ApiResponse.success(Map("requestId" -> requestId), "区域变更申请提交成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("学生区域变更申请失败", error)
    BadRequest(ApiResponse.error(s"申请失败: ${error.getMessage}").asJson)
  }

  private def handleGetStudentRegionChangeRequests(username: String): IO[Response[IO]] = {
    for {
      requests <- userManagementService.getUserRegionChangeRequests(username)
      response <- Ok(ApiResponse.success(requests, "获取学生区域变更申请成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("获取学生区域变更申请失败", error)
    InternalServerError(ApiResponse.error(s"获取失败: ${error.getMessage}").asJson)
  }

  private def handleGetCoachProfile(username: String): IO[Response[IO]] = {
    for {
      profileOpt <- userManagementService.getUserProfile(username)
      response <- profileOpt match {
        case Some(profile) => Ok(ApiResponse.success(profile, "获取教练资料成功").asJson)
        case None => NotFound(ApiResponse.error("教练不存在").asJson)
      }
    } yield response
  }.handleErrorWith { error =>
    logger.error("获取教练资料失败", error)
    InternalServerError(ApiResponse.error(s"获取失败: ${error.getMessage}").asJson)
  }

  private def handleUpdateCoachProfile(req: Request[IO], username: String): IO[Response[IO]] = {
    for {
      updateReq <- req.as[UpdateProfileRequest]
      _ <- userManagementService.updateUserProfile(username, updateReq)
      response <- Ok(ApiResponse.success((), "教练资料更新成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("教练资料更新失败", error)
    BadRequest(ApiResponse.error(s"更新失败: ${error.getMessage}").asJson)
  }

  private def handleChangeCoachPassword(req: Request[IO], username: String): IO[Response[IO]] = {
    for {
      changeReq <- req.as[ChangePasswordRequest]
      _ <- userManagementService.changeUserPassword(username, changeReq)
      response <- Ok(ApiResponse.success((), "教练密码修改成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("教练密码修改失败", error)
    BadRequest(ApiResponse.error(s"修改失败: ${error.getMessage}").asJson)
  }

  private def handleCoachRegionChangeRequest(req: Request[IO], username: String): IO[Response[IO]] = {
    for {
      changeReq <- req.as[RegionChangeRequest]
      requestId <- userManagementService.createRegionChangeRequest(username, changeReq)
      response <- Ok(ApiResponse.success(Map("requestId" -> requestId), "区域变更申请提交成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("教练区域变更申请失败", error)
    BadRequest(ApiResponse.error(s"申请失败: ${error.getMessage}").asJson)
  }

  private def handleGetCoachRegionChangeRequests(username: String): IO[Response[IO]] = {
    for {
      requests <- userManagementService.getUserRegionChangeRequests(username)
      response <- Ok(ApiResponse.success(requests, "获取教练区域变更申请成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("获取教练区域变更申请失败", error)
    InternalServerError(ApiResponse.error(s"获取失败: ${error.getMessage}").asJson)
  }

  private def handleGetCoachManagedStudents(req: Request[IO], username: String): IO[Response[IO]] = {
    for {
      students <- coachStudentService.getStudentsForCoach(username)
      response <- Ok(ApiResponse.success(students, "获取教练管理的学生成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("获取教练管理的学生失败", error)
    InternalServerError(ApiResponse.error(s"获取失败: ${error.getMessage}").asJson)
  }

  private def handleAddCoachManagedStudent(req: Request[IO], username: String): IO[Response[IO]] = {
    for {
      // 解析前端请求
      frontendReq <- req.as[Map[String, String]]
      studentUsername = frontendReq.getOrElse("username", "")
      
      // 验证参数
      _ <- if (studentUsername.isEmpty) {
        IO.raiseError(new RuntimeException("学生用户名不能为空"))
      } else IO.unit
      
      // 获取教练ID
      _ = logger.info(s"正在查找用户名为 $username 的教练信息")
      coachInfo <- userManagementService.getUserByUsername(username)
      _ = logger.info(s"查找结果: ${coachInfo.map(u => s"userId=${u.userID}, role=${u.role}, status=${u.status}").getOrElse("未找到")}")
      _ <- coachInfo match {
        case Some(_) => IO.unit
        case None => IO.raiseError(new RuntimeException(s"未找到用户名为 $username 的教练"))
      }
      coachId = coachInfo.get.userID
      
      // 验证教练角色
      _ <- coachInfo.get.role match {
        case UserRole.Coach => IO.unit
        case _ => IO.raiseError(new RuntimeException(s"用户 $username 不是教练角色，当前角色：${coachInfo.get.role}"))
      }
      
      // 创建请求对象
      addReq = CreateCoachStudentRequest(
        coachId = coachId,
        studentUsername = studentUsername,
        studentName = studentUsername // 默认使用用户名作为姓名
      )
      
      relationshipId <- coachStudentService.createCoachStudentRelationship(addReq)
      response <- Ok(ApiResponse.success(Map("id" -> relationshipId), "添加学生成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("添加教练管理的学生失败", error)
    BadRequest(ApiResponse.error(s"添加失败: ${error.getMessage}").asJson)
  }

  private def handleUpdateCoachManagedStudent(req: Request[IO], username: String, studentId: String): IO[Response[IO]] = {
    // 目前只支持状态更新，实际实现可能需要更复杂的逻辑
    for {
      updateReq <- req.as[Map[String, String]]
      // 这里可以添加更新学生状态的逻辑
      response <- Ok(ApiResponse.success((), "更新学生信息成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("更新教练管理的学生失败", error)
    BadRequest(ApiResponse.error(s"更新失败: ${error.getMessage}").asJson)
  }

  private def handleDeleteCoachManagedStudent(studentId: String, username: String): IO[Response[IO]] = {
    for {
      _ <- coachStudentService.deleteCoachManagedStudentByStudentId(studentId)
      response <- Ok(ApiResponse.success((), "删除学生成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("删除教练管理的学生失败", error)
    BadRequest(ApiResponse.error(s"删除失败: ${error.getMessage}").asJson)
  }

  private def handleGetGraderProfile(username: String): IO[Response[IO]] = {
    for {
      profileOpt <- userManagementService.getUserProfile(username)
      response <- profileOpt match {
        case Some(profile) => Ok(ApiResponse.success(profile, "获取阅卷员资料成功").asJson)
        case None => NotFound(ApiResponse.error("阅卷员不存在").asJson)
      }
    } yield response
  }.handleErrorWith { error =>
    logger.error("获取阅卷员资料失败", error)
    InternalServerError(ApiResponse.error(s"获取失败: ${error.getMessage}").asJson)
  }

  private def handleUpdateGraderProfile(req: Request[IO], username: String): IO[Response[IO]] = {
    for {
      updateReq <- req.as[UpdateProfileRequest]
      _ <- userManagementService.updateUserProfile(username, updateReq)
      response <- Ok(ApiResponse.success((), "阅卷员资料更新成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("更新阅卷员资料失败", error)
    BadRequest(ApiResponse.error(s"更新失败: ${error.getMessage}").asJson)
  }

  private def handleChangeGraderPassword(req: Request[IO], username: String): IO[Response[IO]] = {
    for {
      changeReq <- req.as[ChangeGraderPasswordRequest]
      _ <- userManagementService.changeGraderPassword(username, changeReq)
      response <- Ok(ApiResponse.success((), "阅卷员密码修改成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("修改阅卷员密码失败", error)
    BadRequest(ApiResponse.error(s"修改失败: ${error.getMessage}").asJson)
  }

  // ===================== 系统管理员管理处理方法 =====================

  private def handleGetSystemAdmins(): IO[Response[IO]] = {
    for {
      _ <- IO(logger.info("Controller: 开始获取管理员列表"))
      admins <- userManagementService.getSystemAdmins()
      _ <- IO(logger.info(s"Controller: 获取到 ${admins.length} 个管理员"))
      _ <- IO(admins.headOption.foreach(admin => logger.info(s"Controller: 第一个管理员: $admin")))
      response <- Ok(ApiResponse.success(admins, "获取管理员列表成功").asJson)
      _ <- IO(logger.info("Controller: API响应构建成功"))
    } yield response
  }.handleErrorWith { error =>
    logger.error("获取管理员列表失败", error)
    InternalServerError(ApiResponse.error(s"获取失败: ${error.getMessage}").asJson)
  }

  private def handleCreateSystemAdmin(req: Request[IO]): IO[Response[IO]] = {
    for {
      createReq <- req.as[CreateSystemAdminRequest]
      _ = logger.info(s"接收到创建管理员请求: username=${createReq.username}, role=${createReq.role}")
      _ = logger.info(s"请求详情: password长度=${createReq.password.length}, name=${createReq.name}")
      adminId <- userManagementService.createSystemAdmin(createReq)
      // 获取完整的管理员信息
      adminProfile <- userManagementService.getSystemAdminById(adminId)
      adminData = adminProfile match {
        case Some(profile) => Map(
          "id" -> profile.id,
          "username" -> profile.username,
          "role" -> profile.role,
          "status" -> profile.status,
          "createdAt" -> profile.createdAt.getOrElse(""),
          "lastLoginAt" -> profile.lastLoginAt.getOrElse("")
        )
        case None => Map("adminId" -> adminId)
      }
      response <- Ok(ApiResponse.success(adminData, "创建管理员成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("创建管理员失败", error)
    error match {
      case e: RuntimeException if e.getMessage.contains("已存在") =>
        BadRequest(ApiResponse.error(s"用户名已存在: ${e.getMessage}").asJson)
      case e: RuntimeException if e.getMessage.contains("数据库") =>
        InternalServerError(ApiResponse.error(s"数据库错误: ${e.getMessage}").asJson)
      case e: RuntimeException if e.getMessage.contains("影响行数为0") =>
        InternalServerError(ApiResponse.error("创建管理员失败: 数据库插入失败").asJson)
      case _ =>
        InternalServerError(ApiResponse.error(s"创建管理员失败: ${error.getMessage}").asJson)
    }
  }

  private def handleUpdateSystemAdmin(req: Request[IO], adminId: String): IO[Response[IO]] = {
    for {
      updateReq <- req.as[UpdateSystemAdminRequest]
      _ <- userManagementService.updateSystemAdmin(adminId, updateReq)
      response <- Ok(ApiResponse.success((), "更新管理员成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("更新管理员失败", error)
    BadRequest(ApiResponse.error(s"更新失败: ${error.getMessage}").asJson)
  }

  private def handleDeleteSystemAdmin(adminId: String): IO[Response[IO]] = {
    for {
      _ <- userManagementService.deleteSystemAdmin(adminId)
      response <- Ok(ApiResponse.success((), "删除管理员成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("删除管理员失败", error)
    BadRequest(ApiResponse.error(s"删除失败: ${error.getMessage}").asJson)
  }

  private def handleResetSystemAdminPassword(req: Request[IO], adminId: String): IO[Response[IO]] = {
    for {
      resetReq <- req.as[ResetAdminPasswordRequest]
      _ <- userManagementService.resetSystemAdminPassword(adminId, resetReq.password)
      response <- Ok(ApiResponse.success((), "重置管理员密码成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("重置管理员密码失败", error)
    BadRequest(ApiResponse.error(s"重置失败: ${error.getMessage}").asJson)
  }

  private def handleTestDatabase(): IO[Response[IO]] = {
    for {
      response <- Ok(ApiResponse.success(Map("status" -> "ok", "message" -> "数据库连接正常"), "数据库测试成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("数据库测试失败", error)
    InternalServerError(ApiResponse.error(s"数据库测试失败: ${error.getMessage}").asJson)
  }

  private def handleHealthCheck(): IO[Response[IO]] = {
    for {
      response <- Ok(ApiResponse.success(Map("status" -> "healthy", "timestamp" -> System.currentTimeMillis().toString), "健康检查成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("健康检查失败", error)
    InternalServerError(ApiResponse.error(s"健康检查失败: ${error.getMessage}").asJson)
  }
}
