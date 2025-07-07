package Config

import io.circe.*
import io.circe.generic.semiauto.*
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

case class FileStorageServiceConfig(
  host: String,
  port: Int,
  internalApiKey: String,
  timeout: Int,
  uploadMaxSize: Long,
  allowedImageTypes: List[String],
  allowedDocumentTypes: List[String]
)

case class ExternalServiceConfig(
  host: String,
  port: Int
)

case class ExternalServicesConfig(
  examService: ExternalServiceConfig,
  userManagementService: ExternalServiceConfig,
  authService: ExternalServiceConfig
)

case class ServiceConfig(
  serverIP: String,
  serverPort: Int,
  maximumServerConnection: Int,
  maximumClientConnection: Int,
  jdbcUrl: String,
  username: String,
  password: String,
  prepStmtCacheSize: Int,
  prepStmtCacheSqlLimit: Int,
  maximumPoolSize: Int,
  connectionLiveMinutes: Int,
  isTest: Boolean,
  fileStorageService: FileStorageServiceConfig,
  jwtSecret: String,
  jwtExpiration: Int,
  externalServices: ExternalServicesConfig
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
}

object ConfigLoader {
  given Decoder[FileStorageServiceConfig] = deriveDecoder[FileStorageServiceConfig]
  given Decoder[ExternalServiceConfig] = deriveDecoder[ExternalServiceConfig]
  given Decoder[ExternalServicesConfig] = deriveDecoder[ExternalServicesConfig]
  given Decoder[ServiceConfig] = deriveDecoder[ServiceConfig]

  def loadConfig(configPath: String = "server_config.json"): ServiceConfig = {
    val source = Source.fromFile(configPath)
    try {
      val content = source.mkString
      decode[ServiceConfig](content) match {
        case Right(config) => config
        case Left(error) => throw new Exception(s"Failed to parse config: $error")
      }
    } finally {
      source.close()
    }
  }
}
