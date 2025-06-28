package Services

import cats.effect.IO
import cats.implicits.*
import io.circe.*
import io.circe.generic.auto.*
import io.circe.parser.*
import io.circe.syntax.*
import Database.{DatabaseManager, SqlParameter}
import Models.*
import org.slf4j.LoggerFactory
import sttp.client3.*
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend
import Config.ServerConfig
import java.time.Instant
import java.util.UUID
import java.nio.file.{Files, Paths, StandardCopyOption}
import java.io.{File, FileInputStream}

class FileService(config: ServerConfig) {
  private val logger = LoggerFactory.getLogger("FileService")
  private val backend = AsyncHttpClientCatsBackend[IO]()

  def uploadExamFiles(examId: String, files: List[UploadedFile], uploadedBy: String): IO[List[ExamFile]] = {
    files.traverse { file =>
      uploadSingleFile(examId, file, uploadedBy)
    }
  }

  def downloadExamFile(examId: String, fileType: String): IO[Option[Array[Byte]]] = {
    // First get file info from database
    getExamFileByType(examId, fileType).flatMap {
      case Some(examFile) =>
        downloadFileContent(examFile.filePath)
      case None =>
        IO.pure(None)
    }
  }

  def deleteExamFile(fileId: String): IO[Boolean] = {
    for {
      fileOpt <- getExamFileById(fileId)
      result <- fileOpt match {
        case Some(file) =>
          for {
            _ <- deleteFileFromStorage(file.filePath)
            deleted <- deleteFileFromDatabase(fileId)
          } yield deleted
        case None => IO.pure(false)
      }
    } yield result
  }

  private def uploadSingleFile(examId: String, file: UploadedFile, uploadedBy: String): IO[ExamFile] = {
    val fileId = UUID.randomUUID().toString
    val fileName = file.fileName
    val fileExtension = fileName.substring(fileName.lastIndexOf('.') + 1)
    val storedFileName = s"${fileId}.${fileExtension}"
    val filePath = s"exam_files/${examId}/${storedFileName}"
    
    for {
      // Save file to local storage or forward to file storage service
      savedPath <- saveFileToStorage(file, filePath)
      
      // Save file metadata to database
      examFile <- saveFileMetadata(ExamFile(
        id = fileId,
        examId = examId,
        fileName = fileName,
        filePath = savedPath,
        fileType = determineFileType(fileName),
        fileSize = file.size,
        mimeType = Some(file.mimeType),
        uploadedBy = uploadedBy,
        uploadedAt = Instant.now()
      ))
    } yield examFile
  }

  private def saveFileToStorage(file: UploadedFile, filePath: String): IO[String] = {
    if (config.fileStorageServiceUrl.nonEmpty) {
      // Forward to file storage service
      forwardToFileStorageService(file, filePath)
    } else {
      // Save locally
      saveFileLocally(file, filePath)
    }
  }

  private def saveFileLocally(file: UploadedFile, filePath: String): IO[String] = IO {
    val fullPath = Paths.get("uploads", filePath)
    Files.createDirectories(fullPath.getParent)
    Files.write(fullPath, file.content)
    fullPath.toString
  }

  private def forwardToFileStorageService(file: UploadedFile, filePath: String): IO[String] = {
    val request = basicRequest
      .post(uri"${config.fileStorageServiceUrl}/api/files/upload")
      .multipartBody(
        multipart("file", file.content)
          .fileName(file.fileName)
          .contentType(file.mimeType),
        multipart("path", filePath)
      )
      .response(asString)

    backend.flatMap { b =>
      b.send(request).flatMap { response =>
      response.body match {
        case Right(body) =>
          parse(body) match {
            case Right(json) =>
              json.hcursor.get[String]("filePath") match {
                case Right(path) => IO.pure(path)
                case Left(error) => IO.raiseError(new RuntimeException(s"File storage service response parsing failed: $error"))
              }
            case Left(error) => 
              IO.raiseError(new RuntimeException(s"File storage service response parsing failed: $error"))
          }
        case Left(error) =>
          IO.raiseError(new RuntimeException(s"File storage service upload failed: $error"))
      }
    }
    }
  }

  private def saveFileMetadata(examFile: ExamFile): IO[ExamFile] = {
    val sql = """
      INSERT INTO exam_files (id, exam_id, file_name, file_path, file_type, file_size, mime_type, uploaded_by)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    """
    val params = List(
      SqlParameter("uuid", examFile.id),
      SqlParameter("uuid", examFile.examId),
      SqlParameter("string", examFile.fileName),
      SqlParameter("string", examFile.filePath),
      SqlParameter("string", examFile.fileType.toString),
      SqlParameter("long", examFile.fileSize),
      SqlParameter("string", examFile.mimeType.getOrElse("")),
      SqlParameter("uuid", examFile.uploadedBy)
    )
    
    DatabaseManager.executeUpdate(sql, params).map(_ => examFile)
  }

  private def getExamFileByType(examId: String, fileType: String): IO[Option[ExamFile]] = {
    val sql = """
      SELECT id, exam_id, file_name, file_path, file_type, file_size, mime_type, uploaded_by, uploaded_at
      FROM exam_files 
      WHERE exam_id = ? AND file_type = ?
      ORDER BY uploaded_at DESC
      LIMIT 1
    """
    val params = List(
      SqlParameter("uuid", examId),
      SqlParameter("string", fileType)
    )
    
    DatabaseManager.executeQuery(sql, params).map { results =>
      results.headOption.flatMap(jsonToExamFile)
    }
  }

  private def getExamFileById(fileId: String): IO[Option[ExamFile]] = {
    val sql = """
      SELECT id, exam_id, file_name, file_path, file_type, file_size, mime_type, uploaded_by, uploaded_at
      FROM exam_files 
      WHERE id = ?
    """
    val params = List(SqlParameter("uuid", fileId))
    
    DatabaseManager.executeQuery(sql, params).map { results =>
      results.headOption.flatMap(jsonToExamFile)
    }
  }

  private def downloadFileContent(filePath: String): IO[Option[Array[Byte]]] = {
    if (config.fileStorageServiceUrl.nonEmpty) {
      downloadFromFileStorageService(filePath)
    } else {
      downloadFromLocalStorage(filePath)
    }
  }

  private def downloadFromLocalStorage(filePath: String): IO[Option[Array[Byte]]] = IO {
    val file = new File(filePath)
    if (file.exists()) {
      Some(Files.readAllBytes(file.toPath))
    } else {
      None
    }
  }

  private def downloadFromFileStorageService(filePath: String): IO[Option[Array[Byte]]] = {
    val request = basicRequest
      .get(uri"${config.fileStorageServiceUrl}/api/files/download")
      .body(Map("filePath" -> filePath))
      .response(asByteArray)

    backend.flatMap { b =>
      b.send(request).map { response =>
      response.body match {
        case Right(bytes) => Some(bytes)
        case Left(_) => None
      }
    }
    }
  }

  private def deleteFileFromStorage(filePath: String): IO[Boolean] = {
    if (config.fileStorageServiceUrl.nonEmpty) {
      deleteFromFileStorageService(filePath)
    } else {
      deleteFromLocalStorage(filePath)
    }
  }

  private def deleteFromLocalStorage(filePath: String): IO[Boolean] = IO {
    val file = new File(filePath)
    if (file.exists()) {
      file.delete()
    } else {
      true // File doesn't exist, consider it deleted
    }
  }

  private def deleteFromFileStorageService(filePath: String): IO[Boolean] = {
    val request = basicRequest
      .delete(uri"${config.fileStorageServiceUrl}/api/files/delete")
      .body(Map("filePath" -> filePath))
      .response(asString)

    backend.flatMap { b =>
      b.send(request).map { response =>
      response.body match {
        case Right(_) => true
        case Left(_) => false
      }
    }
    }
  }

  private def deleteFileFromDatabase(fileId: String): IO[Boolean] = {
    val sql = "DELETE FROM exam_files WHERE id = ?"
    val params = List(SqlParameter("uuid", fileId))
    
    DatabaseManager.executeUpdate(sql, params).map(_ > 0)
  }

  private def determineFileType(fileName: String): FileType = {
    val extension = fileName.toLowerCase.substring(fileName.lastIndexOf('.') + 1)
    extension match {
      case "pdf" | "doc" | "docx" => FileType.question_paper
      case "jpg" | "jpeg" | "png" | "gif" => FileType.resource
      case "txt" | "md" => FileType.answer_key
      case _ => FileType.attachment
    }
  }

  private def jsonToExamFile(json: Json): Option[ExamFile] = {
    val cursor = json.hcursor
    for {
      id <- cursor.get[String]("id").toOption
      examId <- cursor.get[String]("exam_id").toOption
      fileName <- cursor.get[String]("file_name").toOption
      filePath <- cursor.get[String]("file_path").toOption
      fileType <- cursor.get[String]("file_type").toOption.flatMap(s => scala.util.Try(FileType.valueOf(s)).toOption)
      fileSize <- cursor.get[Long]("file_size").toOption
      mimeType = cursor.get[String]("mime_type").toOption.filter(_.nonEmpty)
      uploadedBy <- cursor.get[String]("uploaded_by").toOption
      uploadedAt <- cursor.get[String]("uploaded_at").toOption.flatMap(s => scala.util.Try(Instant.parse(s)).toOption)
    } yield ExamFile(id, examId, fileName, filePath, fileType, fileSize, mimeType, uploadedBy, uploadedAt)
  }
}

case class UploadedFile(
  fileName: String,
  content: Array[Byte],
  size: Long,
  mimeType: String
)
