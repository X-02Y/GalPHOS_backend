package Main

import cats.effect.{IO, IOApp, ExitCode}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.ember.client.EmberClientBuilder
import org.slf4j.LoggerFactory
import Config.{ConfigLoader, ServiceConfig}
import Database.{DatabaseManager, FileRepositoryImpl}
import Services.{FileStorageServiceImpl, JwtAuthService}
import Controllers.FileStorageController
import Process.Init
import com.comcast.ip4s.*

object FileStorageServiceApp extends IOApp {
  private val logger = LoggerFactory.getLogger("FileStorageServiceApp")

  override def run(args: List[String]): IO[ExitCode] = {
    println("=" * 60)
    println("🚀 Starting File Storage Service...")
    println("=" * 60)
    logger.info("Starting File Storage Service...")

    val app = for {
      // Load configuration
      config <- IO(ConfigLoader.loadConfig())
      _ = println(s"Configuration loaded: ${config.serverIP}:${config.serverPort}")
      _ = logger.info(s"Configuration loaded: ${config.serverIP}:${config.serverPort}")

      // Initialize database connection pool
      _ <- DatabaseManager.initializeDataSource(config.toDatabaseConfig)
      _ = println("Database connection initialized")
      
      // Initialize database tables and data
      _ <- Init.performStartupTasks()
      _ = println("Database initialization completed")

      // Create service instances
      fileRepository = new FileRepositoryImpl()
      fileStorageService = new FileStorageServiceImpl(config, fileRepository)
      authService = new JwtAuthService(config)
      _ = println("Service instances created")

      // Create controller
      fileStorageController = new FileStorageController(
        fileStorageService,
        authService,
        fileRepository
      )
      _ = println("Controller created")

      // Start HTTP server
      _ <- EmberServerBuilder.default[IO]
        .withHost(Host.fromString(config.serverIP).getOrElse(ipv4"0.0.0.0"))
        .withPort(Port.fromInt(config.serverPort).getOrElse(port"3008"))
        .withHttpApp(fileStorageController.routes.orNotFound)
        .build
        .use { server =>
          println("=" * 60)
          println(s"🚀 File Storage Service started at 127.0.0.1:${config.serverPort}")
          println("📁 Service is ready to accept file upload/download requests")
          println("=" * 60)
          logger.info(s"File Storage Service started at ${server.address}")
          logger.info("Service is ready to accept requests")
          IO.never // Keep the server running forever
        }

    } yield ()

    app.handleErrorWith { error =>
      println(s"Failed to start File Storage Service: ${error.getMessage}")
      error.printStackTrace()
      logger.error("Failed to start File Storage Service", error)
      IO.never // Even on error, keep running to see what's wrong
    }.as(ExitCode.Success)
  }
}
