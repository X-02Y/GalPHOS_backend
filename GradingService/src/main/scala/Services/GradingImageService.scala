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

class GradingImageService(externalServiceClient: ExternalServiceClient) {
  private val logger = LoggerFactory.getLogger("GradingImageService")

  def getGradingImages(examId: Long, studentId: Option[Long] = None, questionNumber: Option[Int] = None): IO[List[GradingImage]] = {
    logger.info(s"获取阅卷图片: examId=$examId, studentId=$studentId, questionNumber=$questionNumber")
    
    // 首先从ExamMS验证考试是否存在
    externalServiceClient.getExamById(examId).flatMap {
      case Some(examData) =>
        logger.info(s"找到考试: ${examData.title}")
        
        // 从FileStorageService获取图片数据
        externalServiceClient.getImagesByExam(examId, studentId, questionNumber).flatMap { fileStorageImages =>
          if (fileStorageImages.nonEmpty) {
            logger.info(s"从FileStorageService获取到${fileStorageImages.length}张图片")
            
            // 转换为GradingImage格式
            val gradingImages = fileStorageImages.map { fsImage =>
              GradingImage(
                imageUrl = fsImage.fileUrl,
                fileName = fsImage.fileName,
                examId = fsImage.examId,
                studentId = fsImage.studentId,
                questionNumber = fsImage.questionNumber,
                uploadTime = LocalDateTime.parse(fsImage.uploadTime.take(19))
              )
            }
            
            // 将图片信息同步到本地数据库（可选）
            syncImagesToLocalDatabase(gradingImages).map(_ => gradingImages)
          } else {
            logger.info("FileStorageService没有返回图片数据，尝试从本地数据库获取")
            getImagesFromLocalDatabase(examId, studentId, questionNumber)
          }
        }
        
      case None =>
        logger.warn(s"考试不存在或无法访问: examId=$examId")
        // 如果ExamMS无法访问，尝试从本地数据库获取
        getImagesFromLocalDatabase(examId, studentId, questionNumber)
    }
  }

  // 从本地数据库获取图片（后备方案）
  private def getImagesFromLocalDatabase(examId: Long, studentId: Option[Long], questionNumber: Option[Int]): IO[List[GradingImage]] = {
    logger.info("从本地数据库获取图片数据")
    
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

  // 将图片信息同步到本地数据库
  private def syncImagesToLocalDatabase(images: List[GradingImage]): IO[Unit] = {
    logger.info(s"同步${images.length}张图片信息到本地数据库")
    
    val insertSQL = """
      INSERT INTO grading_images (image_url, file_name, exam_id, student_id, question_number, upload_time)
      VALUES (?, ?, ?, ?, ?, ?)
      ON CONFLICT (exam_id, student_id, question_number, file_name) 
      DO UPDATE SET 
        image_url = EXCLUDED.image_url,
        upload_time = EXCLUDED.upload_time
    """
    
    val operations = images.map { image =>
      val params = List(
        SqlParameter("string", image.imageUrl),
        SqlParameter("string", image.fileName),
        SqlParameter("long", image.examId),
        SqlParameter("long", image.studentId),
        SqlParameter("int", image.questionNumber),
        SqlParameter("timestamp", Timestamp.valueOf(image.uploadTime))
      )
      DatabaseManager.executeUpdate(insertSQL, params)
    }
    
    operations.sequence.map(_ => ())
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

  // 获取图片及其内容（用于前端显示）
  def getGradingImagesWithContent(examId: Long, studentId: Option[Long] = None, questionNumber: Option[Int] = None): IO[List[GradingImageWithContent]] = {
    logger.info(s"获取带内容的阅卷图片: examId=$examId, studentId=$studentId, questionNumber=$questionNumber")
    
    getGradingImages(examId, studentId, questionNumber).flatMap { images =>
      // 为每张图片获取内容
      val imageContentOps = images.map { image =>
        externalServiceClient.getImageContent(image.imageUrl).map { contentOpt =>
          GradingImageWithContent(
            imageUrl = image.imageUrl,
            fileName = image.fileName,
            examId = image.examId,
            studentId = image.studentId,
            questionNumber = image.questionNumber,
            uploadTime = image.uploadTime,
            base64Content = contentOpt
          )
        }
      }
      
      imageContentOps.sequence
    }
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
