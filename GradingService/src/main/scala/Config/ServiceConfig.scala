package Config

import io.circe.*
import io.circe.parser.*
import scala.io.Source
import java.io.File

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
  maximumClientConnection: Int
)

case class ExternalServiceConfig(
  host: String,
  port: Int,
  internalApiKey: String,
  timeout: Int,
  healthCheckInterval: Int
)

case class ServiceConfig(
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
  isTest: Boolean,
  userManagementService: ExternalServiceConfig,
  examManagementService: ExternalServiceConfig,
  submissionService: ExternalServiceConfig
) {
  def toDatabaseConfig: DatabaseConfig = DatabaseConfig(
    jdbcUrl = jdbcUrl,
    username = username,
    password = password,
    prepStmtCacheSize = prepStmtCacheSize,
    prepStmtCacheSqlLimit = prepStmtCacheSqlLimit,
    maximumPoolSize = maximumPoolSize,
    connectionLiveMinutes = connectionLiveMinutes
  )
  
  def toServerConfig: ServerConfig = ServerConfig(
    serverIP = serverIP,
    serverPort = serverPort,
    maximumServerConnection = maximumServerConnection,
    maximumClientConnection = maximumClientConnection
  )
}

object ConfigLoader {
  def loadConfig(configPath: String = "server_config.json"): ServiceConfig = {
    val file = new File(configPath)
    val source = if (file.exists()) {
      Source.fromFile(file)
    } else {
      Source.fromResource(configPath)
    }
    
    try {
      val jsonStr = source.mkString
      decode[ServiceConfig](jsonStr) match {
        case Right(config) => config
        case Left(error) => throw new RuntimeException(s"配置文件解析失败: $error")
      }
    } finally {
      source.close()
    }
  }
  
  // JSON编解码器
  implicit val externalServiceConfigDecoder: Decoder[ExternalServiceConfig] = Decoder.derived[ExternalServiceConfig]
  implicit val serviceConfigDecoder: Decoder[ServiceConfig] = Decoder.derived[ServiceConfig]
}

object Constants {
  // 通用常量
  val DEFAULT_PAGE_SIZE = 20
  val MAX_PAGE_SIZE = 100
  
  // 认证服务URL
  val AUTH_SERVICE_BASE_URL = "http://localhost:3001/api/auth"
  
  // 阅卷任务状态
  val TASK_STATUS_PENDING = "PENDING"
  val TASK_STATUS_ASSIGNED = "ASSIGNED"
  val TASK_STATUS_IN_PROGRESS = "IN_PROGRESS"
  val TASK_STATUS_COMPLETED = "COMPLETED"
  val TASK_STATUS_ABANDONED = "ABANDONED"
  
  // 用户角色
  val USER_ROLE_STUDENT = "student"
  val USER_ROLE_COACH = "coach"
  val USER_ROLE_GRADER = "grader"
  val USER_ROLE_ADMIN = "admin"
  
  // 阅卷状态
  val GRADING_STATUS_NOT_STARTED = "NOT_STARTED"
  val GRADING_STATUS_IN_PROGRESS = "IN_PROGRESS"
  val GRADING_STATUS_COMPLETED = "COMPLETED"
  
  // 题目类型
  val QUESTION_TYPE_MULTIPLE_CHOICE = "MULTIPLE_CHOICE"
  val QUESTION_TYPE_SUBJECTIVE = "SUBJECTIVE"
}
