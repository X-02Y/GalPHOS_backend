package Models

import io.circe.{Decoder, Encoder}
import io.circe.generic.auto.*
import java.time.LocalDateTime

// 统一API响应格式
case class ApiResponse[T](
  success: Boolean,
  data: Option[T] = None,
  message: Option[String] = None,
  token: Option[String] = None
)

object ApiResponse {
  def success[T](data: T, message: String = "操作成功"): ApiResponse[T] =
    ApiResponse(success = true, data = Some(data), message = Some(message))

  def successWithToken[T](data: T, token: String, message: String = "操作成功"): ApiResponse[T] =
    ApiResponse(success = true, data = Some(data), message = Some(message), token = Some(token))

  def error(message: String): ApiResponse[String] =
    ApiResponse(success = false, message = Some(message))
}

// 用户登录请求
case class LoginRequest(
  role: String,
  username: String,
  password: String
)

// 用户注册请求
case class RegisterRequest(
  role: String,
  username: String,
  phone: String,
  password: String,
  confirmPassword: String,
  province: Option[String] = None,
  school: Option[String] = None
)

// 管理员登录请求
case class AdminLoginRequest(
  username: String,
  password: String
)

// 用户信息（普通用户返回role，管理员返回type）
case class UserInfo(
  username: String,
  role: Option[String] = None,
  `type`: Option[String] = None,
  province: Option[String] = None,
  school: Option[String] = None,
  avatar: Option[String] = None
)

// 省份信息
case class ProvinceInfo(
  id: String,
  name: String,
  schools: List[SchoolInfo]
)

// 学校信息
case class SchoolInfo(
  id: String,
  name: String
)

// 数据库模型

// 用户角色枚举
enum UserRole(val value: String):
  case Student extends UserRole("学生角色")
  case Coach extends UserRole("教练角色")
  case Grader extends UserRole("阅卷者角色")

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

  implicit val encoder: Encoder[UserStatus] = Encoder.encodeString.contramap(_.value)
  implicit val decoder: Decoder[UserStatus] = Decoder.decodeString.emap { str =>
    try Right(UserStatus.fromString(str))
    catch case _: IllegalArgumentException => Left(s"Invalid status: $str")
  }
}

// 用户数据库模型
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
  createdAt: Option[java.time.LocalDateTime] = None,
  updatedAt: Option[java.time.LocalDateTime] = None
)

// 管理员数据库模型
case class Admin(
  adminID: String,
  username: String,
  passwordHash: String,
  salt: String,
  role: String = "admin",  // admin 或 super_admin
  status: String = "active", // active 或 disabled
  name: Option[String] = None,  // 显示名称
  avatarUrl: Option[String] = None,  // 头像链接
  createdAt: Option[java.time.LocalDateTime] = None,
  lastLoginAt: Option[java.time.LocalDateTime] = None
)

// 省份数据库模型
case class Province(
  provinceId: String,
  name: String,
  createdAt: Option[java.time.LocalDateTime] = None
)

// 学校数据库模型
case class School(
  schoolId: String,
  provinceId: String,
  name: String,
  createdAt: Option[java.time.LocalDateTime] = None
)
