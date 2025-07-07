package Database

import cats.effect.IO
import Models.*
import Database.{DatabaseUtils, DatabaseManager}
import java.time.LocalDateTime
import java.util.UUID
import org.slf4j.LoggerFactory

trait SubmissionDAO {
  def createSubmission(submission: SubmissionEntity): IO[String]
  def getSubmissionById(id: String): IO[Option[SubmissionEntity]]
  def getSubmissionsByExamAndStudent(examId: String, studentUsername: String): IO[List[SubmissionEntity]]
  def getSubmissionsByExam(examId: String): IO[List[SubmissionEntity]]
  def getSubmissionsByStudent(studentUsername: String): IO[List[SubmissionEntity]]
  def updateSubmissionStatus(id: String, status: String): IO[Boolean]
  def updateSubmissionScore(id: String, totalScore: Double, maxScore: Double, gradedBy: String): IO[Boolean]
  def deleteSubmission(id: String): IO[Boolean]
  def getSubmissionsByGrader(graderId: String): IO[List[SubmissionEntity]]
}

class SubmissionDAOImpl extends SubmissionDAO {
  private val logger = LoggerFactory.getLogger("SubmissionDAOImpl")

  override def createSubmission(submission: SubmissionEntity): IO[String] = {
    val sql = """
      INSERT INTO submissions (id, exam_id, student_id, student_username, submitted_at, 
                              status, total_score, max_score, graded_at, graded_by, 
                              feedback, submitted_by)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """
    
    val params = List(
      submission.id,
      submission.examId,
      submission.studentId,
      submission.studentUsername,
      submission.submittedAt,
      submission.status,
      submission.totalScore.orNull,
      submission.maxScore.orNull,
      submission.gradedAt.orNull,
      submission.gradedBy.orNull,
      submission.feedback.orNull,
      submission.submittedBy.orNull
    )
    
    DatabaseUtils.executeUpdate(sql, params).map(_ => submission.id)
  }

  override def getSubmissionById(id: String): IO[Option[SubmissionEntity]] = {
    val sql = """
      SELECT id, exam_id, student_id, student_username, submitted_at, status,
             total_score, max_score, graded_at, graded_by, feedback, submitted_by
      FROM submissions 
      WHERE id = ?
    """
    
    DatabaseUtils.queryFirst(sql, List(id)) { rs =>
      SubmissionEntity(
        id = rs.getString("id"),
        examId = rs.getString("exam_id"),
        studentId = rs.getString("student_id"),
        studentUsername = rs.getString("student_username"),
        submittedAt = rs.getTimestamp("submitted_at").toLocalDateTime,
        status = rs.getString("status"),
        totalScore = Option(rs.getDouble("total_score")).filter(_ != 0.0),
        maxScore = Option(rs.getDouble("max_score")).filter(_ != 0.0),
        gradedAt = DatabaseUtils.getLocalDateTime(rs, "graded_at"),
        gradedBy = Option(rs.getString("graded_by")),
        feedback = Option(rs.getString("feedback")),
        submittedBy = Option(rs.getString("submitted_by"))
      )
    }
  }

  override def getSubmissionsByExamAndStudent(examId: String, studentUsername: String): IO[List[SubmissionEntity]] = {
    val sql = """
      SELECT id, exam_id, student_id, student_username, submitted_at, status,
             total_score, max_score, graded_at, graded_by, feedback, submitted_by
      FROM submissions 
      WHERE exam_id = ? AND student_username = ?
      ORDER BY submitted_at DESC
    """
    
    DatabaseUtils.executeQuery(sql, List(examId, studentUsername)) { rs =>
      SubmissionEntity(
        id = rs.getString("id"),
        examId = rs.getString("exam_id"),
        studentId = rs.getString("student_id"),
        studentUsername = rs.getString("student_username"),
        submittedAt = rs.getTimestamp("submitted_at").toLocalDateTime,
        status = rs.getString("status"),
        totalScore = Option(rs.getDouble("total_score")).filter(_ != 0.0),
        maxScore = Option(rs.getDouble("max_score")).filter(_ != 0.0),
        gradedAt = DatabaseUtils.getLocalDateTime(rs, "graded_at"),
        gradedBy = Option(rs.getString("graded_by")),
        feedback = Option(rs.getString("feedback")),
        submittedBy = Option(rs.getString("submitted_by"))
      )
    }
  }

  override def getSubmissionsByExam(examId: String): IO[List[SubmissionEntity]] = {
    val sql = """
      SELECT id, exam_id, student_id, student_username, submitted_at, status,
             total_score, max_score, graded_at, graded_by, feedback, submitted_by
      FROM submissions 
      WHERE exam_id = ?
      ORDER BY submitted_at DESC
    """
    
    DatabaseUtils.executeQuery(sql, List(examId)) { rs =>
      SubmissionEntity(
        id = rs.getString("id"),
        examId = rs.getString("exam_id"),
        studentId = rs.getString("student_id"),
        studentUsername = rs.getString("student_username"),
        submittedAt = rs.getTimestamp("submitted_at").toLocalDateTime,
        status = rs.getString("status"),
        totalScore = Option(rs.getDouble("total_score")).filter(_ != 0.0),
        maxScore = Option(rs.getDouble("max_score")).filter(_ != 0.0),
        gradedAt = DatabaseUtils.getLocalDateTime(rs, "graded_at"),
        gradedBy = Option(rs.getString("graded_by")),
        feedback = Option(rs.getString("feedback")),
        submittedBy = Option(rs.getString("submitted_by"))
      )
    }
  }

  override def getSubmissionsByStudent(studentUsername: String): IO[List[SubmissionEntity]] = {
    val sql = """
      SELECT id, exam_id, student_id, student_username, submitted_at, status,
             total_score, max_score, graded_at, graded_by, feedback, submitted_by
      FROM submissions 
      WHERE student_username = ?
      ORDER BY submitted_at DESC
    """
    
    DatabaseUtils.executeQuery(sql, List(studentUsername)) { rs =>
      SubmissionEntity(
        id = rs.getString("id"),
        examId = rs.getString("exam_id"),
        studentId = rs.getString("student_id"),
        studentUsername = rs.getString("student_username"),
        submittedAt = rs.getTimestamp("submitted_at").toLocalDateTime,
        status = rs.getString("status"),
        totalScore = Option(rs.getDouble("total_score")).filter(_ != 0.0),
        maxScore = Option(rs.getDouble("max_score")).filter(_ != 0.0),
        gradedAt = DatabaseUtils.getLocalDateTime(rs, "graded_at"),
        gradedBy = Option(rs.getString("graded_by")),
        feedback = Option(rs.getString("feedback")),
        submittedBy = Option(rs.getString("submitted_by"))
      )
    }
  }

  override def updateSubmissionStatus(id: String, status: String): IO[Boolean] = {
    val sql = "UPDATE submissions SET status = ? WHERE id = ?"
    DatabaseUtils.executeUpdate(sql, List(status, id)).map(_ > 0)
  }

  override def updateSubmissionScore(id: String, totalScore: Double, maxScore: Double, gradedBy: String): IO[Boolean] = {
    val sql = """
      UPDATE submissions 
      SET total_score = ?, max_score = ?, graded_by = ?, graded_at = CURRENT_TIMESTAMP, status = 'graded' 
      WHERE id = ?
    """
    DatabaseUtils.executeUpdate(sql, List(totalScore, maxScore, gradedBy, id)).map(_ > 0)
  }

  override def deleteSubmission(id: String): IO[Boolean] = {
    val sql = "DELETE FROM submissions WHERE id = ?"
    DatabaseUtils.executeUpdate(sql, List(id)).map(_ > 0)
  }

  override def getSubmissionsByGrader(graderId: String): IO[List[SubmissionEntity]] = {
    val sql = """
      SELECT id, exam_id, student_id, student_username, submitted_at, status,
             total_score, max_score, graded_at, graded_by, feedback, submitted_by
      FROM submissions 
      WHERE graded_by = ?
      ORDER BY graded_at DESC
    """
    
    DatabaseUtils.executeQuery(sql, List(graderId)) { rs =>
      SubmissionEntity(
        id = rs.getString("id"),
        examId = rs.getString("exam_id"),
        studentId = rs.getString("student_id"),
        studentUsername = rs.getString("student_username"),
        submittedAt = rs.getTimestamp("submitted_at").toLocalDateTime,
        status = rs.getString("status"),
        totalScore = Option(rs.getDouble("total_score")).filter(_ != 0.0),
        maxScore = Option(rs.getDouble("max_score")).filter(_ != 0.0),
        gradedAt = DatabaseUtils.getLocalDateTime(rs, "graded_at"),
        gradedBy = Option(rs.getString("graded_by")),
        feedback = Option(rs.getString("feedback")),
        submittedBy = Option(rs.getString("submitted_by"))
      )
    }
  }
}

trait AnswerDAO {
  def createAnswer(answer: AnswerEntity): IO[String]
  def getAnswerById(id: String): IO[Option[AnswerEntity]]
  def getAnswersBySubmission(submissionId: String): IO[List[AnswerEntity]]
  def updateAnswer(answer: AnswerEntity): IO[Boolean]
  def deleteAnswer(id: String): IO[Boolean]
  def deleteAnswersBySubmission(submissionId: String): IO[Boolean]
}

class AnswerDAOImpl extends AnswerDAO {
  private val logger = LoggerFactory.getLogger("AnswerDAOImpl")

  override def createAnswer(answer: AnswerEntity): IO[String] = {
    val sql = """
      INSERT INTO answers (id, submission_id, question_id, question_number, answer_text,
                          score, max_score, comments, image_url, upload_time,
                          grader_id, grader_name, graded_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """
    
    val params = List(
      answer.id,
      answer.submissionId,
      answer.questionId,
      answer.questionNumber,
      answer.answer.orNull,
      answer.score.orNull,
      answer.maxScore,
      answer.comments.orNull,
      answer.imageUrl.orNull,
      answer.uploadTime.orNull,
      answer.graderId.orNull,
      answer.graderName.orNull,
      answer.gradedAt.orNull
    )
    
    DatabaseUtils.executeUpdate(sql, params).map(_ => answer.id)
  }

  override def getAnswerById(id: String): IO[Option[AnswerEntity]] = {
    val sql = """
      SELECT id, submission_id, question_id, question_number, answer_text,
             score, max_score, comments, image_url, upload_time,
             grader_id, grader_name, graded_at
      FROM answers 
      WHERE id = ?
    """
    
    DatabaseUtils.queryFirst(sql, List(id)) { rs =>
      AnswerEntity(
        id = rs.getString("id"),
        submissionId = rs.getString("submission_id"),
        questionId = rs.getString("question_id"),
        questionNumber = rs.getInt("question_number"),
        answer = Option(rs.getString("answer_text")),
        score = Option(rs.getDouble("score")).filter(_ != 0.0),
        maxScore = rs.getDouble("max_score"),
        comments = Option(rs.getString("comments")),
        imageUrl = Option(rs.getString("image_url")),
        uploadTime = DatabaseUtils.getLocalDateTime(rs, "upload_time"),
        graderId = Option(rs.getString("grader_id")),
        graderName = Option(rs.getString("grader_name")),
        gradedAt = DatabaseUtils.getLocalDateTime(rs, "graded_at")
      )
    }
  }

  override def getAnswersBySubmission(submissionId: String): IO[List[AnswerEntity]] = {
    val sql = """
      SELECT id, submission_id, question_id, question_number, answer_text,
             score, max_score, comments, image_url, upload_time,
             grader_id, grader_name, graded_at
      FROM answers 
      WHERE submission_id = ?
      ORDER BY question_number
    """
    
    DatabaseUtils.executeQuery(sql, List(submissionId)) { rs =>
      AnswerEntity(
        id = rs.getString("id"),
        submissionId = rs.getString("submission_id"),
        questionId = rs.getString("question_id"),
        questionNumber = rs.getInt("question_number"),
        answer = Option(rs.getString("answer_text")),
        score = Option(rs.getDouble("score")).filter(_ != 0.0),
        maxScore = rs.getDouble("max_score"),
        comments = Option(rs.getString("comments")),
        imageUrl = Option(rs.getString("image_url")),
        uploadTime = DatabaseUtils.getLocalDateTime(rs, "upload_time"),
        graderId = Option(rs.getString("grader_id")),
        graderName = Option(rs.getString("grader_name")),
        gradedAt = DatabaseUtils.getLocalDateTime(rs, "graded_at")
      )
    }
  }

  override def updateAnswer(answer: AnswerEntity): IO[Boolean] = {
    val sql = """
      UPDATE answers 
      SET answer_text = ?, score = ?, comments = ?, grader_id = ?, 
          grader_name = ?, graded_at = ?
      WHERE id = ?
    """
    
    val params = List(
      answer.answer.orNull,
      answer.score.orNull,
      answer.comments.orNull,
      answer.graderId.orNull,
      answer.graderName.orNull,
      answer.gradedAt.orNull,
      answer.id
    )
    
    DatabaseUtils.executeUpdate(sql, params).map(_ > 0)
  }

  override def deleteAnswer(id: String): IO[Boolean] = {
    val sql = "DELETE FROM answers WHERE id = ?"
    DatabaseUtils.executeUpdate(sql, List(id)).map(_ > 0)
  }

  override def deleteAnswersBySubmission(submissionId: String): IO[Boolean] = {
    val sql = "DELETE FROM answers WHERE submission_id = ?"
    DatabaseUtils.executeUpdate(sql, List(submissionId)).map(_ > 0)
  }
}
