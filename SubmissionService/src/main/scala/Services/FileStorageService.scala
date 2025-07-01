package Services

import cats.effect.IO
import cats.implicits.*
import sttp.client3.*
import sttp.client3.circe.*
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend
import io.circe.generic.auto.*
import org.slf4j.LoggerFactory
import Config.ServerConfig
import Models.*
import org.apache.commons.io.IOUtils
import java.io.{File, FileInputStream}
import java.nio.file.{Files, Paths}
import java.util.Base64

class FileStorageService(config: ServerConfig) {
  private val logger = LoggerFactory.getLogger("FileStorageService")
  private val backend = AsyncHttpClientCatsBackend[IO]()
  private val fileServiceUrl = config.fileStorageServiceUrl

  case class FileUploadResponse(
    success: Boolean,
    data: Option[FileUploadData] = None,
    error: Option[String] = None
  )

  case class FileUploadData(
    fileId: String,
    fileName: String,
    fileUrl: String,
    fileSize: Long
  )

  def uploadFile(
    fileContent: Array[Byte],
    originalName: String,
    fileType: String,
    uploadUserId: String,
    uploadUserType: String,
    examId: String,
    description: String,
    token: String
  ): IO[Either[String, FileUploadData]] = {
    val encodedContent = Base64.getEncoder.encodeToString(fileContent)
    
    val uploadRequest = Map(
      "originalName" -> originalName,
      "fileContent" -> encodedContent,
      "fileType" -> fileType,
      "mimeType" -> getMimeType(fileType),
      "uploadUserId" -> uploadUserId,
      "uploadUserType" -> uploadUserType,
      "examId" -> examId,
      "description" -> description,
      "category" -> "submission"
    )

    val request = basicRequest
      .post(uri"$fileServiceUrl/api/internal/upload")
      .header("Authorization", s"Bearer $token")
      .header("Content-Type", "application/json")
      .body(uploadRequest)
      .response(asJson[FileUploadResponse])

    backend.flatMap { implicit b =>
      request.send(b).map(_.body match {
        case Right(response) if response.success =>
          response.data match {
            case Some(data) => Right(data)
            case None => Left("文件上传响应异常")
          }
        case Right(response) => Left(response.error.getOrElse("文件上传失败"))
        case Left(error) => Left(s"文件服务通信失败: ${error.getMessage}")
      })
    }.handleErrorWith { error =>
      logger.error("文件上传失败", error)
      IO.pure(Left(s"文件服务不可用: ${error.getMessage}"))
    }
  }

  def getFileUrl(fileId: String, token: String): IO[Either[String, String]] = {
    val request = basicRequest
      .get(uri"$fileServiceUrl/api/internal/files/$fileId/url")
      .header("Authorization", s"Bearer $token")
      .response(asJson[ApiResponse[Map[String, String]]])

    backend.flatMap { implicit b =>
      request.send(b).map(_.body match {
        case Right(response) if response.success =>
          response.data.flatMap(_.get("url")) match {
            case Some(url) => Right(url)
            case None => Left("获取文件URL失败")
          }
        case Right(response) => Left(response.message.getOrElse("获取文件URL失败"))
        case Left(error) => Left(s"文件服务通信失败: ${error.getMessage}")
      })
    }.handleErrorWith { error =>
      logger.error(s"获取文件URL失败: fileId=$fileId", error)
      IO.pure(Left(s"文件服务不可用: ${error.getMessage}"))
    }
  }

  private def getMimeType(fileType: String): String = {
    fileType.toLowerCase match {
      case "jpg" | "jpeg" => "image/jpeg"
      case "png" => "image/png"
      case "pdf" => "application/pdf"
      case _ => "application/octet-stream"
    }
  }

  def validateFileType(fileType: String): Boolean = {
    config.allowedFileTypes.contains(fileType.toLowerCase)
  }

  def validateFileSize(fileSize: Long): Boolean = {
    fileSize <= config.maxFileSize
  }
}
