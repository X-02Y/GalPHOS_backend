package Process

import cats.effect.IO
import io.circe.parser.decode
import io.circe.yaml.parser as yamlParser
import Config.ServerConfig
import scala.io.Source

object ProcessUtils {
  
  def readConfig(configPath: String): IO[ServerConfig] = {
    IO.blocking {
      val source = Source.fromFile(configPath)
      try {
        val content = source.mkString
        val result = if (configPath.endsWith(".yaml") || configPath.endsWith(".yml")) {
          yamlParser.parse(content).flatMap(_.as[ServerConfig])
        } else {
          decode[ServerConfig](content)
        }
        
        result match {
          case Right(config) => config
          case Left(error) => throw new RuntimeException(s"Failed to parse config file: ${error.getMessage}")
        }
      } finally {
        source.close()
      }
    }
  }
}
