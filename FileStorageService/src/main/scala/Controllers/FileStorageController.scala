package Controllers

import cats.effect.IO
import cats.implicits.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.headers.{Authorization, `Content-Type`}
import org.http4s.circe.CirceEntityCodec.*
import org.typelevel.ci.CIStringSyntax
import Models.*
import Services.{FileStorageService, AuthService}
import Database.FileRepository
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import io.circe.syntax.*
import io.circe.generic.auto.*
import fs2.Stream

class FileStorageController(
  fileStorageService: FileStorageService,
  authService: AuthService,
  fileRepository: FileRepository
) {
  private val logger = LoggerFactory.getLogger("FileStorageController")

  private def extractToken(req: Request[IO]): Option[String] = {
    req.headers.get[Authorization].map(_.credentials.toString).orElse {
      req.headers.get(ci"Authorization").map(_.head.value)
    }.flatMap { header =>
      AuthService.extractBearerToken(Some(header))
    }
  }

  private def requireAuth(req: Request[IO])(action: UserClaims => IO[Response[IO]]): IO[Response[IO]] = {
    extractToken(req) match {
      case Some(token) =>
        authService.validateToken(token).flatMap {
          case Some(user) => action(user)
          case None => 
            Response[IO](Status.Unauthorized)
              .withEntity(ApiResponse.error[String]("Invalid or expired token").asJson)
              .pure[IO]
        }
      case None =>
        Response[IO](Status.Unauthorized)
          .withEntity(ApiResponse.error[String]("Authorization token required").asJson)
          .pure[IO]
    }
  }

  private def requireRole(user: UserClaims, allowedRoles: String*)(action: => IO[Response[IO]]): IO[Response[IO]] = {
    if (allowedRoles.contains(user.role)) {
      action
    } else {
      Forbidden(ApiResponse.error[String](s"Access denied. Required roles: ${allowedRoles.mkString(", ")}").asJson)
    }
  }

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    
    // 1. Admin exam file upload: POST /api/admin/exams/{examId}/upload
    case req @ POST -> Root / "api" / "admin" / "exams" / examId / "upload" =>
      requireAuth(req) { user =>
        requireRole(user, "admin") {
          req.body.compile.toVector.flatMap { bodyBytes =>
            val content = bodyBytes.toArray
            val fileName = req.params.getOrElse("filename", "exam-file.pdf")
            
            fileStorageService.uploadFile(
              fileName = fileName,
              fileContent = content,
              mimeType = "application/octet-stream",
              category = "exam-file",
              examId = Some(examId),
              questionNumber = None,
              studentId = None,
              uploadedBy = user.username
            ).flatMap { fileRecord =>
              Ok(ApiResponse.successWithMessage(fileRecord.toFileUploadResult, "File uploaded successfully").asJson)
            }
          }.handleErrorWith { error =>
            logger.error(s"Error in admin exam upload: ${error.getMessage}", error)
            BadRequest(ApiResponse.error[FileUploadResult](s"Upload failed: ${error.getMessage}").asJson)
          }
        }
      }

    // 2. Student answer image upload: POST /api/student/upload/answer-image
    case req @ POST -> Root / "api" / "student" / "upload" / "answer-image" =>
      requireAuth(req) { user =>
        requireRole(user, "student") {
          // For now, let's implement a simple file upload without multipart
          // In a real implementation, you'd handle multipart form data
          req.body.compile.toVector.flatMap { bodyBytes =>
            val content = bodyBytes.toArray
            val fileName = req.params.getOrElse("filename", "answer-image.jpg")
            val examId = req.params.get("relatedId")
            val questionNumber = req.params.get("questionNumber").flatMap(_.toIntOption)
            
            fileStorageService.uploadFile(
              fileName = fileName,
              fileContent = content,
              mimeType = "image/jpeg", // Default, should be detected
              category = "answer-image",
              examId = examId,
              questionNumber = questionNumber,
              studentId = Some(user.userId),
              uploadedBy = user.username
            ).flatMap { fileRecord =>
              Ok(ApiResponse.successWithMessage(fileRecord.toFileUploadResult, "File uploaded successfully").asJson)
            }
          }.handleErrorWith { error =>
            logger.error(s"Error in student answer upload: ${error.getMessage}", error)
            BadRequest(ApiResponse.error[FileUploadResult](s"Upload failed: ${error.getMessage}").asJson)
          }
        }
      }

    // 3. Coach proxy upload: POST /api/coach/exams/{examId}/upload-answer
    case req @ POST -> Root / "api" / "coach" / "exams" / examId / "upload-answer" =>
      requireAuth(req) { user =>
        requireRole(user, "coach") {
          req.body.compile.toVector.flatMap { bodyBytes =>
            val content = bodyBytes.toArray
            val fileName = req.params.getOrElse("filename", "answer-image.jpg")
            val questionNumber = req.params.get("questionNumber").flatMap(_.toIntOption)
            val studentUsername = req.params.get("studentUsername")
            
            fileStorageService.uploadFile(
              fileName = fileName,
              fileContent = content,
              mimeType = "image/jpeg",
              category = "answer-image",
              examId = Some(examId),
              questionNumber = questionNumber,
              studentId = studentUsername,
              uploadedBy = user.username
            ).flatMap { fileRecord =>
              Ok(ApiResponse.successWithMessage(fileRecord.toFileUploadResult, "File uploaded successfully").asJson)
            }
          }.handleErrorWith { error =>
            logger.error(s"Error in coach proxy upload: ${error.getMessage}", error)
            BadRequest(ApiResponse.error[FileUploadResult](s"Upload failed: ${error.getMessage}").asJson)
          }
        }
      }

    // 4. General document upload: POST /api/upload/document
    case req @ POST -> Root / "api" / "upload" / "document" =>
      requireAuth(req) { user =>
        req.body.compile.toVector.flatMap { bodyBytes =>
          val content = bodyBytes.toArray
          val fileName = req.params.getOrElse("filename", "document.pdf")
          
          fileStorageService.uploadFile(
            fileName = fileName,
            fileContent = content,
            mimeType = "application/octet-stream",
            category = "document",
            examId = None,
            questionNumber = None,
            studentId = None,
            uploadedBy = user.username
          ).flatMap { fileRecord =>
            Ok(ApiResponse.successWithMessage(fileRecord.toFileUploadResult, "File uploaded successfully").asJson)
          }
        }.handleErrorWith { error =>
          logger.error(s"Error in document upload: ${error.getMessage}", error)
          BadRequest(ApiResponse.error[FileUploadResult](s"Upload failed: ${error.getMessage}").asJson)
        }
      }

    // 5. General file upload: POST /api/upload/file
    case req @ POST -> Root / "api" / "upload" / "file" =>
      requireAuth(req) { user =>
        req.body.compile.toVector.flatMap { bodyBytes =>
          val content = bodyBytes.toArray
          val fileName = req.params.getOrElse("filename", "file.bin")
          val category = req.params.getOrElse("category", "other")
          val relatedId = req.params.get("relatedId")
          val questionNumber = req.params.get("questionNumber").flatMap(_.toIntOption)
          
          fileStorageService.uploadFile(
            fileName = fileName,
            fileContent = content,
            mimeType = "application/octet-stream",
            category = category,
            examId = relatedId,
            questionNumber = questionNumber,
            studentId = None,
            uploadedBy = user.username
          ).flatMap { fileRecord =>
            Ok(ApiResponse.successWithMessage(fileRecord.toFileUploadResult, "File uploaded successfully").asJson)
          }
        }.handleErrorWith { error =>
          logger.error(s"Error in general file upload: ${error.getMessage}", error)
          BadRequest(ApiResponse.error[FileUploadResult](s"Upload failed: ${error.getMessage}").asJson)
        }
      }

    // 6. Download file: GET /api/files/{fileId}/download
    case GET -> Root / "api" / "files" / fileId / "download" =>
      fileStorageService.downloadFile(fileId).flatMap {
        case Some((content, originalName, mimeType)) =>
          // Convert Array[Byte] to proper binary response using fs2.Stream[IO, Byte]
          val stream = fs2.Stream.emits(content).covary[IO]
          Response[IO](
            status = Status.Ok,
            headers = Headers(
              Header.Raw(ci"Content-Disposition", s"""attachment; filename="$originalName""""),
              Header.Raw(ci"Content-Type", mimeType),
              Header.Raw(ci"Content-Length", content.length.toString)
            ),
            body = stream
          ).pure[IO]
        case None =>
          NotFound(ApiResponse.error[String]("File not found").asJson)
      }.handleErrorWith { error =>
        logger.error(s"Error downloading file $fileId: ${error.getMessage}", error)
        InternalServerError(ApiResponse.error[String]("Download failed").asJson)
      }

    // 7. Student file download: GET /api/student/files/{fileId}/download
    case req @ GET -> Root / "api" / "student" / "files" / fileId / "download" =>
      requireAuth(req) { user =>
        requireRole(user, "student", "coach") {
          fileStorageService.downloadFile(fileId).flatMap {
            case Some((content, originalName, mimeType)) =>
              // Convert Array[Byte] to proper binary response
              val stream = fs2.Stream.emits(content).covary[IO]
              Response[IO](
                status = Status.Ok,
                headers = Headers(
                  Header.Raw(ci"Content-Disposition", s"""attachment; filename="$originalName""""),
                  Header.Raw(ci"Content-Type", mimeType),
                  Header.Raw(ci"Content-Length", content.length.toString)
                ),
                body = stream
              ).pure[IO]
            case None =>
              NotFound(ApiResponse.error[String]("File not found").asJson)
          }.handleErrorWith { error =>
            logger.error(s"Error downloading file $fileId: ${error.getMessage}", error)
            InternalServerError(ApiResponse.error[String]("Download failed").asJson)
          }
        }
      }

    // 8. Grader file download: GET /api/grader/files/{fileId}/download
    case req @ GET -> Root / "api" / "grader" / "files" / fileId / "download" =>
      requireAuth(req) { user =>
        requireRole(user, "grader", "admin") {
          fileStorageService.downloadFile(fileId).flatMap {
            case Some((content, originalName, mimeType)) =>
              // Convert Array[Byte] to proper binary response
              val stream = fs2.Stream.emits(content).covary[IO]
              Response[IO](
                status = Status.Ok,
                headers = Headers(
                  Header.Raw(ci"Content-Disposition", s"""attachment; filename="$originalName""""),
                  Header.Raw(ci"Content-Type", mimeType),
                  Header.Raw(ci"Content-Length", content.length.toString)
                ),
                body = stream
              ).pure[IO]
            case None =>
              NotFound(ApiResponse.error[String]("File not found").asJson)
          }
        }
      }

    // 9. Delete file: DELETE /api/files/{fileId}
    case req @ DELETE -> Root / "api" / "files" / fileId =>
      requireAuth(req) { user =>
        requireRole(user, "admin") {
          fileStorageService.deleteFile(fileId).flatMap { success =>
            if (success) {
              Ok(ApiResponse.successWithMessage((), "File deleted successfully").asJson)
            } else {
              NotFound(ApiResponse.error[Unit]("File not found").asJson)
            }
          }.handleErrorWith { error =>
            logger.error(s"Error deleting file $fileId: ${error.getMessage}", error)
            InternalServerError(ApiResponse.error[Unit]("Delete failed").asJson)
          }
        }
      }

    // 10. Get file info: GET /api/files/{fileId}
    case req @ GET -> Root / "api" / "files" / fileId =>
      requireAuth(req) { user =>
        fileStorageService.getFileInfo(fileId).flatMap {
          case Some(fileRecord) =>
            Ok(ApiResponse.successWithMessage(fileRecord.toFileUploadResult, "File info retrieved").asJson)
          case None =>
            NotFound(ApiResponse.error[FileUploadResult]("File not found").asJson)
        }.handleErrorWith { error =>
          logger.error(s"Error getting file info $fileId: ${error.getMessage}", error)
          InternalServerError(ApiResponse.error[FileUploadResult]("Failed to get file info").asJson)
        }
      }

    // 11. List files: GET /api/files
    case req @ GET -> Root / "api" / "files" =>
      requireAuth(req) { user =>
        val category = req.params.get("category")
        val examId = req.params.get("examId")
        val fileType = req.params.get("fileType")
        val uploadedBy = req.params.get("uploadedBy")
        val page = req.params.get("page").flatMap(_.toIntOption).getOrElse(1)
        val limit = req.params.get("limit").flatMap(_.toIntOption).getOrElse(20)

        fileRepository.listFiles(category, examId, fileType, uploadedBy, page, limit).flatMap { case (files, total) =>
          val totalPages = (total + limit - 1) / limit
          val response = PaginatedResponse(
            files = files.map(_.toFileUploadResult),
            pagination = PaginationInfo(page, limit, total, totalPages)
          )
          Ok(ApiResponse.successWithMessage(response, "Files retrieved").asJson)
        }.handleErrorWith { error =>
          logger.error(s"Error listing files: ${error.getMessage}", error)
          InternalServerError(ApiResponse.error[PaginatedResponse[FileUploadResult]]("Failed to list files").asJson)
        }
      }

    // 12. Grader images: GET /api/grader/images
    case req @ GET -> Root / "api" / "grader" / "images" =>
      requireAuth(req) { user =>
        requireRole(user, "grader", "admin") {
          val examId = req.params.get("examId")
          val studentId = req.params.get("studentId")
          val questionNumber = req.params.get("questionNumber").flatMap(_.toIntOption)

          examId match {
            case Some(id) =>
              fileRepository.getGradingImages(id, studentId, questionNumber).flatMap { files =>
                val gradingImages = files.map { file =>
                  GradingImage(
                    imageUrl = file.fileUrl,
                    fileName = file.originalName,
                    examId = file.examId.getOrElse(""),
                    studentId = file.studentId.getOrElse(""),
                    questionNumber = file.questionNumber.getOrElse(0),
                    uploadTime = file.uploadTime.toString
                  )
                }
                Ok(ApiResponse.successWithMessage(gradingImages, "Grading images retrieved").asJson)
              }
            case None =>
              BadRequest(ApiResponse.error[List[GradingImage]]("examId parameter is required").asJson)
          }
        }
      }

    // 13. Internal upload endpoint (for microservice communication): POST /internal/upload
    case req @ POST -> Root / "internal" / "upload" =>
      // This endpoint is for internal microservice communication and should be protected by internal API key
      logger.info(s"Received internal upload request")
      val apiKey = req.headers.get(ci"X-API-Key").map(_.head.value)
      logger.info(s"API Key present: ${apiKey.isDefined}, value: ${apiKey.getOrElse("none")}")
      
      if (apiKey.contains("internal-api-key")) { // This should match your internal API key
        logger.info(s"API key validation successful, proceeding with upload")
        req.as[InternalFileUploadRequest].flatMap { uploadReq =>
          logger.info(s"Parsed upload request: fileName=${uploadReq.originalName}, category=${uploadReq.category}")
          
          // Decode Base64 file content
          val fileContentBytes = try {
            val decoded = java.util.Base64.getDecoder.decode(uploadReq.fileContent)
            logger.info(s"Successfully decoded Base64 content: ${decoded.length} bytes")
            decoded
          } catch {
            case ex: Exception =>
              logger.error(s"Failed to decode Base64 content: ${ex.getMessage}")
              throw new IllegalArgumentException(s"Invalid Base64 content: ${ex.getMessage}")
          }
          
          logger.info(s"Internal upload request: fileName=${uploadReq.originalName}, size=${fileContentBytes.length}, category=${uploadReq.category}")
          
          fileStorageService.uploadFile(
            fileName = uploadReq.originalName,
            fileContent = fileContentBytes,
            mimeType = uploadReq.mimeType,
            category = uploadReq.category,
            examId = uploadReq.examId,
            questionNumber = None,
            studentId = uploadReq.uploadUserId,
            uploadedBy = uploadReq.uploadUserId.getOrElse("system")
          ).flatMap { fileRecord =>
            logger.info(s"File upload successful: fileId=${fileRecord.id}")
            Ok(InternalFileResponse(
              success = true,
              fileId = Some(fileRecord.id),
              url = Some(fileRecord.fileUrl),
              message = Some("File uploaded successfully")
            ).asJson)
          }.handleErrorWith { error =>
            logger.error(s"Internal upload failed: ${error.getMessage}", error)
            BadRequest(InternalFileResponse(
              success = false,
              message = Some(error.getMessage)
            ).asJson)
          }
        }.handleErrorWith { error =>
          logger.error(s"Failed to parse upload request: ${error.getMessage}", error)
          BadRequest(InternalFileResponse(
            success = false,
            message = Some(s"Failed to parse request: ${error.getMessage}")
          ).asJson)
        }
      } else {
        logger.warn(s"Invalid API key for internal upload: ${apiKey.getOrElse("none")}")
        Response[IO](Status.Unauthorized)
          .withEntity(InternalFileResponse(success = false, message = Some("Invalid API key")).asJson)
          .pure[IO]
      }
  }
}
