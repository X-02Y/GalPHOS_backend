package Process

import cats.effect.IO
import cats.implicits.*
import Database.DatabaseManager
import org.slf4j.LoggerFactory

object Init {
  private val logger = LoggerFactory.getLogger("Init")

  def performStartupTasks(): IO[Unit] = {
    for {
      _ <- createTablesIfNotExists()
      _ <- IO(logger.info("Startup tasks completed successfully"))
    } yield ()
  }

  private def createTablesIfNotExists(): IO[Unit] = {
    val createFilesTableQuery = """
      CREATE TABLE IF NOT EXISTS files (
        id VARCHAR(36) PRIMARY KEY,
        file_name VARCHAR(255) NOT NULL,
        original_name VARCHAR(255) NOT NULL,
        file_url VARCHAR(500) NOT NULL,
        file_size BIGINT NOT NULL,
        mime_type VARCHAR(100) NOT NULL,
        file_type VARCHAR(50),
        category VARCHAR(50),
        exam_id VARCHAR(36),
        question_number INTEGER,
        student_id VARCHAR(36),
        uploaded_by VARCHAR(100) NOT NULL,
        upload_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      )
    """

    val createIndexesQueries = List(
      "CREATE INDEX IF NOT EXISTS idx_files_exam_id ON files(exam_id)",
      "CREATE INDEX IF NOT EXISTS idx_files_student_id ON files(student_id)",
      "CREATE INDEX IF NOT EXISTS idx_files_uploaded_by ON files(uploaded_by)",
      "CREATE INDEX IF NOT EXISTS idx_files_file_type ON files(file_type)",
      "CREATE INDEX IF NOT EXISTS idx_files_category ON files(category)",
      "CREATE INDEX IF NOT EXISTS idx_files_upload_time ON files(upload_time)"
    )

    for {
      _ <- DatabaseManager.executeUpdate(createFilesTableQuery)
      _ <- IO(logger.info("Files table created/verified"))
      
      _ <- createIndexesQueries.traverse_(query => 
        DatabaseManager.executeUpdate(query).void
      )
      _ <- IO(logger.info("Database indexes created/verified"))
      
    } yield ()
  }
}
