package Utils

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import spray.json._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Failure}
import Models._
import Models.JsonProtocol._

/**
 * FileStorageService 客户端工具类
 * 供其他微服务调用 FileStorageService 的内部通信接口
 */
class FileStorageServiceClient(fileStorageServiceUrl: String)
                             (implicit system: ActorSystem, materializer: ActorMaterializer, ec: ExecutionContext) {

  /**
   * 上传文件到 FileStorageService
   */
  def uploadFile(
    originalName: String,
    fileContent: Array[Byte],
    fileType: String,
    mimeType: String,
    uploadUserId: Option[String],
    uploadUserType: Option[String],
    examId: Option[String] = None,
    submissionId: Option[String] = None,
    description: Option[String] = None,
    category: String = "general"
  ): Future[Either[String, InternalUploadResponse]] = {
    
    val request = InternalUploadRequest(
      originalName, fileContent, fileType, mimeType,
      uploadUserId, uploadUserType, examId, submissionId, description, category
    )

    val httpRequest = HttpRequest(
      method = HttpMethods.POST,
      uri = s"$fileStorageServiceUrl/internal/upload",
      entity = HttpEntity(
        ContentTypes.`application/json`,
        request.toJson.compactPrint
      )
    )

    Http().singleRequest(httpRequest).flatMap { response =>
      response.status match {
        case StatusCodes.OK =>
          Unmarshal(response.entity).to[String].map { responseBody =>
            try {
              val uploadResponse = responseBody.parseJson.convertTo[InternalUploadResponse]
              Right(uploadResponse)
            } catch {
              case ex: Exception =>
                Left(s"Failed to parse response: ${ex.getMessage}")
            }
          }
        case _ =>
          Unmarshal(response.entity).to[String].map { errorBody =>
            try {
              val errorResponse = errorBody.parseJson.convertTo[InternalUploadResponse]
              Left(errorResponse.error.getOrElse("Unknown error"))
            } catch {
              case _: Exception =>
                Left(s"HTTP ${response.status.intValue()}: $errorBody")
            }
          }
      }
    }.recover {
      case ex => Left(s"Request failed: ${ex.getMessage}")
    }
  }

  /**
   * 从 FileStorageService 下载文件
   */
  def downloadFile(
    fileId: String,
    requestUserId: Option[String],
    requestUserType: Option[String],
    purpose: String = "download"
  ): Future[Either[String, InternalDownloadResponse]] = {
    
    val request = InternalDownloadRequest(fileId, requestUserId, requestUserType, purpose)

    val httpRequest = HttpRequest(
      method = HttpMethods.POST,
      uri = s"$fileStorageServiceUrl/internal/download",
      entity = HttpEntity(
        ContentTypes.`application/json`,
        request.toJson.compactPrint
      )
    )

    Http().singleRequest(httpRequest).flatMap { response =>
      response.status match {
        case StatusCodes.OK =>
          Unmarshal(response.entity).to[String].map { responseBody =>
            try {
              val downloadResponse = responseBody.parseJson.convertTo[InternalDownloadResponse]
              Right(downloadResponse)
            } catch {
              case ex: Exception =>
                Left(s"Failed to parse response: ${ex.getMessage}")
            }
          }
        case _ =>
          Unmarshal(response.entity).to[String].map { errorBody =>
            try {
              val errorResponse = errorBody.parseJson.convertTo[InternalDownloadResponse]
              Left(errorResponse.error.getOrElse("Unknown error"))
            } catch {
              case _: Exception =>
                Left(s"HTTP ${response.status.intValue()}: $errorBody")
            }
          }
      }
    }.recover {
      case ex => Left(s"Request failed: ${ex.getMessage}")
    }
  }

  /**
   * 删除文件
   */
  def deleteFile(
    fileId: String,
    requestUserId: Option[String],
    requestUserType: Option[String],
    reason: Option[String] = None
  ): Future[Either[String, String]] = {
    
    val request = InternalFileDeleteRequest(fileId, requestUserId, requestUserType, reason)

    val httpRequest = HttpRequest(
      method = HttpMethods.POST,
      uri = s"$fileStorageServiceUrl/internal/delete",
      entity = HttpEntity(
        ContentTypes.`application/json`,
        request.toJson.compactPrint
      )
    )

    Http().singleRequest(httpRequest).flatMap { response =>
      response.status match {
        case StatusCodes.OK =>
          Unmarshal(response.entity).to[String].map { responseBody =>
            try {
              val jsonResponse = responseBody.parseJson.asJsObject
              if (jsonResponse.fields("success").convertTo[Boolean]) {
                Right(jsonResponse.fields("message").convertTo[String])
              } else {
                Left(jsonResponse.fields("error").convertTo[String])
              }
            } catch {
              case ex: Exception =>
                Left(s"Failed to parse response: ${ex.getMessage}")
            }
          }
        case _ =>
          Unmarshal(response.entity).to[String].map { errorBody =>
            Left(s"HTTP ${response.status.intValue()}: $errorBody")
          }
      }
    }.recover {
      case ex => Left(s"Request failed: ${ex.getMessage}")
    }
  }

  /**
   * 获取文件信息
   */
  def getFileInfo(
    fileId: String,
    requestUserId: Option[String] = None,
    requestUserType: Option[String] = None
  ): Future[Either[String, JsObject]] = {
    
    val queryParams = List(
      requestUserId.map(uid => s"userId=$uid"),
      requestUserType.map(utype => s"userType=$utype")
    ).flatten.mkString("&")
    
    val uri = if (queryParams.nonEmpty) {
      s"$fileStorageServiceUrl/internal/info/$fileId?$queryParams"
    } else {
      s"$fileStorageServiceUrl/internal/info/$fileId"
    }

    val httpRequest = HttpRequest(
      method = HttpMethods.GET,
      uri = uri
    )

    Http().singleRequest(httpRequest).flatMap { response =>
      response.status match {
        case StatusCodes.OK =>
          Unmarshal(response.entity).to[String].map { responseBody =>
            try {
              val jsonResponse = responseBody.parseJson.asJsObject
              if (jsonResponse.fields("success").convertTo[Boolean]) {
                Right(jsonResponse.fields("data").asJsObject)
              } else {
                Left(jsonResponse.fields("error").convertTo[String])
              }
            } catch {
              case ex: Exception =>
                Left(s"Failed to parse response: ${ex.getMessage}")
            }
          }
        case _ =>
          Unmarshal(response.entity).to[String].map { errorBody =>
            Left(s"HTTP ${response.status.intValue()}: $errorBody")
          }
      }
    }.recover {
      case ex => Left(s"Request failed: ${ex.getMessage}")
    }
  }

  /**
   * 健康检查
   */
  def healthCheck(): Future[Boolean] = {
    val httpRequest = HttpRequest(
      method = HttpMethods.GET,
      uri = s"$fileStorageServiceUrl/internal/health"
    )

    Http().singleRequest(httpRequest).map { response =>
      response.status == StatusCodes.OK
    }.recover {
      case _ => false
    }
  }
}
