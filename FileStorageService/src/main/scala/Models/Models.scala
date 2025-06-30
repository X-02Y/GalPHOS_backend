package Models

import java.time.LocalDateTime

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

// 文件信息
case class FileInfo(
  fileId: String,
  originalName: String,
  storedName: String,
  filePath: String,
  fileSize: Long,
  fileType: String,
  mimeType: Option[String],
  uploadUserId: Option[String],
  uploadUserType: Option[String],
  uploadTime: LocalDateTime,
  fileStatus: String,
  accessCount: Int,
  downloadCount: Int,
  lastAccessTime: Option[LocalDateTime],
  description: Option[String],
  fileHash: Option[String],
  relatedExamId: Option[String],
  relatedSubmissionId: Option[String],
  createdAt: LocalDateTime,
  updatedAt: LocalDateTime
)

// 文件上传请求
case class FileUploadRequest(
  examId: Option[String] = None,
  submissionId: Option[String] = None,
  description: Option[String] = None
)

// 文件上传响应
case class FileUploadResponse(
  fileId: String,
  originalName: String,
  fileSize: Long,
  fileType: String,
  uploadTime: LocalDateTime,
  success: Boolean,
  message: String
)

// 文件下载响应
case class FileDownloadResponse(
  fileId: String,
  originalName: String,
  fileContent: Array[Byte],
  fileSize: Long,
  mimeType: String,
  success: Boolean,
  message: String
)

// 文件导出响应
case class FileExportResponse(
  fileId: String,
  fileName: String,
  fileSize: Long,
  exportTime: LocalDateTime,
  success: Boolean,
  message: String
)

// 文件访问日志
case class FileAccessLog(
  logId: Int,
  fileId: String,
  accessUserId: Option[String],
  accessUserType: Option[String],
  accessTime: LocalDateTime,
  accessType: String,
  clientIp: Option[String],
  userAgent: Option[String],
  success: Boolean,
  errorMessage: Option[String]
)

// 统计信息
case class DashboardStats(
  totalFiles: Int,
  totalSize: Long,
  activeFiles: Int,
  deletedFiles: Int,
  todayUploads: Int,
  weekUploads: Int,
  monthUploads: Int
)

// 分页响应
case class PaginatedResponse[T](
  items: List[T],
  total: Int,
  page: Int,
  limit: Int
)

// 内部通信模型
case class InternalFileOperationRequest(
  operation: String, // upload, download, delete, info
  fileId: Option[String] = None,
  originalName: Option[String] = None,
  fileContent: Option[Array[Byte]] = None,
  fileType: Option[String] = None,
  mimeType: Option[String] = None,
  uploadUserId: Option[String] = None,
  uploadUserType: Option[String] = None,
  requestUserId: Option[String] = None,
  requestUserType: Option[String] = None,
  examId: Option[String] = None,
  submissionId: Option[String] = None,
  description: Option[String] = None,
  purpose: Option[String] = None // download, display, proxy 等
)

case class InternalFileOperationResponse(
  success: Boolean,
  fileId: Option[String] = None,
  originalName: Option[String] = None,
  fileSize: Option[Long] = None,
  fileContent: Option[Array[Byte]] = None,
  mimeType: Option[String] = None,
  uploadTime: Option[LocalDateTime] = None,
  error: Option[String] = None,
  fileInfo: Option[FileInfo] = None
)

// 微服务通信配置
case class MicroserviceConfig(
  examManagementUrl: String,
  submissionServiceUrl: String,
  gradingServiceUrl: String,
  userManagementUrl: String,
  authServiceUrl: String
)

// JSON 格式化器
import spray.json._
import DefaultJsonProtocol._
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object JsonProtocol extends DefaultJsonProtocol {
  
  // LocalDateTime 格式化器
  implicit val localDateTimeFormat: RootJsonFormat[LocalDateTime] = new RootJsonFormat[LocalDateTime] {
    def write(dateTime: LocalDateTime): JsValue = JsString(dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
    def read(value: JsValue): LocalDateTime = value match {
      case JsString(dateStr) => LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
      case _ => throw new DeserializationException("Expected datetime string")
    }
  }

  // 基础模型格式化器
  implicit val fileInfoFormat: RootJsonFormat[FileInfo] = jsonFormat20(FileInfo)
  implicit val fileUploadRequestFormat: RootJsonFormat[FileUploadRequest] = jsonFormat3(FileUploadRequest)
  implicit val fileUploadResponseFormat: RootJsonFormat[FileUploadResponse] = jsonFormat7(FileUploadResponse)
  implicit val fileDownloadResponseFormat: RootJsonFormat[FileDownloadResponse] = jsonFormat7(FileDownloadResponse)
  implicit val fileExportResponseFormat: RootJsonFormat[FileExportResponse] = jsonFormat6(FileExportResponse)
  implicit val dashboardStatsFormat: RootJsonFormat[DashboardStats] = jsonFormat7(DashboardStats)
  implicit val internalFileOperationResponseFormat: RootJsonFormat[InternalFileOperationResponse] = jsonFormat9(InternalFileOperationResponse)
  implicit val microserviceConfigFormat: RootJsonFormat[MicroserviceConfig] = jsonFormat5(MicroserviceConfig)
  
  // ApiResponse 格式化器（泛型）
  implicit def apiResponseFormat[T: JsonFormat]: RootJsonFormat[ApiResponse[T]] = jsonFormat3(ApiResponse[T])

  // 内部通信模型格式化器
  implicit val internalUploadRequestFormat: RootJsonFormat[InternalUploadRequest] = jsonFormat10(InternalUploadRequest)
  implicit val internalDownloadRequestFormat: RootJsonFormat[InternalDownloadRequest] = jsonFormat4(InternalDownloadRequest)
  implicit val internalUploadResponseFormat: RootJsonFormat[InternalUploadResponse] = jsonFormat6(InternalUploadResponse)
  implicit val internalDownloadResponseFormat: RootJsonFormat[InternalDownloadResponse] = jsonFormat6(InternalDownloadResponse)
  implicit val internalFileDeleteRequestFormat: RootJsonFormat[InternalFileDeleteRequest] = jsonFormat4(InternalFileDeleteRequest)
  implicit val internalBatchOperationRequestFormat: RootJsonFormat[InternalBatchOperationRequest] = jsonFormat5(InternalBatchOperationRequest)
}

// 内部通信请求/响应模型
case class InternalUploadRequest(
  originalName: String,
  fileContent: Array[Byte],
  fileType: String,
  mimeType: String,
  uploadUserId: Option[String],
  uploadUserType: Option[String],
  examId: Option[String] = None,
  submissionId: Option[String] = None,
  description: Option[String] = None,
  category: String // exam, submission, user_avatar, score_import 等
)

case class InternalDownloadRequest(
  fileId: String,
  requestUserId: Option[String],
  requestUserType: Option[String],
  purpose: String // download, display, proxy 等
)

case class InternalUploadResponse(
  success: Boolean,
  fileId: Option[String] = None,
  originalName: Option[String] = None,
  fileSize: Option[Long] = None,
  uploadTime: Option[LocalDateTime] = None,
  error: Option[String] = None
)

case class InternalDownloadResponse(
  success: Boolean,
  fileId: Option[String] = None,
  originalName: Option[String] = None,
  fileContent: Option[Array[Byte]] = None,
  mimeType: Option[String] = None,
  error: Option[String] = None
)

case class InternalFileDeleteRequest(
  fileId: String,
  requestUserId: Option[String],
  requestUserType: Option[String],
  reason: Option[String] = None
)

case class InternalBatchOperationRequest(
  operation: String, // delete, archive, restore
  fileIds: List[String],
  requestUserId: Option[String],
  requestUserType: Option[String],
  reason: Option[String] = None
)
