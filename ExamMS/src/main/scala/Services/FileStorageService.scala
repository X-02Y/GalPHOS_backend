package Services

import cats.effect.IO
import org.http4s.Request
import org.http4s.client.Client
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.Headers
import org.http4s.headers.{Authorization, `Content-Type`}
import org.http4s.MediaType
import io.circe.generic.auto.*
import Models.*
import Config.ServiceConfig
import org.slf4j.LoggerFactory
import java.util.Base64

trait FileStorageService {
  def uploadFile(request: FileStorageUploadRequest): IO[FileStorageResponse]
  def downloadFile(request: FileStorageDownloadRequest): IO[FileStorageResponse]
  def deleteFile(request: FileStorageDeleteRequest): IO[FileStorageResponse]
}

class FileStorageServiceImpl(config: ServiceConfig, client: Client[IO]) extends FileStorageService {
  private val logger = LoggerFactory.getLogger("FileStorageService")
  private val fileStorageConfig = config.fileStorageService
  private val baseUrl = s"http://${fileStorageConfig.host}:${fileStorageConfig.port}"

  override def uploadFile(request: FileStorageUploadRequest): IO[FileStorageResponse] = {
    val url = s"$baseUrl/internal/upload"
    
    val uploadRequest = Request[IO](
      method = org.http4s.Method.POST,
      uri = org.http4s.Uri.fromString(url).toOption.get,
      headers = Headers(
        Authorization(org.http4s.Credentials.Token(org.http4s.AuthScheme.Bearer, fileStorageConfig.internalApiKey)),
        `Content-Type`(MediaType.application.json)
      )
    ).withEntity(request)

    logger.info(s"Uploading file to FileStorage: ${request.originalName}")
    
    client.expect[FileStorageResponse](uploadRequest).handleErrorWith { error =>
      logger.error(s"Failed to upload file: ${error.getMessage}")
      IO.pure(FileStorageResponse(success = false, message = Some(s"Upload failed: ${error.getMessage}")))
    }
  }

  override def downloadFile(request: FileStorageDownloadRequest): IO[FileStorageResponse] = {
    val url = s"$baseUrl/internal/download"
    
    val downloadRequest = Request[IO](
      method = org.http4s.Method.POST,
      uri = org.http4s.Uri.fromString(url).toOption.get,
      headers = Headers(
        Authorization(org.http4s.Credentials.Token(org.http4s.AuthScheme.Bearer, fileStorageConfig.internalApiKey)),
        `Content-Type`(MediaType.application.json)
      )
    ).withEntity(request)

    logger.info(s"Downloading file from FileStorage: ${request.fileId}")
    
    client.expect[FileStorageResponse](downloadRequest).handleErrorWith { error =>
      logger.error(s"Failed to download file: ${error.getMessage}")
      IO.pure(FileStorageResponse(success = false, message = Some(s"Download failed: ${error.getMessage}")))
    }
  }

  override def deleteFile(request: FileStorageDeleteRequest): IO[FileStorageResponse] = {
    val url = s"$baseUrl/internal/delete"
    
    val deleteRequest = Request[IO](
      method = org.http4s.Method.POST,
      uri = org.http4s.Uri.fromString(url).toOption.get,
      headers = Headers(
        Authorization(org.http4s.Credentials.Token(org.http4s.AuthScheme.Bearer, fileStorageConfig.internalApiKey)),
        `Content-Type`(MediaType.application.json)
      )
    ).withEntity(request)

    logger.info(s"Deleting file from FileStorage: ${request.fileId}")
    
    client.expect[FileStorageResponse](deleteRequest).handleErrorWith { error =>
      logger.error(s"Failed to delete file: ${error.getMessage}")
      IO.pure(FileStorageResponse(success = false, message = Some(s"Delete failed: ${error.getMessage}")))
    }
  }
}

// 文件处理工具类
object FileUtils {
  def encodeFileToBase64(fileBytes: Array[Byte]): String = {
    Base64.getEncoder.encodeToString(fileBytes)
  }

  def decodeBase64ToFile(base64String: String): Array[Byte] = {
    Base64.getDecoder.decode(base64String)
  }

  def getMimeType(fileName: String): String = {
    val extension = fileName.split('.').lastOption.getOrElse("").toLowerCase
    extension match {
      case "pdf" => "application/pdf"
      case "doc" => "application/msword"
      case "docx" => "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
      case "jpg" | "jpeg" => "image/jpeg"
      case "png" => "image/png"
      case "gif" => "image/gif"
      case "txt" => "text/plain"
      case _ => "application/octet-stream"
    }
  }

  def isImageFile(fileName: String): Boolean = {
    val extension = fileName.split('.').lastOption.getOrElse("").toLowerCase
    Set("jpg", "jpeg", "png", "gif", "bmp", "webp").contains(extension)
  }

  def isDocumentFile(fileName: String): Boolean = {
    val extension = fileName.split('.').lastOption.getOrElse("").toLowerCase
    Set("pdf", "doc", "docx", "txt", "rtf").contains(extension)
  }

  def validateFileSize(fileSize: Long, maxSize: Long): Boolean = {
    fileSize <= maxSize
  }

  def validateFileType(fileName: String, allowedTypes: List[String]): Boolean = {
    val extension = fileName.split('.').lastOption.getOrElse("").toLowerCase
    allowedTypes.contains(extension)
  }
}
