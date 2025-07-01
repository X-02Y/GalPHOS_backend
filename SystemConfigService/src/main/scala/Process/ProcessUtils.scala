package Process

import cats.effect.IO
import io.circe.*
import io.circe.parser.*
import io.circe.generic.auto.*
import java.nio.file.{Files, Paths}
import Config.ServerConfig

object ProcessUtils {
  
  def readConfig(configFile: String): IO[ServerConfig] = {
    for {
      configPath <- IO.pure(Paths.get(configFile))
      exists <- IO(Files.exists(configPath))
      _ <- if (!exists) IO.raiseError(new RuntimeException(s"配置文件不存在: $configFile")) else IO.unit
      content <- IO(Files.readString(configPath))
      config <- parseConfig(content)
    } yield config
  }

  private def parseConfig(content: String): IO[ServerConfig] = {
    io.circe.parser.parse(content) match {
      case Right(json) =>
        json.as[ServerConfig] match {
          case Right(serverConfig) => IO.pure(serverConfig)
          case Left(decodingError) => 
            IO.raiseError(new RuntimeException(s"解析配置文件失败: ${decodingError.getMessage}"))
        }
      case Left(parsingError) =>
        IO.raiseError(new RuntimeException(s"配置文件格式错误: ${parsingError.getMessage}"))
    }
  }
}
