package Process

import cats.effect.*
import com.comcast.ip4s.*
import org.http4s.*
import org.http4s.ember.server.*
import org.http4s.implicits.*
import org.http4s.server.middleware.CORS
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.{Slf4jFactory, Slf4jLogger}
import java.nio.channels.ClosedChannelException
import scala.concurrent.duration.*

object Server extends IOApp {
  given logger: Logger[IO] = Slf4jLogger.getLogger[IO]
  given Slf4jFactory[IO] = Slf4jFactory.create[IO]

  override protected def reportFailure(err: Throwable): IO[Unit] =
    err match {
      case e: ClosedChannelException =>
        IO.unit
      case _ =>
        super.reportFailure(err)
    }

  def run(args: List[String]): IO[ExitCode] = {
    val configFile = args.headOption.getOrElse("server_config.json")
    
    ProcessUtils.readConfig(configFile).flatMap { config =>
      (for {
        scoreController <- Resource.eval(Init.init(config))
        
        // 构建HTTP应用 - 使用CORS
        httpApp = scoreController.routesWithCORS.orNotFound

        // 创建服务器
        server <- EmberServerBuilder.default[IO]
          .withHost(Host.fromString(config.serverIP).getOrElse(
            throw new IllegalArgumentException(s"Invalid IPv4 address: ${config.serverIP}")
          ))
          .withPort(Port.fromInt(config.serverPort).getOrElse(
            throw new IllegalArgumentException(s"Invalid port: ${config.serverPort}")
          ))
          .withIdleTimeout(30.minutes)
          .withShutdownTimeout(30.minutes)
          .withRequestHeaderReceiveTimeout(30.minutes)
          .withMaxConnections(config.maximumServerConnection)
          .withHttpApp(httpApp)
          .build
      } yield server).useForever.as(ExitCode.Success)
    }
  }
}
