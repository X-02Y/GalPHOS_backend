package Services

import cats.effect.IO
import org.http4s.Request
import org.http4s.client.Client
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.Headers
import org.http4s.headers.{Authorization, `Content-Type`}
import org.http4s.MediaType
import io.circe.generic.auto.*
import io.circe.syntax.*
import io.circe.Json
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
    
    logger.info(s"[FileStorageService] Starting file upload process")
    logger.info(s"[FileStorageService] File name: ${request.originalName}")
    logger.info(s"[FileStorageService] FileStorage URL: $url")
    logger.info(s"[FileStorageService] Request details - examId: ${request.examId}, fileType: ${request.fileType}")
    logger.info(s"[FileStorageService] File content size: ${request.fileContent.length} integers")
    logger.info(s"[FileStorageService] Upload user: ${request.uploadUserId} (${request.uploadUserType})")
    
    // Convert List[Int] to Array[Byte] for compatibility with FileStorageService
    val fileContentBytes = request.fileContent.map(_.toByte).toArray
    val base64Content = Base64.getEncoder.encodeToString(fileContentBytes)
    
    logger.info(s"[FileStorageService] Converted to byte array size: ${fileContentBytes.length}")
    logger.info(s"[FileStorageService] Base64 content length: ${base64Content.length}")
    logger.debug(s"[FileStorageService] Base64 content preview: ${base64Content.take(100)}...")
    
    // Create the request format exactly as expected by FileStorageService Spray JSON
    // Spray JSON converts Array[Byte] to JSON array of integers, not base64 string
    val fileContentJsonArray = fileContentBytes.map(_.toInt).mkString("[", ",", "]")
    
    val jsonPayload = s"""{
      "originalName": "${request.originalName.replace("\"", "\\\"")}",
      "fileContent": $fileContentJsonArray,
      "fileType": "${request.fileType}",
      "mimeType": "${request.mimeType}",
      "uploadUserId": ${request.uploadUserId match {
        case userId if userId.nonEmpty => s""""$userId""""
        case _ => "null"
      }},
      "uploadUserType": ${request.uploadUserType match {
        case userType if userType.nonEmpty => s""""$userType""""
        case _ => "null"
      }},
      "examId": ${request.examId.map(id => s""""$id"""").getOrElse("null")},
      "submissionId": ${request.submissionId.map(id => s""""$id"""").getOrElse("null")},
      "description": ${request.description.map(desc => s""""${desc.replace("\"", "\\\"").replace("考试", "exam").replace("文件", "file")}"""").getOrElse("null")},
      "category": "${request.category}"
    }"""
    
    logger.info(s"[FileStorageService] Prepared JSON request")
    logger.info(s"[FileStorageService] JSON payload length: ${jsonPayload.length}")
    logger.debug(s"[FileStorageService] JSON payload (first 500 chars): ${jsonPayload.take(500)}...")
    logger.debug(s"[FileStorageService] JSON payload (last 500 chars): ...${jsonPayload.takeRight(500)}")
    
    // Let's also log the structure without the large file content array for debugging
    val debugJsonWithoutContent = jsonPayload.replace(fileContentJsonArray, s"[<byte-array-${fileContentBytes.length}-elements>]")
    logger.info(s"[FileStorageService] Request structure: $debugJsonWithoutContent")
    
    val uploadRequest = Request[IO](
      method = org.http4s.Method.POST,
      uri = org.http4s.Uri.fromString(url).toOption.get,
      headers = Headers(
        `Content-Type`(MediaType.application.json)
      )
    ).withEntity(jsonPayload)

    logger.info(s"[FileStorageService] Sending HTTP POST request to FileStorage")
    
    client.expect[String](uploadRequest).flatMap { responseBody =>
      logger.info(s"[FileStorageService] Received response from FileStorage")
      logger.info(s"[FileStorageService] Response body: $responseBody")
      
      // Parse the response manually since it's from Spray JSON
      io.circe.parser.parse(responseBody) match {
        case Right(json) =>
          logger.info(s"[FileStorageService] Successfully parsed JSON response")
          val cursor = json.hcursor
          val success = cursor.downField("success").as[Boolean].getOrElse(false)
          
          logger.info(s"[FileStorageService] Response success status: $success")
          
          if (success) {
            val fileId = cursor.downField("fileId").as[String].toOption
            val originalName = cursor.downField("originalName").as[String].toOption
            
            logger.info(s"[FileStorageService] Upload successful - fileId: $fileId, originalName: $originalName")
            
            IO.pure(FileStorageResponse(
              success = true,
              fileId = fileId,
              url = fileId.map(id => s"$baseUrl/api/files/$id"),
              message = Some("File uploaded successfully")
            ))
          } else {
            val error = cursor.downField("error").as[String].getOrElse("Unknown error")
            logger.error(s"[FileStorageService] Upload failed with error: $error")
            IO.pure(FileStorageResponse(success = false, message = Some(error)))
          }
        case Left(parseError) =>
          logger.error(s"[FileStorageService] Failed to parse JSON response: $parseError")
          logger.error(s"[FileStorageService] Raw response: $responseBody")
          IO.pure(FileStorageResponse(success = false, message = Some(s"Failed to parse response: $parseError")))
      }
    }.handleErrorWith { error =>
      logger.error(s"[FileStorageService] HTTP request failed: ${error.getClass.getSimpleName}: ${error.getMessage}", error)
      
      // Try to get the actual error response if it's an UnexpectedStatus
      error match {
        case unexpectedStatus: org.http4s.client.UnexpectedStatus =>
          unexpectedStatus.response.as[String].flatMap { errorBody =>
            logger.error(s"[FileStorageService] Error response body: $errorBody")
            IO.pure(FileStorageResponse(success = false, message = Some(s"Upload failed (${unexpectedStatus.status}): $errorBody")))
          }.handleErrorWith { _ =>
            IO.pure(FileStorageResponse(success = false, message = Some(s"Upload failed: ${error.getMessage}")))
          }
        case _ =>
          IO.pure(FileStorageResponse(success = false, message = Some(s"Upload failed: ${error.getMessage}")))
      }
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
