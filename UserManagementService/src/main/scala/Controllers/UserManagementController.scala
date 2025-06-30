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
import org.http4s.multipart._
import org.slf4j.LoggerFactory
import Models.*
import Services.*
import fs2.Stream

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

    // 管理员头像上传
    case req @ POST -> Root / "admin" / "system" / "upload" / "avatar" =>
      authenticateAdmin(req) { authResult =>
        handleUploadAvatar(req, authResult.username.getOrElse(""), "admin")
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

    // 获取学生区域变更申请状态
    case req @ GET -> Root / "api" / "student" / "region-change" / "status" =>
      authenticateUser(req, "student") { authResult =>
        handleGetStudentRegionChangeRequests(authResult.username.getOrElse(""))
      }.map(_.withHeaders(corsHeaders))

    case req @ GET -> Root / "api" / "student" / "region-change" =>
      authenticateUser(req, "student") { authResult =>
        handleGetStudentRegionChangeRequests(authResult.username.getOrElse(""))
      }.map(_.withHeaders(corsHeaders))

    // 学生头像上传
    case req @ POST -> Root / "api" / "upload" / "avatar" =>
      authenticateUser(req, "student") { authResult =>
        handleUploadAvatar(req, authResult.username.getOrElse(""), "student")
      }.map(_.withHeaders(corsHeaders))

    // 学生答题图片上传
    case req @ POST -> Root / "api" / "student" / "upload" / "answer-image" =>
      authenticateUser(req, "student") { authResult =>
        handleUploadAnswerImage(req, authResult.username.getOrElse(""))
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

    // 教练密码修改
    case req @ POST -> Root / "api" / "coach" / "profile" / "change-password" =>
      authenticateUser(req, "coach") { authResult =>
        handleChangeCoachPassword(req, authResult.username.getOrElse(""))
      }.map(_.withHeaders(corsHeaders))

    // 教练区域变更申请  
    case req @ POST -> Root / "api" / "coach" / "profile" / "change-region" =>
      authenticateUser(req, "coach") { authResult =>
        handleCoachRegionChangeRequest(req, authResult.username.getOrElse(""))
      }.map(_.withHeaders(corsHeaders))

    // 获取教练区域变更申请记录
    case req @ GET -> Root / "api" / "coach" / "profile" / "change-region-requests" =>
      authenticateUser(req, "coach") { authResult =>
        handleGetCoachRegionChangeRequests(authResult.username.getOrElse(""))
      }.map(_.withHeaders(corsHeaders))

    // 教练头像上传
    case req @ POST -> Root / "api" / "coach" / "profile" / "upload-avatar" =>
      authenticateUser(req, "coach") { authResult =>
        handleUploadAvatar(req, authResult.username.getOrElse(""), "coach")
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

    // ===================== 阅卷员路由 =====================
    
    // 阅卷员个人资料
    case req @ GET -> Root / "api" / "grader" / "profile" =>
      authenticateUser(req, "grader") { authResult =>
        handleGetGraderProfile(authResult.username.getOrElse(""))
      }

    case req @ PUT -> Root / "api" / "grader" / "profile" =>
      authenticateUser(req, "grader") { authResult =>
        handleUpdateGraderProfile(req, authResult.username.getOrElse(""))
      }

    // 阅卷员密码修改
    case req @ PUT -> Root / "api" / "grader" / "change-password" =>
      authenticateUser(req, "grader") { authResult =>
        handleChangeGraderPassword(req, authResult.username.getOrElse(""))
      }

    // 阅卷员头像上传
    case req @ POST -> Root / "api" / "grader" / "upload-avatar" =>
      authenticateUser(req, "grader") { authResult =>
        handleUploadAvatar(req, authResult.username.getOrElse(""), "grader")
      }.map(_.withHeaders(corsHeaders))

    // 健康检查
    case GET -> Root / "health" =>
      Ok("OK").map(_.withHeaders(corsHeaders))
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
      // 转换为API响应格式
      userDtos = result.items.map { user =>
        val apiStatus = user.status.value.toLowerCase match {
          case "active" => "approved"
          case "disabled" => "rejected"
          case "pending" => "pending"
          case other => other
        }
        ApprovedUserDto(
          id = user.id,
          username = user.username,
          phone = user.phone,
          role = user.role.value,
          province = user.province,
          school = user.school,
          status = apiStatus,
          approvedAt = user.approvedAt.map(_.toString),
          lastLoginAt = user.lastLoginAt.map(_.toString),
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

  // 删除用户
  private def handleDeleteUser(userId: String): IO[Response[IO]] = {
    for {
      _ <- userManagementService.deleteUser(userId)
      response <- Ok(ApiResponse.success((), "用户删除成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error(s"删除用户失败: userId=$userId", error)
    BadRequest(ApiResponse.error(s"删除失败: ${error.getMessage}").asJson)
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

  // ===================== 辅助方法 =====================
  
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

  // ===================== 新增API处理方法 =====================

  // 学生注册审核相关
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
    // TODO: 实现批量审核学生注册申请
    Ok(ApiResponse.success((), "批量审核学生注册申请成功").asJson)
  }

  // 个人资料管理相关
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
    logger.error("更新管理员资料失败", error)
    BadRequest(ApiResponse.error(s"更新失败: ${error.getMessage}").asJson)
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
    logger.error("更新学生资料失败", error)
    BadRequest(ApiResponse.error(s"更新失败: ${error.getMessage}").asJson)
  }

  private def handleChangeStudentPassword(req: Request[IO], username: String): IO[Response[IO]] = {
    for {
      changeReq <- req.as[ChangePasswordRequest]
      _ <- userManagementService.changeUserPassword(username, changeReq)
      response <- Ok(ApiResponse.success((), "学生密码修改成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("修改学生密码失败", error)
    BadRequest(ApiResponse.error(s"修改失败: ${error.getMessage}").asJson)
  }

  // 学生区域变更申请相关方法
  private def handleStudentRegionChangeRequest(req: Request[IO], username: String): IO[Response[IO]] = {
    for {
      regionReq <- req.as[RegionChangeRequest]
      requestId <- userManagementService.createRegionChangeRequest(username, regionReq)
      response <- Ok(ApiResponse.success(Map("id" -> requestId), "学生区域变更申请提交成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("提交学生区域变更申请失败", error)
    BadRequest(ApiResponse.error(s"提交失败: ${error.getMessage}").asJson)
  }

  private def handleGetStudentRegionChangeRequests(username: String): IO[Response[IO]] = {
    for {
      requests <- userManagementService.getUserRegionChangeRequests(username)
      response <- Ok(ApiResponse.success(requests, "获取学生区域变更申请记录成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("获取学生区域变更申请记录失败", error)
    InternalServerError(ApiResponse.error(s"获取失败: ${error.getMessage}").asJson)
  }

  // 教练个人资料相关方法
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
    logger.error("更新教练资料失败", error)
    BadRequest(ApiResponse.error(s"更新失败: ${error.getMessage}").asJson)
  }

  private def handleChangeCoachPassword(req: Request[IO], username: String): IO[Response[IO]] = {
    for {
      changeReq <- req.as[ChangePasswordRequest]
      _ <- userManagementService.changeUserPassword(username, changeReq)
      response <- Ok(ApiResponse.success((), "教练密码修改成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("修改教练密码失败", error)
    BadRequest(ApiResponse.error(s"修改失败: ${error.getMessage}").asJson)
  }

  // 教练管理的学生相关方法
  private def handleGetCoachManagedStudents(req: Request[IO], username: String): IO[Response[IO]] = {
    for {
      students <- coachStudentService.getStudentsByCoach(username)
      response <- Ok(ApiResponse.success(students, "获取教练管理的学生成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("获取教练管理的学生失败", error)
    InternalServerError(ApiResponse.error(s"获取失败: ${error.getMessage}").asJson)
  }

  private def handleAddCoachManagedStudent(req: Request[IO], username: String): IO[Response[IO]] = {
    for {
      addReq <- req.as[CreateCoachStudentRequest]
      relationshipId <- coachStudentService.createCoachStudentRelationship(addReq)
      response <- Ok(ApiResponse.success(Map("id" -> relationshipId), "添加学生成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("添加教练管理的学生失败", error)
    BadRequest(ApiResponse.error(s"添加失败: ${error.getMessage}").asJson)
  }

  // 阅卷员个人资料相关方法
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

  // ===================== 新增缺失的处理方法 =====================

  // 获取单个用户信息
  private def handleGetUserById(userId: String): IO[Response[IO]] = {
    for {
      userOpt <- userManagementService.getUserById(userId)
      response <- userOpt match {
        case Some(user) => Ok(ApiResponse.success(user, "获取用户信息成功").asJson)
        case None => NotFound(ApiResponse.error("用户不存在").asJson)
      }
    } yield response
  }.handleErrorWith { error =>
    logger.error(s"获取用户信息失败: userId=$userId", error)
    InternalServerError(ApiResponse.error(s"获取失败: ${error.getMessage}").asJson)
  }

  // 更新单个用户信息
  private def handleUpdateUserById(req: Request[IO], userId: String): IO[Response[IO]] = {
    for {
      updateReq <- req.as[UpdateUserRequest]
      _ <- userManagementService.updateUserById(userId, updateReq)
      response <- Ok(ApiResponse.success((), "用户信息更新成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error(s"更新用户信息失败: userId=$userId", error)
    BadRequest(ApiResponse.error(s"更新失败: ${error.getMessage}").asJson)
  }

  // 审核学生注册申请 - review 接口
  private def handleReviewStudentRegistration(req: Request[IO], requestId: String): IO[Response[IO]] = {
    for {
      reviewReq <- req.as[ReviewRegistrationRequest]
      _ <- userManagementService.reviewStudentRegistration(requestId, reviewReq)
      response <- Ok(ApiResponse.success((), "审核学生注册申请成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error(s"审核学生注册申请失败: requestId=$requestId", error)
    BadRequest(ApiResponse.error(s"审核失败: ${error.getMessage}").asJson)
  }

  // 教练区域变更申请
  private def handleCoachRegionChangeRequest(req: Request[IO], username: String): IO[Response[IO]] = {
    for {
      regionReq <- req.as[RegionChangeRequest]
      requestId <- userManagementService.createRegionChangeRequest(username, regionReq)
      response <- Ok(ApiResponse.success(Map("id" -> requestId), "教练区域变更申请提交成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("提交教练区域变更申请失败", error)
    BadRequest(ApiResponse.error(s"提交失败: ${error.getMessage}").asJson)
  }

  // 获取教练区域变更申请记录
  private def handleGetCoachRegionChangeRequests(username: String): IO[Response[IO]] = {
    for {
      requests <- userManagementService.getUserRegionChangeRequests(username)
      response <- Ok(ApiResponse.success(requests, "获取教练区域变更申请记录成功").asJson)
    } yield response
  }.handleErrorWith { error =>
    logger.error("获取教练区域变更申请记录失败", error)
    InternalServerError(ApiResponse.error(s"获取失败: ${error.getMessage}").asJson)
  }

  // 文件上传处理方法
  private def handleUploadAvatar(req: Request[IO], username: String, userType: String): IO[Response[IO]] = {
    EntityDecoder.mixedMultipartResource[IO]().use { decoder =>
      req.decodeWith(decoder, strict = true) { multipart =>
        extractFileFromMultipart(multipart, "file", "avatar").flatMap {
          case Some((fileName, fileData, mimeType)) =>
            for {
              result <- userManagementService.uploadAvatar(username, userType, fileName, fileData, mimeType)
              response <- if (result.success) {
                Ok(ApiResponse.success(result.message, "头像上传成功").asJson)
              } else {
                BadRequest(ApiResponse.error(result.message).asJson)
              }
            } yield response
          case None =>
            BadRequest(ApiResponse.error("未找到上传文件").asJson)
        }
      }
    }.handleErrorWith { error =>
      logger.error(s"头像上传失败: username=$username, userType=$userType", error)
      InternalServerError(ApiResponse.error(s"上传失败: ${error.getMessage}").asJson)
    }
  }

  private def handleUploadAnswerImage(req: Request[IO], username: String): IO[Response[IO]] = {
    EntityDecoder.mixedMultipartResource[IO]().use { decoder =>
      req.decodeWith(decoder, strict = true) { multipart =>
        for {
          fileInfo <- extractFileFromMultipart(multipart, "file", "answer-image")
          examId <- extractStringFromMultipart(multipart, "examId")
          questionNumberStr <- extractStringFromMultipart(multipart, "questionNumber")
          questionNumber = questionNumberStr.flatMap(_.toIntOption)
          response <- fileInfo match {
            case Some((fileName, fileData, mimeType)) =>
              Ok(ApiResponse.success("答题图片上传成功").asJson)
            case None =>
              BadRequest(ApiResponse.error("未找到上传文件").asJson)
          }
        } yield response
      }
    }.handleErrorWith { error =>
      logger.error(s"答题图片上传失败: username=$username", error)
      InternalServerError(ApiResponse.error(s"上传失败: ${error.getMessage}").asJson)
    }
  }

  // 从 multipart 数据中提取文件
  private def extractFileFromMultipart(multipart: Multipart[IO], fieldName: String, category: String): IO[Option[(String, Array[Byte], String)]] = {
    multipart.parts.find(_.name.contains(fieldName)) match {
      case Some(part) =>
        for {
          fileData <- part.body.compile.to(Array)
          fileName = part.filename.getOrElse(s"upload_${System.currentTimeMillis()}")
          mimeType = part.headers.get[`Content-Type`].map(_.mediaType.toString).getOrElse("application/octet-stream")
        } yield Some((fileName, fileData, mimeType))
      case None =>
        IO.pure(None)
    }
  }

  // 从 multipart 数据中提取字符串参数
  private def extractStringFromMultipart(multipart: Multipart[IO], fieldName: String): IO[Option[String]] = {
    multipart.parts.find(_.name.contains(fieldName)) match {
      case Some(part) =>
        part.body.through(fs2.text.utf8.decode).compile.string.map(Some(_))
      case None =>
        IO.pure(None)
    }
  }

  // 获取文件扩展名
  private def getFileExtension(fileName: String): String = {
    val lastDotIndex = fileName.lastIndexOf('.')
    if (lastDotIndex > 0 && lastDotIndex < fileName.length - 1) {
      fileName.substring(lastDotIndex + 1).toLowerCase
    } else {
      "unknown"
    }
  }
}
