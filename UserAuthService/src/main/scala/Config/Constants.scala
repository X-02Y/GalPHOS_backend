package Config

object Constants {
  // 密码哈希盐值
  val SALT_VALUE = "GalPHOS_2025_SALT"
  
  // JWT 密钥
  val JWT_SECRET = "GalPHOS_2025_SECRET_KEY"
  
  // JWT 过期时间（小时）
  val JWT_EXPIRATION_HOURS = 24
  
  // 数据库模式名
  val DB_SCHEMA = "authservice"
  
  // 支持的用户角色
  val SUPPORTED_ROLES = Set("student", "coach", "grader")
  
  // 密码最小长度
  val MIN_PASSWORD_LENGTH = 6
  
  // 用户名最小长度
  val MIN_USERNAME_LENGTH = 3
}
