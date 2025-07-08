package Services

import Models.*
import Database.{DatabaseManager, SqlParameter}
import Config.Constants
import cats.effect.IO
import io.circe.*
import io.circe.syntax.*
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.sql.Timestamp

class GraderService {
  private val logger = LoggerFactory.getLogger("GraderService")

  def getAllGraders(): IO[List[Grader]] = {
    val sql = """
      SELECT id, username, full_name, email, phone, status, created_at, updated_at
      FROM users 
      WHERE role = 'grader' AND status != 'DELETED'
      ORDER BY created_at DESC
    """
    
    DatabaseManager.executeQuery(sql).map { rows =>
      rows.map { row =>
        Grader(
          id = DatabaseManager.decodeFieldUnsafe[Long](row, "id"),
          username = DatabaseManager.decodeFieldUnsafe[String](row, "username"),
          fullName = DatabaseManager.decodeFieldUnsafe[String](row, "full_name"),
          email = DatabaseManager.decodeFieldUnsafe[String](row, "email"),
          phone = DatabaseManager.decodeFieldOptional[String](row, "phone"),
          status = DatabaseManager.decodeFieldUnsafe[String](row, "status"),
          createdAt = LocalDateTime.parse(DatabaseManager.decodeFieldUnsafe[String](row, "created_at").take(19)),
          updatedAt = LocalDateTime.parse(DatabaseManager.decodeFieldUnsafe[String](row, "updated_at").take(19))
        )
      }
    }
  }

  def getGraderById(graderId: Long): IO[Option[Grader]] = {
    val sql = """
      SELECT id, username, full_name, email, phone, status, created_at, updated_at
      FROM users 
      WHERE id = ? AND role = 'grader' AND status != 'DELETED'
    """
    
    val params = List(SqlParameter("long", graderId))
    
    DatabaseManager.executeQueryOptional(sql, params).map { maybeRow =>
      maybeRow.map { row =>
        Grader(
          id = DatabaseManager.decodeFieldUnsafe[Long](row, "id"),
          username = DatabaseManager.decodeFieldUnsafe[String](row, "username"),
          fullName = DatabaseManager.decodeFieldUnsafe[String](row, "full_name"),
          email = DatabaseManager.decodeFieldUnsafe[String](row, "email"),
          phone = DatabaseManager.decodeFieldOptional[String](row, "phone"),
          status = DatabaseManager.decodeFieldUnsafe[String](row, "status"),
          createdAt = LocalDateTime.parse(DatabaseManager.decodeFieldUnsafe[String](row, "created_at").take(19)),
          updatedAt = LocalDateTime.parse(DatabaseManager.decodeFieldUnsafe[String](row, "updated_at").take(19))
        )
      }
    }
  }
}

class GradingTaskService {
  private val logger = LoggerFactory.getLogger("GradingTaskService")

  def assignTasks(request: TaskAssignmentRequest): IO[Int] = {
    logger.info(s"为阅卷员 ${request.graderId} 分配考试 ${request.examId} 的阅卷任务")
    
    val updateSql = """
      UPDATE grading_tasks 
      SET grader_id = ?, status = ?, assigned_at = ?, updated_at = ?
      WHERE exam_id = ? AND question_number = ANY(?) AND status = 'PENDING'
    """
    
    val now = Timestamp.valueOf(LocalDateTime.now())
    val questionArray = request.questionNumbers.mkString("{", ",", "}")
    
    val params = List(
      SqlParameter("long", request.graderId),
      SqlParameter("string", Constants.TASK_STATUS_ASSIGNED),
      SqlParameter("timestamp", now),
      SqlParameter("timestamp", now),
      SqlParameter("long", request.examId),
      SqlParameter("string", questionArray)
    )
    
    DatabaseManager.executeUpdate(updateSql, params)
  }

  def getGradingProgress(examId: Long): IO[GradingProgress] = {
    val sql = """
      SELECT 
        COUNT(*) as total_tasks,
        COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) as completed_tasks,
        COUNT(CASE WHEN status = 'IN_PROGRESS' THEN 1 END) as in_progress_tasks,
        COUNT(CASE WHEN status IN ('PENDING', 'ASSIGNED') THEN 1 END) as pending_tasks
      FROM grading_tasks 
      WHERE exam_id = ?
    """
    
    val params = List(SqlParameter("long", examId))
    
    DatabaseManager.executeQueryOptional(sql, params).map { maybeRow =>
      maybeRow.map { row =>
        val totalTasks = DatabaseManager.decodeFieldUnsafe[Long](row, "total_tasks").toInt
        val completedTasks = DatabaseManager.decodeFieldUnsafe[Long](row, "completed_tasks").toInt
        val inProgressTasks = DatabaseManager.decodeFieldUnsafe[Long](row, "in_progress_tasks").toInt
        val pendingTasks = DatabaseManager.decodeFieldUnsafe[Long](row, "pending_tasks").toInt
        
        val completionPercentage = if (totalTasks > 0) {
          BigDecimal(completedTasks * 100.0 / totalTasks).setScale(2, BigDecimal.RoundingMode.HALF_UP)
        } else {
          BigDecimal(0)
        }
        
        GradingProgress(
          examId = examId,
          totalTasks = totalTasks,
          completedTasks = completedTasks,
          inProgressTasks = inProgressTasks,
          pendingTasks = pendingTasks,
          completionPercentage = completionPercentage
        )
      }.getOrElse(
        GradingProgress(examId, 0, 0, 0, 0, BigDecimal(0))
      )
    }
  }

  def getAllTasks(): IO[List[GradingTask]] = {
    val sql = """
      SELECT id, exam_id, submission_id, grader_id, question_number, status, 
             max_score, actual_score, feedback, assigned_at, started_at, 
             completed_at, created_at, updated_at
      FROM grading_tasks 
      ORDER BY created_at DESC
    """
    
    DatabaseManager.executeQuery(sql).map { rows =>
      rows.map(convertRowToGradingTask)
    }
  }

  def getTasksByGrader(graderId: Long): IO[List[GradingTask]] = {
    val sql = """
      SELECT id, exam_id, submission_id, grader_id, question_number, status, 
             max_score, actual_score, feedback, assigned_at, started_at, 
             completed_at, created_at, updated_at
      FROM grading_tasks 
      WHERE grader_id = ?
      ORDER BY created_at DESC
    """
    
    val params = List(SqlParameter("long", graderId))
    
    DatabaseManager.executeQuery(sql, params).map { rows =>
      rows.map(convertRowToGradingTask)
    }
  }

  def getTaskById(taskId: Long): IO[Option[GradingTask]] = {
    val sql = """
      SELECT id, exam_id, submission_id, grader_id, question_number, status, 
             max_score, actual_score, feedback, assigned_at, started_at, 
             completed_at, created_at, updated_at
      FROM grading_tasks 
      WHERE id = ?
    """
    
    val params = List(SqlParameter("long", taskId))
    
    DatabaseManager.executeQueryOptional(sql, params).map { maybeRow =>
      maybeRow.map(convertRowToGradingTask)
    }
  }

  def startTask(taskId: Long): IO[Int] = {
    val sql = """
      UPDATE grading_tasks 
      SET status = ?, started_at = ?, updated_at = ?
      WHERE id = ? AND status = 'ASSIGNED'
    """
    
    val now = Timestamp.valueOf(LocalDateTime.now())
    val params = List(
      SqlParameter("string", Constants.TASK_STATUS_IN_PROGRESS),
      SqlParameter("timestamp", now),
      SqlParameter("timestamp", now),
      SqlParameter("long", taskId)
    )
    
    DatabaseManager.executeUpdate(sql, params)
  }

  def submitTask(request: SubmitGradingRequest): IO[Int] = {
    // 更新任务状态为完成
    val updateTaskSql = """
      UPDATE grading_tasks 
      SET status = ?, completed_at = ?, updated_at = ?, feedback = ?
      WHERE id = ? AND status = 'IN_PROGRESS'
    """
    
    val now = Timestamp.valueOf(LocalDateTime.now())
    val params = List(
      SqlParameter("string", Constants.TASK_STATUS_COMPLETED),
      SqlParameter("timestamp", now),
      SqlParameter("timestamp", now),
      SqlParameter("string", request.feedback.getOrElse("")),
      SqlParameter("long", request.taskId)
    )
    
    for {
      taskUpdated <- DatabaseManager.executeUpdate(updateTaskSql, params)
      // 这里可以添加分数记录的逻辑
    } yield taskUpdated
  }

  def abandonTask(taskId: Long): IO[Int] = {
    val sql = """
      UPDATE grading_tasks 
      SET status = ?, grader_id = NULL, assigned_at = NULL, 
          started_at = NULL, updated_at = ?
      WHERE id = ? AND status IN ('ASSIGNED', 'IN_PROGRESS')
    """
    
    val now = Timestamp.valueOf(LocalDateTime.now())
    val params = List(
      SqlParameter("string", Constants.TASK_STATUS_PENDING),
      SqlParameter("timestamp", now),
      SqlParameter("long", taskId)
    )
    
    DatabaseManager.executeUpdate(sql, params)
  }

  def saveProgress(request: SaveProgressRequest): IO[Int] = {
    // 这里可以保存中间进度，比如单题的分数
    val sql = """
      INSERT INTO score_history (task_id, grader_id, question_number, score, feedback, created_at)
      SELECT ?, grader_id, ?, ?, ?, ? FROM grading_tasks WHERE id = ?
    """
    
    val now = Timestamp.valueOf(LocalDateTime.now())
    val params = List(
      SqlParameter("long", request.taskId),
      SqlParameter("int", request.questionNumber),
      SqlParameter("bigdecimal", request.score.bigDecimal),
      SqlParameter("string", request.feedback.getOrElse("")),
      SqlParameter("timestamp", now),
      SqlParameter("long", request.taskId)
    )
    
    DatabaseManager.executeUpdate(sql, params)
  }

  def getScoreHistory(taskId: Long, questionNumber: Int): IO[List[ScoreHistory]] = {
    val sql = """
      SELECT id, task_id, grader_id, question_number, score, feedback, created_at
      FROM score_history 
      WHERE task_id = ? AND question_number = ?
      ORDER BY created_at DESC
    """
    
    val params = List(
      SqlParameter("long", taskId),
      SqlParameter("int", questionNumber)
    )
    
    DatabaseManager.executeQuery(sql, params).map { rows =>
      rows.map { row =>
        ScoreHistory(
          id = DatabaseManager.decodeFieldUnsafe[Long](row, "id"),
          taskId = DatabaseManager.decodeFieldUnsafe[Long](row, "task_id"),
          graderId = DatabaseManager.decodeFieldUnsafe[Long](row, "grader_id"),
          score = BigDecimal(DatabaseManager.decodeFieldUnsafe[String](row, "score")),
          feedback = DatabaseManager.decodeFieldOptional[String](row, "feedback"),
          createdAt = LocalDateTime.parse(DatabaseManager.decodeFieldUnsafe[String](row, "created_at").take(19))
        )
      }
    }
  }

  private def convertRowToGradingTask(row: Json): GradingTask = {
    GradingTask(
      id = DatabaseManager.decodeFieldUnsafe[Long](row, "id"),
      examId = DatabaseManager.decodeFieldUnsafe[Long](row, "exam_id"),
      submissionId = DatabaseManager.decodeFieldUnsafe[Long](row, "submission_id"),
      graderId = DatabaseManager.decodeFieldOptional[Long](row, "grader_id"),
      questionNumber = DatabaseManager.decodeFieldUnsafe[Int](row, "question_number"),
      status = DatabaseManager.decodeFieldUnsafe[String](row, "status"),
      maxScore = BigDecimal(DatabaseManager.decodeFieldUnsafe[String](row, "max_score")),
      actualScore = DatabaseManager.decodeFieldOptional[String](row, "actual_score").map(BigDecimal(_)),
      feedback = DatabaseManager.decodeFieldOptional[String](row, "feedback"),
      assignedAt = DatabaseManager.decodeFieldOptional[String](row, "assigned_at").map(s => LocalDateTime.parse(s.take(19))),
      startedAt = DatabaseManager.decodeFieldOptional[String](row, "started_at").map(s => LocalDateTime.parse(s.take(19))),
      completedAt = DatabaseManager.decodeFieldOptional[String](row, "completed_at").map(s => LocalDateTime.parse(s.take(19))),
      createdAt = LocalDateTime.parse(DatabaseManager.decodeFieldUnsafe[String](row, "created_at").take(19)),
      updatedAt = LocalDateTime.parse(DatabaseManager.decodeFieldUnsafe[String](row, "updated_at").take(19))
    )
  }
}
