package Database

import cats.effect.IO
import cats.implicits.*
import Models.*
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.sql.{ResultSet, Timestamp}

trait FileRepository {
  def createFile(file: FileRecord): IO[Unit]
  def getFileById(fileId: String): IO[Option[FileRecord]]
  def updateFile(file: FileRecord): IO[Boolean]
  def deleteFile(fileId: String): IO[Boolean]
  def listFiles(
    category: Option[String] = None,
    examId: Option[String] = None,
    fileType: Option[String] = None,
    uploadedBy: Option[String] = None,
    page: Int = 1,
    limit: Int = 20
  ): IO[(List[FileRecord], Int)]
  def getGradingImages(examId: String, studentId: Option[String] = None, questionNumber: Option[Int] = None): IO[List[FileRecord]]
}

class FileRepositoryImpl extends FileRepository {
  private val logger = LoggerFactory.getLogger("FileRepository")

  private def mapResultSetToFileRecord(rs: ResultSet): FileRecord = {
    FileRecord(
      id = rs.getString("id"),
      fileName = rs.getString("file_name"),
      originalName = rs.getString("original_name"),
      fileUrl = rs.getString("file_url"),
      fileSize = rs.getLong("file_size"),
      mimeType = rs.getString("mime_type"),
      fileType = Option(rs.getString("file_type")),
      category = Option(rs.getString("category")),
      examId = Option(rs.getString("exam_id")),
      questionNumber = Option(rs.getInt("question_number")).filter(_ != 0),
      studentId = Option(rs.getString("student_id")),
      uploadedBy = rs.getString("uploaded_by"),
      uploadTime = rs.getTimestamp("upload_time").toLocalDateTime
    )
  }

  override def createFile(file: FileRecord): IO[Unit] = {
    val query = """
      INSERT INTO files (
        id, file_name, original_name, file_url, file_size, mime_type, 
        file_type, category, exam_id, question_number, student_id, 
        uploaded_by, upload_time
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """
    
    val params = List(
      file.id,
      file.fileName,
      file.originalName,
      file.fileUrl,
      file.fileSize,
      file.mimeType,
      file.fileType.orNull,
      file.category.orNull,
      file.examId.orNull,
      file.questionNumber.map(Integer.valueOf).orNull,
      file.studentId.orNull,
      file.uploadedBy,
      java.sql.Timestamp.valueOf(file.uploadTime)
    )

    DatabaseManager.executeUpdate(query, params).map { rowsAffected =>
      if (rowsAffected > 0) {
        logger.info(s"File record created successfully: ${file.id}")
      } else {
        throw new RuntimeException("Failed to create file record")
      }
    }
  }

  override def getFileById(fileId: String): IO[Option[FileRecord]] = {
    val query = "SELECT * FROM files WHERE id = ?"
    
    DatabaseManager.executeQuery(query, List(fileId))(mapResultSetToFileRecord)
      .map(_.headOption)
  }

  override def updateFile(file: FileRecord): IO[Boolean] = {
    val query = """
      UPDATE files SET 
        file_name = ?, original_name = ?, file_url = ?, file_size = ?, 
        mime_type = ?, file_type = ?, category = ?, exam_id = ?, 
        question_number = ?, student_id = ?, uploaded_by = ?
      WHERE id = ?
    """
    
    val params = List(
      file.fileName,
      file.originalName,
      file.fileUrl,
      file.fileSize,
      file.mimeType,
      file.fileType.orNull,
      file.category.orNull,
      file.examId.orNull,
      file.questionNumber.map(Integer.valueOf).orNull,
      file.studentId.orNull,
      file.uploadedBy,
      file.id
    )

    DatabaseManager.executeUpdate(query, params).map(_ > 0)
  }

  override def deleteFile(fileId: String): IO[Boolean] = {
    val query = "DELETE FROM files WHERE id = ?"
    
    DatabaseManager.executeUpdate(query, List(fileId)).map(_ > 0)
  }

  override def listFiles(
    category: Option[String] = None,
    examId: Option[String] = None,
    fileType: Option[String] = None,
    uploadedBy: Option[String] = None,
    page: Int = 1,
    limit: Int = 20
  ): IO[(List[FileRecord], Int)] = {
    val offset = (page - 1) * limit
    
    val conditions = List(
      category.map(_ => "category = ?"),
      examId.map(_ => "exam_id = ?"),
      fileType.map(_ => "file_type = ?"),
      uploadedBy.map(_ => "uploaded_by = ?")
    ).flatten
    
    val whereClause = if (conditions.nonEmpty) s"WHERE ${conditions.mkString(" AND ")}" else ""
    
    val params = List(
      category,
      examId,
      fileType,
      uploadedBy
    ).flatten
    
    val countQuery = s"SELECT COUNT(*) FROM files $whereClause"
    val dataQuery = s"SELECT * FROM files $whereClause ORDER BY upload_time DESC LIMIT $limit OFFSET $offset"
    
    for {
      total <- DatabaseManager.executeQuery(countQuery, params)(_.getInt(1)).map(_.headOption.getOrElse(0))
      files <- DatabaseManager.executeQuery(dataQuery, params)(mapResultSetToFileRecord)
    } yield (files, total)
  }

  override def getGradingImages(
    examId: String, 
    studentId: Option[String] = None, 
    questionNumber: Option[Int] = None
  ): IO[List[FileRecord]] = {
    val conditions = List(
      Some("exam_id = ?"),
      Some("category = 'answer-image'"),
      studentId.map(_ => "student_id = ?"),
      questionNumber.map(_ => "question_number = ?")
    ).flatten
    
    val whereClause = s"WHERE ${conditions.mkString(" AND ")}"
    val query = s"SELECT * FROM files $whereClause ORDER BY question_number, upload_time"
    
    val params = List(
      Some(examId),
      studentId,
      questionNumber.map(_.toString)
    ).flatten
    
    DatabaseManager.executeQuery(query, params)(mapResultSetToFileRecord)
  }
}
