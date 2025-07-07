package Services

import cats.effect.IO
import cats.implicits.*
import org.http4s.*
import org.http4s.client.Client
import org.http4s.circe.*
import org.http4s.headers.*
import io.circe.generic.auto.*
import io.circe.parser.*
import io.circe.syntax.*
import Models.*
import Config.ServiceConfig
import org.slf4j.LoggerFactory
import fs2.Stream
import java.time.LocalDateTime

trait FileStorageService {
  def uploadAnswerImage(
    fileBytes: Array[Byte],
    fileName: String,
    examId: String,
    questionNumber: Int,
    studentUsername: Option[String] = None
  ): IO[Either[ServiceError, FileUploadResponse]]
  
  def getFileUrl(fileId: String): IO[Either[ServiceError, String]]
  def deleteFile(fileId: String): IO[Either[ServiceError, Boolean]]
}

class FileStorageServiceImpl(config: ServiceConfig, client: Client[IO]) extends FileStorageService {
  private val logger = LoggerFactory.getLogger("FileStorageServiceImpl")
  
  private val baseUrl = s"http://${config.fileStorageService.host}:${config.fileStorageService.port}"

  override def uploadAnswerImage(
    fileBytes: Array[Byte],
    fileName: String,
    examId: String,
    questionNumber: Int,
    studentUsername: Option[String] = None
  ): IO[Either[ServiceError, FileUploadResponse]] = {
    
    val uploadEndpoint = studentUsername match {
      case Some(username) => s"$baseUrl/api/coach/exams/$examId/upload-answer"
      case None => s"$baseUrl/api/student/upload/answer-image"
    }

    // Create JSON payload for file upload
    val uploadData = Map(
      "fileName" -> fileName,
      "fileData" -> java.util.Base64.getEncoder.encodeToString(fileBytes),
      "category" -> "answer-image",
      "relatedId" -> examId,
      "questionNumber" -> questionNumber.toString,
      "timestamp" -> LocalDateTime.now().toString
    ) ++ studentUsername.map("studentUsername" -> _).toMap

    val request = Request[IO](
      method = Method.POST,
      uri = Uri.unsafeFromString(uploadEndpoint),
      headers = Headers(
        Authorization(Credentials.Token(AuthScheme.Bearer, config.fileStorageService.internalApiKey)),
        `Content-Type`(MediaType.application.json)
      )
    ).withEntity(uploadData.asJson)

    client.expect[String](request).attempt.flatMap {
      case Right(response) =>
        IO {
          decode[ApiResponse[FileUploadResponse]](response) match {
            case Right(apiResponse) if apiResponse.success =>
              apiResponse.data match {
                case Some(fileResponse) => Right(fileResponse)
                case None => Left(ServiceError.internalError("No file data returned"))
              }
            case Right(apiResponse) =>
              Left(ServiceError.badRequest(apiResponse.message.getOrElse("File upload failed")))
            case Left(error) =>
              logger.error(s"Failed to parse file upload response: $error")
              Left(ServiceError.internalError("Failed to parse upload response"))
          }
        }
      case Left(error) =>
        logger.error(s"File upload failed: $error")
        IO.pure(Left(ServiceError.internalError("File upload service unavailable")))
    }
  }

  override def getFileUrl(fileId: String): IO[Either[ServiceError, String]] = {
    val uri = Uri.unsafeFromString(s"$baseUrl/api/files/$fileId/url")
    
    val request = Request[IO](
      method = Method.GET,
      uri = uri,
      headers = Headers(
        Authorization(Credentials.Token(AuthScheme.Bearer, config.fileStorageService.internalApiKey))
      )
    )

    client.expect[String](request).attempt.flatMap {
      case Right(response) =>
        IO {
          decode[ApiResponse[Map[String, String]]](response) match {
            case Right(apiResponse) if apiResponse.success =>
              apiResponse.data.flatMap(_.get("url")) match {
                case Some(url) => Right(url)
                case None => Left(ServiceError.notFound("File URL not found"))
              }
            case Right(apiResponse) =>
              Left(ServiceError.badRequest(apiResponse.message.getOrElse("Failed to get file URL")))
            case Left(error) =>
              logger.error(s"Failed to parse file URL response: $error")
              Left(ServiceError.internalError("Failed to parse URL response"))
          }
        }
      case Left(error) =>
        logger.error(s"Failed to get file URL: $error")
        IO.pure(Left(ServiceError.internalError("File storage service unavailable")))
    }
  }

  override def deleteFile(fileId: String): IO[Either[ServiceError, Boolean]] = {
    val uri = Uri.unsafeFromString(s"$baseUrl/api/files/$fileId")
    
    val request = Request[IO](
      method = Method.DELETE,
      uri = uri,
      headers = Headers(
        Authorization(Credentials.Token(AuthScheme.Bearer, config.fileStorageService.internalApiKey))
      )
    )

    client.expect[String](request).attempt.flatMap {
      case Right(response) =>
        IO {
          decode[ApiResponse[String]](response) match {
            case Right(apiResponse) => Right(apiResponse.success)
            case Left(error) =>
              logger.error(s"Failed to parse file delete response: $error")
              Left(ServiceError.internalError("Failed to parse delete response"))
          }
        }
      case Left(error) =>
        logger.error(s"Failed to delete file: $error")
        IO.pure(Left(ServiceError.internalError("File storage service unavailable")))
    }
  }
}

// Mock implementation for testing
class MockFileStorageService extends FileStorageService {
  private val logger = LoggerFactory.getLogger("MockFileStorageService")

  override def uploadAnswerImage(
    fileBytes: Array[Byte],
    fileName: String,
    examId: String,
    questionNumber: Int,
    studentUsername: Option[String] = None
  ): IO[Either[ServiceError, FileUploadResponse]] = {
    IO.pure(Right(FileUploadResponse(
      fileId = s"file-${java.util.UUID.randomUUID()}",
      fileName = fileName,
      fileUrl = s"http://localhost:3008/files/$fileName",
      fileSize = fileBytes.length,
      fileType = "image/jpeg",
      uploadTime = LocalDateTime.now()
    )))
  }

  override def getFileUrl(fileId: String): IO[Either[ServiceError, String]] = {
    IO.pure(Right(s"http://localhost:3008/files/$fileId"))
  }

  override def deleteFile(fileId: String): IO[Either[ServiceError, Boolean]] = {
    IO.pure(Right(true))
  }
}
