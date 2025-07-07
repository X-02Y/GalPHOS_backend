package Models

import io.circe.{Decoder, Encoder}
import io.circe.generic.auto.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// JSON encoders/decoders for LocalDateTime
given Encoder[LocalDateTime] = 
  Encoder.encodeString.contramap(_.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))

given Decoder[LocalDateTime] = 
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

  def successWithMessage[T](data: T, message: String): ApiResponse[T] =
    ApiResponse(success = true, data = Some(data), message = Some(message))

  def error[T](message: String): ApiResponse[T] =
    ApiResponse(success = false, data = None, message = Some(message))
}

// Pagination response
case class PaginatedResponse[T](
  files: List[T],
  pagination: PaginationInfo
)

case class PaginationInfo(
  page: Int,
  limit: Int,
  total: Int,
  totalPages: Int
)

// File category enumeration
enum FileCategory(val value: String):
  case Avatar extends FileCategory("avatar")
  case AnswerImage extends FileCategory("answer-image")
  case ExamFile extends FileCategory("exam-file")
  case Document extends FileCategory("document")
  case Other extends FileCategory("other")

object FileCategory {
  def fromString(category: String): FileCategory = category.toLowerCase match {
    case "avatar" => Avatar
    case "answer-image" => AnswerImage
    case "exam-file" => ExamFile
    case "document" => Document
    case "other" => Other
    case _ => Other
  }

  given Encoder[FileCategory] = Encoder.encodeString.contramap(_.value)
  given Decoder[FileCategory] = Decoder.decodeString.map(fromString)
}

// File type enumeration
enum FileType(val value: String):
  case Question extends FileType("question")
  case Answer extends FileType("answer")
  case AnswerSheet extends FileType("answerSheet")
  case Image extends FileType("image")
  case Document extends FileType("document")
  case Other extends FileType("other")

object FileType {
  def fromString(fileType: String): FileType = fileType.toLowerCase match {
    case "question" => Question
    case "answer" => Answer
    case "answersheet" => AnswerSheet
    case "image" => Image
    case "document" => Document
    case _ => Other
  }

  given Encoder[FileType] = Encoder.encodeString.contramap(_.value)
  given Decoder[FileType] = Decoder.decodeString.map(fromString)
}

// File upload result (frontend interface)
case class FileUploadResult(
  fileId: String,
  fileName: String,
  fileUrl: String,
  fileSize: Long,
  fileType: String,
  uploadTime: String
)

// File record for database and API responses
case class FileRecord(
  id: String,
  fileName: String,
  originalName: String,
  fileUrl: String,
  fileSize: Long,
  mimeType: String,
  fileType: Option[String] = None,
  category: Option[String] = None,
  examId: Option[String] = None,
  questionNumber: Option[Int] = None,
  studentId: Option[String] = None,
  uploadedBy: String,
  uploadTime: LocalDateTime
) {
  def toFileUploadResult: FileUploadResult = FileUploadResult(
    fileId = id,
    fileName = originalName,
    fileUrl = fileUrl,
    fileSize = fileSize,
    fileType = fileType.getOrElse("other"),
    uploadTime = uploadTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
  )
}

// Exam file interface (frontend)
case class ExamFile(
  id: String,
  name: String,
  filename: Option[String] = None,
  originalName: Option[String] = None,
  url: String,
  size: Long,
  uploadTime: String,
  uploadedAt: Option[String] = None,
  mimetype: Option[String] = None,
  `type`: Option[String] = None
)

// Upload request models
case class FileUploadRequest(
  category: String,
  relatedId: Option[String] = None,
  questionNumber: Option[Int] = None,
  studentUsername: Option[String] = None,
  timestamp: Option[String] = None
)

// Grading image response
case class GradingImage(
  imageUrl: String,
  fileName: String,
  examId: String,
  studentId: String,
  questionNumber: Int,
  uploadTime: String
)

// JWT claims
case class UserClaims(
  userId: String,
  username: String,
  role: String,
  exp: Long
)

// Internal file transfer models (for microservice communication)
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
