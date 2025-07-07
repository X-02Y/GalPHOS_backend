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

class QuestionScoreService {
  private val logger = LoggerFactory.getLogger("QuestionScoreService")

  def getQuestionScores(examId: Long): IO[List[QuestionScore]] = {
    val sql = """
      SELECT exam_id, question_number, max_score, question_type, description, created_at, updated_at
      FROM question_scores 
      WHERE exam_id = ?
      ORDER BY question_number ASC
    """
    
    val params = List(SqlParameter("long", examId))
    
    DatabaseManager.executeQuery(sql, params).map { rows =>
      rows.map { row =>
        QuestionScore(
          examId = DatabaseManager.decodeFieldUnsafe[Long](row, "exam_id"),
          questionNumber = DatabaseManager.decodeFieldUnsafe[Int](row, "question_number"),
          maxScore = BigDecimal(DatabaseManager.decodeFieldUnsafe[String](row, "max_score")),
          questionType = DatabaseManager.decodeFieldUnsafe[String](row, "question_type"),
          description = DatabaseManager.decodeFieldOptional[String](row, "description"),
          createdAt = LocalDateTime.parse(DatabaseManager.decodeFieldUnsafe[String](row, "created_at").take(19)),
          updatedAt = LocalDateTime.parse(DatabaseManager.decodeFieldUnsafe[String](row, "updated_at").take(19))
        )
      }
    }
  }

  def createOrUpdateQuestionScores(examId: Long, scores: List[QuestionScoreRequest]): IO[Int] = {
    logger.info(s"为考试 $examId 设置题目分数")
    
    val insertOrUpdateSql = """
      INSERT INTO question_scores (exam_id, question_number, max_score, question_type, description, created_at, updated_at)
      VALUES (?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT (exam_id, question_number) 
      DO UPDATE SET 
        max_score = EXCLUDED.max_score,
        question_type = EXCLUDED.question_type,
        description = EXCLUDED.description,
        updated_at = EXCLUDED.updated_at
    """
    
    val now = Timestamp.valueOf(LocalDateTime.now())
    
    val insertResults = scores.map { score =>
      val params = List(
        SqlParameter("long", examId),
        SqlParameter("int", score.questionNumber),
        SqlParameter("bigdecimal", score.maxScore.bigDecimal),
        SqlParameter("string", score.questionType),
        SqlParameter("string", score.description.getOrElse("")),
        SqlParameter("timestamp", now),
        SqlParameter("timestamp", now)
      )
      
      DatabaseManager.executeUpdate(insertOrUpdateSql, params)
    }
    
    // 等待所有插入完成并返回总影响行数
    insertResults.foldLeft(IO.pure(0)) { (acc, result) =>
      for {
        total <- acc
        current <- result
      } yield total + current
    }
  }

  def updateQuestionScore(examId: Long, questionNumber: Int, request: UpdateQuestionScoreRequest): IO[Int] = {
    val sql = """
      UPDATE question_scores 
      SET max_score = ?, description = ?, updated_at = ?
      WHERE exam_id = ? AND question_number = ?
    """
    
    val now = Timestamp.valueOf(LocalDateTime.now())
    val params = List(
      SqlParameter("bigdecimal", request.maxScore.bigDecimal),
      SqlParameter("string", request.description.getOrElse("")),
      SqlParameter("timestamp", now),
      SqlParameter("long", examId),
      SqlParameter("int", questionNumber)
    )
    
    DatabaseManager.executeUpdate(sql, params)
  }

  def getQuestionScore(examId: Long, questionNumber: Int): IO[Option[QuestionScore]] = {
    val sql = """
      SELECT exam_id, question_number, max_score, question_type, description, created_at, updated_at
      FROM question_scores 
      WHERE exam_id = ? AND question_number = ?
    """
    
    val params = List(
      SqlParameter("long", examId),
      SqlParameter("int", questionNumber)
    )
    
    DatabaseManager.executeQueryOptional(sql, params).map { maybeRow =>
      maybeRow.map { row =>
        QuestionScore(
          examId = DatabaseManager.decodeFieldUnsafe[Long](row, "exam_id"),
          questionNumber = DatabaseManager.decodeFieldUnsafe[Int](row, "question_number"),
          maxScore = BigDecimal(DatabaseManager.decodeFieldUnsafe[String](row, "max_score")),
          questionType = DatabaseManager.decodeFieldUnsafe[String](row, "question_type"),
          description = DatabaseManager.decodeFieldOptional[String](row, "description"),
          createdAt = LocalDateTime.parse(DatabaseManager.decodeFieldUnsafe[String](row, "created_at").take(19)),
          updatedAt = LocalDateTime.parse(DatabaseManager.decodeFieldUnsafe[String](row, "updated_at").take(19))
        )
      }
    }
  }
}

class CoachStudentService {
  private val logger = LoggerFactory.getLogger("CoachStudentService")

  def getCoachStudents(coachId: Long): IO[List[CoachStudent]] = {
    val sql = """
      SELECT id, coach_id, student_name, student_school, student_province, 
             grade, is_active, created_at, updated_at
      FROM coach_students 
      WHERE coach_id = ? AND is_active = true
      ORDER BY created_at DESC
    """
    
    val params = List(SqlParameter("long", coachId))
    
    DatabaseManager.executeQuery(sql, params).map { rows =>
      rows.map { row =>
        CoachStudent(
          id = DatabaseManager.decodeFieldUnsafe[Long](row, "id"),
          coachId = DatabaseManager.decodeFieldUnsafe[Long](row, "coach_id"),
          studentName = DatabaseManager.decodeFieldUnsafe[String](row, "student_name"),
          studentSchool = DatabaseManager.decodeFieldUnsafe[String](row, "student_school"),
          studentProvince = DatabaseManager.decodeFieldUnsafe[String](row, "student_province"),
          grade = DatabaseManager.decodeFieldOptional[String](row, "grade"),
          isActive = DatabaseManager.decodeFieldUnsafe[Boolean](row, "is_active"),
          createdAt = LocalDateTime.parse(DatabaseManager.decodeFieldUnsafe[String](row, "created_at").take(19)),
          updatedAt = LocalDateTime.parse(DatabaseManager.decodeFieldUnsafe[String](row, "updated_at").take(19))
        )
      }
    }
  }

  def getCoachStudentById(coachId: Long, studentId: Long): IO[Option[CoachStudent]] = {
    val sql = """
      SELECT id, coach_id, student_name, student_school, student_province, 
             grade, is_active, created_at, updated_at
      FROM coach_students 
      WHERE id = ? AND coach_id = ?
    """
    
    val params = List(
      SqlParameter("long", studentId),
      SqlParameter("long", coachId)
    )
    
    DatabaseManager.executeQueryOptional(sql, params).map { maybeRow =>
      maybeRow.map { row =>
        CoachStudent(
          id = DatabaseManager.decodeFieldUnsafe[Long](row, "id"),
          coachId = DatabaseManager.decodeFieldUnsafe[Long](row, "coach_id"),
          studentName = DatabaseManager.decodeFieldUnsafe[String](row, "student_name"),
          studentSchool = DatabaseManager.decodeFieldUnsafe[String](row, "student_school"),
          studentProvince = DatabaseManager.decodeFieldUnsafe[String](row, "student_province"),
          grade = DatabaseManager.decodeFieldOptional[String](row, "grade"),
          isActive = DatabaseManager.decodeFieldUnsafe[Boolean](row, "is_active"),
          createdAt = LocalDateTime.parse(DatabaseManager.decodeFieldUnsafe[String](row, "created_at").take(19)),
          updatedAt = LocalDateTime.parse(DatabaseManager.decodeFieldUnsafe[String](row, "updated_at").take(19))
        )
      }
    }
  }

  def createCoachStudent(coachId: Long, request: CreateCoachStudentRequest): IO[String] = {
    val sql = """
      INSERT INTO coach_students (coach_id, student_name, student_school, student_province, grade, is_active, created_at, updated_at)
      VALUES (?, ?, ?, ?, ?, true, ?, ?)
    """
    
    val now = Timestamp.valueOf(LocalDateTime.now())
    val params = List(
      SqlParameter("long", coachId),
      SqlParameter("string", request.studentName),
      SqlParameter("string", request.studentSchool),
      SqlParameter("string", request.studentProvince),
      SqlParameter("string", request.grade.getOrElse("")),
      SqlParameter("timestamp", now),
      SqlParameter("timestamp", now)
    )
    
    DatabaseManager.executeInsertWithGeneratedKey(sql, params)
  }

  def updateCoachStudent(coachId: Long, studentId: Long, request: UpdateCoachStudentRequest): IO[Int] = {
    // 构建动态更新SQL
    val updates = scala.collection.mutable.ListBuffer[String]()
    val params = scala.collection.mutable.ListBuffer[SqlParameter]()
    
    request.studentName.foreach { name =>
      updates += "student_name = ?"
      params += SqlParameter("string", name)
    }
    
    request.studentSchool.foreach { school =>
      updates += "student_school = ?"
      params += SqlParameter("string", school)
    }
    
    request.studentProvince.foreach { province =>
      updates += "student_province = ?"
      params += SqlParameter("string", province)
    }
    
    request.grade.foreach { grade =>
      updates += "grade = ?"
      params += SqlParameter("string", grade)
    }
    
    request.isActive.foreach { active =>
      updates += "is_active = ?"
      params += SqlParameter("boolean", active)
    }
    
    if (updates.nonEmpty) {
      updates += "updated_at = ?"
      params += SqlParameter("timestamp", Timestamp.valueOf(LocalDateTime.now()))
      
      val sql = s"""
        UPDATE coach_students 
        SET ${updates.mkString(", ")}
        WHERE id = ? AND coach_id = ?
      """
      
      params += SqlParameter("long", studentId)
      params += SqlParameter("long", coachId)
      
      DatabaseManager.executeUpdate(sql, params.toList)
    } else {
      IO.pure(0)
    }
  }

  def deleteCoachStudent(coachId: Long, studentId: Long): IO[Int] = {
    val sql = """
      UPDATE coach_students 
      SET is_active = false, updated_at = ?
      WHERE id = ? AND coach_id = ?
    """
    
    val now = Timestamp.valueOf(LocalDateTime.now())
    val params = List(
      SqlParameter("timestamp", now),
      SqlParameter("long", studentId),
      SqlParameter("long", coachId)
    )
    
    DatabaseManager.executeUpdate(sql, params)
  }
}
