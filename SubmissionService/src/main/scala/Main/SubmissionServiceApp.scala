package Main

import cats.effect.{IO, IOApp, ExitCode, Resource}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.ember.client.EmberClientBuilder
import org.slf4j.LoggerFactory
import Config.{ConfigLoader, ServiceConfig}
import Database.{DatabaseManager, SubmissionDAOImpl, AnswerDAOImpl}
import Services.*
import Controllers.SubmissionController
import Process.Init
import com.comcast.ip4s.*

object SubmissionServiceApp extends IOApp {
  private val logger = LoggerFactory.getLogger("SubmissionServiceApp")

  override def run(args: List[String]): IO[ExitCode] = {
    logger.info("Starting Submission Service...")

    val app = for {
      // Load configuration and initialize database
      config <- IO(ConfigLoader.loadConfig())
      _ = logger.info(s"Configuration loaded: ${config.serverIP}:${config.serverPort}")
      _ <- DatabaseManager.initializeDataSource(config.toDatabaseConfig)
      _ <- Init.performStartupTasks()

      // Run the server
      _ <- serverResource(config).use { server =>
        logger.info(s"Submission Service started at ${server.address}")
        IO.never // Keep running forever
      }
    } yield ExitCode.Success

    app.handleErrorWith { error =>
      logger.error(s"Failed to start Submission Service: ${error.getMessage}", error)
      IO.pure(ExitCode.Error)
    }
  }

  private def serverResource(config: ServiceConfig) = {
    val resources = for {
      client <- EmberClientBuilder.default[IO].build
      server <- EmberServerBuilder
        .default[IO]
        .withHost(Host.fromString(config.serverIP).getOrElse(host"0.0.0.0"))
        .withPort(Port.fromInt(config.serverPort).getOrElse(port"3004"))
        .withHttpApp(createRoutes(config, client))
        .build
    } yield server

    resources
  }

  private def createRoutes(config: ServiceConfig, client: org.http4s.client.Client[IO]) = {
    // Create DAO instances
    val submissionDAO = new SubmissionDAOImpl()
    val answerDAO = new AnswerDAOImpl()

    // Create service instances
    val authService = new HttpAuthService(config, client)
    val fileStorageService = new FileStorageServiceImpl(config, client)
    val submissionService = new SubmissionServiceImpl(
      submissionDAO,
      answerDAO,
      authService,
      config,
      client
    )

    // Create controller and return routes
    val submissionController = new SubmissionController(
      submissionService,
      authService,
      fileStorageService
    )
    
    submissionController.routes.orNotFound
  }
}
