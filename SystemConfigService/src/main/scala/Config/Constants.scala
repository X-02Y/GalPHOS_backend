package Config

object Constants {
  // 密码哈希盐值
  val SALT_VALUE = "SYSTEM_CONFIG_SALT"
  val PASSWORD_SALT = "SYSTEM_CONFIG_SALT"
  
  // JWT 密钥
  val JWT_SECRET = "SYSTEM_CONFIG_SECRET_KEY"
  
  // JWT 过期时间（小时）
  val JWT_EXPIRATION_HOURS = 24
  
  // 数据库模式名
  val DB_SCHEMA = "systemconfig"
  
  // 支持的管理员角色
  val SUPPORTED_ADMIN_ROLES = Set("admin", "super_admin")
  
  // 支持的管理员状态
  val SUPPORTED_ADMIN_STATUS = Set("active", "disabled")
  val ADMIN_STATUS_ACTIVE = "active"
  val ADMIN_STATUS_DISABLED = "disabled"
  
  // 系统设置键名
  val SETTING_KEY_MAINTENANCE_MODE = "maintenanceMode"
  val SETTING_KEY_SYSTEM_TITLE = "systemTitle"
  val SETTING_KEY_SYSTEM_VERSION = "systemVersion"
  val SETTING_KEY_MAX_UPLOAD_SIZE = "maxUploadSize"
  val SETTING_KEY_SESSION_TIMEOUT = "sessionTimeout"
  val SETTING_KEY_ENABLE_REGISTRATION = "enableRegistration"
  val SETTING_KEY_DEFAULT_LANGUAGE = "defaultLanguage"
  val SETTING_KEY_SYSTEM_LOGO_URL = "systemLogoUrl"
  val SETTING_KEY_ANNOUNCEMENT_ENABLED = "announcementEnabled"
  val SETTING_KEY_BUILD_TIME = "buildTime"
  
  // 支持的设置类型
  val SUPPORTED_SETTING_TYPES = Set("text", "boolean", "number", "json")
  
  // 密码最小长度
  val MIN_PASSWORD_LENGTH = 6
  
  // 用户名最小长度
  val MIN_USERNAME_LENGTH = 3
  
  // 分页默认参数
  val DEFAULT_PAGE_SIZE = 20
  val MAX_PAGE_SIZE = 100
}
