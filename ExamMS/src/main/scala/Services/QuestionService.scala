package Services

import cats.effect.IO
import cats.implicits.*
import Models.*
import Database.DatabaseUtils
import java.sql.ResultSet

trait QuestionService {
  def setQuestionScores(examId: String, request: SetQuestionScoresRequest): IO[QuestionScoresResponse]
  def getQuestionScores(examId: String): IO[Option[QuestionScoresResponse]]
  def updateQuestionScore(examId: String, questionNumber: Int, score: Double): IO[Boolean]
  def deleteQuestionsByExam(examId: String): IO[Boolean]
  def getQuestionsByExam(examId: String): IO[List[Question]]
}

class QuestionServiceImpl extends QuestionService {

  override def setQuestionScores(examId: String, request: SetQuestionScoresRequest): IO[QuestionScoresResponse] = {
    for {
      // 先删除现有的问题分数
      _ <- deleteQuestionsByExam(examId)
      
      // 插入新的问题分数
      _ <- request.questions.traverse { question =>
        val sql = """
          INSERT INTO exam_questions (exam_id, question_number, score)
          VALUES (?::uuid, ?, ?)
        """
        DatabaseUtils.executeUpdate(sql, List(examId, question.number, question.score))
      }
      
      // 更新考试的总分
      totalScore = request.questions.map(_.score).sum
      _ <- updateExamMaxScore(examId, totalScore)
      
      // 返回结果
      response <- getQuestionScores(examId).map(_.getOrElse(
        throw new RuntimeException("Failed to retrieve question scores")
      ))
    } yield response
  }

  override def getQuestionScores(examId: String): IO[Option[QuestionScoresResponse]] = {
    val sql = """
      SELECT eq.id, eq.exam_id, eq.question_number, eq.score, eq.max_score, eq.content, e.total_questions
      FROM exam_questions eq
      JOIN exams e ON eq.exam_id = e.id
      WHERE eq.exam_id = ?::uuid
      ORDER BY eq.question_number
    """

    for {
      questions <- DatabaseUtils.executeQuery(sql, List(examId))(parseQuestion)
      totalQuestions <- getTotalQuestions(examId)
    } yield {
      if (questions.nonEmpty) {
        Some(QuestionScoresResponse(
          examId = examId,
          totalQuestions = totalQuestions,
          totalScore = questions.map(_.score).sum,
          questions = questions.map(q => QuestionResponse(
            id = q.id,
            number = q.number,
            score = q.score,
            maxScore = q.maxScore
          ))
        ))
      } else {
        None
      }
    }
  }

  override def updateQuestionScore(examId: String, questionNumber: Int, score: Double): IO[Boolean] = {
    val sql = """
      UPDATE exam_questions 
      SET score = ?, updated_at = CURRENT_TIMESTAMP
      WHERE exam_id = ?::uuid AND question_number = ?
    """

    for {
      updated <- DatabaseUtils.executeUpdate(sql, List(score, examId, questionNumber))
      _ <- if (updated > 0) updateExamMaxScoreFromQuestions(examId) else IO.unit
    } yield updated > 0
  }

  override def deleteQuestionsByExam(examId: String): IO[Boolean] = {
    val sql = "DELETE FROM exam_questions WHERE exam_id = ?::uuid"
    DatabaseUtils.executeUpdate(sql, List(examId)).map(_ >= 0)
  }

  override def getQuestionsByExam(examId: String): IO[List[Question]] = {
    val sql = """
      SELECT id, exam_id, question_number, score, max_score, content
      FROM exam_questions
      WHERE exam_id = ?::uuid
      ORDER BY question_number
    """

    DatabaseUtils.executeQuery(sql, List(examId))(parseQuestion)
  }

  private def getTotalQuestions(examId: String): IO[Int] = {
    val sql = "SELECT total_questions FROM exams WHERE id = ?::uuid"
    DatabaseUtils.executeQuerySingle(sql, List(examId))(rs => rs.getInt("total_questions"))
      .map(_.getOrElse(0))
  }

  private def updateExamMaxScore(examId: String, maxScore: Double): IO[Int] = {
    val sql = "UPDATE exams SET max_score = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?::uuid"
    DatabaseUtils.executeUpdate(sql, List(maxScore, examId))
  }

  private def updateExamMaxScoreFromQuestions(examId: String): IO[Int] = {
    val sql = """
      UPDATE exams 
      SET max_score = (
        SELECT COALESCE(SUM(score), 0) 
        FROM exam_questions 
        WHERE exam_id = ?::uuid
      ), updated_at = CURRENT_TIMESTAMP 
      WHERE id = ?::uuid
    """
    DatabaseUtils.executeUpdate(sql, List(examId, examId))
  }

  private def parseQuestion(rs: ResultSet): Question = {
    Question(
      id = rs.getString("id"),
      number = rs.getInt("question_number"),
      score = rs.getDouble("score"),
      maxScore = Option(rs.getDouble("max_score")).filter(_ != 0.0),
      content = Option(rs.getString("content")).filter(_.nonEmpty),
      examId = rs.getString("exam_id")
    )
  }
}
