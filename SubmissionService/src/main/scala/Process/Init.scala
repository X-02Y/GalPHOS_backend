package Process

import cats.effect.IO
import Database.DatabaseManager
import org.slf4j.LoggerFactory
import scala.io.Source
import java.io.FileNotFoundException

object Init {
  private val logger = LoggerFactory.getLogger("Init")

  def performStartupTasks(): IO[Unit] = {
    for {
      _ <- IO(logger.info("Starting initialization tasks..."))
      _ <- initializeDatabase()
      _ <- IO(logger.info("Initialization tasks completed successfully"))
    } yield ()
  }

  private def initializeDatabase(): IO[Unit] = {
    logger.info("Initializing database schema...")
    
    DatabaseManager.withConnection { connection =>
      try {
        // Read the SQL initialization script
        val sqlScript = Source.fromFile("init_database.sql").mkString
        
        // Split by semicolon and execute each statement
        val statements = sqlScript.split(";").map(_.trim).filter(_.nonEmpty)
        
        statements.foreach { statement =>
          if (statement.nonEmpty && !statement.startsWith("--")) {
            try {
              val preparedStatement = connection.prepareStatement(statement)
              preparedStatement.execute()
              preparedStatement.close()
              logger.debug(s"Executed SQL statement: ${statement.take(50)}...")
            } catch {
              case ex: Exception =>
                // Log warning but continue - some statements might fail if already executed
                logger.warn(s"SQL statement failed (continuing): ${ex.getMessage}")
            }
          }
        }
        
        logger.info("Database schema initialization completed")
      } catch {
        case _: FileNotFoundException =>
          logger.warn("init_database.sql file not found, skipping database initialization")
        case ex: Exception =>
          logger.error(s"Database initialization failed: ${ex.getMessage}")
          throw ex
      }
    }
  }
}
