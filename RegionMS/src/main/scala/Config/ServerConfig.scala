package Config

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

case class ServerConfig(
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
  authServiceUrl: String
)

object ServerConfig {
  given Decoder[ServerConfig] = deriveDecoder[ServerConfig]
  given Encoder[ServerConfig] = deriveEncoder[ServerConfig]
}
