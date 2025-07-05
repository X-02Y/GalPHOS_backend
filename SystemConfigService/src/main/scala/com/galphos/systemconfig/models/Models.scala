package com.galphos.systemconfig.models

import io.circe.{Encoder, Decoder}
import io.circe.generic.semiauto._
import io.circe.syntax._
import java.time.ZonedDateTime

// 系统配置模型
case class SystemConfig(
  id: Option[Long],
  configKey: String,
  configValue: String,
  description: Option[String],
  isPublic: Boolean,
  createdAt: Option[ZonedDateTime],
  updatedAt: Option[ZonedDateTime]
)

// 管理员模型
case class Admin(
  adminId: Option[Long],
  username: String,
  passwordHash: Option[String], // 仅在内部使用，不会输出到JSON
  role: String, // 角色字段
  isSuperAdmin: Boolean,
  createdAt: Option[ZonedDateTime],
  updatedAt: Option[ZonedDateTime],
  lastLogin: Option[ZonedDateTime]
)

// 管理员响应模型（不包含密码哈希）
case class AdminResponse(
  adminId: Option[Long],
  username: String,
  role: String,
  isSuperAdmin: Boolean,
  createdAt: Option[ZonedDateTime],
  updatedAt: Option[ZonedDateTime],
  lastLogin: Option[ZonedDateTime]
)

// 创建管理员请求
case class CreateAdminRequest(
  username: String,
  password: String,
  role: Option[String] = Some("admin") // 与前端保持一致，默认为admin
)

// 更新管理员请求
case class UpdateAdminRequest(
  role: Option[String],
  isSuperAdmin: Option[Boolean]
)

// 密码重置请求
case class ResetPasswordRequest(
  password: String
)

// 用户模型
case class User(
  userId: Option[String],
  username: String,
  phone: Option[String],
  role: String, // student, coach, grader
  status: String, // pending, active, disabled
  province: Option[String],
  school: Option[String],
  avatarUrl: Option[String],
  createdAt: Option[ZonedDateTime],
  updatedAt: Option[ZonedDateTime],
  lastLogin: Option[ZonedDateTime]
)

// 用户响应模型
case class UserResponse(
  userId: Option[String],
  username: String,
  phone: Option[String],
  role: String,
  status: String,
  province: Option[String],
  school: Option[String],
  avatarUrl: Option[String],
  createdAt: Option[ZonedDateTime],
  updatedAt: Option[ZonedDateTime],
  lastLogin: Option[ZonedDateTime]
)

// 创建用户请求
case class CreateUserRequest(
  username: String,
  phone: Option[String],
  role: String,
  province: Option[String] = None,
  school: Option[String] = None
)

// 更新用户请求
case class UpdateUserRequest(
  phone: Option[String] = None,
  role: Option[String] = None,
  status: Option[String] = None,
  province: Option[String] = None,
  school: Option[String] = None,
  avatarUrl: Option[String] = None
)

// 用户审核请求
case class UserApprovalRequest(
  userId: String,
  action: String, // approve, reject
  reason: Option[String] = None
)

// 所有用户响应模型
case class AllUsersResponse(
  users: List[User],
  admins: List[Admin],
  totalUsers: Int,
  totalAdmins: Int,
  total: Int
)

// 统计响应模型
case class StatisticsResponse(
  totalUsers: Int,
  totalAdmins: Int,
  totalAll: Int,
  pendingUsers: Int,
  usersByRole: UsersByRoleResponse,
  lastUpdated: String
)

case class UsersByRoleResponse(
  student: Int,
  coach: Int,
  grader: Int,
  admin: Int,
  super_admin: Int
)

// 服务信息响应模型
case class ServiceInfoResponse(
  service: String,
  version: String,
  description: String,
  endpoints: List[String]
)

// 系统版本模型
case class SystemVersion(
  id: Option[Long],
  version: String,
  buildNumber: String,
  releaseDate: ZonedDateTime,
  releaseNotes: Option[String],
  isCurrent: Boolean
)

// 标准API响应格式
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

// 错误响应模型（向后兼容）
case class ErrorResponse(
  error: String
)

// 成功响应模型（向后兼容）
case class SuccessResponse(
  message: String
)

// Circe编解码器
object Models {
  // 自定义ZonedDateTime编解码
  implicit val zonedDateTimeEncoder: Encoder[ZonedDateTime] = Encoder.encodeString.contramap[ZonedDateTime](_.toString)
  implicit val zonedDateTimeDecoder: Decoder[ZonedDateTime] = Decoder.decodeString.map(ZonedDateTime.parse)

  // 基本模型的编解码器
  implicit val systemConfigEncoder: Encoder[SystemConfig] = deriveEncoder[SystemConfig]
  implicit val systemConfigDecoder: Decoder[SystemConfig] = deriveDecoder[SystemConfig]
  
  implicit val adminEncoder: Encoder[Admin] = (a: Admin) => {
    // 创建与前端AdminUser接口兼容的JSON格式
    io.circe.Json.obj(
      "id" -> a.adminId.map(_.toString).getOrElse("").asJson,  // 将adminId转换为字符串id
      "username" -> a.username.asJson,
      "role" -> a.role.asJson,
      "status" -> "active".asJson,  // 添加前端期望的status字段，默认为active
      "createdAt" -> a.createdAt.map(_.toString).asJson,  // 转换为字符串
      "lastLoginAt" -> a.lastLogin.map(_.toString).asJson,  // 将lastLogin映射为lastLoginAt
      "avatar" -> Option.empty[String].asJson  // 添加avatar字段，当前为空
    )
  }
  
  implicit val adminDecoder: Decoder[Admin] = deriveDecoder[Admin]
  implicit val adminResponseEncoder: Encoder[AdminResponse] = deriveEncoder[AdminResponse]
  implicit val adminResponseDecoder: Decoder[AdminResponse] = deriveDecoder[AdminResponse]
  
  implicit val createAdminRequestEncoder: Encoder[CreateAdminRequest] = deriveEncoder[CreateAdminRequest]
  implicit val createAdminRequestDecoder: Decoder[CreateAdminRequest] = deriveDecoder[CreateAdminRequest]
  
  implicit val updateAdminRequestEncoder: Encoder[UpdateAdminRequest] = deriveEncoder[UpdateAdminRequest]
  implicit val updateAdminRequestDecoder: Decoder[UpdateAdminRequest] = deriveDecoder[UpdateAdminRequest]
  
  implicit val resetPasswordRequestEncoder: Encoder[ResetPasswordRequest] = deriveEncoder[ResetPasswordRequest]
  implicit val resetPasswordRequestDecoder: Decoder[ResetPasswordRequest] = deriveDecoder[ResetPasswordRequest]
  
  // 用户模型编解码器
  implicit val userEncoder: Encoder[User] = (u: User) => {
    // 创建UserResponse，确保数据结构一致
    val response = UserResponse(
      userId = u.userId,
      username = u.username,
      phone = u.phone,
      role = u.role,
      status = u.status,
      province = u.province,
      school = u.school,
      avatarUrl = u.avatarUrl,
      createdAt = u.createdAt,
      updatedAt = u.updatedAt,
      lastLogin = u.lastLogin
    )
    response.asJson
  }
  
  implicit val userDecoder: Decoder[User] = deriveDecoder[User]
  implicit val userResponseEncoder: Encoder[UserResponse] = deriveEncoder[UserResponse]
  implicit val userResponseDecoder: Decoder[UserResponse] = deriveDecoder[UserResponse]
  
  implicit val createUserRequestEncoder: Encoder[CreateUserRequest] = deriveEncoder[CreateUserRequest]
  implicit val createUserRequestDecoder: Decoder[CreateUserRequest] = deriveDecoder[CreateUserRequest]
  
  implicit val updateUserRequestEncoder: Encoder[UpdateUserRequest] = deriveEncoder[UpdateUserRequest]
  implicit val updateUserRequestDecoder: Decoder[UpdateUserRequest] = deriveDecoder[UpdateUserRequest]
  
  implicit val userApprovalRequestEncoder: Encoder[UserApprovalRequest] = deriveEncoder[UserApprovalRequest]
  implicit val userApprovalRequestDecoder: Decoder[UserApprovalRequest] = deriveDecoder[UserApprovalRequest]
  
  implicit val allUsersResponseEncoder: Encoder[AllUsersResponse] = deriveEncoder[AllUsersResponse]
  implicit val allUsersResponseDecoder: Decoder[AllUsersResponse] = deriveDecoder[AllUsersResponse]
  
  implicit val usersByRoleResponseEncoder: Encoder[UsersByRoleResponse] = deriveEncoder[UsersByRoleResponse]
  implicit val usersByRoleResponseDecoder: Decoder[UsersByRoleResponse] = deriveDecoder[UsersByRoleResponse]
  
  implicit val statisticsResponseEncoder: Encoder[StatisticsResponse] = deriveEncoder[StatisticsResponse]
  implicit val statisticsResponseDecoder: Decoder[StatisticsResponse] = deriveDecoder[StatisticsResponse]
  
  implicit val serviceInfoResponseEncoder: Encoder[ServiceInfoResponse] = deriveEncoder[ServiceInfoResponse]
  implicit val serviceInfoResponseDecoder: Decoder[ServiceInfoResponse] = deriveDecoder[ServiceInfoResponse]
  
  implicit val systemVersionEncoder: Encoder[SystemVersion] = deriveEncoder[SystemVersion]
  implicit val systemVersionDecoder: Decoder[SystemVersion] = deriveDecoder[SystemVersion]
  
  // ApiResponse 编解码器
  implicit def apiResponseEncoder[T](implicit encoder: Encoder[T]): Encoder[ApiResponse[T]] = deriveEncoder[ApiResponse[T]]
  implicit def apiResponseDecoder[T](implicit decoder: Decoder[T]): Decoder[ApiResponse[T]] = deriveDecoder[ApiResponse[T]]
  
  implicit val errorResponseEncoder: Encoder[ErrorResponse] = deriveEncoder[ErrorResponse]
  implicit val errorResponseDecoder: Decoder[ErrorResponse] = deriveDecoder[ErrorResponse]
  
  implicit val successResponseEncoder: Encoder[SuccessResponse] = deriveEncoder[SuccessResponse]
  implicit val successResponseDecoder: Decoder[SuccessResponse] = deriveDecoder[SuccessResponse]
}
