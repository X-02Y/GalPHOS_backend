package Controllers

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{`Content-Disposition`, ContentDispositionTypes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.marshalling.{ToResponseMarshallable, ToEntityMarshaller}
import akka.stream.ActorMaterializer
import spray.json._
import DefaultJsonProtocol._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Failure}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import Services.FileStorageService
import Models._
import Models.JsonProtocol._
import Config.ServerConfig

class FileStorageController(fileStorageService: FileStorageService, config: ServerConfig)
                          (implicit system: ActorSystem, materializer: ActorMaterializer, ec: ExecutionContext) {

  // 内部通信控制器
  val internalController = new FileStorageInternalController(fileStorageService, config)
  
  // 创建标准响应格式
  def createSuccessResponse(data: JsValue): JsObject = {
    JsObject("success" -> JsBoolean(true), "data" -> data)
  }
  
  def createErrorResponse(error: String): JsObject = {
    JsObject("success" -> JsBoolean(false), "error" -> JsString(error))
  }

  // 路由定义
  val routes: Route = {
    pathPrefix("api") {
      concat(
        // 学生文件下载 API
        pathPrefix("student") {
          path("files" / "download" / Segment) { fileId =>
            get {
              optionalHeaderValueByName("Authorization") { authHeaderOpt =>
                val userId = authHeaderOpt.map(extractUserIdFromToken).getOrElse("anonymous")
                val userType = "student"
                
                onComplete(fileStorageService.downloadFile(fileId, Some(userId), Some(userType))) {
                  case Success(Right(response)) =>
                    respondWithHeaders(
                      `Content-Disposition`(ContentDispositionTypes.attachment, Map("filename" -> response.originalName))
                    ) {
                      complete(HttpEntity(
                        ContentType(MediaTypes.`application/octet-stream`),
                        response.fileContent
                      ))
                    }
                  case Success(Left(error)) =>
                    complete(HttpResponse(
                      status = StatusCodes.NotFound,
                      entity = HttpEntity(
                        ContentTypes.`application/json`,
                        createErrorResponse(error).compactPrint
                      )
                    ))
                  case Failure(ex) =>
                    complete(HttpResponse(
                      status = StatusCodes.InternalServerError,
                      entity = HttpEntity(
                        ContentTypes.`application/json`,
                        createErrorResponse(ex.getMessage).compactPrint
                      )
                    ))
                }
              }
            }
          }
        },
        
        // 阅卷员图片代理 API
        pathPrefix("grader") {
          path("images") {
            get {
              parameter("url") { imageUrl =>
                optionalHeaderValueByName("Authorization") { authHeaderOpt =>
                  val userId = authHeaderOpt.map(extractUserIdFromToken).getOrElse("anonymous")
                  
                  onComplete(fileStorageService.getImageProxy(imageUrl)) {
                    case Success(Right(imageContent)) =>
                      complete(HttpEntity(
                        ContentType(MediaTypes.`image/jpeg`),
                        imageContent
                      ))
                    case Success(Left(error)) =>
                      complete(HttpResponse(
                        status = StatusCodes.NotFound,
                        entity = HttpEntity(
                          ContentTypes.`application/json`,
                          createErrorResponse(error).compactPrint
                        )
                      ))
                    case Failure(ex) =>
                      complete(HttpResponse(
                        status = StatusCodes.InternalServerError,
                        entity = HttpEntity(
                          ContentTypes.`application/json`,
                          createErrorResponse(ex.getMessage).compactPrint
                        )
                      ))
                  }
                }
              }
            }
          }
        },
        
        // 教练相关 API
        pathPrefix("coach") {
          concat(
            // 考试排名导出
            path("exams" / Segment / "ranking") { examId =>
              get {
                optionalHeaderValueByName("Authorization") { authHeaderOpt =>
                  val userId = authHeaderOpt.map(extractUserIdFromToken).getOrElse("anonymous")
                  
                  onComplete(fileStorageService.exportScores(examId, "excel")) {
                    case Success(Right(response)) =>
                      complete(HttpResponse(
                        status = StatusCodes.OK,
                        entity = HttpEntity(
                          ContentTypes.`application/json`,
                          createSuccessResponse(
                            JsObject(
                              "message" -> JsString(response.message),
                              "fileId" -> JsString(response.fileId),
                              "fileName" -> JsString(response.fileName),
                              "fileSize" -> JsNumber(response.fileSize),
                              "exportTime" -> JsString(response.exportTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                            )
                          ).compactPrint
                        )
                      ))
                    case Success(Left(error)) =>
                      complete(HttpResponse(
                        status = StatusCodes.BadRequest,
                        entity = HttpEntity(
                          ContentTypes.`application/json`,
                          createErrorResponse(error).compactPrint
                        )
                      ))
                    case Failure(ex) =>
                      complete(HttpResponse(
                        status = StatusCodes.InternalServerError,
                        entity = HttpEntity(
                          ContentTypes.`application/json`,
                          createErrorResponse(ex.getMessage).compactPrint
                        )
                      ))
                  }
                }
              }
            },
            
            // 成绩导出
            path("exams" / Segment / "scores" / "export") { examId =>
              post {
                entity(as[String]) { body =>
                  optionalHeaderValueByName("Authorization") { authHeaderOpt =>
                    val userId = authHeaderOpt.map(extractUserIdFromToken).getOrElse("anonymous")
                    
                    // 解析请求体获取导出格式
                    val format = try {
                      val requestData = body.parseJson.asJsObject
                      requestData.fields.get("format") match {
                        case Some(JsString(f)) => f
                        case _ => "excel"
                      }
                    } catch {
                      case _: Exception => "excel"
                    }
                    
                    onComplete(fileStorageService.exportScores(examId, format)) {
                      case Success(Right(response)) =>
                        complete(HttpResponse(
                          status = StatusCodes.OK,
                          entity = HttpEntity(
                            ContentTypes.`application/json`,
                            createSuccessResponse(
                              JsObject(
                                "message" -> JsString(response.message),
                                "fileId" -> JsString(response.fileId),
                                "fileName" -> JsString(response.fileName),
                                "fileSize" -> JsNumber(response.fileSize),
                                "exportTime" -> JsString(response.exportTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                              )
                            ).compactPrint
                          )
                        ))
                      case Success(Left(error)) =>
                        complete(HttpResponse(
                          status = StatusCodes.BadRequest,
                          entity = HttpEntity(
                            ContentTypes.`application/json`,
                            createErrorResponse(error).compactPrint
                          )
                        ))
                      case Failure(ex) =>
                        complete(HttpResponse(
                          status = StatusCodes.InternalServerError,
                          entity = HttpEntity(
                            ContentTypes.`application/json`,
                            createErrorResponse(ex.getMessage).compactPrint
                          )
                        ))
                    }
                  }
                }
              }
            },
            
            // 成绩统计
            path("exams" / Segment / "scores" / "statistics") { examId =>
              get {
                optionalHeaderValueByName("Authorization") { authHeaderOpt =>
                  val userId = authHeaderOpt.map(extractUserIdFromToken).getOrElse("anonymous")
                  
                  // 返回模拟统计数据
                  complete(HttpResponse(
                    status = StatusCodes.OK,
                    entity = HttpEntity(
                      ContentTypes.`application/json`,
                      createSuccessResponse(
                        JsObject(
                          "examId" -> JsString(examId),
                          "totalStudents" -> JsNumber(100),
                          "averageScore" -> JsNumber(82.5),
                          "highestScore" -> JsNumber(98),
                          "lowestScore" -> JsNumber(65),
                          "passRate" -> JsNumber(0.85)
                        )
                      ).compactPrint
                    )
                  ))
                }
              }
            },
            
            // 教练仪表盘统计
            path("dashboard" / "stats") {
              get {
                optionalHeaderValueByName("Authorization") { authHeaderOpt =>
                  val userId = authHeaderOpt.map(extractUserIdFromToken).getOrElse("anonymous")
                  
                  onComplete(fileStorageService.getDashboardStats()) {
                    case Success(Right(stats)) =>
                      complete(HttpResponse(
                        status = StatusCodes.OK,
                        entity = HttpEntity(
                          ContentTypes.`application/json`,
                          createSuccessResponse(
                            JsObject(
                              "totalFiles" -> JsNumber(stats.totalFiles),
                              "totalSize" -> JsNumber(stats.totalSize),
                              "activeFiles" -> JsNumber(stats.activeFiles),
                              "todayUploads" -> JsNumber(stats.todayUploads),
                              "weekUploads" -> JsNumber(stats.weekUploads),
                              "monthUploads" -> JsNumber(stats.monthUploads)
                            )
                          ).compactPrint
                        )
                      ))
                    case Success(Left(error)) =>
                      complete(HttpResponse(
                        status = StatusCodes.InternalServerError,
                        entity = HttpEntity(
                          ContentTypes.`application/json`,
                          createErrorResponse(error).compactPrint
                        )
                      ))
                    case Failure(ex) =>
                      complete(HttpResponse(
                        status = StatusCodes.InternalServerError,
                        entity = HttpEntity(
                          ContentTypes.`application/json`,
                          createErrorResponse(ex.getMessage).compactPrint
                        )
                      ))
                  }
                }
              }
            }
          )
        },
        
        // 管理员仪表盘统计
        pathPrefix("admin") {
          path("dashboard" / "stats") {
            get {
              optionalHeaderValueByName("Authorization") { authHeaderOpt =>
                val userId = authHeaderOpt.map(extractUserIdFromToken).getOrElse("anonymous")
                
                onComplete(fileStorageService.getDashboardStats()) {
                  case Success(Right(stats)) =>
                    complete(HttpResponse(
                      status = StatusCodes.OK,
                      entity = HttpEntity(
                        ContentTypes.`application/json`,
                        createSuccessResponse(
                          JsObject(
                            "totalFiles" -> JsNumber(stats.totalFiles),
                            "totalSize" -> JsNumber(stats.totalSize),
                            "activeFiles" -> JsNumber(stats.activeFiles),
                            "deletedFiles" -> JsNumber(stats.deletedFiles),
                            "todayUploads" -> JsNumber(stats.todayUploads),
                            "weekUploads" -> JsNumber(stats.weekUploads),
                            "monthUploads" -> JsNumber(stats.monthUploads),
                            "storageUsage" -> JsObject(
                              "usedSpace" -> JsNumber(stats.totalSize),
                              "totalSpace" -> JsNumber(1000000000L),
                              "usagePercent" -> JsNumber((stats.totalSize.toDouble / 1000000000L * 100).toInt)
                            )
                          )
                        ).compactPrint
                      )
                    ))
                  case Success(Left(error)) =>
                    complete(HttpResponse(
                      status = StatusCodes.InternalServerError,
                      entity = HttpEntity(
                        ContentTypes.`application/json`,
                        createErrorResponse(error).compactPrint
                      )
                    ))
                  case Failure(ex) =>
                    complete(HttpResponse(
                      status = StatusCodes.InternalServerError,
                      entity = HttpEntity(
                        ContentTypes.`application/json`,
                        createErrorResponse(ex.getMessage).compactPrint
                      )
                    ))
                }
              }
            }
          }
        },
        
        // 健康检查
        path("health") {
          get {
            complete(HttpResponse(
              status = StatusCodes.OK,
              entity = HttpEntity(
                ContentTypes.`application/json`,
                JsObject(
                  "status" -> JsString("healthy"),
                  "service" -> JsString("FileStorageService"),
                  "timestamp" -> JsString(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)),
                  "version" -> JsString("1.0.0")
                ).compactPrint
              )
            ))
          }
        }
      )
    } ~ internalController.internalRoutes  // 添加内部通信路由
  }
  
  // 启动服务器
  def startServer(): Future[Http.ServerBinding] = {
    val combinedRoutes = routes
    val bindingFuture = Http().bindAndHandle(combinedRoutes, config.serverIP, config.serverPort)
    
    bindingFuture.onComplete {
      case Success(binding) =>
        println(s"FileStorageService started at http://${binding.localAddress.getHostString}:${binding.localAddress.getPort}")
        println(s"External API: http://${binding.localAddress.getHostString}:${binding.localAddress.getPort}/api/")
        println(s"Internal API: http://${binding.localAddress.getHostString}:${binding.localAddress.getPort}/internal/")
      case Failure(exception) =>
        println(s"Failed to start FileStorageService: ${exception.getMessage}")
        system.terminate()
    }
    
    bindingFuture
  }
  
  // 辅助方法：从JWT token中提取用户ID
  private def extractUserIdFromToken(authHeader: String): String = {
    val token = authHeader.replace("Bearer ", "")
    s"user_${token.hashCode.abs}"
  }
}
