package com.galphos.systemconfig.config

import cats.effect.IO
import com.comcast.ip4s.{Host, Port}
import io.circe.parser.decode
import io.circe.generic.auto._

import java.nio.file.{Files, Paths}
import scala.util.Try

case class AppConfig(
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
  authServiceUrl: String,
  userManagementServiceUrl: String,
  isTest: Boolean,
  enableStrictAuth: Boolean = false  // 新增配置项，控制是否启用严格的权限验证
) {
  // 为了兼容性提供端口和主机访问器
  def port: Port = Port.fromInt(serverPort).getOrElse(Port.fromInt(3009).get)
  def host: Host = Host.fromString(serverIP).getOrElse(Host.fromString("127.0.0.1").get)
  
  // 数据库配置访问器
  def databaseUrl: String = jdbcUrl
  def databaseUser: String = username
  def databasePassword: String = password
}

object AppConfig {
  def load(): AppConfig = {
    val configPath = Paths.get("server_config.json")
    
    try {
      if (!Files.exists(configPath)) {
        throw new RuntimeException(s"配置文件不存在: ${configPath.toAbsolutePath}")
      }
      
      val configJson = new String(Files.readAllBytes(configPath))
      
      // 直接解析扁平JSON配置
      decode[AppConfig](configJson) match {
        case Right(config) => config
        case Left(error) => throw new RuntimeException(s"无法解析配置文件: ${error.getMessage}", error)
      }
    } catch {
      case e: Exception => 
        System.err.println(s"加载配置失败: ${e.getMessage}")
        throw e
    }
  }
}
