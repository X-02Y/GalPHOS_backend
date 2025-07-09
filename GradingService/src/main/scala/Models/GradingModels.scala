package Models

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import java.time.{LocalDateTime, ZonedDateTime}

// 阅卷员信息
case class Grader(
  id: String, // 改为String类型以支持UUID
  username: String,
  fullName: String,
  email: String,
  phone: Option[String],
  status: String,
  createdAt: LocalDateTime,
  updatedAt: LocalDateTime
)

// 阅卷员信息（包含统计数据）- 对应前端GraderInfo
case class GraderInfo(
  id: String,
  username: String,
  phone: Option[String],
  status: String, // available, busy, offline
  currentTasks: Int,    // 阅卷队列数（当前待处理任务数）
  completedTasks: Int   // 已完成数（已完成的阅卷任务数）
)

// 已结束考试信息
case class EndedExamInfo(
  examId: String,
  submissionCount: Int,
  questionCount: Int
)

// 阅卷任务
case class GradingTask(
  id: Long,
  examId: String, // 改为String类型以支持UUID
  submissionId: String, // 改为String类型以支持UUID
  graderId: Option[String], // 改为String类型以支持UUID
  questionNumber: Int,
  status: String,
  maxScore: BigDecimal,
  actualScore: Option[BigDecimal],
  feedback: Option[String],
  assignedAt: Option[LocalDateTime],
  startedAt: Option[LocalDateTime],
  completedAt: Option[LocalDateTime],
  createdAt: LocalDateTime,
  updatedAt: LocalDateTime
)

// 题目分数配置
case class QuestionScore(
  examId: Long,
  questionNumber: Int,
  maxScore: BigDecimal,
  questionType: String,
  description: Option[String],
  createdAt: LocalDateTime,
  updatedAt: LocalDateTime
)

// 阅卷进度
case class GradingProgress(
  examId: String, // 改为String以支持UUID
  totalTasks: Int,
  completedTasks: Int,
  inProgressTasks: Int,
  pendingTasks: Int,
  completionPercentage: BigDecimal
)

// 非独立学生（教练管理的学生）
case class CoachStudent(
  id: Long,
  coachId: Long,
  studentName: String,
  studentSchool: String,
  studentProvince: String,
  grade: Option[String],
  isActive: Boolean,
  createdAt: LocalDateTime,
  updatedAt: LocalDateTime
)

// 评分历史
case class ScoreHistory(
  id: Long,
  taskId: Long,
  graderId: String, // 改为String类型以支持UUID
  score: BigDecimal,
  feedback: Option[String],
  createdAt: LocalDateTime
)

// 阅卷任务分配请求
case class TaskAssignmentRequest(
  examId: String, // 改为String类型以支持UUID
  graderId: String, // UUID字符串
  questionIds: List[String] // 支持前端的questionIds字段名，改为String列表
)

// 阅卷任务详情（包含提交信息）
case class GradingTaskDetail(
  task: GradingTask,
  examTitle: String,
  studentName: String,
  submissionContent: Option[String],
  questionScores: List[QuestionScore]
)

// 管理员阅卷任务视图（包含考试名称和阅卷员姓名）
case class AdminGradingTask(
  id: Long,
  examId: String,
  examTitle: String, // 考试名称
  submissionId: String,
  graderId: Option[String],
  graderName: Option[String], // 阅卷员姓名
  questionNumber: Int,
  status: String,
  maxScore: Option[BigDecimal],
  actualScore: Option[BigDecimal],
  feedback: Option[String],
  assignedAt: Option[LocalDateTime],
  startedAt: Option[LocalDateTime],
  completedAt: Option[LocalDateTime],
  createdAt: LocalDateTime,
  updatedAt: LocalDateTime
)

// 开始阅卷请求
case class StartGradingRequest(
  taskId: Long
)

// 提交阅卷结果请求
case class SubmitGradingRequest(
  taskId: Long,
  scores: Map[Int, BigDecimal], // questionNumber -> score
  feedback: Option[String]
)

// 保存阅卷进度请求
case class SaveProgressRequest(
  taskId: Long,
  questionNumber: Int,
  score: BigDecimal,
  feedback: Option[String]
)

// 创建/更新题目分数请求
case class QuestionScoreRequest(
  questionNumber: Int,
  maxScore: BigDecimal,
  questionType: String,
  description: Option[String]
)

// 更新单题分数请求
case class UpdateQuestionScoreRequest(
  maxScore: BigDecimal,
  description: Option[String]
)

// 创建教练学生请求
case class CreateCoachStudentRequest(
  studentName: String,
  studentSchool: String,
  studentProvince: String,
  grade: Option[String]
)

// 更新教练学生请求
case class UpdateCoachStudentRequest(
  studentName: Option[String],
  studentSchool: Option[String],
  studentProvince: Option[String],
  grade: Option[String],
  isActive: Option[Boolean]
)

// 通用响应
case class ApiResponse[T](
  success: Boolean,
  message: String,
  data: Option[T] = None
)

case class PaginatedResponse[T](
  success: Boolean,
  message: String,
  data: List[T],
  total: Long,
  page: Int,
  pageSize: Int
)

// JSON编解码器
import io.circe.generic.auto.given

// 阅卷图片信息
case class GradingImage(
  imageUrl: String,
  fileName: String,
  examId: Long,
  studentId: Long,
  questionNumber: Int,
  uploadTime: LocalDateTime
)

// 带内容的阅卷图片信息
case class GradingImageWithContent(
  imageUrl: String,
  fileName: String,
  examId: Long,
  studentId: Long,
  questionNumber: Int,
  uploadTime: LocalDateTime,
  base64Content: Option[String]
)

// 图片查询请求
case class ImageQueryRequest(
  examId: Long,
  studentId: Option[Long] = None,
  questionNumber: Option[Int] = None
)

// 外部服务数据模型
case class ExamData(
  id: Long,
  title: String,
  description: Option[String],
  questionCount: Option[Int],
  createdAt: String
)

case class FileStorageImage(
  id: String,
  fileName: String,
  fileUrl: String,
  examId: Long,
  studentId: Long,
  questionNumber: Int,
  uploadTime: String,
  fileSize: Option[Long],
  contentType: Option[String]
)

case class ExternalApiResponse[T](
  success: Boolean,
  message: String,
  data: Option[T] = None
)

// 隐式JSON编解码器
object Implicits {
  implicit val graderEncoder: Encoder[Grader] = deriveEncoder[Grader]
  implicit val graderDecoder: Decoder[Grader] = deriveDecoder[Grader]
  
  implicit val graderInfoEncoder: Encoder[GraderInfo] = deriveEncoder[GraderInfo]
  implicit val graderInfoDecoder: Decoder[GraderInfo] = deriveDecoder[GraderInfo]
  
  implicit val gradingTaskEncoder: Encoder[GradingTask] = deriveEncoder[GradingTask]
  implicit val gradingTaskDecoder: Decoder[GradingTask] = deriveDecoder[GradingTask]
  
  implicit val questionScoreEncoder: Encoder[QuestionScore] = deriveEncoder[QuestionScore]
  implicit val questionScoreDecoder: Decoder[QuestionScore] = deriveDecoder[QuestionScore]
  
  implicit val gradingProgressEncoder: Encoder[GradingProgress] = deriveEncoder[GradingProgress]
  implicit val gradingProgressDecoder: Decoder[GradingProgress] = deriveDecoder[GradingProgress]
  
  implicit val coachStudentEncoder: Encoder[CoachStudent] = deriveEncoder[CoachStudent]
  implicit val coachStudentDecoder: Decoder[CoachStudent] = deriveDecoder[CoachStudent]
  
  implicit val scoreHistoryEncoder: Encoder[ScoreHistory] = deriveEncoder[ScoreHistory]
  implicit val scoreHistoryDecoder: Decoder[ScoreHistory] = deriveDecoder[ScoreHistory]
  
  implicit val taskAssignmentRequestEncoder: Encoder[TaskAssignmentRequest] = deriveEncoder[TaskAssignmentRequest]
  implicit val taskAssignmentRequestDecoder: Decoder[TaskAssignmentRequest] = deriveDecoder[TaskAssignmentRequest]
  
  implicit val gradingTaskDetailEncoder: Encoder[GradingTaskDetail] = deriveEncoder[GradingTaskDetail]
  implicit val gradingTaskDetailDecoder: Decoder[GradingTaskDetail] = deriveDecoder[GradingTaskDetail]
  
  implicit val adminGradingTaskEncoder: Encoder[AdminGradingTask] = deriveEncoder[AdminGradingTask]
  implicit val adminGradingTaskDecoder: Decoder[AdminGradingTask] = deriveDecoder[AdminGradingTask]
  
  implicit val startGradingRequestEncoder: Encoder[StartGradingRequest] = deriveEncoder[StartGradingRequest]
  implicit val startGradingRequestDecoder: Decoder[StartGradingRequest] = deriveDecoder[StartGradingRequest]
  
  implicit val submitGradingRequestEncoder: Encoder[SubmitGradingRequest] = deriveEncoder[SubmitGradingRequest]
  implicit val submitGradingRequestDecoder: Decoder[SubmitGradingRequest] = deriveDecoder[SubmitGradingRequest]
  
  implicit val saveProgressRequestEncoder: Encoder[SaveProgressRequest] = deriveEncoder[SaveProgressRequest]
  implicit val saveProgressRequestDecoder: Decoder[SaveProgressRequest] = deriveDecoder[SaveProgressRequest]
  
  implicit val questionScoreRequestEncoder: Encoder[QuestionScoreRequest] = deriveEncoder[QuestionScoreRequest]
  implicit val questionScoreRequestDecoder: Decoder[QuestionScoreRequest] = deriveDecoder[QuestionScoreRequest]
  
  implicit val updateQuestionScoreRequestEncoder: Encoder[UpdateQuestionScoreRequest] = deriveEncoder[UpdateQuestionScoreRequest]
  implicit val updateQuestionScoreRequestDecoder: Decoder[UpdateQuestionScoreRequest] = deriveDecoder[UpdateQuestionScoreRequest]
  
  implicit val createCoachStudentRequestEncoder: Encoder[CreateCoachStudentRequest] = deriveEncoder[CreateCoachStudentRequest]
  implicit val createCoachStudentRequestDecoder: Decoder[CreateCoachStudentRequest] = deriveDecoder[CreateCoachStudentRequest]
  
  implicit val updateCoachStudentRequestEncoder: Encoder[UpdateCoachStudentRequest] = deriveEncoder[UpdateCoachStudentRequest]
  implicit val updateCoachStudentRequestDecoder: Decoder[UpdateCoachStudentRequest] = deriveDecoder[UpdateCoachStudentRequest]
  
  implicit val gradingImageEncoder: Encoder[GradingImage] = deriveEncoder[GradingImage]
  implicit val gradingImageDecoder: Decoder[GradingImage] = deriveDecoder[GradingImage]

  implicit val gradingImageWithContentEncoder: Encoder[GradingImageWithContent] = deriveEncoder[GradingImageWithContent]
  implicit val gradingImageWithContentDecoder: Decoder[GradingImageWithContent] = deriveDecoder[GradingImageWithContent]

  implicit val imageQueryRequestEncoder: Encoder[ImageQueryRequest] = deriveEncoder[ImageQueryRequest]
  implicit val imageQueryRequestDecoder: Decoder[ImageQueryRequest] = deriveDecoder[ImageQueryRequest]
  
  // 外部服务模型编解码器
  implicit val examDataEncoder: Encoder[ExamData] = deriveEncoder[ExamData]
  implicit val examDataDecoder: Decoder[ExamData] = deriveDecoder[ExamData]
  
  implicit val fileStorageImageEncoder: Encoder[FileStorageImage] = deriveEncoder[FileStorageImage]
  implicit val fileStorageImageDecoder: Decoder[FileStorageImage] = deriveDecoder[FileStorageImage]
  
  implicit def externalApiResponseEncoder[T: Encoder]: Encoder[ExternalApiResponse[T]] = deriveEncoder[ExternalApiResponse[T]]
  implicit def externalApiResponseDecoder[T: Decoder]: Decoder[ExternalApiResponse[T]] = deriveDecoder[ExternalApiResponse[T]]
  
  implicit def apiResponseEncoder[T: Encoder]: Encoder[ApiResponse[T]] = deriveEncoder[ApiResponse[T]]
  implicit def apiResponseDecoder[T: Decoder]: Decoder[ApiResponse[T]] = deriveDecoder[ApiResponse[T]]
  
  implicit def paginatedResponseEncoder[T: Encoder]: Encoder[PaginatedResponse[T]] = deriveEncoder[PaginatedResponse[T]]
  implicit def paginatedResponseDecoder[T: Decoder]: Decoder[PaginatedResponse[T]] = deriveDecoder[PaginatedResponse[T]]
}
