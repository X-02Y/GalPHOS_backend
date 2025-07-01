package Models

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.*
import io.circe.syntax.*
import java.time.LocalDateTime

// 统一API响应格式
case class ApiResponse[T](
  success: Boolean,
  data: Option[T] = None,
  message: Option[String] = None,
  error: Option[String] = None
)

object ApiResponse {
  def success[T](data: T, message: String = "操作成功"): ApiResponse[T] =
    ApiResponse(success = true, data = Some(data), message = Some(message))

  def successMessage(message: String): ApiResponse[String] =
    ApiResponse(success = true, message = Some(message))

  def error(message: String): ApiResponse[String] =
    ApiResponse(success = false, error = Some(message))
}

// 系统管理员相关模型
case class SystemAdmin(
  adminId: String,
  username: String,
  role: String,
  status: String,
  name: Option[String] = None,
  email: Option[String] = None,
  phone: Option[String] = None,
  avatarUrl: Option[String] = None,
  createdAt: Option[LocalDateTime] = None,
  updatedAt: Option[LocalDateTime] = None,
  lastLoginAt: Option[LocalDateTime] = None
)

case class CreateAdminRequest(
  username: String,
  password: String,
  role: String = "admin",
  name: Option[String] = None,
  email: Option[String] = None,
  phone: Option[String] = None
)

case class UpdateAdminRequest(
  name: Option[String] = None,
  email: Option[String] = None,
  phone: Option[String] = None,
  avatarUrl: Option[String] = None,
  status: Option[String] = None
)

case class ChangeAdminPasswordRequest(
  currentPassword: String,
  newPassword: String
)

// 管理员密码重置请求（管理员重置其他管理员密码）
case class ResetAdminPasswordRequest(
  password: String
)

// 系统设置相关模型
case class SystemSetting(
  settingId: String,
  settingKey: String,
  settingValue: String,
  settingType: String,
  category: String,
  description: Option[String] = None,
  isPublic: Boolean = false,
  createdAt: Option[LocalDateTime] = None,
  updatedAt: Option[LocalDateTime] = None
)

case class SystemSettings(
  announcementEnabled: Boolean = true,
  systemName: String = "GalPHOS",
  version: String = "1.0.0",
  buildTime: String = ""
) {
  // 添加 getOrElse 方法来兼容控制器中的用法
  def getOrElse[T](key: String, default: T): T = key match {
    case "announcementEnabled" => announcementEnabled.asInstanceOf[T]
    case "systemName" => systemName.asInstanceOf[T]
    case "version" => version.asInstanceOf[T]
    case "buildTime" => buildTime.asInstanceOf[T]
    case _ => default
  }
}

case class UpdateSystemSettingsRequest(
  announcementEnabled: Option[Boolean] = None,
  systemName: Option[String] = None,
  version: Option[String] = None,
  buildTime: Option[String] = None
)



// 分页相关模型
case class PaginatedResponse[T](
  data: List[T],
  total: Int,
  page: Int,
  limit: Int,
  totalPages: Int = 0
) {
  def withTotalPages: PaginatedResponse[T] = {
    val calculatedTotalPages = if (limit > 0) math.ceil(total.toDouble / limit).toInt else 0
    this.copy(totalPages = calculatedTotalPages)
  }
}

case class QueryParams(
  page: Option[Int] = None,
  limit: Option[Int] = None,
  search: Option[String] = None,
  category: Option[String] = None,
  isPublic: Option[Boolean] = None,
  isActive: Option[Boolean] = None
)

// 通用状态枚举
object AdminStatus extends Enumeration {
  type AdminStatus = Value
  val ACTIVE = Value("active")
  val DISABLED = Value("disabled")

  def fromString(status: String): AdminStatus = values.find(_.toString == status).getOrElse(ACTIVE)
}

object SettingType extends Enumeration {
  type SettingType = Value
  val TEXT = Value("text")
  val BOOLEAN = Value("boolean")
  val NUMBER = Value("number")
  val JSON = Value("json")

  def fromString(settingType: String): SettingType = values.find(_.toString == settingType).getOrElse(TEXT)
}

object AnnouncementType extends Enumeration {
  type AnnouncementType = Value
  val INFO = Value("info")
  val WARNING = Value("warning")
  val SUCCESS = Value("success")
  val ERROR = Value("error")

  def fromString(announcementType: String): AnnouncementType = values.find(_.toString == announcementType).getOrElse(INFO)
}

// 定义所有的 Circe 编码器和解码器
object Encoders {
  // LocalDateTime 编码器
  implicit val localDateTimeEncoder: Encoder[LocalDateTime] = Encoder.encodeString.contramap[LocalDateTime](_.toString)
  implicit val localDateTimeDecoder: Decoder[LocalDateTime] = Decoder.decodeString.emap { str =>
    try {
      Right(LocalDateTime.parse(str))
    } catch {
      case _: Exception => Left(s"Invalid LocalDateTime format: $str")
    }
  }

  // 系统管理员相关
  implicit val systemAdminEncoder: Encoder[SystemAdmin] = deriveEncoder[SystemAdmin]
  implicit val systemAdminDecoder: Decoder[SystemAdmin] = deriveDecoder[SystemAdmin]
  
  implicit val createAdminRequestEncoder: Encoder[CreateAdminRequest] = deriveEncoder[CreateAdminRequest]
  implicit val createAdminRequestDecoder: Decoder[CreateAdminRequest] = deriveDecoder[CreateAdminRequest]
  
  implicit val updateAdminRequestEncoder: Encoder[UpdateAdminRequest] = deriveEncoder[UpdateAdminRequest]
  implicit val updateAdminRequestDecoder: Decoder[UpdateAdminRequest] = deriveDecoder[UpdateAdminRequest]
  
  implicit val changeAdminPasswordRequestEncoder: Encoder[ChangeAdminPasswordRequest] = deriveEncoder[ChangeAdminPasswordRequest]
  implicit val changeAdminPasswordRequestDecoder: Decoder[ChangeAdminPasswordRequest] = deriveDecoder[ChangeAdminPasswordRequest]
  
  implicit val resetAdminPasswordRequestEncoder: Encoder[ResetAdminPasswordRequest] = deriveEncoder[ResetAdminPasswordRequest]
  implicit val resetAdminPasswordRequestDecoder: Decoder[ResetAdminPasswordRequest] = deriveDecoder[ResetAdminPasswordRequest]

  // 系统设置相关
  implicit val systemSettingEncoder: Encoder[SystemSetting] = deriveEncoder[SystemSetting]
  implicit val systemSettingDecoder: Decoder[SystemSetting] = deriveDecoder[SystemSetting]
  
  implicit val systemSettingsEncoder: Encoder[SystemSettings] = deriveEncoder[SystemSettings]
  implicit val systemSettingsDecoder: Decoder[SystemSettings] = deriveDecoder[SystemSettings]
  
  implicit val updateSystemSettingsRequestEncoder: Encoder[UpdateSystemSettingsRequest] = deriveEncoder[UpdateSystemSettingsRequest]
  implicit val updateSystemSettingsRequestDecoder: Decoder[UpdateSystemSettingsRequest] = deriveDecoder[UpdateSystemSettingsRequest]

  // 分页相关
  implicit val queryParamsEncoder: Encoder[QueryParams] = deriveEncoder[QueryParams]
  implicit val queryParamsDecoder: Decoder[QueryParams] = deriveDecoder[QueryParams]
  
  implicit def paginatedResponseEncoder[T: Encoder]: Encoder[PaginatedResponse[T]] = deriveEncoder[PaginatedResponse[T]]
  implicit def paginatedResponseDecoder[T: Decoder]: Decoder[PaginatedResponse[T]] = deriveDecoder[PaginatedResponse[T]]
  
  // 列表响应类型的编码器
  implicit val systemAdminListEncoder: Encoder[List[SystemAdmin]] = Encoder.encodeList[SystemAdmin]
  
  // 泛型 ApiResponse 编码器
  implicit def apiResponseGenericEncoder[T: Encoder]: Encoder[ApiResponse[T]] = deriveEncoder[ApiResponse[T]]
  implicit def apiResponseGenericDecoder[T: Decoder]: Decoder[ApiResponse[T]] = deriveDecoder[ApiResponse[T]]
}

// 自动导入所有编码器
import Encoders.given
