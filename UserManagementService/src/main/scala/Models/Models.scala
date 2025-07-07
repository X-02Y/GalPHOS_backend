package Models

import io.circe.{Decoder, Encoder}
import io.circe.generic.auto.*
import java.time.LocalDateTime
import java.util.UUID

// 统一API响应格式
case class ApiResponse[T](
  success: Boolean,
  data: Option[T] = None,
  message: Option[String] = None
)

object ApiResponse {
  def success[T](data: T, message: String = "操作成功"): ApiResponse[T] =
    ApiResponse(success = true, data = Some(data), message = Some(message))

  def error(message: String): ApiResponse[String] =
    ApiResponse(success = false, message = Some(message))
}

// 分页响应
case class PaginatedResponse[T](
  items: List[T],
  total: Int,
  page: Int,
  limit: Int
)

// 用户状态枚举
// 支持前端的 pending/approved/disabled/active
// 兼容大小写
// 新增 Approved
//
enum UserStatus(val value: String):
  case Pending extends UserStatus("pending")
  case Approved extends UserStatus("approved")
  case Active extends UserStatus("active")
  case Disabled extends UserStatus("disabled")

object UserStatus {
  def fromString(status: String): UserStatus = status.toLowerCase match {
    case "pending" => Pending
    case "approved" => Approved
    case "active" => Active
    case "disabled" => Disabled
    case _ => throw new IllegalArgumentException(s"未知状态: $status")
  }
  implicit val encoder: Encoder[UserStatus] = Encoder.encodeString.contramap(_.value)
  implicit val decoder: Decoder[UserStatus] = Decoder.decodeString.emap { str =>
    try Right(UserStatus.fromString(str))
    catch case _: IllegalArgumentException => Left(s"Invalid status: $str")
  }
}

// 用户角色枚举
// 支持前端的 student/coach/grader/admin/super_admin
//
enum UserRole(val value: String):
  case Student extends UserRole("student")
  case Coach extends UserRole("coach")
  case Grader extends UserRole("grader")
  case Admin extends UserRole("admin")
  case SuperAdmin extends UserRole("super_admin")

object UserRole {
  def fromString(role: String): UserRole = role.toLowerCase match {
    case "student" | "学生角色" => Student
    case "coach" | "教练角色" => Coach
    case "grader" | "阅卷者角色" => Grader
    case "admin" => Admin
    case "super_admin" => SuperAdmin
    case _ => throw new IllegalArgumentException(s"未知角色: $role")
  }
  implicit val encoder: Encoder[UserRole] = Encoder.encodeString.contramap(_.value)
  implicit val decoder: Decoder[UserRole] = Decoder.decodeString.emap { str =>
    try Right(UserRole.fromString(str))
    catch case _: IllegalArgumentException => Left(s"Invalid role: $str")
  }
}

// 待审核用户
case class PendingUser(
  id: String,
  username: String,
  phone: Option[String],
  role: UserRole,
  province: Option[String],
  school: Option[String],
  appliedAt: String, // ISO字符串
  status: UserStatus
)
object PendingUser {
  import io.circe.syntax.*
  import io.circe.{Json, Encoder}
  implicit val pendingUserEncoder: Encoder[PendingUser] = Encoder.instance { user =>
    Json.obj(
      "id" -> user.id.asJson,
      "username" -> user.username.asJson,
      "phone" -> user.phone.asJson,
      "role" -> user.role.asJson,
      "province" -> user.province.asJson,
      "school" -> user.school.asJson,
      "appliedAt" -> user.appliedAt.asJson,
      "status" -> user.status.asJson
    )
  }
}

// 已审核用户
case class ApprovedUser(
  id: String,
  username: String,
  phone: Option[String],
  role: UserRole,
  province: Option[String],
  school: Option[String],
  status: UserStatus,
  approvedAt: Option[String],
  lastLoginAt: Option[String],
  avatarUrl: Option[String]
)
object ApprovedUser {
  import io.circe.syntax.*
  import io.circe.{Json, Encoder}
  implicit val approvedUserEncoder: Encoder[ApprovedUser] = Encoder.instance { user =>
    Json.obj(
      "id" -> user.id.asJson,
      "username" -> user.username.asJson,
      "phone" -> user.phone.asJson,
      "role" -> user.role.asJson,
      "province" -> user.province.asJson,
      "school" -> user.school.asJson,
      "status" -> user.status.asJson,
      "approvedAt" -> user.approvedAt.asJson,
      "lastLoginAt" -> user.lastLoginAt.asJson,
      "avatarUrl" -> user.avatarUrl.asJson
    )
  }
}

// 管理员用户（简化版，兼容前端 AdminUser）
case class AdminProfile(
  id: String,
  username: String,
  avatar: Option[String] = None, // base64
  avatarUrl: Option[String] = None, // url
  role: String, // "admin" | "super_admin"
  status: String, // "active" | "disabled"
  createdAt: Option[String] = None,
  lastLoginAt: Option[String] = None
)
object AdminProfile {
  import io.circe.syntax.*
  import io.circe.{Json, Encoder}
  implicit val adminProfileEncoder: Encoder[AdminProfile] = Encoder.instance { admin =>
    Json.obj(
      "id" -> admin.id.asJson,
      "username" -> admin.username.asJson,
      "avatar" -> admin.avatar.asJson,
      "avatarUrl" -> admin.avatarUrl.asJson,
      "role" -> admin.role.asJson,
      "status" -> admin.status.asJson,
      "createdAt" -> admin.createdAt.asJson,
      "lastLoginAt" -> admin.lastLoginAt.asJson
    )
  }
}

// 用户审核请求
case class UserApprovalRequest(
  userId: String,
  action: String, // approve | reject
  reason: Option[String] = None
)

// 用户状态更新请求
case class UserStatusUpdateRequest(
  userId: String,
  status: String
)

// 教练学生关系
case class CoachStudentRelationship(
  id: String,
  coachId: String,
  coachUsername: String,
  coachName: Option[String],
  studentId: String,
  studentUsername: String,
  studentName: Option[String],
  createdAt: LocalDateTime
)

// 教练学生关系创建请求
case class CreateCoachStudentRequest(
  coachId: String,
  studentUsername: String,
  studentName: String
)

// 教练学生统计
case class CoachStudentStats(
  totalCoaches: Int,
  totalManagedStudents: Int,
  averageStudentsPerCoach: Double
)

// 学生注册申请
case class StudentRegistrationRequest(
  id: String,
  username: String,
  province: String,
  school: String,
  coachUsername: Option[String],
  reason: Option[String],
  status: String,
  createdAt: LocalDateTime,
  reviewedBy: Option[String] = None,
  reviewedAt: Option[LocalDateTime] = None,
  reviewNote: Option[String] = None
)

// 创建学生注册申请
case class CreateStudentRegistrationRequest(
  username: String,
  password: String,
  province: String,
  school: String,
  coachUsername: String,
  reason: Option[String] = None
)

// 审核学生注册申请
case class ReviewStudentRegistrationRequest(
  action: String, // approve | reject
  note: Option[String] = None
)

// 查询参数
case class QueryParams(
  page: Option[Int] = None,
  limit: Option[Int] = None,
  role: Option[String] = None,
  status: Option[String] = None,
  search: Option[String] = None,
  provinceId: Option[String] = None,
  coachId: Option[String] = None
)

// 用户数据库模型（从认证服务同步）
case class User(
  userID: String,
  username: String,
  passwordHash: String,
  salt: String,
  role: UserRole,
  status: UserStatus,
  phone: Option[String] = None,
  provinceId: Option[String] = None,
  schoolId: Option[String] = None,
  avatarUrl: Option[String] = None,
  createdAt: Option[LocalDateTime] = None,
  updatedAt: Option[LocalDateTime] = None
)

// 教练管理的学生数据库模型
case class CoachManagedStudent(
  id: String,
  coachId: String,
  studentId: String,
  studentUsername: String,
  studentName: Option[String],
  createdAt: LocalDateTime
)

// 前端期望的学生数据结构（用于教练管理学生列表）
case class StudentForCoach(
  id: String,
  name: String,
  username: String,
  phone: String,
  province: String,
  school: String,
  status: String,
  createdAt: String
)

// 已审核用户DTO（用于API响应，严格适配前端）
case class ApprovedUserDto(
  id: String,
  username: String,
  phone: Option[String],
  role: String,
  province: String, // 保证为字符串
  school: String,   // 保证为字符串
  status: String,   // 只允许"approved"或"disabled"
  approvedAt: String, // 保证为字符串
  lastLoginAt: String, // 保证为字符串
  avatarUrl: Option[String]
)

// 已审核用户列表响应
case class ApprovedUsersResponse(
  users: List[ApprovedUserDto],
  total: Int,
  page: Int,
  limit: Int
)

// 个人资料相关模型
case class UserProfile(
  id: String,
  username: String,
  phone: Option[String],
  role: String,
  province: Option[String],
  school: Option[String],
  avatarUrl: Option[String],
  createdAt: Option[String],
  lastLoginAt: Option[String]
)

case class UpdateProfileRequest(
  username: Option[String] = None,    // 新用户名（需要检查唯一性）
  name: Option[String] = None,        // 显示名称
  phone: Option[String] = None,
  bio: Option[String] = None,         // 个人简介
  expertise: Option[List[String]] = None, // 专业领域
  avatar: Option[String] = None,      // 头像URL（前端传递的字段名）
  province: Option[String] = None,
  school: Option[String] = None,
  avatarUrl: Option[String] = None    // 兼容性字段
)

// 密码修改请求 - 学生和教练使用
case class ChangePasswordRequest(
  oldPassword: String,
  newPassword: String
)

// 密码修改请求 - 阅卷员使用
case class ChangeGraderPasswordRequest(
  currentPassword: String,
  newPassword: String
)

// 创建系统管理员请求
case class CreateSystemAdminRequest(
  username: String,
  password: String,
  role: String = "admin",  // "admin" 或 "super_admin"
  name: Option[String] = None,
  avatarUrl: Option[String] = None
)

// 更新系统管理员请求
case class UpdateSystemAdminRequest(
  username: Option[String] = None,
  role: Option[String] = None,
  status: Option[String] = None,
  name: Option[String] = None,
  avatarUrl: Option[String] = None
)

// ===================== 新增的数据模型 =====================

// 更新用户请求
case class UpdateUserRequest(
  phone: Option[String] = None,
  role: Option[String] = None,
  status: Option[String] = None,
  province: Option[String] = None,
  school: Option[String] = None,
  avatarUrl: Option[String] = None
)

// 审核注册申请请求
case class ReviewRegistrationRequest(
  action: String, // "approve" | "reject"
  adminComment: Option[String] = None
)

// 区域变更申请
case class RegionChangeRequest(
  province: String,
  school: String,
  reason: String
)

// 区域变更申请记录
case class RegionChangeRequestRecord(
  id: String,
  userId: String,
  username: String,
  role: String,
  currentProvince: String,
  currentSchool: String,
  requestedProvince: String,
  requestedSchool: String,
  reason: String,
  status: String, // "pending" | "approved" | "rejected"
  createdAt: LocalDateTime,
  processedAt: Option[LocalDateTime] = None,
  processedBy: Option[String] = None,
  adminComment: Option[String] = None
)

// 文件上传相关数据模型
case class FileUploadRequest(
  category: String,        // "avatar", "document", etc.
  relatedId: Option[String] = None,
  description: Option[String] = None
)

case class FileUploadResponse(
  fileId: String,
  fileName: String,
  fileUrl: String,
  fileSize: Long,
  fileType: String,
  uploadTime: String
)

case class AvatarUploadResponse(
  avatarUrl: String,
  fileName: String,
  fileSize: Long,
  uploadTime: String
)

// 通用文件操作响应
case class FileOperationResponse(
  success: Boolean,
  message: String,
  fileUrl: Option[String] = None,
  fileId: Option[String] = None
)

// 学生注册申请批量审核请求
case class StudentRegistrationApprovalRequest(
  requestIds: List[String],
  action: String, // "approve" | "reject"
  adminComment: Option[String] = None
)

// ===================== 管理员相关补充模型 =====================

// 更新管理员个人资料请求
case class UpdateAdminProfileRequest(
  username: Option[String] = None,    // 新用户名（需要检查唯一性）
  role: Option[String] = None,        // "admin" 或 "super_admin"
  status: Option[String] = None,      // "active" 或 "disabled"
  name: Option[String] = None,        // 管理员显示名称
  avatar: Option[String] = None,      // base64头像（兼容前端）
  avatarUrl: Option[String] = None    // 头像URL
)

// 管理员修改密码请求
case class ChangeAdminPasswordRequest(
  currentPassword: String, // 旧密码
  newPassword: String      // 新密码
)

// 管理员重置密码请求（通常由超级管理员操作）
case class ResetAdminPasswordRequest(
  adminId: String,         // 管理员ID
  password: String         // 新密码
)
