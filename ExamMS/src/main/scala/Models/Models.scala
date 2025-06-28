package Models

import io.circe.{Decoder, Encoder}
import io.circe.generic.auto.*
import java.time.{Instant, LocalDateTime}
import java.util.UUID

// Enums
enum ExamStatus:
  case draft, published, active, completed, cancelled

enum QuestionType:
  case multiple_choice, short_answer, essay, calculation

enum FileType:
  case question_paper, answer_key, resource, attachment

// Request/Response DTOs
case class LoginRequest(username: String, password: String)
case class AuthResponse(success: Boolean, token: Option[String], message: String, user: Option[UserInfo])
case class UserInfo(id: String, username: String, role: String, email: Option[String])

// Pagination
case class PaginationInfo(page: Int, limit: Int, total: Int, totalPages: Int)

// Core Models
case class Exam(
  id: String,
  title: String,
  description: Option[String],
  subject: String,
  startTime: Instant,
  endTime: Instant,
  duration: Int, // in minutes
  status: ExamStatus,
  totalScore: BigDecimal,
  questionCount: Int,
  createdAt: Instant,
  updatedAt: Instant
)

case class ExamDetail(
  id: String,
  title: String,
  description: Option[String],
  subject: String,
  startTime: Instant,
  endTime: Instant,
  duration: Int,
  status: ExamStatus,
  totalScore: BigDecimal,
  questionCount: Int,
  instructions: Option[String],
  questions: List[Question],
  files: List[ExamFile],
  settings: Option[ExamSettings],
  createdAt: Instant,
  updatedAt: Instant
)

case class Question(
  number: Int,
  content: String,
  questionType: QuestionType,
  score: BigDecimal,
  options: Option[List[String]] = None, // for multiple choice
  correctAnswer: Option[String] = None
)

case class QuestionScore(
  questionNumber: Int,
  maxScore: BigDecimal,
  partialScoring: Option[Boolean] = Some(false),
  scoringCriteria: Option[List[ScoringCriteria]] = None
)

case class ScoringCriteria(
  description: String,
  points: BigDecimal
)

case class ExamFile(
  id: String,
  examId: String,
  fileName: String,
  filePath: String,
  fileType: FileType,
  fileSize: Long,
  mimeType: Option[String],
  uploadedBy: String,
  uploadedAt: Instant
)

case class ExamSettings(
  allowLateSubmission: Boolean = false,
  shuffleQuestions: Boolean = false,
  showResultsImmediately: Boolean = false,
  maxAttempts: Int = 1,
  requirePassword: Boolean = false,
  examPassword: Option[String] = None
)

// Statistics Models
case class ExamStats(
  totalParticipants: Int,
  submittedCount: Int,
  averageScore: BigDecimal,
  highestScore: BigDecimal,
  lowestScore: BigDecimal,
  passRate: BigDecimal
)

case class ExamScoreStats(
  totalParticipants: Int,
  averageScore: BigDecimal,
  median: BigDecimal,
  standardDeviation: BigDecimal,
  highestScore: BigDecimal,
  lowestScore: BigDecimal,
  passRate: BigDecimal
)

case class ScoreDistribution(
  ranges: List[ScoreRange]
)

case class ScoreRange(
  min: BigDecimal,
  max: BigDecimal,
  count: Int,
  percentage: BigDecimal
)

// Grading Models
case class GraderExam(
  id: String,
  title: String,
  subject: String,
  status: ExamStatus,
  totalScore: BigDecimal,
  questionCount: Int,
  participantCount: Int,
  gradedCount: Int,
  endTime: Instant
)

case class GradingInfo(
  totalStudents: Int,
  gradedStudents: Int,
  pendingGrading: Int,
  averageScore: Option[BigDecimal]
)

case class GradingProgress(
  completed: Int,
  total: Int,
  percentage: BigDecimal
)

case class ExamGradingProgress(
  examId: String,
  totalSubmissions: Int,
  gradedSubmissions: Int,
  pendingSubmissions: Int,
  progressPercentage: BigDecimal
)

case class GradingStats(
  averageGradingTime: Option[Long], // in minutes
  gradersAssigned: Int,
  questionsGraded: Int,
  totalQuestions: Int
)

// Student Models
case class DetailedScore(
  examId: String,
  studentId: String,
  totalScore: BigDecimal,
  maxScore: BigDecimal,
  percentage: BigDecimal,
  rank: Option[Int],
  questionScores: List[QuestionDetailScore]
)

case class QuestionDetailScore(
  questionNumber: Int,
  score: BigDecimal,
  maxScore: BigDecimal,
  feedback: Option[String]
)

case class ScoreBreakdown(
  byQuestion: List[QuestionBreakdown],
  byCategory: Option[List[CategoryBreakdown]]
)

case class QuestionBreakdown(
  questionNumber: Int,
  score: BigDecimal,
  maxScore: BigDecimal,
  correct: Boolean
)

case class CategoryBreakdown(
  category: String,
  score: BigDecimal,
  maxScore: BigDecimal,
  questionCount: Int
)

case class Ranking(
  rank: Int,
  studentId: String,
  studentName: String,
  score: BigDecimal,
  percentage: BigDecimal
)

// Coach Models
case class StudentRanking(
  rank: Int,
  studentId: String,
  studentName: String,
  score: BigDecimal,
  percentage: BigDecimal,
  submissionTime: Instant
)

case class StudentRank(
  studentId: String,
  studentName: String,
  rank: Int,
  score: BigDecimal,
  percentage: BigDecimal
)

// Admin Models
case class AdminExam(
  id: String,
  title: String,
  description: Option[String],
  subject: String,
  startTime: Instant,
  endTime: Instant,
  duration: Int,
  status: ExamStatus,
  totalScore: BigDecimal,
  questionCount: Int,
  participantCount: Int,
  submissionCount: Int,
  createdBy: String,
  createdAt: Instant,
  updatedAt: Instant
)

case class CreateExamRequest(
  title: String,
  description: Option[String],
  subject: String,
  startTime: Instant,
  endTime: Instant,
  duration: Int,
  instructions: Option[String],
  questions: List[CreateQuestionRequest],
  settings: Option[ExamSettings]
)

case class CreateQuestionRequest(
  number: Int,
  content: String,
  questionType: QuestionType,
  score: BigDecimal,
  options: Option[List[String]] = None,
  correctAnswer: Option[String] = None
)

case class UpdateExamRequest(
  title: Option[String],
  description: Option[String],
  subject: Option[String],
  startTime: Option[Instant],
  endTime: Option[Instant],
  duration: Option[Int],
  instructions: Option[String],
  settings: Option[ExamSettings]
)

case class SetQuestionScoresRequest(
  scores: List[QuestionScore]
)

// Response Models
case class ExamsResponse(
  exams: List[Exam],
  total: Int,
  pagination: Option[PaginationInfo] = None
)

case class AdminExamsResponse(
  exams: List[AdminExam],
  total: Int,
  pagination: PaginationInfo
)

case class GraderExamsResponse(
  exams: List[GraderExam],
  total: Int,
  pagination: PaginationInfo
)

case class ExamDetailResponse(
  exam: ExamDetail,
  statistics: Option[ExamStats] = None,
  gradingInfo: Option[GradingInfo] = None,
  progress: Option[GradingProgress] = None
)

case class QuestionScoresResponse(
  questions: List[QuestionScore],
  totalScore: BigDecimal
)

case class ScoreDetailResponse(
  score: DetailedScore,
  breakdown: ScoreBreakdown
)

case class ScoreRankingResponse(
  ranking: List[Ranking],
  myRank: Int,
  totalParticipants: Int
)

case class StudentRankingResponse(
  rankings: List[StudentRanking],
  myStudents: List[StudentRank]
)

case class ExamScoreStatisticsResponse(
  statistics: ExamScoreStats,
  distribution: ScoreDistribution
)

case class ExamGradingProgressResponse(
  progress: ExamGradingProgress,
  statistics: GradingStats
)

case class SuccessResponse(
  success: Boolean,
  message: String,
  exam: Option[Exam] = None,
  files: Option[List[ExamFile]] = None,
  scores: Option[List[QuestionScore]] = None
)

case class ErrorResponse(
  error: Boolean,
  message: String,
  code: String,
  details: Option[Map[String, String]] = None
)
