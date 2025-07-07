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

class GradingImageService {
  private val logger = LoggerFactory.getLogger("GradingImageService")

  def getGradingImages(examId: Long, studentId: Option[Long] = None, questionNumber: Option[Int] = None): IO[List[GradingImage]] = {
    logger.info(s"获取阅卷图片: examId=$examId, studentId=$studentId, questionNumber=$questionNumber")
    
    // 构建动态查询SQL
    val baseSQL = """
      SELECT image_url, file_name, exam_id, student_id, question_number, upload_time
      FROM grading_images 
      WHERE exam_id = ?
    """
    
    val (sql, params) = buildImageQuery(baseSQL, examId, studentId, questionNumber)
    
    DatabaseManager.executeQuery(sql, params).map { rows =>
      rows.map { row =>
        GradingImage(
          imageUrl = DatabaseManager.decodeFieldUnsafe[String](row, "image_url"),
          fileName = DatabaseManager.decodeFieldUnsafe[String](row, "file_name"),
          examId = DatabaseManager.decodeFieldUnsafe[Long](row, "exam_id"),
          studentId = DatabaseManager.decodeFieldUnsafe[Long](row, "student_id"),
          questionNumber = DatabaseManager.decodeFieldUnsafe[Int](row, "question_number"),
          uploadTime = LocalDateTime.parse(DatabaseManager.decodeFieldUnsafe[String](row, "upload_time").take(19))
        )
      }
    }
  }

  private def buildImageQuery(baseSQL: String, examId: Long, studentId: Option[Long], questionNumber: Option[Int]): (String, List[SqlParameter]) = {
    val conditions = scala.collection.mutable.ListBuffer[String]()
    val params = scala.collection.mutable.ListBuffer[SqlParameter]()
    
    // 基础参数
    params += SqlParameter("long", examId)
    
    // 可选的学生ID筛选
    studentId.foreach { id =>
      conditions += "AND student_id = ?"
      params += SqlParameter("long", id)
    }
    
    // 可选的题目编号筛选
    questionNumber.foreach { qn =>
      conditions += "AND question_number = ?"
      params += SqlParameter("int", qn)
    }
    
    val finalSQL = if (conditions.nonEmpty) {
      baseSQL + " " + conditions.mkString(" ") + " ORDER BY upload_time DESC"
    } else {
      baseSQL + " ORDER BY upload_time DESC"
    }
    
    (finalSQL, params.toList)
  }

  def getImagesByStudent(examId: Long, studentId: Long): IO[List[GradingImage]] = {
    getGradingImages(examId, Some(studentId), None)
  }

  def getImagesByQuestion(examId: Long, questionNumber: Int): IO[List[GradingImage]] = {
    getGradingImages(examId, None, Some(questionNumber))
  }

  def getImagesByStudentAndQuestion(examId: Long, studentId: Long, questionNumber: Int): IO[List[GradingImage]] = {
    getGradingImages(examId, Some(studentId), Some(questionNumber))
  }

  // 验证阅卷员是否有权限访问特定考试的图片
  def validateGraderAccess(graderId: Long, examId: Long): IO[Boolean] = {
    val sql = """
      SELECT COUNT(*) as count
      FROM grading_tasks 
      WHERE grader_id = ? AND exam_id = ?
    """
    
    val params = List(
      SqlParameter("long", graderId),
      SqlParameter("long", examId)
    )
    
    DatabaseManager.executeQueryOptional(sql, params).map { maybeRow =>
      maybeRow.map { row =>
        DatabaseManager.decodeFieldUnsafe[Long](row, "count") > 0
      }.getOrElse(false)
    }
  }

  // 获取阅卷员分配的任务相关图片
  def getGraderAssignedImages(graderId: Long, examId: Option[Long] = None): IO[List[GradingImage]] = {
    val baseSQL = """
      SELECT DISTINCT gi.image_url, gi.file_name, gi.exam_id, gi.student_id, 
             gi.question_number, gi.upload_time
      FROM grading_images gi
      INNER JOIN grading_tasks gt ON gi.exam_id = gt.exam_id 
        AND gi.student_id = gt.submission_id 
        AND gi.question_number = gt.question_number
      WHERE gt.grader_id = ?
    """
    
    val (sql, params) = examId match {
      case Some(eid) =>
        (baseSQL + " AND gi.exam_id = ? ORDER BY gi.upload_time DESC", 
         List(SqlParameter("long", graderId), SqlParameter("long", eid)))
      case None =>
        (baseSQL + " ORDER BY gi.upload_time DESC", 
         List(SqlParameter("long", graderId)))
    }
    
    DatabaseManager.executeQuery(sql, params).map { rows =>
      rows.map { row =>
        GradingImage(
          imageUrl = DatabaseManager.decodeFieldUnsafe[String](row, "image_url"),
          fileName = DatabaseManager.decodeFieldUnsafe[String](row, "file_name"),
          examId = DatabaseManager.decodeFieldUnsafe[Long](row, "exam_id"),
          studentId = DatabaseManager.decodeFieldUnsafe[Long](row, "student_id"),
          questionNumber = DatabaseManager.decodeFieldUnsafe[Int](row, "question_number"),
          uploadTime = LocalDateTime.parse(DatabaseManager.decodeFieldUnsafe[String](row, "upload_time").take(19))
        )
      }
    }
  }
}
