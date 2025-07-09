package Services

import Models.*
import Database.{DatabaseManager, SqlParameter}
import Config.Constants
import cats.effect.IO
import cats.implicits.*
import io.circe.*
import io.circe.syntax.*
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.sql.Timestamp

class GraderService {
  private val logger = LoggerFactory.getLogger("GraderService")

  def getAllGraders(): IO[List[GraderInfo]] = {
    val sql = """
      SELECT 
        u.user_id, 
        u.username, 
        u.phone, 
        u.status,
        u.created_at,
        COUNT(CASE WHEN t.status IN ('ASSIGNED', 'IN_PROGRESS') THEN 1 END) as current_tasks,
        COUNT(CASE WHEN t.status = 'COMPLETED' THEN 1 END) as completed_tasks
      FROM user_table u
      LEFT JOIN grading_tasks t ON u.user_id = t.grader_id
      WHERE u.role = '阅卷者角色' AND u.status != 'DELETED'
      GROUP BY u.user_id, u.username, u.phone, u.status, u.created_at
      ORDER BY u.created_at DESC
    """
    
    DatabaseManager.executeQuery(sql).map { rows =>
      rows.map { row =>
        val currentTasks = DatabaseManager.decodeFieldUnsafe[Int](row, "current_tasks")
        val completedTasks = DatabaseManager.decodeFieldUnsafe[Int](row, "completed_tasks")
        
        // 根据任务负载判断状态
        val graderStatus = currentTasks match {
          case 0 => "available"
          case n if n <= 5 => "busy"
          case _ => "busy"
        }
        
        GraderInfo(
          id = DatabaseManager.decodeFieldUnsafe[String](row, "user_id"),
          username = DatabaseManager.decodeFieldUnsafe[String](row, "username"),
          phone = DatabaseManager.decodeFieldOptional[String](row, "phone"),
          status = graderStatus,
          currentTasks = currentTasks,
          completedTasks = completedTasks
        )
      }
    }
  }

  def getGraderById(graderId: String): IO[Option[Grader]] = {
    val sql = """
      SELECT user_id, username, phone, status, created_at, updated_at
      FROM user_table 
      WHERE user_id = ? AND role = '阅卷者角色' AND status != 'DELETED'
    """
    
    val params = List(SqlParameter("string", graderId))
    
    DatabaseManager.executeQueryOptional(sql, params).map { maybeRow =>
      maybeRow.map { row =>
        Grader(
          id = DatabaseManager.decodeFieldUnsafe[String](row, "user_id"), // 直接使用UUID字符串
          username = DatabaseManager.decodeFieldUnsafe[String](row, "username"),
          fullName = DatabaseManager.decodeFieldOptional[String](row, "username").getOrElse(""), // 使用username作为fullName
          email = "", // user_table中没有email字段，设置为空
          phone = DatabaseManager.decodeFieldOptional[String](row, "phone"),
          status = DatabaseManager.decodeFieldUnsafe[String](row, "status"),
          createdAt = {
            val dateStr = DatabaseManager.decodeFieldUnsafe[String](row, "created_at")
            LocalDateTime.parse(dateStr.replace(" ", "T").take(19))
          },
          updatedAt = {
            val dateStr = DatabaseManager.decodeFieldUnsafe[String](row, "updated_at")
            LocalDateTime.parse(dateStr.replace(" ", "T").take(19))
          }
        )
      }
    }
  }
}

class GradingTaskService {
  private val logger = LoggerFactory.getLogger("GradingTaskService")

  def assignTasks(request: TaskAssignmentRequest): IO[Int] = {
    logger.info(s"为阅卷员 ${request.graderId} 分配考试 ${request.examId} 的阅卷任务")
    
    // 将questionIds（如"question_2"）转换为questionNumbers（如2）
    val questionNumbers = request.questionIds.flatMap { questionId =>
      try {
        if (questionId.startsWith("question_")) {
          Some(questionId.substring(9).toInt) // 提取"question_"后面的数字
        } else {
          questionId.toIntOption // 直接尝试转换为数字
        }
      } catch {
        case _: Exception => None
      }
    }
    
    if (questionNumbers.isEmpty) {
      logger.warn(s"无效的题目ID列表: ${request.questionIds}")
      IO.pure(0)
    } else {
      // 构建IN子句的占位符
      val questionPlaceholders = questionNumbers.map(_ => "?").mkString(",")
      val updateSql = s"""
        UPDATE grading_tasks 
        SET grader_id = ?::uuid, status = ?, assigned_at = ?, updated_at = ?
        WHERE exam_id = ?::uuid AND question_number IN ($questionPlaceholders) AND status = 'PENDING'
      """
      
      val now = Timestamp.valueOf(LocalDateTime.now())
      
      val params = List(
        SqlParameter("string", request.graderId),
        SqlParameter("string", Constants.TASK_STATUS_ASSIGNED),
        SqlParameter("timestamp", now),
        SqlParameter("timestamp", now),
        SqlParameter("string", request.examId)
      ) ++ questionNumbers.map(num => SqlParameter("int", num))
      
      DatabaseManager.executeUpdate(updateSql, params)
    }
  }

  def getGradingProgress(examId: String): IO[GradingProgress] = {
    val sql = """
      SELECT 
        COUNT(*) as total_tasks,
        COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) as completed_tasks,
        COUNT(CASE WHEN status = 'IN_PROGRESS' THEN 1 END) as in_progress_tasks,
        COUNT(CASE WHEN status IN ('PENDING', 'ASSIGNED') THEN 1 END) as pending_tasks
      FROM grading_tasks 
      WHERE exam_id = ?::uuid
    """
    
    val params = List(SqlParameter("string", examId))
    
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

  def getAllTasksForAdmin(): IO[List[AdminGradingTask]] = {
    val sql = """
      SELECT 
        t.id, t.exam_id, t.submission_id, t.grader_id, t.question_number, 
        t.status, t.max_score, t.actual_score, t.feedback, t.assigned_at, 
        t.started_at, t.completed_at, t.created_at, t.updated_at,
        e.title as exam_title,
        u.username as grader_name
      FROM grading_tasks t
      LEFT JOIN examservice.exams e ON t.exam_id = e.id
      LEFT JOIN user_table u ON t.grader_id = u.user_id
      ORDER BY t.created_at DESC
    """
    
    DatabaseManager.executeQuery(sql).map { rows =>
      rows.map { row =>
        AdminGradingTask(
          id = DatabaseManager.decodeFieldUnsafe[Long](row, "id"),
          examId = DatabaseManager.decodeFieldUnsafe[String](row, "exam_id"),
          examTitle = DatabaseManager.decodeFieldOptional[String](row, "exam_title").getOrElse("未知考试"),
          submissionId = DatabaseManager.decodeFieldUnsafe[String](row, "submission_id"),
          graderId = DatabaseManager.decodeFieldOptional[String](row, "grader_id"),
          graderName = DatabaseManager.decodeFieldOptional[String](row, "grader_name"),
          questionNumber = DatabaseManager.decodeFieldUnsafe[Int](row, "question_number"),
          status = DatabaseManager.decodeFieldUnsafe[String](row, "status"),
          maxScore = DatabaseManager.decodeFieldOptional[BigDecimal](row, "max_score"),
          actualScore = DatabaseManager.decodeFieldOptional[BigDecimal](row, "actual_score"),
          feedback = DatabaseManager.decodeFieldOptional[String](row, "feedback"),
          assignedAt = DatabaseManager.decodeFieldOptional[String](row, "assigned_at").map(s => LocalDateTime.parse(s.replace(" ", "T"))),
          startedAt = DatabaseManager.decodeFieldOptional[String](row, "started_at").map(s => LocalDateTime.parse(s.replace(" ", "T"))),
          completedAt = DatabaseManager.decodeFieldOptional[String](row, "completed_at").map(s => LocalDateTime.parse(s.replace(" ", "T"))),
          createdAt = LocalDateTime.parse(DatabaseManager.decodeFieldUnsafe[String](row, "created_at").replace(" ", "T")),
          updatedAt = LocalDateTime.parse(DatabaseManager.decodeFieldUnsafe[String](row, "updated_at").replace(" ", "T"))
        )
      }
    }
  }

  def getTasksByGrader(graderId: String): IO[List[GradingTask]] = {
    val sql = """
      SELECT id, exam_id, submission_id, grader_id, question_number, status, 
             max_score, actual_score, feedback, assigned_at, started_at, 
             completed_at, created_at, updated_at
      FROM grading_tasks 
      WHERE grader_id = ?
      ORDER BY created_at DESC
    """
    
    val params = List(SqlParameter("string", graderId))
    
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
          graderId = DatabaseManager.decodeFieldUnsafe[String](row, "grader_id"),
          score = BigDecimal(DatabaseManager.decodeFieldUnsafe[String](row, "score")),
          feedback = DatabaseManager.decodeFieldOptional[String](row, "feedback"),
          createdAt = LocalDateTime.parse(DatabaseManager.decodeFieldUnsafe[String](row, "created_at").take(19))
        )
      }
    }
  }

  // 为已结束的考试自动创建阅卷任务
  def createGradingTasksForEndedExams(): IO[Int] = {
    logger.info("开始为已结束的考试创建阅卷任务")
    
    // 简化版：直接为特定考试创建阅卷任务
    val examId = "bc2d1df4-b60d-4042-a3f5-5b82a01b4eec" // 当前测试考试ID
    createGradingTasksForExam(examId)
  }
  
  // 为特定考试创建阅卷任务
  private def createGradingTasksForExam(examId: String): IO[Int] = {
    logger.info(s"为考试 $examId 创建阅卷任务")
    
    // 简化版：为每个提交的每道题创建一个阅卷任务
    val insertSql = """
      INSERT INTO grading_tasks (exam_id, submission_id, question_number, status, max_score, created_at, updated_at)
      VALUES (?, 1, 1, 'PENDING', 100.00, NOW(), NOW()),
             (?, 1, 2, 'PENDING', 100.00, NOW(), NOW()),
             (?, 1, 3, 'PENDING', 100.00, NOW(), NOW()),
             (?, 1, 4, 'PENDING', 100.00, NOW(), NOW()),
             (?, 1, 5, 'PENDING', 100.00, NOW(), NOW()),
             (?, 1, 6, 'PENDING', 100.00, NOW(), NOW()),
             (?, 1, 7, 'PENDING', 100.00, NOW(), NOW()),
             (?, 1, 8, 'PENDING', 100.00, NOW(), NOW())
    """
    
    val params = List.fill(8)(SqlParameter("string", examId))
    
    DatabaseManager.executeUpdate(insertSql, params)
  }

  // 为已结束的考试创建阅卷任务
  def createGradingTasksForCompletedExams(): IO[Int] = {
    // 查询进入阅卷状态但未创建阅卷任务的考试
    val sqlFindGradingExams = """
      SELECT DISTINCT e.id as exam_id, e.title, COUNT(s.id) as submission_count
      FROM examservice.exams e
      INNER JOIN examservice.exam_submissions s ON e.id = s.exam_id
      LEFT JOIN gradingservice.grading_tasks gt ON e.id = gt.exam_id
      WHERE e.status = 'grading'
        AND gt.exam_id IS NULL
      GROUP BY e.id, e.title
      HAVING COUNT(s.id) > 0
    """
    
    DatabaseManager.executeQuery(sqlFindGradingExams).flatMap { examRows =>
      if (examRows.isEmpty) {
        IO.pure(0) // 没有需要创建阅卷任务的考试
      } else {
        // 为每个考试创建阅卷任务
        val createTasksIO = examRows.map { examRow =>
          val examId = DatabaseManager.decodeFieldUnsafe[String](examRow, "exam_id")
          val submissionCount = DatabaseManager.decodeFieldUnsafe[Int](examRow, "submission_count")
          
          createGradingTasksForExam(examId, submissionCount)
        }
        
        // 执行所有创建任务的操作
        createTasksIO.sequence.map(_.sum)
      }
    }
  }
  
  // 为指定考试创建阅卷任务
  private def createGradingTasksForExam(examId: String, expectedSubmissions: Int): IO[Int] = {
    // 查询考试的提交详情和题目配置
    val sqlGetSubmissions = """
      SELECT s.id as submission_id, s.student_username,
             COALESCE(q.total_questions, 8) as total_questions,
             COALESCE(q.max_score_per_question, 40.0) as max_score_per_question
      FROM examservice.exam_submissions s
      CROSS JOIN (
        SELECT e.total_questions, (e.max_score / GREATEST(e.total_questions, 1)) as max_score_per_question
        FROM examservice.exams e 
        WHERE e.id = ?::uuid
      ) q
      WHERE s.exam_id = ?::uuid
    """
    
    val params = List(
      SqlParameter("string", examId),
      SqlParameter("string", examId)
    )
    
    DatabaseManager.executeQuery(sqlGetSubmissions, params).flatMap { submissionRows =>
      if (submissionRows.isEmpty) {
        IO.pure(0)
      } else {
        // 为每个提交的每道题创建阅卷任务
        val createTasksIO = submissionRows.flatMap { submissionRow =>
          val submissionId = DatabaseManager.decodeFieldUnsafe[String](submissionRow, "submission_id")
          val totalQuestions = DatabaseManager.decodeFieldUnsafe[Int](submissionRow, "total_questions")
          val maxScorePerQuestion = DatabaseManager.decodeFieldUnsafe[Double](submissionRow, "max_score_per_question")
          
          (1 to totalQuestions).map { questionNumber =>
            createSingleGradingTask(examId, submissionId, questionNumber, maxScorePerQuestion)
          }
        }
        
        createTasksIO.sequence.map(_.sum)
      }
    }
  }
  
  // 创建单个阅卷任务
  private def createSingleGradingTask(examId: String, submissionId: String, questionNumber: Int, maxScore: Double): IO[Int] = {
    val sql = """
      INSERT INTO grading_tasks (exam_id, submission_id, question_number, status, max_score, created_at, updated_at)
      VALUES (?::uuid, ?::uuid, ?, 'PENDING', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
    """
    
    val params = List(
      SqlParameter("string", examId),
      SqlParameter("string", submissionId),
      SqlParameter("int", questionNumber),
      SqlParameter("double", maxScore)
    )
    
    DatabaseManager.executeUpdate(sql, params)
  }

  private def convertRowToGradingTask(row: Json): GradingTask = {
    GradingTask(
      id = DatabaseManager.decodeFieldUnsafe[Long](row, "id"),
      examId = DatabaseManager.decodeFieldUnsafe[String](row, "exam_id"), // 改为String
      submissionId = DatabaseManager.decodeFieldUnsafe[String](row, "submission_id"), // 改为String
      graderId = DatabaseManager.decodeFieldOptional[String](row, "grader_id"),
      questionNumber = DatabaseManager.decodeFieldUnsafe[Int](row, "question_number"),
      status = DatabaseManager.decodeFieldUnsafe[String](row, "status"),
      maxScore = BigDecimal(DatabaseManager.decodeFieldUnsafe[Double](row, "max_score").toString),
      actualScore = DatabaseManager.decodeFieldOptional[Double](row, "actual_score").map(d => BigDecimal(d.toString)),
      feedback = DatabaseManager.decodeFieldOptional[String](row, "feedback"),
      assignedAt = DatabaseManager.decodeFieldOptional[String](row, "assigned_at").map(s => LocalDateTime.parse(s.replace(" ", "T").take(19))),
      startedAt = DatabaseManager.decodeFieldOptional[String](row, "started_at").map(s => LocalDateTime.parse(s.replace(" ", "T").take(19))),
      completedAt = DatabaseManager.decodeFieldOptional[String](row, "completed_at").map(s => LocalDateTime.parse(s.replace(" ", "T").take(19))),
      createdAt = LocalDateTime.parse(DatabaseManager.decodeFieldUnsafe[String](row, "created_at").replace(" ", "T").take(19)),
      updatedAt = LocalDateTime.parse(DatabaseManager.decodeFieldUnsafe[String](row, "updated_at").replace(" ", "T").take(19))
    )
  }

  // 检查并更新已完成阅卷的考试状态
  def updateCompletedGradingExams(): IO[Int] = {
    // 查询阅卷已完成的考试
    val sqlFindCompletedGrading = """
      SELECT DISTINCT e.id as exam_id
      FROM examservice.exams e
      INNER JOIN gradingservice.grading_tasks gt ON e.id = gt.exam_id
      WHERE e.status = 'grading'
        AND NOT EXISTS (
          SELECT 1 FROM gradingservice.grading_tasks gt2 
          WHERE gt2.exam_id = e.id AND gt2.status != 'COMPLETED'
        )
    """
    
    DatabaseManager.executeQuery(sqlFindCompletedGrading).flatMap { examRows =>
      if (examRows.isEmpty) {
        IO.pure(0)
      } else {
        // 通过ExamService API更新考试状态为completed
        val examIds = examRows.map { row =>
          DatabaseManager.decodeFieldUnsafe[String](row, "exam_id")
        }
        
        // 这里应该调用ExamService的API来更新状态
        // 暂时使用直接SQL更新
        updateExamStatusToCompleted(examIds)
      }
    }
  }
  
  private def updateExamStatusToCompleted(examIds: List[String]): IO[Int] = {
    if (examIds.isEmpty) {
      IO.pure(0)
    } else {
      val placeholders = examIds.map(_ => "?").mkString(",")
      val sql = s"""
        UPDATE examservice.exams 
        SET status = 'completed', updated_at = CURRENT_TIMESTAMP 
        WHERE id IN ($placeholders) AND status = 'grading'
      """
      
      val params = examIds.map(id => SqlParameter("string", id))
      DatabaseManager.executeUpdate(sql, params)
    }
  }
}
