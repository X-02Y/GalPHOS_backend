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

case class FileStorageServiceConfig(
  host: String,
  port: Int,
  internalApiKey: String,
  timeout: Int,
  uploadMaxSize: Long,
  allowedImageTypes: List[String],
  allowedDocumentTypes: List[String]
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
  jwtExpiration: Int
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
  def loadConfig(): ServiceConfig = {
    val configFile = new File("server_config.json")
    if (!configFile.exists()) {
      throw new RuntimeException("Configuration file server_config.json not found")
    }

    val configContent = Source.fromFile(configFile).getLines().mkString
    decode[ServiceConfig](configContent) match {
      case Right(config) => config
      case Left(error) => throw new RuntimeException(s"Failed to parse configuration: $error")
    }
  }
}

// JSON编码器和解码器
given Encoder[FileStorageServiceConfig] = Encoder.forProduct7(
  "host", "port", "internalApiKey", "timeout", "uploadMaxSize", "allowedImageTypes", "allowedDocumentTypes"
)(fsc => (fsc.host, fsc.port, fsc.internalApiKey, fsc.timeout, fsc.uploadMaxSize, fsc.allowedImageTypes, fsc.allowedDocumentTypes))

given Decoder[FileStorageServiceConfig] = Decoder.forProduct7(
  "host", "port", "internalApiKey", "timeout", "uploadMaxSize", "allowedImageTypes", "allowedDocumentTypes"
)(FileStorageServiceConfig.apply)

given Encoder[ServiceConfig] = Encoder.forProduct13(
  "serverIP", "serverPort", "maximumServerConnection", "maximumClientConnection", 
  "jdbcUrl", "username", "password", "prepStmtCacheSize", "prepStmtCacheSqlLimit", 
  "maximumPoolSize", "connectionLiveMinutes", "isTest", "fileStorageService"
)(sc => (sc.serverIP, sc.serverPort, sc.maximumServerConnection, sc.maximumClientConnection,
  sc.jdbcUrl, sc.username, sc.password, sc.prepStmtCacheSize, sc.prepStmtCacheSqlLimit,
  sc.maximumPoolSize, sc.connectionLiveMinutes, sc.isTest, sc.fileStorageService))

given Decoder[ServiceConfig] = Decoder.instance { cursor =>
  for {
    serverIP <- cursor.downField("serverIP").as[String]
    serverPort <- cursor.downField("serverPort").as[Int]
    maximumServerConnection <- cursor.downField("maximumServerConnection").as[Int]
    maximumClientConnection <- cursor.downField("maximumClientConnection").as[Int]
    jdbcUrl <- cursor.downField("jdbcUrl").as[String]
    username <- cursor.downField("username").as[String]
    password <- cursor.downField("password").as[String]
    prepStmtCacheSize <- cursor.downField("prepStmtCacheSize").as[Int]
    prepStmtCacheSqlLimit <- cursor.downField("prepStmtCacheSqlLimit").as[Int]
    maximumPoolSize <- cursor.downField("maximumPoolSize").as[Int]
    connectionLiveMinutes <- cursor.downField("connectionLiveMinutes").as[Int]
    isTest <- cursor.downField("isTest").as[Boolean]
    fileStorageService <- cursor.downField("fileStorageService").as[FileStorageServiceConfig]
    jwtSecret <- cursor.downField("jwtSecret").as[String].orElse(Right("default-secret"))
    jwtExpiration <- cursor.downField("jwtExpiration").as[Int].orElse(Right(86400))
  } yield ServiceConfig(
    serverIP, serverPort, maximumServerConnection, maximumClientConnection,
    jdbcUrl, username, password, prepStmtCacheSize, prepStmtCacheSqlLimit,
    maximumPoolSize, connectionLiveMinutes, isTest, fileStorageService, jwtSecret, jwtExpiration
  )
}
