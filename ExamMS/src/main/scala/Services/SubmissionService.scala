package Services

import cats.effect.IO
import cats.implicits.*
import Models.*
import Database.DatabaseUtils
import java.time.LocalDateTime
import java.sql.ResultSet

trait SubmissionService {
  def submitAnswers(examId: String, studentUsername: String, request: SubmitAnswersRequest, submittedBy: Option[String] = None): IO[ExamSubmission]
  def getSubmissionStatus(examId: String, studentUsername: String): IO[Option[ExamSubmission]]
  def getSubmissionsByExam(examId: String): IO[List[ExamSubmission]]
  def getSubmissionsByStudent(studentUsername: String): IO[List[ExamSubmission]]
  def getCoachSubmissions(examId: String, coachUsername: String): IO[List[ExamSubmission]]
  def updateSubmissionScore(submissionId: String, score: Double): IO[Boolean]
  def getSubmissionById(submissionId: String): IO[Option[ExamSubmission]]
}

class SubmissionServiceImpl extends SubmissionService {

  override def submitAnswers(examId: String, studentUsername: String, request: SubmitAnswersRequest, submittedBy: Option[String] = None): IO[ExamSubmission] = {
    for {
      // 检查是否已有提交记录
      existingSubmission <- getSubmissionStatus(examId, studentUsername)
      
      submissionId <- existingSubmission match {
        case Some(submission) =>
          // 更新现有提交
          updateSubmissionAnswers(submission.id, request.answers)
          IO.pure(submission.id)
        case None =>
          // 创建新提交
          createNewSubmission(examId, studentUsername, submittedBy)
      }
      
      // 插入答案
      _ <- insertAnswers(submissionId, request.answers)
      
      // 获取完整的提交记录
      submission <- getSubmissionById(submissionId).map(_.getOrElse(
        throw new RuntimeException("Failed to retrieve submission")
      ))
    } yield submission
  }

  override def getSubmissionStatus(examId: String, studentUsername: String): IO[Option[ExamSubmission]] = {
    val sql = """
      SELECT s.id, s.exam_id, s.student_username, s.submitted_by, s.submitted_at, 
             s.status, s.total_score, s.rank_position
      FROM exam_submissions s
      WHERE s.exam_id = ? AND s.student_username = ?
    """

    for {
      submissionOpt <- DatabaseUtils.executeQuerySingle(sql, List(examId, studentUsername))(parseSubmissionBase)
      submission <- submissionOpt match {
        case Some(sub) => 
          getAnswersBySubmission(sub.id).map(answers => Some(sub.copy(answers = answers)))
        case None => IO.pure(None)
      }
    } yield submission
  }

  override def getSubmissionsByExam(examId: String): IO[List[ExamSubmission]] = {
    val sql = """
      SELECT s.id, s.exam_id, s.student_username, s.submitted_by, s.submitted_at, 
             s.status, s.total_score, s.rank_position
      FROM exam_submissions s
      WHERE s.exam_id = ?
      ORDER BY s.submitted_at DESC
    """

    for {
      submissions <- DatabaseUtils.executeQuery(sql, List(examId))(parseSubmissionBase)
      submissionsWithAnswers <- submissions.traverse { submission =>
        getAnswersBySubmission(submission.id).map(answers => submission.copy(answers = answers))
      }
    } yield submissionsWithAnswers
  }

  override def getSubmissionsByStudent(studentUsername: String): IO[List[ExamSubmission]] = {
    val sql = """
      SELECT s.id, s.exam_id, s.student_username, s.submitted_by, s.submitted_at, 
             s.status, s.total_score, s.rank_position
      FROM exam_submissions s
      WHERE s.student_username = ?
      ORDER BY s.submitted_at DESC
    """

    for {
      submissions <- DatabaseUtils.executeQuery(sql, List(studentUsername))(parseSubmissionBase)
      submissionsWithAnswers <- submissions.traverse { submission =>
        getAnswersBySubmission(submission.id).map(answers => submission.copy(answers = answers))
      }
    } yield submissionsWithAnswers
  }

  override def getCoachSubmissions(examId: String, coachUsername: String): IO[List[ExamSubmission]] = {
    val sql = """
      SELECT s.id, s.exam_id, s.student_username, s.submitted_by, s.submitted_at, 
             s.status, s.total_score, s.rank_position
      FROM exam_submissions s
      WHERE s.exam_id = ? AND s.submitted_by = ?
      ORDER BY s.submitted_at DESC
    """

    for {
      submissions <- DatabaseUtils.executeQuery(sql, List(examId, coachUsername))(parseSubmissionBase)
      submissionsWithAnswers <- submissions.traverse { submission =>
        getAnswersBySubmission(submission.id).map(answers => submission.copy(answers = answers))
      }
    } yield submissionsWithAnswers
  }

  override def updateSubmissionScore(submissionId: String, score: Double): IO[Boolean] = {
    val sql = """
      UPDATE exam_submissions 
      SET total_score = ?, status = ?, updated_at = CURRENT_TIMESTAMP
      WHERE id = ?
    """

    DatabaseUtils.executeUpdate(sql, List(score, SubmissionStatus.Graded.value, submissionId)).map(_ > 0)
  }

  override def getSubmissionById(submissionId: String): IO[Option[ExamSubmission]] = {
    val sql = """
      SELECT s.id, s.exam_id, s.student_username, s.submitted_by, s.submitted_at, 
             s.status, s.total_score, s.rank_position
      FROM exam_submissions s
      WHERE s.id = ?
    """

    for {
      submissionOpt <- DatabaseUtils.executeQuerySingle(sql, List(submissionId))(parseSubmissionBase)
      submission <- submissionOpt match {
        case Some(sub) => 
          getAnswersBySubmission(sub.id).map(answers => Some(sub.copy(answers = answers)))
        case None => IO.pure(None)
      }
    } yield submission
  }

  private def createNewSubmission(examId: String, studentUsername: String, submittedBy: Option[String]): IO[String] = {
    val sql = """
      INSERT INTO exam_submissions (exam_id, student_username, submitted_by, status)
      VALUES (?, ?, ?, ?)
    """

    val params = List(examId, studentUsername, submittedBy.orNull, SubmissionStatus.Submitted.value)
    DatabaseUtils.executeInsertWithId(sql, params)
  }

  private def updateSubmissionAnswers(submissionId: String, answers: List[ExamAnswer]): IO[Unit] = {
    for {
      // 删除现有答案
      _ <- DatabaseUtils.executeUpdate("DELETE FROM exam_answers WHERE submission_id = ?", List(submissionId))
      
      // 插入新答案
      _ <- insertAnswers(submissionId, answers)
      
      // 更新提交时间
      _ <- DatabaseUtils.executeUpdate(
        "UPDATE exam_submissions SET submitted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
        List(submissionId)
      )
    } yield ()
  }

  private def insertAnswers(submissionId: String, answers: List[ExamAnswer]): IO[Unit] = {
    val sql = """
      INSERT INTO exam_answers (submission_id, question_number, answer_image_url, upload_time)
      VALUES (?, ?, ?, ?)
    """

    answers.traverse { answer =>
      val params = List(submissionId, answer.questionNumber, answer.imageUrl, answer.uploadTime)
      DatabaseUtils.executeUpdate(sql, params)
    }.void
  }

  private def getAnswersBySubmission(submissionId: String): IO[List[ExamAnswer]] = {
    val sql = """
      SELECT question_number, answer_image_url, upload_time
      FROM exam_answers
      WHERE submission_id = ?
      ORDER BY question_number
    """

    DatabaseUtils.executeQuery(sql, List(submissionId))(parseAnswer)
  }

  private def parseSubmissionBase(rs: ResultSet): ExamSubmission = {
    ExamSubmission(
      id = rs.getString("id"),
      examId = rs.getString("exam_id"),
      studentUsername = rs.getString("student_username"),
      submittedBy = Option(rs.getString("submitted_by")).filter(_.nonEmpty),
      answers = List.empty, // 将在后续填充
      submittedAt = rs.getTimestamp("submitted_at").toLocalDateTime,
      status = SubmissionStatus.fromString(rs.getString("status")),
      score = Option(rs.getDouble("total_score")).filter(_ != 0.0),
      rank = Option(rs.getInt("rank_position")).filter(_ != 0)
    )
  }

  private def parseAnswer(rs: ResultSet): ExamAnswer = {
    ExamAnswer(
      questionNumber = rs.getInt("question_number"),
      imageUrl = rs.getString("answer_image_url"),
      uploadTime = rs.getTimestamp("upload_time").toLocalDateTime
    )
  }
}
