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

// 系统版本模型
case class SystemVersion(
  id: Option[Long],
  version: String,
  buildNumber: String,
  releaseDate: ZonedDateTime,
  releaseNotes: Option[String],
  isCurrent: Boolean
)

// 错误响应模型
case class ErrorResponse(
  error: String
)

// 成功响应模型
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
    // 创建AdminResponse，排除passwordHash字段
    val response = AdminResponse(
      adminId = a.adminId,
      username = a.username,
      role = a.role,
      isSuperAdmin = a.isSuperAdmin,
      createdAt = a.createdAt,
      updatedAt = a.updatedAt,
      lastLogin = a.lastLogin
    )
    response.asJson
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
  
  implicit val systemVersionEncoder: Encoder[SystemVersion] = deriveEncoder[SystemVersion]
  implicit val systemVersionDecoder: Decoder[SystemVersion] = deriveDecoder[SystemVersion]
  
  implicit val errorResponseEncoder: Encoder[ErrorResponse] = deriveEncoder[ErrorResponse]
  implicit val errorResponseDecoder: Decoder[ErrorResponse] = deriveDecoder[ErrorResponse]
  
  implicit val successResponseEncoder: Encoder[SuccessResponse] = deriveEncoder[SuccessResponse]
  implicit val successResponseDecoder: Decoder[SuccessResponse] = deriveDecoder[SuccessResponse]
}
