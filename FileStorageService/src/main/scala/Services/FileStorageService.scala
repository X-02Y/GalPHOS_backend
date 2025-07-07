package Services

import cats.effect.IO
import Models.*
import org.slf4j.LoggerFactory
import java.io.{File, FileOutputStream, FileInputStream}
import java.util.UUID
import java.time.LocalDateTime
import java.nio.file.{Files, Paths, StandardCopyOption}
import org.apache.commons.io.{FileUtils, FilenameUtils}
import Config.ServiceConfig
import Database.FileRepository

trait FileStorageService {
  def uploadFile(
    fileName: String,
    fileContent: Array[Byte],
    mimeType: String,
    category: String,
    examId: Option[String] = None,
    questionNumber: Option[Int] = None,
    studentId: Option[String] = None,
    uploadedBy: String
  ): IO[FileRecord]
  
  def downloadFile(fileId: String): IO[Option[(Array[Byte], String, String)]]
  def getFileInfo(fileId: String): IO[Option[FileRecord]]
  def deleteFile(fileId: String): IO[Boolean]
  def validateFileUpload(fileName: String, fileSize: Long, category: String): IO[Either[String, Unit]]
}

class FileStorageServiceImpl(config: ServiceConfig, fileRepository: FileRepository) extends FileStorageService {
  private val logger = LoggerFactory.getLogger("FileStorageService")
  private val storagePath = config.storage.localPath
  private val baseUrl = config.storage.baseUrl
  
  // Ensure storage directory exists
  private def ensureStorageDirectory(): IO[Unit] = {
    IO {
      val storageDir = new File(storagePath)
      if (!storageDir.exists()) {
        storageDir.mkdirs()
        logger.info(s"Created storage directory: $storagePath")
      }
    }
  }

  override def uploadFile(
    fileName: String,
    fileContent: Array[Byte],
    mimeType: String,
    category: String,
    examId: Option[String] = None,
    questionNumber: Option[Int] = None,
    studentId: Option[String] = None,
    uploadedBy: String
  ): IO[FileRecord] = {
    for {
      _ <- ensureStorageDirectory()
      
      // Validate file upload
      _ <- validateFileUpload(fileName, fileContent.length, category).flatMap {
        case Left(error) => IO.raiseError(new IllegalArgumentException(error))
        case Right(_) => IO.unit
      }
      
      // Generate unique file ID and storage path
      fileId = UUID.randomUUID().toString
      fileExtension = FilenameUtils.getExtension(fileName)
      storedFileName = if (fileExtension.nonEmpty) s"$fileId.$fileExtension" else fileId
      filePath = Paths.get(storagePath, storedFileName)
      
      // Write file to storage
      _ <- IO {
        Files.write(filePath, fileContent)
        logger.info(s"File written to storage: $filePath")
      }
      
      // Generate file URL
      fileUrl = s"$baseUrl/api/files/$fileId/download"
      
      // Create file record
      fileRecord = FileRecord(
        id = fileId,
        fileName = storedFileName,
        originalName = fileName,
        fileUrl = fileUrl,
        fileSize = fileContent.length.toLong,
        mimeType = mimeType,
        fileType = Some(determineFileType(fileName, mimeType)),
        category = Some(category),
        examId = examId,
        questionNumber = questionNumber,
        studentId = studentId,
        uploadedBy = uploadedBy,
        uploadTime = LocalDateTime.now()
      )
      
      // Save to database
      _ <- fileRepository.createFile(fileRecord)
      
      _ = logger.info(s"File uploaded successfully: $fileId (${fileName})")
      
    } yield fileRecord
  }

  override def downloadFile(fileId: String): IO[Option[(Array[Byte], String, String)]] = {
    for {
      fileRecord <- fileRepository.getFileById(fileId)
      result <- fileRecord match {
        case Some(record) =>
          val filePath = Paths.get(storagePath, record.fileName)
          IO {
            if (Files.exists(filePath)) {
              val fileContent = Files.readAllBytes(filePath)
              Some((fileContent, record.originalName, record.mimeType))
            } else {
              logger.error(s"Physical file not found: $filePath")
              None
            }
          }
        case None =>
          logger.warn(s"File record not found: $fileId")
          IO.pure(None)
      }
    } yield result
  }

  override def getFileInfo(fileId: String): IO[Option[FileRecord]] = {
    fileRepository.getFileById(fileId)
  }

  override def deleteFile(fileId: String): IO[Boolean] = {
    for {
      fileRecord <- fileRepository.getFileById(fileId)
      result <- fileRecord match {
        case Some(record) =>
          val filePath = Paths.get(storagePath, record.fileName)
          for {
            // Delete physical file
            physicalDeleted <- IO {
              if (Files.exists(filePath)) {
                Files.delete(filePath)
                logger.info(s"Physical file deleted: $filePath")
                true
              } else {
                logger.warn(s"Physical file not found: $filePath")
                true // Consider as deleted if file doesn't exist
              }
            }
            
            // Delete database record
            dbDeleted <- fileRepository.deleteFile(fileId)
            
            _ = if (dbDeleted) logger.info(s"File record deleted from database: $fileId")
            
          } yield physicalDeleted && dbDeleted
          
        case None =>
          logger.warn(s"File not found for deletion: $fileId")
          IO.pure(false)
      }
    } yield result
  }

  override def validateFileUpload(fileName: String, fileSize: Long, category: String): IO[Either[String, Unit]] = {
    IO {
      // Check file size limits
      val maxSize = config.storage.maxFileSize.getOrElse(category, config.storage.maxFileSize.getOrElse("default", 10485760L))
      if (fileSize > maxSize) {
        Left(s"File size ($fileSize bytes) exceeds maximum allowed size ($maxSize bytes) for category $category")
      } else {
        // Check file type
        val extension = FilenameUtils.getExtension(fileName).toLowerCase
        val mimeType = determineMimeType(fileName)
        
        val allowedTypes = category match {
          case "avatar" | "answer-image" => config.storage.allowedTypes.getOrElse("image", List.empty)
          case "exam-file" | "document" => 
            config.storage.allowedTypes.getOrElse("image", List.empty) ++ 
            config.storage.allowedTypes.getOrElse("document", List.empty)
          case _ => 
            config.storage.allowedTypes.getOrElse("image", List.empty) ++ 
            config.storage.allowedTypes.getOrElse("document", List.empty)
        }
        
        if (allowedTypes.nonEmpty && !allowedTypes.contains(mimeType)) {
          Left(s"File type not allowed: $mimeType. Allowed types: ${allowedTypes.mkString(", ")}")
        } else {
          Right(())
        }
      }
    }
  }
  
  private def determineFileType(fileName: String, mimeType: String): String = {
    val extension = FilenameUtils.getExtension(fileName).toLowerCase
    
    if (mimeType.startsWith("image/")) {
      "image"
    } else if (mimeType.startsWith("application/pdf") || mimeType.contains("document") || mimeType.contains("text")) {
      "document"
    } else {
      extension match {
        case "pdf" | "doc" | "docx" | "txt" | "rtf" => "document"
        case "jpg" | "jpeg" | "png" | "gif" | "bmp" | "webp" => "image"
        case _ => "other"
      }
    }
  }
  
  private def determineMimeType(fileName: String): String = {
    val extension = FilenameUtils.getExtension(fileName).toLowerCase
    extension match {
      case "pdf" => "application/pdf"
      case "doc" => "application/msword"
      case "docx" => "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
      case "xls" => "application/vnd.ms-excel"
      case "xlsx" => "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
      case "txt" => "text/plain"
      case "jpg" | "jpeg" => "image/jpeg"
      case "png" => "image/png"
      case "gif" => "image/gif"
      case "bmp" => "image/bmp"
      case "webp" => "image/webp"
      case _ => "application/octet-stream"
    }
  }
}
