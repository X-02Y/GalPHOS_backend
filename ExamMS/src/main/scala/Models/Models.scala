package Models

import io.circe.{Decoder, Encoder}
import io.circe.generic.auto.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// JSON encoders/decoders for LocalDateTime
implicit val localDateTimeEncoder: Encoder[LocalDateTime] = 
  Encoder.encodeString.contramap(_.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))

implicit val localDateTimeDecoder: Decoder[LocalDateTime] = 
  Decoder.decodeString.emap { str =>
    try {
      // Try ISO format first
      Right(LocalDateTime.parse(str, DateTimeFormatter.ISO_DATE_TIME))
    } catch {
      case _: Exception =>
        try {
          // Try local datetime format
          Right(LocalDateTime.parse(str, DateTimeFormatter.ISO_LOCAL_DATE_TIME))
        } catch {
          case _: Exception => Left(s"Invalid datetime format: $str")
        }
    }
  }

// JSON encoders/decoders for Array[Byte] (for FileStorageService communication)
implicit val byteArrayEncoder: Encoder[Array[Byte]] = 
  Encoder.encodeList[Int].contramap(_.map(b => (b & 0xFF)).toList)

implicit val byteArrayDecoder: Decoder[Array[Byte]] = 
  Decoder.decodeList[Int].map(_.map(i => (i & 0xFF).toByte).toArray)

// 统一API响应格式
case class ApiResponse[T](
  success: Boolean,
  data: Option[T] = None,
  message: Option[String] = None
)

object ApiResponse {
  def success[T](data: T, message: String = "操作成功"): ApiResponse[T] =
    ApiResponse(success = true, data = Some(data), message = Some(message))

  def error(message: String): ApiResponse[String] =
    ApiResponse(success = false, message = Some(message))
}

// 分页响应
case class PaginatedResponse[T](
  items: List[T],
  total: Int,
  page: Int,
  limit: Int,
  pages: Int
)

// 考试状态枚举
enum ExamStatus(val value: String):
  case Draft extends ExamStatus("draft")
  case Published extends ExamStatus("published")
  case Ongoing extends ExamStatus("ongoing")
  case Grading extends ExamStatus("grading")
  case Completed extends ExamStatus("completed")

object ExamStatus {
  def fromString(status: String): ExamStatus = status.toLowerCase match {
    case "draft" => Draft
    case "published" => Published
    case "ongoing" => Ongoing
    case "grading" => Grading
    case "completed" => Completed
    case _ => throw new IllegalArgumentException(s"Unknown status: $status")
  }

  implicit val encoder: Encoder[ExamStatus] = Encoder.encodeString.contramap(_.value)
  implicit val decoder: Decoder[ExamStatus] = Decoder.decodeString.emap { str =>
    try Right(ExamStatus.fromString(str))
    catch case _: IllegalArgumentException => Left(s"Invalid status: $str")
  }
}

// 提交状态枚举
enum SubmissionStatus(val value: String):
  case Submitted extends SubmissionStatus("submitted")
  case Graded extends SubmissionStatus("graded")

object SubmissionStatus {
  def fromString(status: String): SubmissionStatus = status.toLowerCase match {
    case "submitted" => Submitted
    case "graded" => Graded
    case _ => throw new IllegalArgumentException(s"Unknown status: $status")
  }

  implicit val encoder: Encoder[SubmissionStatus] = Encoder.encodeString.contramap(_.value)
  implicit val decoder: Decoder[SubmissionStatus] = Decoder.decodeString.emap { str =>
    try Right(SubmissionStatus.fromString(str))
    catch case _: IllegalArgumentException => Left(s"Invalid status: $str")
  }
}

// 文件类型枚举
enum FileType(val value: String):
  case Question extends FileType("question")
  case Answer extends FileType("answer")
  case AnswerSheet extends FileType("answerSheet")
  case Submission extends FileType("submission")

object FileType {
  def fromString(fileType: String): FileType = fileType.toLowerCase match {
    case "question" => Question
    case "answer" => Answer
    case "answersheet" => AnswerSheet
    case "submission" => Submission
    case _ => throw new IllegalArgumentException(s"Unknown file type: $fileType")
  }

  implicit val encoder: Encoder[FileType] = Encoder.encodeString.contramap(_.value)
  implicit val decoder: Decoder[FileType] = Decoder.decodeString.emap { str =>
    try Right(FileType.fromString(str))
    catch case _: IllegalArgumentException => Left(s"Invalid file type: $str")
  }
}

// 考试文件
case class ExamFile(
  id: String,
  name: String,
  filename: Option[String] = None,
  originalName: Option[String] = None,
  url: String,
  size: Long,
  uploadTime: LocalDateTime,
  mimetype: Option[String] = None,
  fileType: Option[FileType] = None
)

// 考试基础信息
case class BaseExam(
  id: String,
  title: String,
  description: String,
  startTime: LocalDateTime,
  endTime: LocalDateTime,
  status: ExamStatus,
  createdAt: LocalDateTime,
  updatedAt: LocalDateTime,
  duration: Option[Int] = None
)

// 完整考试信息
case class Exam(
  id: String,
  title: String,
  description: String,
  startTime: LocalDateTime,
  endTime: LocalDateTime,
  status: ExamStatus,
  createdAt: LocalDateTime,
  updatedAt: LocalDateTime,
  duration: Option[Int] = None,
  questionFile: Option[ExamFile] = None,
  answerFile: Option[ExamFile] = None,
  answerSheetFile: Option[ExamFile] = None,
  createdBy: String,
  participants: List[String] = List.empty,
  totalQuestions: Option[Int] = None,
  maxScore: Option[Double] = None,
  totalScore: Option[Double] = None,
  subject: Option[String] = None,
  instructions: Option[String] = None
)

// 考试问题
case class Question(
  id: String,
  number: Int,
  score: Double,
  maxScore: Option[Double] = None,
  content: Option[String] = None,
  examId: String
)

// 考试答案
case class ExamAnswer(
  questionNumber: Int,
  imageUrl: String,
  uploadTime: LocalDateTime
)

// 考试提交
case class ExamSubmission(
  id: String,
  examId: String,
  studentUsername: String,
  submittedBy: Option[String] = None,
  answers: List[ExamAnswer],
  submittedAt: LocalDateTime,
  status: SubmissionStatus,
  score: Option[Double] = None,
  rank: Option[Int] = None
)

// 请求模型
case class CreateExamRequest(
  title: String,
  description: String,
  startTime: LocalDateTime,
  endTime: LocalDateTime,
  totalQuestions: Int,
  duration: Int,
  status: ExamStatus = ExamStatus.Draft,
  totalScore: Option[Double] = None,
  questions: Option[List[CreateExamQuestionRequest]] = None
)

case class CreateExamQuestionRequest(
  number: Int,
  score: Double
)

case class UpdateExamRequest(
  title: Option[String] = None,
  description: Option[String] = None,
  startTime: Option[LocalDateTime] = None,
  endTime: Option[LocalDateTime] = None,
  totalQuestions: Option[Int] = None,
  duration: Option[Int] = None,
  resetScoresIfNeeded: Option[Boolean] = None
)

case class SetQuestionScoresRequest(
  questions: List[QuestionScoreRequest]
)

case class QuestionScoreRequest(
  number: Int,
  score: Double
)

case class UpdateQuestionScoreRequest(
  score: Double
)

case class PublishExamRequest(
  questionFileId: Option[String] = None,
  answerFileId: Option[String] = None,
  answerSheetFileId: Option[String] = None
)

case class SubmitAnswersRequest(
  answers: List[ExamAnswer]
)

case class CoachSubmitAnswersRequest(
  studentUsername: String,
  answers: List[ExamAnswer]
)

case class FileUploadRequest(
  file: Array[Byte],
  originalName: String,
  examId: String,
  fileType: FileType
)

case class JsonFileUploadRequest(
  fileContent: String, // Base64 encoded file content
  originalName: String,
  examId: String,
  fileType: String // "question", "answer", "answerSheet"
)

case class SimpleJsonFileUploadRequest(
  fileContent: String, // Base64 encoded file content
  originalName: String
)

case class ImageUploadRequest(
  file: Array[Byte],
  examId: String,
  questionNumber: Int,
  studentUsername: Option[String] = None
)

// 响应模型
case class ExamListResponse(
  id: String,
  title: String,
  description: String,
  questionFile: Option[ExamFile] = None,
  answerFile: Option[ExamFile] = None,
  answerSheetFile: Option[ExamFile] = None,
  startTime: LocalDateTime,
  endTime: LocalDateTime,
  status: ExamStatus,
  totalQuestions: Option[Int] = None,
  duration: Option[Int] = None,
  createdAt: LocalDateTime,
  createdBy: String
)

case class QuestionScoresResponse(
  examId: String,
  totalQuestions: Int,
  totalScore: Double,
  questions: List[QuestionResponse]
)

case class QuestionResponse(
  id: String,
  number: Int,
  score: Double,
  maxScore: Option[Double] = None
)

case class FileUploadResponse(
  fileId: String,
  originalName: String,
  url: String,
  size: Long,
  uploadTime: LocalDateTime
)

case class ImageUploadResponse(
  imageUrl: String,
  fileName: String,
  fileSize: Long,
  uploadTime: LocalDateTime
)

case class CoachExamResponse(
  id: String,
  title: String,
  description: String,
  startTime: LocalDateTime,
  endTime: LocalDateTime,
  duration: Int,
  status: ExamStatus,
  totalQuestions: Int,
  maxScore: Double,
  questionFile: Option[ExamFile] = None,
  answerSheetFile: Option[ExamFile] = None,
  totalParticipants: Int,
  myStudentsParticipated: Int,
  myStudentsTotal: Int
)

case class ParticipationStats(
  totalStudents: Int,
  submittedStudents: Int,
  gradedStudents: Int,
  avgScore: Double,
  submissions: List[SubmissionSummary]
)

case class SubmissionSummary(
  studentId: String,
  studentName: String,
  submittedAt: LocalDateTime,
  status: SubmissionStatus,
  score: Option[Double] = None,
  rank: Option[Int] = None
)

case class ExamDetailsWithStats(
  exam: Exam,
  participationStats: ParticipationStats
)

case class ScoreStats(
  totalStudents: Int,
  submittedStudents: Int,
  averageScore: Double,
  scores: List[StudentScore]
)

case class StudentScore(
  studentId: String,
  studentName: String,
  score: Double,
  submittedAt: LocalDateTime
)

case class GradableExam(
  id: String,
  title: String,
  description: String,
  startTime: LocalDateTime,
  endTime: LocalDateTime,
  status: ExamStatus,
  maxScore: Double,
  subject: Option[String] = None,
  totalSubmissions: Int,
  pendingGrading: Int
)

case class GradingProgress(
  examId: String,
  examTitle: String,
  totalSubmissions: Int,
  gradedSubmissions: Int,
  pendingSubmissions: Int,
  myCompletedTasks: Int,
  progress: Double
)

// 内部服务通信模型
case class FileStorageUploadRequest(
  originalName: String,
  fileContent: String, // Base64 encoded file content for JSON serialization
  fileType: String,
  mimeType: String,
  uploadUserId: String,
  uploadUserType: String,
  examId: Option[String] = None,
  submissionId: Option[String] = None,
  description: Option[String] = None,
  category: String = "exam"
)

case class FileStorageDownloadRequest(
  fileId: String,
  requestUserId: String,
  requestUserType: String,
  purpose: String = "download"
)

case class FileStorageDeleteRequest(
  fileId: String,
  requestUserId: String,
  requestUserType: String,
  reason: String
)

case class FileStorageResponse(
  success: Boolean,
  fileId: Option[String] = None,
  url: Option[String] = None,
  message: Option[String] = None
)

// 文件上传响应模型
case class ExamFileUploadResponse(
  fileId: String,
  originalName: String,
  url: String,
  size: Long,
  uploadTime: String
)

case class AnswerImageUploadResponse(
  imageUrl: String,
  fileName: String,
  fileSize: Long,
  uploadTime: String
)

// 预申请考试ID响应模型
case class ReserveExamIdResponse(
  examId: String
)

case class ReservedExamId(
  id: String,
  examId: String,
  reservedBy: String,
  reservedAt: LocalDateTime,
  expiresAt: LocalDateTime,
  isUsed: Boolean,
  usedAt: Option[LocalDateTime]
)

// JWT载荷
case class JwtPayload(
  userId: String,
  username: String,
  role: String,
  exp: Long
)

// 错误代码
enum ErrorCode(val code: String, val message: String):
  case ExamNotFound extends ErrorCode("EXAM_NOT_FOUND", "Exam not found")
  case Unauthorized extends ErrorCode("UNAUTHORIZED", "User not authorized")
  case Forbidden extends ErrorCode("FORBIDDEN", "Access denied")
  case ValidationError extends ErrorCode("VALIDATION_ERROR", "Invalid input data")
  case FileUploadError extends ErrorCode("FILE_UPLOAD_ERROR", "File upload failed")
  case ExamAlreadyPublished extends ErrorCode("EXAM_ALREADY_PUBLISHED", "Exam already published")
  case ExamNotPublished extends ErrorCode("EXAM_NOT_PUBLISHED", "Exam not published")
  case SubmissionDeadlinePassed extends ErrorCode("SUBMISSION_DEADLINE_PASSED", "Submission deadline passed")
  case DuplicateSubmission extends ErrorCode("DUPLICATE_SUBMISSION", "Duplicate submission")

object ErrorCode {
  def toApiResponse(error: ErrorCode): ApiResponse[String] = 
    ApiResponse(success = false, message = Some(error.message))
}

// Internal file transfer models (for FileStorageService communication)
case class InternalFileUploadRequest(
  originalName: String,
  fileContent: String, // Base64 encoded file content for JSON serialization
  fileType: String,
  mimeType: String,
  uploadUserId: Option[String] = None,
  uploadUserType: Option[String] = None,
  examId: Option[String] = None,
  submissionId: Option[String] = None,
  description: Option[String] = None,
  category: String
)

case class InternalFileResponse(
  success: Boolean,
  fileId: Option[String] = None,
  url: Option[String] = None,
  message: Option[String] = None
)
