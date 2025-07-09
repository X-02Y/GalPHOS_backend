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
    
    // 1. General file upload: POST /api/upload/file
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

    // 2. Document upload: POST /api/upload/document
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

    // 3. Download file: GET /api/files/{fileId}/download
    case GET -> Root / "api" / "files" / fileId / "download" =>
      fileStorageService.downloadFile(fileId).flatMap {
        case Some((content, originalName, mimeType)) =>
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

    // 4. Get file info: GET /api/files/{fileId}
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

    // 5. Delete file: DELETE /api/files/{fileId}
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

    // 6. List files: GET /api/files
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

    // 7. Student files: GET /api/student/files/*
    case req @ GET -> Root / "api" / "student" / "files" / fileId / "download" =>
      requireAuth(req) { user =>
        requireRole(user, "student", "coach") {
          fileStorageService.downloadFile(fileId).flatMap {
            case Some((content, originalName, mimeType)) =>
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

    // 8. Grader images: GET /api/grader/images
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

    // 9. Internal upload endpoint (for microservice communication): POST /internal/upload
    case req @ POST -> Root / "internal" / "upload" =>
      val apiKey = req.headers.get(ci"X-API-Key").map(_.head.value)
      
      if (apiKey.contains("internal-api-key")) {
        req.as[InternalFileUploadRequest].flatMap { uploadReq =>
          val fileContentBytes = try {
            java.util.Base64.getDecoder.decode(uploadReq.fileContent)
          } catch {
            case ex: Exception =>
              throw new IllegalArgumentException(s"Invalid Base64 content: ${ex.getMessage}")
          }
          
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

    // Health check endpoint
    case GET -> Root / "health" =>
      Ok(Map("status" -> "healthy", "service" -> "FileStorageService").asJson)
        .map(_.withHeaders(
          Header.Raw(ci"Access-Control-Allow-Origin", "*"),
          Header.Raw(ci"Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS"),
          Header.Raw(ci"Access-Control-Allow-Headers", "Content-Type, Authorization")
        ))

    // CORS preflight for health check
    case OPTIONS -> Root / "health" =>
      Ok().map(_.withHeaders(
        Header.Raw(ci"Access-Control-Allow-Origin", "*"),
        Header.Raw(ci"Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS"),
        Header.Raw(ci"Access-Control-Allow-Headers", "Content-Type, Authorization")
      ))
  }
}
