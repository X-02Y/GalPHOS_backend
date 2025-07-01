package Models

import io.circe.{Decoder, Encoder, Json}
import io.circe.syntax.*
import io.circe.generic.semiauto.{deriveEncoder, deriveDecoder}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

// Custom encoders and decoders for LocalDateTime
object CustomCodecs {
  private val dateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
  
  implicit val encodeLocalDateTime: Encoder[LocalDateTime] = new Encoder[LocalDateTime] {
    final def apply(dt: LocalDateTime): Json = Json.fromString(dt.format(dateTimeFormatter))
  }
  
  implicit val decodeLocalDateTime: Decoder[LocalDateTime] = Decoder.decodeString.emap { str =>
    try {
      Right(LocalDateTime.parse(str, dateTimeFormatter))
    } catch {
      case e: Exception => Left(s"Failed to decode LocalDateTime: ${e.getMessage}")
    }
  }
}

// 统一API响应格式
case class ApiResponse[T](
  success: Boolean,
  data: Option[T] = None,
  message: Option[String] = None
)

object ApiResponse {
  def success[T](data: T, message: String = "操作成功"): ApiResponse[T] =
    ApiResponse(success = true, data = Some(data), message = Some(message))

  def error(message: String): ApiResponse[Nothing] =
    ApiResponse(success = false, message = Some(message))
    
  // Provide explicit encoders for ApiResponse
  implicit def encoder[T](implicit enc: Encoder[T]): Encoder[ApiResponse[T]] = new Encoder[ApiResponse[T]] {
    final def apply(a: ApiResponse[T]): Json = Json.obj(
      ("success", Json.fromBoolean(a.success)),
      ("data", a.data.fold(Json.Null)(d => enc(d))),
      ("message", a.message.fold(Json.Null)(m => Json.fromString(m)))
    )
  }
  
  // For ApiResponse[Nothing] which is used by error()
  implicit val encoderNothing: Encoder[ApiResponse[Nothing]] = new Encoder[ApiResponse[Nothing]] {
    final def apply(a: ApiResponse[Nothing]): Json = Json.obj(
      ("success", Json.fromBoolean(a.success)),
      ("data", Json.Null),
      ("message", a.message.fold(Json.Null)(m => Json.fromString(m)))
    )
  }
  
  // For null data
  implicit val encodeNull: Encoder[Null] = new Encoder[Null] {
    final def apply(a: Null): Json = Json.Null
  }
}

// 提交状态枚举
enum SubmissionStatus(val value: String):
  case Submitted extends SubmissionStatus("submitted")
  case Graded extends SubmissionStatus("graded")
  case Cancelled extends SubmissionStatus("cancelled")

object SubmissionStatus {
  def fromString(status: String): SubmissionStatus = status.toLowerCase match {
    case "submitted" => Submitted
    case "graded" => Graded
    case "cancelled" => Cancelled
    case _ => throw new IllegalArgumentException(s"未知状态: $status")
  }

  implicit val encoder: Encoder[SubmissionStatus] = new Encoder[SubmissionStatus] {
    final def apply(a: SubmissionStatus): Json = Json.fromString(a.value)
  }
  
  implicit val decoder: Decoder[SubmissionStatus] = Decoder.decodeString.emap { str =>
    try Right(SubmissionStatus.fromString(str))
    catch case _: IllegalArgumentException => Left(s"Invalid status: $str")
  }
}

// 答案详情
case class SubmissionAnswer(
  questionNumber: Int,
  questionId: Option[String] = None,
  answerText: Option[String] = None,
  answerImageUrl: Option[String] = None,
  uploadTime: LocalDateTime,
  score: Option[BigDecimal] = None,
  maxScore: Option[BigDecimal] = None,
  graderFeedback: Option[String] = None
)

object SubmissionAnswer {
  import CustomCodecs._
  implicit val encoder: Encoder[SubmissionAnswer] = deriveEncoder[SubmissionAnswer]
  implicit val decoder: Decoder[SubmissionAnswer] = deriveDecoder[SubmissionAnswer]
}

// 考试提交记录
case class ExamSubmission(
  id: String,
  examId: String,
  studentId: String,
  studentUsername: String,
  coachId: Option[String] = None,
  isProxySubmission: Boolean = false,
  submissionTime: LocalDateTime,
  status: SubmissionStatus,
  totalScore: Option[BigDecimal] = None,
  maxScore: Option[BigDecimal] = None,
  feedback: Option[String] = None,
  answers: List[SubmissionAnswer] = List.empty
)

object ExamSubmission {
  import CustomCodecs._
  implicit val encoder: Encoder[ExamSubmission] = deriveEncoder[ExamSubmission]
  implicit val decoder: Decoder[ExamSubmission] = deriveDecoder[ExamSubmission]
}

// 学生提交答案请求
case class StudentSubmitRequest(
  answers: List[StudentAnswerRequest]
)

object StudentSubmitRequest {
  import CustomCodecs._ 
  implicit val encoder: Encoder[StudentSubmitRequest] = deriveEncoder[StudentSubmitRequest]
  implicit val decoder: Decoder[StudentSubmitRequest] = deriveDecoder[StudentSubmitRequest]
}

case class StudentAnswerRequest(
  questionNumber: Int,
  imageUrl: String,
  uploadTime: LocalDateTime
)

object StudentAnswerRequest {
  import CustomCodecs._
  implicit val encoder: Encoder[StudentAnswerRequest] = deriveEncoder[StudentAnswerRequest]
  implicit val decoder: Decoder[StudentAnswerRequest] = deriveDecoder[StudentAnswerRequest]
}

// 教练代理提交请求
case class CoachSubmitRequest(
  studentUsername: String,
  answers: List[StudentAnswerRequest]
)

object CoachSubmitRequest {
  import CustomCodecs._
  implicit val encoder: Encoder[CoachSubmitRequest] = deriveEncoder[CoachSubmitRequest]
  implicit val decoder: Decoder[CoachSubmitRequest] = deriveDecoder[CoachSubmitRequest]
}

// 文件上传请求
case class FileUploadRequest(
  examId: String,
  questionNumber: Int,
  studentUsername: Option[String] = None // 教练代理提交时需要
)

// 文件信息
case class FileInfo(
  id: String,
  originalName: String,
  fileType: String,
  fileSize: Long,
  fileUrl: String,
  uploadTime: LocalDateTime
)

// 用户信息（从认证服务获取）
case class UserInfo(
  username: String,
  role: String,
  province: Option[String] = None,
  school: Option[String] = None
)

// JWT Token载荷
case class TokenPayload(
  username: String,
  role: String,
  exp: Long
)

// 考试信息（从考试服务获取）
case class ExamInfo(
  id: String,
  title: String,
  status: String,
  startTime: LocalDateTime,
  endTime: LocalDateTime,
  totalQuestions: Int
)

// 阅卷进度信息
case class GradingProgress(
  examId: String,
  totalSubmissions: Int,
  gradedSubmissions: Int,
  averageScore: Option[BigDecimal] = None,
  gradingStats: Map[String, Any] = Map.empty
)

object GradingProgress {
  // Custom encoder for Map[String, Any] which otherwise doesn't have a built-in encoder
  implicit val encodeMapStringAny: Encoder[Map[String, Any]] = new Encoder[Map[String, Any]] {
    final def apply(map: Map[String, Any]): Json = {
      val jsonMap = map.map { case (key, value) => 
        val jsonValue = value match {
          case s: String => Json.fromString(s)
          case n: Int => Json.fromInt(n)
          case n: Double => Json.fromDoubleOrNull(n)
          case n: BigDecimal => Json.fromBigDecimal(n)
          case b: Boolean => Json.fromBoolean(b)
          case null => Json.Null
          case _ => Json.fromString(value.toString)
        }
        key -> jsonValue
      }
      Json.obj(jsonMap.toSeq*)
    }
  }
  
  implicit val encoder: Encoder[GradingProgress] = new Encoder[GradingProgress] {
    final def apply(a: GradingProgress): Json = Json.obj(
      ("examId", Json.fromString(a.examId)),
      ("totalSubmissions", Json.fromInt(a.totalSubmissions)),
      ("gradedSubmissions", Json.fromInt(a.gradedSubmissions)),
      ("averageScore", a.averageScore.fold(Json.Null)(score => Json.fromBigDecimal(score))),
      ("gradingStats", encodeMapStringAny(a.gradingStats))
    )
  }
  
  implicit val decoder: Decoder[GradingProgress] = new Decoder[GradingProgress] {
    final def apply(c: io.circe.HCursor): io.circe.Decoder.Result[GradingProgress] = for {
      examId <- c.downField("examId").as[String]
      totalSubmissions <- c.downField("totalSubmissions").as[Int]
      gradedSubmissions <- c.downField("gradedSubmissions").as[Int]
      averageScore <- c.downField("averageScore").as[Option[BigDecimal]]
    } yield GradingProgress(
      examId = examId,
      totalSubmissions = totalSubmissions,
      gradedSubmissions = gradedSubmissions,
      averageScore = averageScore
    )
  }
}

// 数据库模型
case class DbExamSubmission(
  id: UUID,
  examId: UUID,
  studentId: String,
  studentUsername: String,
  coachId: Option[String],
  isProxySubmission: Boolean,
  submissionTime: LocalDateTime,
  status: String,
  totalScore: Option[BigDecimal],
  maxScore: Option[BigDecimal],
  feedback: Option[String],
  createdAt: LocalDateTime,
  updatedAt: LocalDateTime
)

case class DbSubmissionAnswer(
  id: UUID,
  submissionId: UUID,
  questionNumber: Int,
  questionId: Option[String],
  answerText: Option[String],
  answerImageUrl: Option[String],
  uploadTime: LocalDateTime,
  score: Option[BigDecimal],
  maxScore: Option[BigDecimal],
  graderFeedback: Option[String],
  createdAt: LocalDateTime,
  updatedAt: LocalDateTime
)

case class DbSubmissionFile(
  id: UUID,
  submissionId: Option[UUID],
  fileStorageId: String,
  originalName: String,
  fileType: String,
  fileSize: Long,
  uploadUserId: String,
  uploadUserType: String,
  uploadTime: LocalDateTime,
  fileUrl: String,
  createdAt: LocalDateTime
)

// 教练文件上传请求
case class CoachFileUploadRequest(
  questionNumber: Int,
  studentUsername: String,
  fileName: String,
  fileContent: String // Base64 编码的文件内容
)

object CoachFileUploadRequest {
  implicit val encoder: Encoder[CoachFileUploadRequest] = deriveEncoder[CoachFileUploadRequest]
  implicit val decoder: Decoder[CoachFileUploadRequest] = deriveDecoder[CoachFileUploadRequest]
}
