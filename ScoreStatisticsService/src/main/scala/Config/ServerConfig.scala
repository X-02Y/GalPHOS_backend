package Config

import io.circe.{Decoder, Encoder}
import io.circe.generic.auto.*

case class DatabaseConfig(
  jdbcUrl: String,
  username: String,
  password: String,
  prepStmtCacheSize: Int,
  prepStmtCacheSqlLimit: Int,
  maximumPoolSize: Int,
  connectionLiveMinutes: Int
)

case class ServerConfig(
  serverIP: String,
  serverPort: Int,
  maximumServerConnection: Int,
  maximumClientConnection: Int,
  gitlabHost: String,
  gitlabNameSpace: String,
  jdbcUrl: String,
  username: String,
  password: String,
  prepStmtCacheSize: Int,
  prepStmtCacheSqlLimit: Int,
  maximumPoolSize: Int,
  connectionLiveMinutes: Int,
  regionServiceUrl: Option[String] = None,
  userManagementServiceUrl: Option[String] = None,
  examServiceUrl: Option[String] = None,
  submissionServiceUrl: Option[String] = None,
  gradingServiceUrl: Option[String] = None,
  isTest: Boolean,
  jwtSecret: Option[String] = None,
  jwtExpirationHours: Option[Int] = None,
  saltValue: Option[String] = None
) {
  // 获取JWT密钥，优先使用配置文件中的值，否则使用默认值
  def getJwtSecret: String = jwtSecret.getOrElse("GalPHOS_2025_SECRET_KEY")
  
  // 获取JWT过期时间，优先使用配置文件中的值，否则使用默认值
  def getJwtExpirationHours: Int = jwtExpirationHours.getOrElse(24)
  
  // 获取盐值，优先使用配置文件中的值，否则使用默认值
  def getSaltValue: String = saltValue.getOrElse("GalPHOS_2025_SALT")
  
  // 获取各种服务URL
  def getRegionServiceUrl: String = regionServiceUrl.getOrElse("http://localhost:3007")
  def getUserManagementServiceUrl: String = userManagementServiceUrl.getOrElse("http://localhost:3002")
  def getExamServiceUrl: String = examServiceUrl.getOrElse("http://localhost:3003")
  def getSubmissionServiceUrl: String = submissionServiceUrl.getOrElse("http://localhost:3004")
  def getGradingServiceUrl: String = gradingServiceUrl.getOrElse("http://localhost:3005")
  
  // 将数据库相关配置转换为 DatabaseConfig
  def toDatabaseConfig: DatabaseConfig = DatabaseConfig(
    jdbcUrl = jdbcUrl,
    username = username,
    password = password,
    prepStmtCacheSize = prepStmtCacheSize,
    prepStmtCacheSqlLimit = prepStmtCacheSqlLimit,
    maximumPoolSize = maximumPoolSize,
    connectionLiveMinutes = connectionLiveMinutes
  )
}
