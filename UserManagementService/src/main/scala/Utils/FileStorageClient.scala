package Utils

import cats.effect.IO
import cats.implicits.*
import io.circe.*
import io.circe.syntax.*
import io.circe.generic.auto.*
import org.http4s.*
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.multipart.*
import org.http4s.implicits.*
import org.http4s.headers.*
import org.slf4j.LoggerFactory
import java.io.{ByteArrayInputStream, InputStream}
import scala.concurrent.ExecutionContext.global
import java.util.concurrent.Executors
import scala.concurrent.duration.*
import Config.ServiceConfig
import fs2.Stream
import java.util.Base64

/**
 * FileStorageService 内部通信客户端
 * 负责与 FileStorageService 的内部接口进行通信
 */
class FileStorageClient(config: ServiceConfig) {
  private val logger = LoggerFactory.getLogger("FileStorageClient")
  
  private val fileStorageConfig = config.fileStorageService
  private val baseUrl = s"http://${fileStorageConfig.host}:${fileStorageConfig.port}"
  private val internalApiKey = fileStorageConfig.internalApiKey
  private val uploadMaxSize = fileStorageConfig.uploadMaxSize
  private val allowedImageTypes = fileStorageConfig.allowedImageTypes
  private val allowedDocumentTypes = fileStorageConfig.allowedDocumentTypes
  
  // 自定义Array[Byte]的Circe编码器/解码器
  implicit val byteArrayEncoder: Encoder[Array[Byte]] = Encoder.encodeString.contramap[Array[Byte]](
    bytes => Base64.getEncoder.encodeToString(bytes)
  )
  
  implicit val byteArrayDecoder: Decoder[Array[Byte]] = Decoder.decodeString.emap { str =>
    try {
      Right(Base64.getDecoder.decode(str))
    } catch {
      case ex: IllegalArgumentException => Left(s"Invalid Base64 string: ${ex.getMessage}")
    }
  }
  
  // 创建HTTP客户端
  private val httpClient: IO[Client[IO]] = {
    EmberClientBuilder.default[IO]
      .withTimeout(fileStorageConfig.timeout.milliseconds)
      .build
      .allocated
      .map(_._1)
  }

  // 内部通信请求/响应模型 - 与FileStorageService兼容
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

  case class InternalUploadResponse(
    success: Boolean,
    fileId: Option[String] = None,
    originalName: Option[String] = None,
    fileSize: Option[Long] = None,
    uploadTime: Option[String] = None,
    error: Option[String] = None
  )

  // 验证文件类型和大小
  private def validateFile(fileName: String, fileSize: Long, mimeType: String, category: String): IO[Unit] = {
    IO {
      // 文件大小验证
      if (fileSize > uploadMaxSize) {
        val maxSizeMB = uploadMaxSize / (1024 * 1024)
        throw new IllegalArgumentException(s"文件大小超过限制 ${maxSizeMB}MB")
      }

      // 文件类型验证
      val allowedTypes = category match {
        case "avatar" => allowedImageTypes
        case "answer-image" => allowedImageTypes
        case "document" => allowedDocumentTypes
        case _ => allowedImageTypes ++ allowedDocumentTypes
      }

      if (!allowedTypes.contains(mimeType)) {
        throw new IllegalArgumentException(s"不支持的文件类型: $mimeType")
      }

      // 文件名验证
      if (fileName.length > 255) {
        throw new IllegalArgumentException("文件名过长")
      }
    }
  }

  /**
   * 上传文件到 FileStorageService
   */
  def uploadFile(
    fileName: String,
    fileData: Array[Byte],
    mimeType: String,
    uploadUserId: String,
    uploadUserType: String,
    category: String,
    relatedId: Option[String] = None,
    description: Option[String] = None
  ): IO[InternalUploadResponse] = {
    for {
      _ <- validateFile(fileName, fileData.length.toLong, mimeType, category)
      client <- httpClient
      response <- performUpload(client, fileName, fileData, mimeType, uploadUserId, uploadUserType, category, relatedId, description)
    } yield response
  }

  private def performUpload(
    client: Client[IO],
    fileName: String,
    fileData: Array[Byte],
    mimeType: String,
    uploadUserId: String,
    uploadUserType: String,
    category: String,
    relatedId: Option[String],
    description: Option[String]
  ): IO[InternalUploadResponse] = {
    val uploadRequest = InternalUploadRequest(
      originalName = fileName,
      fileContent = fileData,
      fileType = getFileExtension(fileName),
      mimeType = mimeType,
      uploadUserId = Some(uploadUserId),
      uploadUserType = Some(uploadUserType),
      examId = relatedId,
      submissionId = None,
      description = description,
      category = category
    )

    val request = Request[IO](
      method = Method.POST,
      uri = Uri.unsafeFromString(s"$baseUrl/internal/upload"),
      headers = Headers(
        "X-Internal-API-Key" -> internalApiKey,
        "Content-Type" -> "application/json",
        "User-Agent" -> "UserManagementService/1.0.0"
      )
    ).withEntity(uploadRequest.asJson)

    client.expect[InternalUploadResponse](request).handleErrorWith { error =>
      logger.error(s"文件上传失败: ${error.getMessage}", error)
      IO.pure(InternalUploadResponse(
        success = false,
        error = Some(s"文件上传失败: ${error.getMessage}")
      ))
    }
  }

  /**
   * 删除文件
   */
  def deleteFile(fileId: String, userId: String, userType: String): IO[InternalUploadResponse] = {
    for {
      client <- httpClient
      response <- performDelete(client, fileId, userId, userType)
    } yield response
  }

  case class InternalFileDeleteRequest(
    fileId: String,
    requestUserId: Option[String],
    requestUserType: Option[String],
    reason: Option[String] = None
  )

  private def performDelete(client: Client[IO], fileId: String, userId: String, userType: String): IO[InternalUploadResponse] = {
    val deleteRequest = InternalFileDeleteRequest(
      fileId = fileId,
      requestUserId = Some(userId),
      requestUserType = Some(userType),
      reason = Some("User requested deletion")
    )

    val request = Request[IO](
      method = Method.POST,
      uri = Uri.unsafeFromString(s"$baseUrl/internal/delete"),
      headers = Headers(
        "X-Internal-API-Key" -> internalApiKey,
        "Content-Type" -> "application/json",
        "User-Agent" -> "UserManagementService/1.0.0"
      )
    ).withEntity(deleteRequest.asJson)

    client.expect[InternalUploadResponse](request).handleErrorWith { error =>
      logger.error(s"文件删除失败: ${error.getMessage}", error)
      IO.pure(InternalUploadResponse(
        success = false,
        error = Some(s"文件删除失败: ${error.getMessage}")
      ))
    }
  }

  /**
   * 获取文件信息
   */
  def getFileInfo(fileId: String): IO[Option[String]] = {
    for {
      client <- httpClient
      response <- performGetFileInfo(client, fileId)
    } yield response
  }

  private def performGetFileInfo(client: Client[IO], fileId: String): IO[Option[String]] = {
    val request = Request[IO](
      method = Method.GET,
      uri = Uri.unsafeFromString(s"$baseUrl/internal/info/$fileId"),
      headers = Headers(
        "X-Internal-API-Key" -> internalApiKey,
        "User-Agent" -> "UserManagementService/1.0.0"
      )
    )

    client.expect[InternalUploadResponse](request).map { response =>
      if (response.success) response.fileId else None
    }.handleErrorWith { error =>
      logger.error(s"获取文件信息失败: ${error.getMessage}", error)
      IO.pure(None)
    }
  }

  // 工具方法：获取文件扩展名
  private def getFileExtension(fileName: String): String = {
    val lastDotIndex = fileName.lastIndexOf('.')
    if (lastDotIndex > 0 && lastDotIndex < fileName.length - 1) {
      fileName.substring(lastDotIndex + 1).toLowerCase
    } else {
      "unknown"
    }
  }

  /**
   * 下载文件并转换为Base64字符串
   */
  def downloadFileAsBase64(fileId: String, userId: String, userType: String): IO[String] = {
    for {
      client <- httpClient
      base64Data <- performDownloadAsBase64(client, fileId, userId, userType)
    } yield base64Data
  }

  private def performDownloadAsBase64(client: Client[IO], fileId: String, userId: String, userType: String): IO[String] = {
    val request = Request[IO](
      method = Method.GET,
      uri = Uri.unsafeFromString(s"$baseUrl/api/$userType/files/download/$fileId"),
      headers = Headers(
        "X-Internal-API-Key" -> internalApiKey,
        "User-Agent" -> "UserManagementService/1.0.0"
      )
    )

    client.expect[Array[Byte]](request).map { bytes =>
      Base64.getEncoder.encodeToString(bytes)
    }.handleErrorWith { error =>
      logger.error(s"下载文件失败: ${error.getMessage}", error)
      IO.raiseError(error)
    }
  }

  /**
   * 健康检查
   */
  def healthCheck(): IO[Boolean] = {
    for {
      client <- httpClient
      result <- performHealthCheck(client)
    } yield result
  }

  private def performHealthCheck(client: Client[IO]): IO[Boolean] = {
    val request = Request[IO](
      method = Method.GET,
      uri = Uri.unsafeFromString(s"$baseUrl/internal/health"),
      headers = Headers(
        "X-Internal-API-Key" -> internalApiKey,
        "User-Agent" -> "UserManagementService/1.0.0"
      )
    )

    client.status(request).map(_ == Status.Ok).handleErrorWith { _ =>
      IO.pure(false)
    }
  }
}

object FileStorageClient {
  def apply(config: ServiceConfig): FileStorageClient = new FileStorageClient(config)
}
