package Services

import cats.effect.IO
import Models.*
import Database.DatabaseUtils
import java.time.LocalDateTime
import java.util.UUID
import java.sql.ResultSet
import org.slf4j.LoggerFactory

trait ExamService {
  def getExamList(): IO[List[ExamListResponse]]
  def getExamsForAdmin(): IO[List[ExamListResponse]]
  def getExamById(examId: String): IO[Option[Exam]]
  def createExam(request: CreateExamRequest, createdBy: String): IO[Exam]
  def updateExam(examId: String, request: UpdateExamRequest): IO[Option[Exam]]
  def deleteExam(examId: String): IO[Boolean]
  def publishExam(examId: String, request: PublishExamRequest): IO[Boolean]
  def unpublishExam(examId: String): IO[Boolean]
  def getExamsByStatus(status: ExamStatus): IO[List[ExamListResponse]]
  def getExamsByUser(username: String): IO[List[ExamListResponse]]
  def reserveExamId(reservedBy: String): IO[String]
  def deleteReservedExamId(examId: String, deletedBy: String): IO[Boolean]
  def isExamIdReserved(examId: String): IO[Boolean]
  def saveExamFile(
    examId: String,
    fileId: String,
    fileName: String,
    originalName: String,
    fileUrl: String,
    fileSize: Long,
    fileType: String,
    mimeType: String,
    uploadedBy: String
  ): IO[Boolean]
}

class ExamServiceImpl extends ExamService {
  private val logger = LoggerFactory.getLogger("ExamService")

  override def getExamList(): IO[List[ExamListResponse]] = {
    val sql = """
      SELECT e.id, e.title, e.description, e.start_time, e.end_time, e.status, 
             e.total_questions, e.duration, e.created_at, e.created_by,
             qf.file_url as question_file_url, qf.file_name as question_file_name,
             qf.file_size as question_file_size, qf.upload_time as question_upload_time,
             qf.mime_type as question_mime_type,
             af.file_url as answer_file_url, af.file_name as answer_file_name,
             af.file_size as answer_file_size, af.upload_time as answer_upload_time,
             af.mime_type as answer_mime_type,
             asf.file_url as answer_sheet_file_url, asf.file_name as answer_sheet_file_name,
             asf.file_size as answer_sheet_file_size, asf.upload_time as answer_sheet_upload_time,
             asf.mime_type as answer_sheet_mime_type
      FROM exams e
      LEFT JOIN exam_files qf ON e.id = qf.exam_id AND qf.file_type = 'question'
      LEFT JOIN exam_files af ON e.id = af.exam_id AND af.file_type = 'answer'
      LEFT JOIN exam_files asf ON e.id = asf.exam_id AND asf.file_type = 'answerSheet'
      ORDER BY e.created_at DESC
    """

    DatabaseUtils.executeQuery(sql)(parseExamListResponse)
  }

  override def getExamsForAdmin(): IO[List[ExamListResponse]] = {
    val sql = """
      SELECT e.id, e.title, e.description, e.start_time, e.end_time, e.status, 
             e.total_questions, e.duration, e.created_at, e.created_by, e.max_score, e.total_score,
             qf.file_url as question_file_url, qf.file_name as question_file_name,
             qf.file_size as question_file_size, qf.upload_time as question_upload_time,
             qf.mime_type as question_mime_type,
             af.file_url as answer_file_url, af.file_name as answer_file_name,
             af.file_size as answer_file_size, af.upload_time as answer_upload_time,
             af.mime_type as answer_mime_type,
             asf.file_url as answer_sheet_file_url, asf.file_name as answer_sheet_file_name,
             asf.file_size as answer_sheet_file_size, asf.upload_time as answer_sheet_upload_time,
             asf.mime_type as answer_sheet_mime_type
      FROM exams e
      LEFT JOIN exam_files qf ON e.id = qf.exam_id AND qf.file_type = 'question'
      LEFT JOIN exam_files af ON e.id = af.exam_id AND af.file_type = 'answer'
      LEFT JOIN exam_files asf ON e.id = asf.exam_id AND asf.file_type = 'answerSheet'
      ORDER BY e.created_at DESC
    """

    DatabaseUtils.executeQuery(sql)(parseExamListResponse)
  }

  override def getExamById(examId: String): IO[Option[Exam]] = {
    val sql = """
      SELECT e.id, e.title, e.description, e.start_time, e.end_time, e.status, 
             e.created_at, e.updated_at, e.created_by, e.duration, e.total_questions,
             e.max_score, e.total_score, e.subject, e.instructions,
             qf.file_url as question_file_url, qf.file_name as question_file_name,
             qf.file_size as question_file_size, qf.upload_time as question_upload_time,
             qf.mime_type as question_mime_type,
             af.file_url as answer_file_url, af.file_name as answer_file_name,
             af.file_size as answer_file_size, af.upload_time as answer_upload_time,
             af.mime_type as answer_mime_type,
             asf.file_url as answer_sheet_file_url, asf.file_name as answer_sheet_file_name,
             asf.file_size as answer_sheet_file_size, asf.upload_time as answer_sheet_upload_time,
             asf.mime_type as answer_sheet_mime_type
      FROM exams e
      LEFT JOIN exam_files qf ON e.id = qf.exam_id AND qf.file_type = 'question'
      LEFT JOIN exam_files af ON e.id = af.exam_id AND af.file_type = 'answer'
      LEFT JOIN exam_files asf ON e.id = asf.exam_id AND asf.file_type = 'answerSheet'
      WHERE e.id = ?::uuid
    """

    DatabaseUtils.executeQuerySingle(sql, List(examId))(parseExam)
  }

  override def createExam(request: CreateExamRequest, createdBy: String): IO[Exam] = {
    val sql = """
      INSERT INTO exams (title, description, start_time, end_time, status, created_by, duration, total_questions)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    """

    val params = List(
      request.title,
      request.description,
      request.startTime,
      request.endTime,
      request.status.value,
      createdBy,
      request.duration,
      request.totalQuestions
    )

    for {
      examId <- DatabaseUtils.executeInsertWithId(sql, params)
      // If questions are provided, set their scores
      _ <- request.questions match {
        case Some(questions) =>
          val questionService = new QuestionServiceImpl()
          val scoreRequest = SetQuestionScoresRequest(
            questions.map(q => QuestionScoreRequest(q.number, q.score))
          )
          questionService.setQuestionScores(examId, scoreRequest)
        case None => IO.unit
      }
      exam <- getExamById(examId).map(_.getOrElse(throw new RuntimeException("Failed to retrieve created exam")))
    } yield exam
  }

  override def updateExam(examId: String, request: UpdateExamRequest): IO[Option[Exam]] = {
    val updateFields = scala.collection.mutable.ListBuffer[String]()
    val updateParams = scala.collection.mutable.ListBuffer[Any]()

    request.title.foreach { title =>
      updateFields += "title = ?"
      updateParams += title
    }

    request.description.foreach { description =>
      updateFields += "description = ?"
      updateParams += description
    }

    request.startTime.foreach { startTime =>
      updateFields += "start_time = ?"
      updateParams += startTime
    }

    request.endTime.foreach { endTime =>
      updateFields += "end_time = ?"
      updateParams += endTime
    }

    request.duration.foreach { duration =>
      updateFields += "duration = ?"
      updateParams += duration
    }

    request.totalQuestions.foreach { totalQuestions =>
      updateFields += "total_questions = ?"
      updateParams += totalQuestions
    }

    if (updateFields.nonEmpty) {
      val sql = s"UPDATE exams SET ${updateFields.mkString(", ")}, updated_at = CURRENT_TIMESTAMP WHERE id = ?::uuid"
      updateParams += examId

      for {
        _ <- DatabaseUtils.executeUpdate(sql, updateParams.toList)
        exam <- getExamById(examId)
      } yield exam
    } else {
      IO.pure(None)
    }
  }

  override def deleteExam(examId: String): IO[Boolean] = {
    val sql = "DELETE FROM exams WHERE id = ?::uuid"
    DatabaseUtils.executeUpdate(sql, List(examId)).map(_ > 0)
  }

  override def publishExam(examId: String, request: PublishExamRequest): IO[Boolean] = {
    val updateFields = scala.collection.mutable.ListBuffer[String]()
    val updateParams = scala.collection.mutable.ListBuffer[Any]()

    updateFields += "status = ?"
    updateParams += ExamStatus.Published.value

    // File associations are managed through the exam_files table
    // No need to update foreign key columns on exams table
    
    val sql = s"UPDATE exams SET ${updateFields.mkString(", ")}, updated_at = CURRENT_TIMESTAMP WHERE id = ?::uuid"
    updateParams += examId

    DatabaseUtils.executeUpdate(sql, updateParams.toList).map(_ > 0)
  }

  override def unpublishExam(examId: String): IO[Boolean] = {
    val sql = "UPDATE exams SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?::uuid"
    DatabaseUtils.executeUpdate(sql, List(ExamStatus.Draft.value, examId)).map(_ > 0)
  }

  override def getExamsByStatus(status: ExamStatus): IO[List[ExamListResponse]] = {
    val sql = """
      SELECT e.id, e.title, e.description, e.start_time, e.end_time, e.status, 
             e.total_questions, e.duration, e.created_at, e.created_by,
             qf.file_url as question_file_url, qf.file_name as question_file_name,
             qf.file_size as question_file_size, qf.upload_time as question_upload_time,
             qf.mime_type as question_mime_type,
             af.file_url as answer_file_url, af.file_name as answer_file_name,
             af.file_size as answer_file_size, af.upload_time as answer_upload_time,
             af.mime_type as answer_mime_type,
             asf.file_url as answer_sheet_file_url, asf.file_name as answer_sheet_file_name,
             asf.file_size as answer_sheet_file_size, asf.upload_time as answer_sheet_upload_time,
             asf.mime_type as answer_sheet_mime_type
      FROM exams e
      LEFT JOIN exam_files qf ON e.id = qf.exam_id AND qf.file_type = 'question'
      LEFT JOIN exam_files af ON e.id = af.exam_id AND af.file_type = 'answer'
      LEFT JOIN exam_files asf ON e.id = asf.exam_id AND asf.file_type = 'answerSheet'
      WHERE e.status = ?
      ORDER BY e.created_at DESC
    """

    DatabaseUtils.executeQuery(sql, List(status.value))(parseExamListResponse)
  }

  override def getExamsByUser(username: String): IO[List[ExamListResponse]] = {
    val sql = """
      SELECT e.id, e.title, e.description, e.start_time, e.end_time, e.status, 
             e.total_questions, e.duration, e.created_at, e.created_by,
             qf.file_url as question_file_url, qf.file_name as question_file_name,
             qf.file_size as question_file_size, qf.upload_time as question_upload_time,
             qf.mime_type as question_mime_type,
             af.file_url as answer_file_url, af.file_name as answer_file_name,
             af.file_size as answer_file_size, af.upload_time as answer_upload_time,
             af.mime_type as answer_mime_type,
             asf.file_url as answer_sheet_file_url, asf.file_name as answer_sheet_file_name,
             asf.file_size as answer_sheet_file_size, asf.upload_time as answer_sheet_upload_time,
             asf.mime_type as answer_sheet_mime_type
      FROM exams e
      LEFT JOIN exam_files qf ON e.id = qf.exam_id AND qf.file_type = 'question'
      LEFT JOIN exam_files af ON e.id = af.exam_id AND af.file_type = 'answer'
      LEFT JOIN exam_files asf ON e.id = asf.exam_id AND asf.file_type = 'answerSheet'
      WHERE e.created_by = ?
      ORDER BY e.created_at DESC
    """

    DatabaseUtils.executeQuery(sql, List(username))(parseExamListResponse)
  }

  override def reserveExamId(reservedBy: String): IO[String] = {
    val examId = UUID.randomUUID().toString
    val sql = """
      INSERT INTO reserved_exam_ids (exam_id, reserved_by, reserved_at, expires_at)
      VALUES (?::uuid, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '24 hours')
    """
    
    for {
      _ <- DatabaseUtils.executeUpdate(sql, List(examId, reservedBy))
    } yield examId
  }

  override def deleteReservedExamId(examId: String, deletedBy: String): IO[Boolean] = {
    val sql = """
      DELETE FROM reserved_exam_ids 
      WHERE exam_id = ?::uuid AND reserved_by = ? AND is_used = FALSE
    """
    
    for {
      rowsAffected <- DatabaseUtils.executeUpdate(sql, List(examId, deletedBy))
    } yield rowsAffected > 0
  }

  override def isExamIdReserved(examId: String): IO[Boolean] = {
    val sql = """
      SELECT COUNT(*) as count FROM reserved_exam_ids 
      WHERE exam_id = ?::uuid AND expires_at > CURRENT_TIMESTAMP AND is_used = FALSE
    """
    
    DatabaseUtils.executeQuerySingle(sql, List(examId)) { rs =>
      rs.getInt("count") > 0
    }.map(_.getOrElse(false))
  }

  private def parseExamListResponse(rs: ResultSet): ExamListResponse = {
    ExamListResponse(
      id = rs.getString("id"),
      title = rs.getString("title"),
      description = rs.getString("description"),
      questionFile = Option(rs.getString("question_file_url")).map { url =>
        ExamFile(
          id = UUID.randomUUID().toString,
          name = rs.getString("question_file_name"),
          url = url,
          size = rs.getLong("question_file_size"),
          uploadTime = rs.getTimestamp("question_upload_time").toLocalDateTime,
          mimetype = Option(rs.getString("question_mime_type"))
        )
      },
      answerFile = Option(rs.getString("answer_file_url")).map { url =>
        ExamFile(
          id = UUID.randomUUID().toString,
          name = rs.getString("answer_file_name"),
          url = url,
          size = rs.getLong("answer_file_size"),
          uploadTime = rs.getTimestamp("answer_upload_time").toLocalDateTime,
          mimetype = Option(rs.getString("answer_mime_type"))
        )
      },
      answerSheetFile = Option(rs.getString("answer_sheet_file_url")).map { url =>
        ExamFile(
          id = UUID.randomUUID().toString,
          name = rs.getString("answer_sheet_file_name"),
          url = url,
          size = rs.getLong("answer_sheet_file_size"),
          uploadTime = rs.getTimestamp("answer_sheet_upload_time").toLocalDateTime,
          mimetype = Option(rs.getString("answer_sheet_mime_type"))
        )
      },
      startTime = rs.getTimestamp("start_time").toLocalDateTime,
      endTime = rs.getTimestamp("end_time").toLocalDateTime,
      status = ExamStatus.fromString(rs.getString("status")),
      totalQuestions = Option(rs.getInt("total_questions")).filter(_ != 0),
      duration = Option(rs.getInt("duration")).filter(_ != 0),
      createdAt = rs.getTimestamp("created_at").toLocalDateTime,
      createdBy = rs.getString("created_by")
    )
  }

  private def parseExam(rs: ResultSet): Exam = {
    Exam(
      id = rs.getString("id"),
      title = rs.getString("title"),
      description = rs.getString("description"),
      startTime = rs.getTimestamp("start_time").toLocalDateTime,
      endTime = rs.getTimestamp("end_time").toLocalDateTime,
      status = ExamStatus.fromString(rs.getString("status")),
      createdAt = rs.getTimestamp("created_at").toLocalDateTime,
      updatedAt = rs.getTimestamp("updated_at").toLocalDateTime,
      duration = Option(rs.getInt("duration")).filter(_ != 0),
      questionFile = Option(rs.getString("question_file_url")).map { url =>
        ExamFile(
          id = UUID.randomUUID().toString,
          name = rs.getString("question_file_name"),
          url = url,
          size = rs.getLong("question_file_size"),
          uploadTime = rs.getTimestamp("question_upload_time").toLocalDateTime,
          mimetype = Option(rs.getString("question_mime_type"))
        )
      },
      answerFile = Option(rs.getString("answer_file_url")).map { url =>
        ExamFile(
          id = UUID.randomUUID().toString,
          name = rs.getString("answer_file_name"),
          url = url,
          size = rs.getLong("answer_file_size"),
          uploadTime = rs.getTimestamp("answer_upload_time").toLocalDateTime,
          mimetype = Option(rs.getString("answer_mime_type"))
        )
      },
      answerSheetFile = Option(rs.getString("answer_sheet_file_url")).map { url =>
        ExamFile(
          id = UUID.randomUUID().toString,
          name = rs.getString("answer_sheet_file_name"),
          url = url,
          size = rs.getLong("answer_sheet_file_size"),
          uploadTime = rs.getTimestamp("answer_sheet_upload_time").toLocalDateTime,
          mimetype = Option(rs.getString("answer_sheet_mime_type"))
        )
      },
      createdBy = rs.getString("created_by"),
      totalQuestions = Option(rs.getInt("total_questions")).filter(_ != 0),
      maxScore = Option(rs.getDouble("max_score")).filter(_ != 0.0),
      totalScore = Option(rs.getDouble("total_score")).filter(_ != 0.0),
      subject = Option(rs.getString("subject")).filter(_.nonEmpty),
      instructions = Option(rs.getString("instructions")).filter(_.nonEmpty)
    )
  }

  def saveExamFile(
    examId: String,
    fileId: String,
    fileName: String,
    originalName: String,
    fileUrl: String,
    fileSize: Long,
    fileType: String,
    mimeType: String,
    uploadedBy: String
  ): IO[Boolean] = {
    logger.info(s"Saving exam file: examId=$examId, fileId=$fileId, fileName=$fileName, fileType=$fileType")
    
    // First, delete any existing file of the same type for this exam
    val deleteSql = """
      DELETE FROM exam_files 
      WHERE exam_id = ?::uuid AND file_type = ?
    """
    
    val insertSql = """
      INSERT INTO exam_files (exam_id, file_id, file_name, original_name, file_url, file_size, file_type, mime_type, uploaded_by)
      VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?, ?)
    """

    val deleteParams = List(examId, fileType)
    val insertParams = List(
      examId,
      fileId,
      fileName,
      originalName,
      fileUrl,
      fileSize,
      fileType,
      mimeType,
      uploadedBy
    )

    logger.info(s"Executing delete with params: $deleteParams")
    logger.info(s"Executing insert with params: $insertParams")

    (for {
      deleteResult <- DatabaseUtils.executeUpdate(deleteSql, deleteParams)
      _ = logger.info(s"Delete result: $deleteResult rows affected")
      insertResult <- DatabaseUtils.executeUpdate(insertSql, insertParams)
      _ = logger.info(s"Insert result: $insertResult rows affected")
    } yield insertResult > 0).handleErrorWith { error =>
      logger.error(s"Error saving exam file: ${error.getMessage}", error)
      IO.raiseError(error)
    }
  }
}
