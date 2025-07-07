package Process

import Database.DatabaseManager
import cats.effect.IO
import org.slf4j.LoggerFactory

object Init {
  private val logger = LoggerFactory.getLogger("Init")

  def initializeDatabase(): IO[Unit] = {
    logger.info("开始初始化阅卷服务数据库表...")
    
    for {
      _ <- createGradingTasksTable()
      _ <- createQuestionScoresTable()
      _ <- createCoachStudentsTable()
      _ <- createScoreHistoryTable()
      _ <- createGradingImagesTable()
      _ <- createIndexes()
    } yield {
      logger.info("阅卷服务数据库表初始化完成")
    }
  }

  private def createGradingTasksTable(): IO[Unit] = {
    val sql = """
      CREATE TABLE IF NOT EXISTS grading_tasks (
        id BIGSERIAL PRIMARY KEY,
        exam_id BIGINT NOT NULL,
        submission_id BIGINT NOT NULL,
        grader_id BIGINT,
        question_number INTEGER NOT NULL,
        status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
        max_score DECIMAL(10,2) NOT NULL,
        actual_score DECIMAL(10,2),
        feedback TEXT,
        assigned_at TIMESTAMP,
        started_at TIMESTAMP,
        completed_at TIMESTAMP,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
      )
    """
    
    DatabaseManager.executeUpdate(sql).map { _ =>
      logger.info("阅卷任务表创建完成")
    }
  }

  private def createQuestionScoresTable(): IO[Unit] = {
    val sql = """
      CREATE TABLE IF NOT EXISTS question_scores (
        exam_id BIGINT NOT NULL,
        question_number INTEGER NOT NULL,
        max_score DECIMAL(10,2) NOT NULL,
        question_type VARCHAR(50) NOT NULL DEFAULT 'SUBJECTIVE',
        description TEXT,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        PRIMARY KEY (exam_id, question_number)
      )
    """
    
    DatabaseManager.executeUpdate(sql).map { _ =>
      logger.info("题目分数表创建完成")
    }
  }

  private def createCoachStudentsTable(): IO[Unit] = {
    val sql = """
      CREATE TABLE IF NOT EXISTS coach_students (
        id BIGSERIAL PRIMARY KEY,
        coach_id BIGINT NOT NULL,
        student_name VARCHAR(100) NOT NULL,
        student_school VARCHAR(200) NOT NULL,
        student_province VARCHAR(50) NOT NULL,
        grade VARCHAR(20),
        is_active BOOLEAN NOT NULL DEFAULT true,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
      )
    """
    
    DatabaseManager.executeUpdate(sql).map { _ =>
      logger.info("教练学生表创建完成")
    }
  }

  private def createScoreHistoryTable(): IO[Unit] = {
    val sql = """
      CREATE TABLE IF NOT EXISTS score_history (
        id BIGSERIAL PRIMARY KEY,
        task_id BIGINT NOT NULL,
        grader_id BIGINT NOT NULL,
        question_number INTEGER NOT NULL,
        score DECIMAL(10,2) NOT NULL,
        feedback TEXT,
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
      )
    """
    
    DatabaseManager.executeUpdate(sql).map { _ =>
      logger.info("评分历史表创建完成")
    }
  }

  private def createGradingImagesTable(): IO[Unit] = {
    val sql = """
      CREATE TABLE IF NOT EXISTS grading_images (
        id BIGSERIAL PRIMARY KEY,
        image_url VARCHAR(500) NOT NULL,
        file_name VARCHAR(255) NOT NULL,
        exam_id BIGINT NOT NULL,
        student_id BIGINT NOT NULL,
        question_number INTEGER NOT NULL,
        upload_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        file_size BIGINT,
        mime_type VARCHAR(100),
        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
      )
    """
    
    DatabaseManager.executeUpdate(sql).map { _ =>
      logger.info("阅卷图片表创建完成")
    }
  }

  private def createIndexes(): IO[Unit] = {
    val indexes = List(
      "CREATE INDEX IF NOT EXISTS idx_grading_tasks_exam_id ON grading_tasks(exam_id)",
      "CREATE INDEX IF NOT EXISTS idx_grading_tasks_grader_id ON grading_tasks(grader_id)",
      "CREATE INDEX IF NOT EXISTS idx_grading_tasks_status ON grading_tasks(status)",
      "CREATE INDEX IF NOT EXISTS idx_grading_tasks_submission_id ON grading_tasks(submission_id)",
      "CREATE INDEX IF NOT EXISTS idx_coach_students_coach_id ON coach_students(coach_id)",
      "CREATE INDEX IF NOT EXISTS idx_coach_students_is_active ON coach_students(is_active)",
      "CREATE INDEX IF NOT EXISTS idx_score_history_task_id ON score_history(task_id)",
      "CREATE INDEX IF NOT EXISTS idx_score_history_grader_id ON score_history(grader_id)",
      "CREATE INDEX IF NOT EXISTS idx_grading_images_exam_id ON grading_images(exam_id)",
      "CREATE INDEX IF NOT EXISTS idx_grading_images_student_id ON grading_images(student_id)",
      "CREATE INDEX IF NOT EXISTS idx_grading_images_question_number ON grading_images(question_number)",
      "CREATE INDEX IF NOT EXISTS idx_grading_images_upload_time ON grading_images(upload_time)"
    )
    
    val createIndexes = indexes.map { indexSql =>
      DatabaseManager.executeUpdate(indexSql).map(_ => ())
    }
    
    createIndexes.foldLeft(IO.unit) { (acc, indexIO) =>
      acc.flatMap(_ => indexIO)
    }.map { _ =>
      logger.info("数据库索引创建完成")
    }
  }
}
