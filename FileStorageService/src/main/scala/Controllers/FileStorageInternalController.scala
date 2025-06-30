package Controllers

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.stream.ActorMaterializer
import spray.json._
import DefaultJsonProtocol._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Failure}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import Services.FileStorageService
import Models._
import Models.JsonProtocol._
import Config.ServerConfig

class FileStorageInternalController(fileStorageService: FileStorageService, config: ServerConfig)
                                  (implicit system: ActorSystem, materializer: ActorMaterializer, ec: ExecutionContext) {

  // JSON格式化器
  implicit val localDateTimeFormat: RootJsonFormat[LocalDateTime] = new RootJsonFormat[LocalDateTime] {
    def write(dateTime: LocalDateTime): JsValue = JsString(dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
    def read(value: JsValue): LocalDateTime = value match {
      case JsString(dateStr) => LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
      case _ => throw new DeserializationException("Expected datetime string")
    }
  }

  // 使用 Models 中定义的内部通信模型
  import Models.{InternalUploadRequest, InternalDownloadRequest, InternalUploadResponse, 
                 InternalDownloadResponse, InternalFileDeleteRequest, InternalBatchOperationRequest}

  // 创建标准响应格式
  def createSuccessResponse(data: JsValue): JsObject = {
    JsObject("success" -> JsBoolean(true), "data" -> data)
  }
  
  def createErrorResponse(error: String): JsObject = {
    JsObject("success" -> JsBoolean(false), "error" -> JsString(error))
  }

  // 内部通信路由
  val internalRoutes: Route = {
    pathPrefix("internal") {
      concat(
        // 文件上传内部接口
        path("upload") {
          post {
            entity(as[String]) { body =>
              try {
                val request = body.parseJson.convertTo[InternalUploadRequest]
                
                onComplete(fileStorageService.uploadFile(
                  request.originalName,
                  request.fileContent,
                  request.fileType,
                  request.mimeType,
                  request.uploadUserId,
                  request.uploadUserType,
                  request.examId,
                  request.submissionId,
                  request.description,
                  request.category
                )) {
                  case Success(Right(response)) =>
                    complete(HttpResponse(
                      status = StatusCodes.OK,
                      entity = HttpEntity(
                        ContentTypes.`application/json`,
                        InternalUploadResponse(
                          success = true,
                          fileId = Some(response.fileId),
                          originalName = Some(response.originalName),
                          fileSize = Some(response.fileSize),
                          uploadTime = Some(response.uploadTime)
                        ).toJson.toString
                      )
                    ))
                  case Success(Left(error)) =>
                    complete(HttpResponse(
                      status = StatusCodes.BadRequest,
                      entity = HttpEntity(
                        ContentTypes.`application/json`,
                        InternalUploadResponse(success = false, error = Some(error)).toJson.toString
                      )
                    ))
                  case Failure(ex) =>
                    complete(HttpResponse(
                      status = StatusCodes.InternalServerError,
                      entity = HttpEntity(
                        ContentTypes.`application/json`,
                        InternalUploadResponse(success = false, error = Some(ex.getMessage)).toJson.toString
                      )
                    ))
                }
              } catch {
                case ex: Exception =>
                  complete(HttpResponse(
                    status = StatusCodes.BadRequest,
                    entity = HttpEntity(
                      ContentTypes.`application/json`,
                      InternalUploadResponse(success = false, error = Some(s"Invalid request format: ${ex.getMessage}")).toJson.toString
                    )
                  ))
              }
            }
          }
        },

        // 文件下载内部接口
        path("download") {
          post {
            entity(as[String]) { body =>
              try {
                val request = body.parseJson.convertTo[InternalDownloadRequest]
                
                onComplete(fileStorageService.downloadFile(
                  request.fileId,
                  request.requestUserId,
                  request.requestUserType
                )) {
                  case Success(Right(response)) =>
                    complete(HttpResponse(
                      status = StatusCodes.OK,
                      entity = HttpEntity(
                        ContentTypes.`application/json`,
                        InternalDownloadResponse(
                          success = true,
                          fileId = Some(response.fileId),
                          originalName = Some(response.originalName),
                          fileContent = Some(response.fileContent),
                          mimeType = Some(response.mimeType)
                        ).toJson.toString
                      )
                    ))
                  case Success(Left(error)) =>
                    complete(HttpResponse(
                      status = StatusCodes.NotFound,
                      entity = HttpEntity(
                        ContentTypes.`application/json`,
                        InternalDownloadResponse(success = false, error = Some(error)).toJson.toString
                      )
                    ))
                  case Failure(ex) =>
                    complete(HttpResponse(
                      status = StatusCodes.InternalServerError,
                      entity = HttpEntity(
                        ContentTypes.`application/json`,
                        InternalDownloadResponse(success = false, error = Some(ex.getMessage)).toJson.toString
                      )
                    ))
                }
              } catch {
                case ex: Exception =>
                  complete(HttpResponse(
                    status = StatusCodes.BadRequest,
                    entity = HttpEntity(
                      ContentTypes.`application/json`,
                      InternalDownloadResponse(success = false, error = Some(s"Invalid request format: ${ex.getMessage}")).toJson.toString
                    )
                  ))
              }
            }
          }
        },

        // 文件删除内部接口
        path("delete") {
          post {
            entity(as[String]) { body =>
              try {
                val request = body.parseJson.convertTo[InternalFileDeleteRequest]
                
                onComplete(fileStorageService.deleteFile(
                  request.fileId,
                  request.requestUserId,
                  request.requestUserType
                )) {
                  case Success(Right(_)) =>
                    complete(HttpResponse(
                      status = StatusCodes.OK,
                      entity = HttpEntity(
                        ContentTypes.`application/json`,
                        JsObject("success" -> JsBoolean(true), "message" -> JsString("File deleted successfully")).toString
                      )
                    ))
                  case Success(Left(error)) =>
                    complete(HttpResponse(
                      status = StatusCodes.BadRequest,
                      entity = HttpEntity(
                        ContentTypes.`application/json`,
                        createErrorResponse(error).toString
                      )
                    ))
                  case Failure(ex) =>
                    complete(HttpResponse(
                      status = StatusCodes.InternalServerError,
                      entity = HttpEntity(
                        ContentTypes.`application/json`,
                        createErrorResponse(ex.getMessage).toString
                      )
                    ))
                }
              } catch {
                case ex: Exception =>
                  complete(HttpResponse(
                    status = StatusCodes.BadRequest,
                    entity = HttpEntity(
                      ContentTypes.`application/json`,
                      createErrorResponse(s"Invalid request format: ${ex.getMessage}").toString
                    )
                  ))
              }
            }
          }
        },

        // 批量操作内部接口
        path("batch") {
          post {
            entity(as[String]) { body =>
              try {
                val request = body.parseJson.convertTo[InternalBatchOperationRequest]
                
                request.operation match {
                  case "delete" =>
                    val deleteFutures = request.fileIds.map { fileId =>
                      fileStorageService.deleteFile(fileId, request.requestUserId, request.requestUserType)
                    }
                    
                    onComplete(Future.sequence(deleteFutures)) {
                      case Success(results) =>
                        val successful = results.count(_.isRight)
                        val failed = results.count(_.isLeft)
                        complete(HttpResponse(
                          status = StatusCodes.OK,
                          entity = HttpEntity(
                            ContentTypes.`application/json`,
                            JsObject(
                              "success" -> JsBoolean(true),
                              "message" -> JsString(s"Batch operation completed: $successful successful, $failed failed"),
                              "details" -> JsObject(
                                "successful" -> JsNumber(successful),
                                "failed" -> JsNumber(failed)
                              )
                            ).toString
                          )
                        ))
                      case Failure(ex) =>
                        complete(HttpResponse(
                          status = StatusCodes.InternalServerError,
                          entity = HttpEntity(
                            ContentTypes.`application/json`,
                            createErrorResponse(ex.getMessage).toString
                          )
                        ))
                    }
                  
                  case _ =>
                    complete(HttpResponse(
                      status = StatusCodes.BadRequest,
                      entity = HttpEntity(
                        ContentTypes.`application/json`,
                        createErrorResponse(s"Unsupported operation: ${request.operation}").toString
                      )
                    ))
                }
              } catch {
                case ex: Exception =>
                  complete(HttpResponse(
                    status = StatusCodes.BadRequest,
                    entity = HttpEntity(
                      ContentTypes.`application/json`,
                      createErrorResponse(s"Invalid request format: ${ex.getMessage}").toString
                    )
                  ))
              }
            }
          }
        },

        // 文件信息查询内部接口
        path("info" / Segment) { fileId =>
          get {
            parameter("userId".?, "userType".?) { (userId, userType) =>
              onComplete(fileStorageService.getFileInfo(fileId, userId, userType)) {
                case Success(Right(fileInfo)) =>
                  complete(HttpResponse(
                    status = StatusCodes.OK,
                    entity = HttpEntity(
                      ContentTypes.`application/json`,
                      createSuccessResponse(fileInfo.toJson).toString
                    )
                  ))
                case Success(Left(error)) =>
                  complete(HttpResponse(
                    status = StatusCodes.NotFound,
                    entity = HttpEntity(
                      ContentTypes.`application/json`,
                      createErrorResponse(error).toString
                    )
                  ))
                case Failure(ex) =>
                  complete(HttpResponse(
                    status = StatusCodes.InternalServerError,
                    entity = HttpEntity(
                      ContentTypes.`application/json`,
                      createErrorResponse(ex.getMessage).toString
                    )
                  ))
              }
            }
          }
        },

        // 健康检查内部接口
        path("health") {
          get {
            complete(HttpResponse(
              status = StatusCodes.OK,
              entity = HttpEntity(
                ContentTypes.`application/json`,
                JsObject(
                  "status" -> JsString("healthy"),
                  "service" -> JsString("FileStorageService-Internal"),
                  "timestamp" -> JsString(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)),
                  "version" -> JsString("1.0.0")
                ).toString
              )
            ))
          }
        }
      )
    }
  }

  // 辅助方法：从JWT token中提取用户ID
  private def extractUserIdFromToken(authHeader: String): String = {
    val token = authHeader.replace("Bearer ", "")
    s"user_${token.hashCode.abs}"
  }
}
