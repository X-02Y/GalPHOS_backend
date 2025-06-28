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
enum UserStatus(val value: String):
  case Pending extends UserStatus("PENDING")
  case Active extends UserStatus("ACTIVE")
  case Disabled extends UserStatus("DISABLED")

object UserStatus {
  def fromString(status: String): UserStatus = status.toUpperCase match {
    case "PENDING" => Pending
    case "ACTIVE" => Active
    case "DISABLED" => Disabled
    case _ => throw new IllegalArgumentException(s"未知状态: $status")
  }

  implicit val encoder: Encoder[UserStatus] = Encoder.encodeString.contramap(_.value.toLowerCase)
  implicit val decoder: Decoder[UserStatus] = Decoder.decodeString.emap { str =>
    try Right(UserStatus.fromString(str))
    catch case _: IllegalArgumentException => Left(s"Invalid status: $str")
  }
}

// 用户角色枚举
enum UserRole(val value: String):
  case Student extends UserRole("student")
  case Coach extends UserRole("coach")
  case Grader extends UserRole("grader")

object UserRole {
  def fromString(role: String): UserRole = role match {
    // 支持英文角色名
    case "student" => Student
    case "coach" => Coach
    case "grader" => Grader
    // 支持中文角色名（数据库中存储的格式）
    case "学生角色" => Student
    case "教练角色" => Coach
    case "阅卷者角色" => Grader
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
  appliedAt: LocalDateTime,
  status: UserStatus
)

// 已审核用户
case class ApprovedUser(
  id: String,
  username: String,
  phone: Option[String],
  role: UserRole,
  province: Option[String],
  school: Option[String],
  status: UserStatus,
  approvedAt: Option[LocalDateTime],
  lastLoginAt: Option[LocalDateTime],
  avatarUrl: Option[String]
)

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

// 已审核用户列表响应
case class ApprovedUsersResponse(
  users: List[ApprovedUserDto],
  total: Int,
  page: Int,
  limit: Int
)

// 已审核用户DTO（用于API响应）
case class ApprovedUserDto(
  id: String,
  username: String,
  phone: Option[String],
  role: String,
  province: Option[String],
  school: Option[String],
  status: String,
  approvedAt: Option[String],
  lastLoginAt: Option[String],
  avatarUrl: Option[String]
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
  phone: Option[String] = None,
  province: Option[String] = None,
  school: Option[String] = None,
  avatarUrl: Option[String] = None
)

case class ChangePasswordRequest(
  currentPassword: String,
  newPassword: String,
  confirmPassword: String
)

case class AdminProfile(
  id: String,
  username: String,
  createdAt: Option[String],
  lastLoginAt: Option[String]
)

case class UpdateAdminProfileRequest(
  // 管理员可能只需要更新一些基本信息
  avatarUrl: Option[String] = None
)
