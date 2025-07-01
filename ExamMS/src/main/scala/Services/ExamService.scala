package Services

import cats.effect.IO
import cats.implicits.*
import io.circe.*
import io.circe.generic.auto.*
import io.circe.parser.*
import io.circe.syntax.*
import Database.{DatabaseManager, SqlParameter}
import Models.*
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

class ExamService {
  private val logger = LoggerFactory.getLogger("ExamService")

  // Student APIs
  def getExamsForStudent(): IO[List[Exam]] = {
    val sql = """
      SELECT id, title, description, subject, start_time, end_time, duration, 
             status, total_score, question_count, created_at, updated_at
      FROM exams 
      WHERE status IN ('published', 'active', 'completed')
      ORDER BY start_time DESC
    """
    
    DatabaseManager.executeQuery(sql).flatMap { results =>
      IO.pure(results.flatMap(jsonToExam))
    }
  }

  def getExamDetailForStudent(examId: String): IO[Option[ExamDetail]] = {
    for {
      examOpt <- getExamById(examId)
      result <- examOpt match {
        case Some(exam) if exam.status == ExamStatus.published || 
                          exam.status == ExamStatus.active || 
                          exam.status == ExamStatus.completed =>
          for {
            questions <- getQuestionsByExamId(examId)
            files <- getExamFiles(examId)
            settings <- getExamSettings(examId)
          } yield Some(ExamDetail(
            id = exam.id,
            title = exam.title,
            description = Some(exam.description.getOrElse("")),
            subject = exam.subject,
            startTime = exam.startTime,
            endTime = exam.endTime,
            duration = exam.duration,
            status = exam.status,
            totalScore = exam.totalScore,
            questionCount = exam.questionCount,
            instructions = None, // Will be fetched from settings
            questions = questions,
            files = files,
            settings = settings,
            createdAt = exam.createdAt,
            updatedAt = exam.updatedAt
          ))
        case _ => IO.pure(None)
      }
    } yield result
  }

  // Coach APIs
  def getExamsForCoach(status: Option[String] = None, timeRange: Option[String] = None): IO[List[Exam]] = {
    val baseConditions = List("status != 'draft'")
    val conditions = status.map(s => s"status = '$s'").toList ++ baseConditions
    val whereClause = if (conditions.nonEmpty) s"WHERE ${conditions.mkString(" AND ")}" else ""
    
    val sql = s"""
      SELECT id, title, description, subject, start_time, end_time, duration, 
             status, total_score, question_count, created_at, updated_at
      FROM exams 
      $whereClause
      ORDER BY start_time DESC
    """
    
    DatabaseManager.executeQuery(sql).flatMap { results =>
      IO.pure(results.flatMap(jsonToExam))
    }
  }

  def getExamDetailsForCoach(examId: String): IO[Option[ExamDetailResponse]] = {
    for {
      examDetailOpt <- getExamDetailForStudent(examId) // Reuse student method
      result <- examDetailOpt match {
        case Some(examDetail) =>
          for {
            stats <- getExamStatistics(examId)
          } yield Some(ExamDetailResponse(
            exam = examDetail,
            statistics = Some(stats)
          ))
        case None => IO.pure(None)
      }
    } yield result
  }

  // Grader APIs
  def getAvailableExamsForGrader(
    status: Option[String] = None, 
    page: Int = 1, 
    limit: Int = 10,
    subject: Option[String] = None
  ): IO[GraderExamsResponse] = {
    val baseConditions = List("status IN ('active', 'completed')")
    val conditions = List(
      status.map(s => s"status = '$s'"),
      subject.map(s => s"subject = '$s'")
    ).flatten ++ baseConditions
    
    val whereClause = s"WHERE ${conditions.mkString(" AND ")}"
    val offset = (page - 1) * limit
    
    val countSql = s"SELECT COUNT(*) as count FROM exams $whereClause"
    val dataSql = s"""
      SELECT id, title, subject, status, total_score, question_count, end_time
      FROM exams 
      $whereClause
      ORDER BY end_time DESC
      LIMIT $limit OFFSET $offset
    """
    
    for {
      countResult <- DatabaseManager.executeQuery(countSql)
      total = countResult.headOption.flatMap(_.hcursor.get[Int]("count").toOption).getOrElse(0)
      dataResults <- DatabaseManager.executeQuery(dataSql)
      graderExams = dataResults.flatMap(jsonToGraderExam)
      totalPages = (total + limit - 1) / limit
    } yield GraderExamsResponse(
      exams = graderExams,
      total = total,
      pagination = PaginationInfo(page, limit, total, totalPages)
    )
  }

  def getExamDetailForGrader(examId: String): IO[Option[ExamDetailResponse]] = {
    for {
      examDetailOpt <- getExamDetailForStudent(examId)
      result <- examDetailOpt match {
        case Some(examDetail) =>
          for {
            gradingInfo <- getGradingInfo(examId)
            progress <- getGradingProgress(examId)
          } yield Some(ExamDetailResponse(
            exam = examDetail,
            gradingInfo = Some(gradingInfo),
            progress = Some(progress)
          ))
        case None => IO.pure(None)
      }
    } yield result
  }

  def getExamGradingProgress(examId: String): IO[Option[ExamGradingProgressResponse]] = {
    for {
      progress <- getExamGradingProgressData(examId)
      stats <- getGradingStatsData(examId)
    } yield Some(ExamGradingProgressResponse(progress, stats))
  }

  def getExamQuestionScores(examId: String): IO[QuestionScoresResponse] = {
    val sql = """
      SELECT question_number, max_score, partial_scoring, scoring_criteria
      FROM question_scores 
      WHERE exam_id = ?
      ORDER BY question_number
    """
    val params = List(SqlParameter("uuid", examId))
    
    DatabaseManager.executeQuery(sql, params).flatMap { results =>
      val questionScores = results.flatMap(jsonToQuestionScore)
      val totalScore = questionScores.map(_.maxScore).sum
      IO.pure(QuestionScoresResponse(questionScores, totalScore))
    }
  }

  // Admin APIs
  def getExamsForAdmin(page: Int = 1, limit: Int = 10, status: Option[String] = None): IO[AdminExamsResponse] = {
    val conditions = status.map(s => s"WHERE status = '$s'").getOrElse("")
    val offset = (page - 1) * limit
    
    val countSql = s"SELECT COUNT(*) as count FROM exams $conditions"
    val dataSql = s"""
      SELECT e.id, e.title, e.description, e.subject, e.start_time, e.end_time, 
             e.duration, e.status, e.total_score, e.question_count, 
             e.created_by, e.created_at, e.updated_at,
             COALESCE(participant_count, 0) as participant_count,
             COALESCE(submission_count, 0) as submission_count
      FROM exams e
      LEFT JOIN (
        SELECT exam_id, COUNT(*) as participant_count 
        FROM exam_permissions 
        WHERE role = 'student' 
        GROUP BY exam_id
      ) p ON e.id = p.exam_id
      LEFT JOIN (
        SELECT exam_id, COUNT(*) as submission_count 
        FROM exam_permissions 
        WHERE role = 'student' 
        GROUP BY exam_id
      ) s ON e.id = s.exam_id
      $conditions
      ORDER BY e.created_at DESC
      LIMIT $limit OFFSET $offset
    """
    
    for {
      countResult <- DatabaseManager.executeQuery(countSql)
      total = countResult.headOption.flatMap(_.hcursor.get[Int]("count").toOption).getOrElse(0)
      dataResults <- DatabaseManager.executeQuery(dataSql)
      adminExams = dataResults.flatMap(jsonToAdminExam)
      totalPages = (total + limit - 1) / limit
    } yield AdminExamsResponse(
      exams = adminExams,
      total = total,
      pagination = PaginationInfo(page, limit, total, totalPages)
    )
  }

  def createExam(examData: CreateExamRequest, createdBy: String): IO[Exam] = {
    val examId = UUID.randomUUID().toString
    val now = Instant.now()
    
    // Parse time strings to Instant
    val parseTime = (timeStr: String) => IO {
      try {
        Instant.parse(timeStr)
      } catch {
        case _: Exception => 
          // Try parsing as ISO LocalDateTime and convert to UTC
          java.time.LocalDateTime.parse(timeStr.replace("Z", "")).atZone(java.time.ZoneOffset.UTC).toInstant
      }
    }
    
    for {
      startTime <- parseTime(examData.startTime)
      endTime <- parseTime(examData.endTime)
      
      insertExamSql = """
        INSERT INTO exams (id, title, description, subject, start_time, end_time, duration, 
                          total_score, question_count, instructions, settings, created_by, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      """
      
      totalScore = examData.questions.map(_.score).sum
      settingsJson = examData.settings.map(settings => s"""
        {
          "allowLateSubmission": ${settings.allowLateSubmission},
          "shuffleQuestions": ${settings.shuffleQuestions},
          "showResultsImmediately": ${settings.showResultsImmediately},
          "maxAttempts": ${settings.maxAttempts},
          "requirePassword": ${settings.requirePassword},
          "examPassword": ${settings.examPassword.map(p => s""""$p"""").getOrElse("null")}
        }
      """.trim).getOrElse("{}")
      
      examParams = List(
        SqlParameter("uuid", examId),
        SqlParameter("string", examData.title),
        SqlParameter("string", examData.description.getOrElse("")),
        SqlParameter("string", examData.subject),
        SqlParameter("timestamp", java.sql.Timestamp.from(startTime)),
        SqlParameter("timestamp", java.sql.Timestamp.from(endTime)),
        SqlParameter("int", examData.duration),
        SqlParameter("bigdecimal", totalScore),
        SqlParameter("int", examData.questions.length),
        SqlParameter("string", examData.instructions.getOrElse("")),
        SqlParameter("jsonb", settingsJson),
        SqlParameter("uuid", createdBy),
        SqlParameter("timestamp", java.sql.Timestamp.from(now)),
        SqlParameter("timestamp", java.sql.Timestamp.from(now))
      )
      
      _ <- DatabaseManager.executeUpdate(insertExamSql, examParams)
      _ <- if (examData.questions.nonEmpty) insertQuestions(examId, examData.questions) else IO.unit
      _ <- if (examData.questions.nonEmpty) insertQuestionScores(examId, examData.questions) else IO.unit
      examOpt <- getExamById(examId)
    } yield examOpt.getOrElse(throw new RuntimeException("Failed to create exam"))
  }

  def updateExam(examId: String, examData: UpdateExamRequest): IO[Option[Exam]] = {
    val updateFields = List(
      examData.title.map(_ => "title = ?"),
      examData.description.map(_ => "description = ?"),
      examData.subject.map(_ => "subject = ?"),
      examData.startTime.map(_ => "start_time = ?"),
      examData.endTime.map(_ => "end_time = ?"),
      examData.duration.map(_ => "duration = ?"),
      examData.instructions.map(_ => "instructions = ?")
    ).flatten
    
    if (updateFields.isEmpty) {
      getExamById(examId)
    } else {
      val sql = s"""
        UPDATE exams 
        SET ${updateFields.mkString(", ")}, updated_at = CURRENT_TIMESTAMP 
        WHERE id = ?
      """
      
      val params = List(
        examData.title.map(SqlParameter("string", _)),
        examData.description.map(SqlParameter("string", _)),
        examData.subject.map(SqlParameter("string", _)),
        examData.startTime.map(t => SqlParameter("timestamp", t.toString)),
        examData.endTime.map(t => SqlParameter("timestamp", t.toString)),
        examData.duration.map(SqlParameter("int", _)),
        examData.instructions.map(SqlParameter("string", _))
      ).flatten :+ SqlParameter("uuid", examId)
      
      for {
        _ <- DatabaseManager.executeUpdate(sql, params)
        examOpt <- getExamById(examId)
      } yield examOpt
    }
  }

  def deleteExam(examId: String): IO[Boolean] = {
    val sql = "DELETE FROM exams WHERE id = ?"
    val params = List(SqlParameter("uuid", examId))
    
    DatabaseManager.executeUpdate(sql, params).map(_ > 0)
  }

  def publishExam(examId: String): IO[Option[Exam]] = {
    updateExamStatus(examId, ExamStatus.published)
  }

  def unpublishExam(examId: String): IO[Option[Exam]] = {
    updateExamStatus(examId, ExamStatus.draft)
  }

  def setQuestionScores(examId: String, scores: List[QuestionScore]): IO[List[QuestionScore]] = {
    val deleteOldScoresSql = "DELETE FROM question_scores WHERE exam_id = ?"
    val deleteParams = List(SqlParameter("uuid", examId))
    
    val insertOperations = scores.map { score =>
      val sql = """
        INSERT INTO question_scores (exam_id, question_number, max_score, partial_scoring, scoring_criteria)
        VALUES (?, ?, ?, ?, ?)
      """
      val params = List(
        SqlParameter("uuid", examId),
        SqlParameter("int", score.questionNumber),
        SqlParameter("bigdecimal", score.maxScore),
        SqlParameter("boolean", score.partialScoring.getOrElse(false)),
        SqlParameter("string", score.scoringCriteria.map(_.asJson.noSpaces).getOrElse("[]"))
      )
      (sql, params)
    }
    
    for {
      _ <- DatabaseManager.executeUpdate(deleteOldScoresSql, deleteParams)
      _ <- DatabaseManager.executeTransaction(insertOperations)
      // Update total score in exams table
      totalScore = scores.map(_.maxScore).sum
      _ <- updateExamTotalScore(examId, totalScore)
    } yield scores
  }

  // Helper methods
  private def getExamById(examId: String): IO[Option[Exam]] = {
    val sql = """
      SELECT id, title, description, subject, start_time, end_time, duration, 
             status, total_score, question_count, created_at, updated_at
      FROM exams WHERE id = ?
    """
    val params = List(SqlParameter("uuid", examId))
    
    DatabaseManager.executeQuery(sql, params).map { results =>
      results.headOption.flatMap(jsonToExam)
    }
  }

  private def getQuestionsByExamId(examId: String): IO[List[Question]] = {
    val sql = """
      SELECT question_number, content, question_type, score, options, correct_answer
      FROM questions 
      WHERE exam_id = ?
      ORDER BY question_number
    """
    val params = List(SqlParameter("uuid", examId))
    
    DatabaseManager.executeQuery(sql, params).map { results =>
      results.flatMap(jsonToQuestion)
    }
  }

  private def getExamFiles(examId: String): IO[List[ExamFile]] = {
    val sql = """
      SELECT id, exam_id, file_name, file_path, file_type, file_size, 
             mime_type, uploaded_by, uploaded_at
      FROM exam_files 
      WHERE exam_id = ?
      ORDER BY uploaded_at DESC
    """
    val params = List(SqlParameter("uuid", examId))
    
    DatabaseManager.executeQuery(sql, params).map { results =>
      results.flatMap(jsonToExamFile)
    }
  }

  private def getExamSettings(examId: String): IO[Option[ExamSettings]] = {
    val sql = "SELECT settings FROM exams WHERE id = ?"
    val params = List(SqlParameter("uuid", examId))
    
    DatabaseManager.executeQuery(sql, params).map { results =>
      results.headOption.flatMap { json =>
        json.hcursor.get[Json]("settings").toOption.flatMap { settingsJson =>
          settingsJson.as[ExamSettings].toOption
        }
      }
    }
  }

  private def getExamStatistics(examId: String): IO[ExamStats] = {
    // Mock implementation - would integrate with actual submission/scoring services
    IO.pure(ExamStats(
      totalParticipants = 0,
      submittedCount = 0,
      averageScore = BigDecimal(0),
      highestScore = BigDecimal(0),
      lowestScore = BigDecimal(0),
      passRate = BigDecimal(0)
    ))
  }

  private def getGradingInfo(examId: String): IO[GradingInfo] = {
    // Mock implementation - would integrate with grading service
    IO.pure(GradingInfo(
      totalStudents = 0,
      gradedStudents = 0,
      pendingGrading = 0,
      averageScore = None
    ))
  }

  private def getGradingProgress(examId: String): IO[GradingProgress] = {
    // Mock implementation - would integrate with grading service
    IO.pure(GradingProgress(
      completed = 0,
      total = 0,
      percentage = BigDecimal(0)
    ))
  }

  private def getExamGradingProgressData(examId: String): IO[ExamGradingProgress] = {
    // Mock implementation - would integrate with grading service
    IO.pure(ExamGradingProgress(
      examId = examId,
      totalSubmissions = 0,
      gradedSubmissions = 0,
      pendingSubmissions = 0,
      progressPercentage = BigDecimal(0)
    ))
  }

  private def getGradingStatsData(examId: String): IO[GradingStats] = {
    // Mock implementation - would integrate with grading service
    IO.pure(GradingStats(
      averageGradingTime = None,
      gradersAssigned = 0,
      questionsGraded = 0,
      totalQuestions = 0
    ))
  }

  private def insertQuestions(examId: String, questions: List[CreateQuestionRequest]): IO[Unit] = {
    val operations = questions.map { question =>
      val sql = """
        INSERT INTO questions (exam_id, question_number, content, question_type, score, options, correct_answer)
        VALUES (?, ?, ?, ?::question_type, ?, ?, ?)
      """
      val params = List(
        SqlParameter("uuid", examId),
        SqlParameter("int", question.number),
        SqlParameter("string", question.content),
        SqlParameter("string", question.questionType.toString),
        SqlParameter("bigdecimal", question.score),
        SqlParameter("jsonb", question.options.map(_.asJson.noSpaces).getOrElse("null")),
        SqlParameter("string", question.correctAnswer.getOrElse(""))
      )
      (sql, params)
    }
    
    DatabaseManager.executeTransaction(operations).void
  }

  private def insertQuestionScores(examId: String, questions: List[CreateQuestionRequest]): IO[Unit] = {
    val operations = questions.map { question =>
      val sql = """
        INSERT INTO question_scores (exam_id, question_number, max_score, partial_scoring)
        VALUES (?, ?, ?, ?)
      """
      val params = List(
        SqlParameter("uuid", examId),
        SqlParameter("int", question.number),
        SqlParameter("bigdecimal", question.score),
        SqlParameter("boolean", false)
      )
      (sql, params)
    }
    
    DatabaseManager.executeTransaction(operations).void
  }

  private def updateExamStatus(examId: String, status: ExamStatus): IO[Option[Exam]] = {
    val sql = "UPDATE exams SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?"
    val params = List(
      SqlParameter("string", status.toString),
      SqlParameter("uuid", examId)
    )
    
    for {
      _ <- DatabaseManager.executeUpdate(sql, params)
      examOpt <- getExamById(examId)
    } yield examOpt
  }

  private def updateExamTotalScore(examId: String, totalScore: BigDecimal): IO[Unit] = {
    val sql = "UPDATE exams SET total_score = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?"
    val params = List(
      SqlParameter("bigdecimal", totalScore),
      SqlParameter("uuid", examId)
    )
    
    DatabaseManager.executeUpdate(sql, params).void
  }

  // JSON conversion helpers
  private def jsonToExam(json: Json): Option[Exam] = {
    val cursor = json.hcursor
    for {
      id <- cursor.get[String]("id").toOption
      title <- cursor.get[String]("title").toOption
      description = cursor.get[String]("description").toOption
      subject <- cursor.get[String]("subject").toOption
      startTime <- cursor.get[String]("start_time").toOption.flatMap(s => scala.util.Try(Instant.parse(s)).toOption)
      endTime <- cursor.get[String]("end_time").toOption.flatMap(s => scala.util.Try(Instant.parse(s)).toOption)
      duration <- cursor.get[Int]("duration").toOption
      status <- cursor.get[String]("status").toOption.flatMap(s => scala.util.Try(ExamStatus.valueOf(s)).toOption)
      totalScore <- cursor.get[BigDecimal]("total_score").toOption
      questionCount <- cursor.get[Int]("question_count").toOption
      createdAt <- cursor.get[String]("created_at").toOption.flatMap(s => scala.util.Try(Instant.parse(s)).toOption)
      updatedAt <- cursor.get[String]("updated_at").toOption.flatMap(s => scala.util.Try(Instant.parse(s)).toOption)
    } yield Exam(id, title, description, subject, startTime, endTime, duration, status, totalScore, questionCount, createdAt, updatedAt)
  }

  private def jsonToQuestion(json: Json): Option[Question] = {
    val cursor = json.hcursor
    for {
      number <- cursor.get[Int]("question_number").toOption
      content <- cursor.get[String]("content").toOption
      questionType <- cursor.get[String]("question_type").toOption.flatMap(s => scala.util.Try(QuestionType.valueOf(s)).toOption)
      score <- cursor.get[BigDecimal]("score").toOption
      options = cursor.get[String]("options").toOption.flatMap(s => parse(s).toOption.flatMap(_.as[List[String]].toOption))
      correctAnswer = cursor.get[String]("correct_answer").toOption.filter(_.nonEmpty)
    } yield Question(number, content, questionType, score, options, correctAnswer)
  }

  private def jsonToQuestionScore(json: Json): Option[QuestionScore] = {
    val cursor = json.hcursor
    for {
      questionNumber <- cursor.get[Int]("question_number").toOption
      maxScore <- cursor.get[BigDecimal]("max_score").toOption
      partialScoring = cursor.get[Boolean]("partial_scoring").toOption
      scoringCriteria = cursor.get[String]("scoring_criteria").toOption.flatMap(s => parse(s).toOption.flatMap(_.as[List[ScoringCriteria]].toOption))
    } yield QuestionScore(questionNumber, maxScore, partialScoring, scoringCriteria)
  }

  private def jsonToExamFile(json: Json): Option[ExamFile] = {
    val cursor = json.hcursor
    for {
      id <- cursor.get[String]("id").toOption
      examId <- cursor.get[String]("exam_id").toOption
      fileName <- cursor.get[String]("file_name").toOption
      filePath <- cursor.get[String]("file_path").toOption
      fileType <- cursor.get[String]("file_type").toOption.flatMap(s => scala.util.Try(FileType.valueOf(s)).toOption)
      fileSize <- cursor.get[Long]("file_size").toOption
      mimeType = cursor.get[String]("mime_type").toOption
      uploadedBy <- cursor.get[String]("uploaded_by").toOption
      uploadedAt <- cursor.get[String]("uploaded_at").toOption.flatMap(s => scala.util.Try(Instant.parse(s)).toOption)
    } yield ExamFile(id, examId, fileName, filePath, fileType, fileSize, mimeType, uploadedBy, uploadedAt)
  }

  private def jsonToGraderExam(json: Json): Option[GraderExam] = {
    val cursor = json.hcursor
    for {
      id <- cursor.get[String]("id").toOption
      title <- cursor.get[String]("title").toOption
      subject <- cursor.get[String]("subject").toOption
      status <- cursor.get[String]("status").toOption.flatMap(s => scala.util.Try(ExamStatus.valueOf(s)).toOption)
      totalScore <- cursor.get[BigDecimal]("total_score").toOption
      questionCount <- cursor.get[Int]("question_count").toOption
      endTime <- cursor.get[String]("end_time").toOption.flatMap(s => scala.util.Try(Instant.parse(s)).toOption)
    } yield GraderExam(id, title, subject, status, totalScore, questionCount, 0, 0, endTime) // participant and graded counts would come from other services
  }

  private def jsonToAdminExam(json: Json): Option[AdminExam] = {
    val cursor = json.hcursor
    for {
      id <- cursor.get[String]("id").toOption
      title <- cursor.get[String]("title").toOption
      description = cursor.get[String]("description").toOption
      subject <- cursor.get[String]("subject").toOption
      startTime <- cursor.get[String]("start_time").toOption.flatMap(s => scala.util.Try(Instant.parse(s)).toOption)
      endTime <- cursor.get[String]("end_time").toOption.flatMap(s => scala.util.Try(Instant.parse(s)).toOption)
      duration <- cursor.get[Int]("duration").toOption
      status <- cursor.get[String]("status").toOption.flatMap(s => scala.util.Try(ExamStatus.valueOf(s)).toOption)
      totalScore <- cursor.get[BigDecimal]("total_score").toOption
      questionCount <- cursor.get[Int]("question_count").toOption
      participantCount <- cursor.get[Int]("participant_count").toOption
      submissionCount <- cursor.get[Int]("submission_count").toOption
      createdBy <- cursor.get[String]("created_by").toOption
      createdAt <- cursor.get[String]("created_at").toOption.flatMap(s => scala.util.Try(Instant.parse(s)).toOption)
      updatedAt <- cursor.get[String]("updated_at").toOption.flatMap(s => scala.util.Try(Instant.parse(s)).toOption)
    } yield AdminExam(id, title, description, subject, startTime, endTime, duration, status, totalScore, questionCount, participantCount, submissionCount, createdBy, createdAt, updatedAt)
  }
}
