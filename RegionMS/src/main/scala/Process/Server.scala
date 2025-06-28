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
        regionController <- Resource.eval(Init.init(config))
        
        // Configure CORS
        corsRoutes = CORS.policy
          .withAllowOriginAll
          .withAllowCredentials(false)
          .withAllowHeadersAll
          .withAllowMethodsAll
          .apply(regionController.routes)
        
        // Build HTTP application
        httpApp <- Resource.eval(corsRoutes.map(_.orNotFound))

        // Create server
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
      } yield server).use { server =>
        logger.info(s"Region Management Service started at http://${config.serverIP}:${config.serverPort}") *>
        IO.never
      }.as(ExitCode.Success)
    }.handleErrorWith { error =>
      logger.error(error)(s"Failed to start server: ${error.getMessage}") *>
      IO.pure(ExitCode.Error)
    }
  }
}
