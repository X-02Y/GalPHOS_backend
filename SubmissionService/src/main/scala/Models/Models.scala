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

// Unified API response format
case class ApiResponse[T](
  success: Boolean,
  data: Option[T] = None,
  message: Option[String] = None
)

object ApiResponse {
  def success[T](data: T, message: String = "Operation successful"): ApiResponse[T] =
    ApiResponse(success = true, data = Some(data), message = Some(message))

  def error(message: String): ApiResponse[String] =
    ApiResponse(success = false, message = Some(message))
}

// Submission status enum
enum SubmissionStatus(val value: String) {
  case Submitted extends SubmissionStatus("submitted")
  case Grading extends SubmissionStatus("grading")
  case Graded extends SubmissionStatus("graded")
}

given Encoder[SubmissionStatus] = Encoder.encodeString.contramap(_.value)
given Decoder[SubmissionStatus] = Decoder.decodeString.emap { str =>
  SubmissionStatus.values.find(_.value == str) match {
    case Some(status) => Right(status)
    case None => Left(s"Invalid submission status: $str")
  }
}

// Exam Answer model
case class ExamAnswer(
  questionId: String,
  questionNumber: Int,
  answer: Option[String] = None,
  answers: Option[List[String]] = None, // For multiple choice questions
  score: Option[Double] = None,
  maxScore: Double,
  comments: Option[String] = None,
  annotations: Option[List[String]] = None,
  imageUrl: Option[String] = None,
  uploadTime: Option[LocalDateTime] = None,
  graderId: Option[String] = None,
  graderName: Option[String] = None,
  gradedAt: Option[LocalDateTime] = None
)

// Exam Submission model
case class ExamSubmission(
  id: String,
  examId: String,
  studentId: String,
  studentUsername: String,
  studentName: Option[String] = None,
  answers: List[ExamAnswer],
  submittedAt: LocalDateTime,
  status: SubmissionStatus,
  totalScore: Option[Double] = None,
  maxScore: Option[Double] = None,
  gradedAt: Option[LocalDateTime] = None,
  gradedBy: Option[String] = None,
  feedback: Option[String] = None,
  submittedBy: Option[String] = None // For coach proxy submissions
)

// Request models for API endpoints

// Student submission request
case class StudentSubmissionRequest(
  answers: List[AnswerSubmission]
)

case class AnswerSubmission(
  questionNumber: Int,
  imageUrl: String,
  uploadTime: LocalDateTime
)

// Coach proxy submission request
case class CoachSubmissionRequest(
  studentUsername: String,
  answers: List[AnswerSubmission]
)

// File upload requests
case class FileUploadRequest(
  fileName: String,
  fileData: String, // Base64 encoded
  relatedId: String, // Exam ID
  questionNumber: Int,
  category: String = "answer-image",
  timestamp: String,
  token: Option[String] = None // Authentication token
)

// Backward compatible file upload request (without token field)
case class LegacyFileUploadRequest(
  fileName: String,
  fileData: String, // Base64 encoded
  relatedId: String, // Exam ID
  questionNumber: Int,
  category: String = "answer-image",
  timestamp: String
)

case class CoachFileUploadRequest(
  fileName: String,
  fileData: String, // Base64 encoded
  questionNumber: Int,
  studentUsername: String,
  category: String = "answer-image",
  timestamp: String
)

// File upload response (from FileStorageService)
case class FileUploadResponse(
  fileId: String,
  fileName: String,
  fileUrl: String,
  fileSize: Long,
  fileType: String,
  uploadTime: LocalDateTime
)

// Grading progress response
case class GradingProgress(
  examId: String,
  examTitle: String,
  totalSubmissions: Int,
  gradedSubmissions: Int,
  pendingSubmissions: Int,
  myCompletedTasks: Int,
  progress: Double // percentage
)

// Database models
case class SubmissionEntity(
  id: String,
  examId: String,
  studentId: String,
  studentUsername: String,
  submittedAt: LocalDateTime,
  status: String,
  totalScore: Option[Double] = None,
  maxScore: Option[Double] = None,
  gradedAt: Option[LocalDateTime] = None,
  gradedBy: Option[String] = None,
  feedback: Option[String] = None,
  submittedBy: Option[String] = None
)

case class AnswerEntity(
  id: String,
  submissionId: String,
  questionId: String,
  questionNumber: Int,
  answer: Option[String] = None,
  score: Option[Double] = None,
  maxScore: Double,
  comments: Option[String] = None,
  imageUrl: Option[String] = None,
  uploadTime: Option[LocalDateTime] = None,
  graderId: Option[String] = None,
  graderName: Option[String] = None,
  gradedAt: Option[LocalDateTime] = None
)

// JWT Claims
case class JwtClaims(
  userId: String,
  username: String,
  role: String,
  exp: Long
)

// External service models
case class UserInfo(
  id: String,
  username: String,
  name: Option[String],
  role: String,
  isIndependent: Option[Boolean] = None
)

case class ExamInfo(
  id: String,
  title: String,
  status: String,
  startTime: Option[LocalDateTime],
  endTime: Option[LocalDateTime],
  maxScore: Option[Double]
)

case class CoachStudentRelation(
  coachId: String,
  studentId: String,
  studentUsername: String
)

// Error models
case class ServiceError(
  code: String,
  message: String,
  details: Option[String] = None
)

object ServiceError {
  def unauthorized(message: String = "Unauthorized access"): ServiceError =
    ServiceError("UNAUTHORIZED", message)
    
  def forbidden(message: String = "Forbidden operation"): ServiceError =
    ServiceError("FORBIDDEN", message)
    
  def notFound(message: String = "Resource not found"): ServiceError =
    ServiceError("NOT_FOUND", message)
    
  def badRequest(message: String = "Bad request"): ServiceError =
    ServiceError("BAD_REQUEST", message)
    
  def internalError(message: String = "Internal server error"): ServiceError =
    ServiceError("INTERNAL_ERROR", message)
}
