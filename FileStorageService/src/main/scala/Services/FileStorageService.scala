package Services

import java.io._
import java.nio.file.{Files, Paths, StandardCopyOption}
import java.security.MessageDigest
import java.util.UUID
import scala.util.{Try, Success, Failure}
import scala.concurrent.{Future, ExecutionContext}
import java.time.LocalDateTime
import Database.FileStorageDB
import Models._
import Config.ServerConfig

class FileStorageService(db: FileStorageDB, config: ServerConfig)(implicit ec: ExecutionContext) {
  
  private val storageBasePath = config.fileStoragePath
  private val maxFileSize = config.maxFileSize
  private val allowedFileTypes = config.allowedFileTypes
  
  // 确保存储目录存在
  initStorageDirectory()
  
  private def initStorageDirectory(): Unit = {
    val baseDir = new File(storageBasePath)
    if (!baseDir.exists()) {
      baseDir.mkdirs()
    }
    
    // 创建子目录
    val subDirs = List("uploads", "temp", "images", "exports", "archives")
    subDirs.foreach { subDir =>
      val dir = new File(baseDir, subDir)
      if (!dir.exists()) {
        dir.mkdirs()
      }
    }
  }
  
  // 文件上传
  def uploadFile(
    originalName: String,
    fileContent: Array[Byte],
    uploadUserId: Option[String],
    uploadUserType: Option[String],
    relatedExamId: Option[String] = None,
    relatedSubmissionId: Option[String] = None,
    description: Option[String] = None
  ): Future[Either[String, FileUploadResponse]] = {
    
    // 验证文件
    validateFile(originalName, fileContent) match {
      case Left(error) => Future.successful(Left(error))
      case Right(_) =>
        val fileId = UUID.randomUUID().toString
        val fileHash = calculateFileHash(fileContent)
        val fileType = extractFileExtension(originalName)
        val mimeType = detectMimeType(originalName)
        
        // 检查文件是否已存在
        db.fileExists(fileHash).flatMap { existingFile =>
          existingFile match {
            case Some(existing) =>
              // 文件已存在，返回现有文件信息
              Future.successful(Right(FileUploadResponse(
                fileId = existing.fileId,
                originalName = existing.originalName,
                fileSize = existing.fileSize,
                fileType = existing.fileType,
                uploadTime = existing.uploadTime,
                success = true,
                message = "文件已存在，返回现有文件信息"
              )))
              
            case None =>
              // 新文件，保存到磁盘
              val storedName = generateStoredName(fileId, fileType)
              val filePath = Paths.get(storageBasePath, "uploads", storedName)
              
              Try {
                Files.write(filePath, fileContent)
              } match {
                case Success(_) =>
                  // 保存文件信息到数据库
                  val fileInfo = FileInfo(
                    fileId = fileId,
                    originalName = originalName,
                    storedName = storedName,
                    filePath = filePath.toString,
                    fileSize = fileContent.length,
                    fileType = fileType,
                    mimeType = Some(mimeType),
                    uploadUserId = uploadUserId,
                    uploadUserType = uploadUserType,
                    uploadTime = LocalDateTime.now(),
                    fileStatus = "active",
                    accessCount = 0,
                    downloadCount = 0,
                    lastAccessTime = None,
                    description = description,
                    fileHash = Some(fileHash),
                    relatedExamId = relatedExamId,
                    relatedSubmissionId = relatedSubmissionId,
                    createdAt = LocalDateTime.now(),
                    updatedAt = LocalDateTime.now()
                  )
                  
                  db.insertFileInfo(fileInfo).map { _ =>
                    Right(FileUploadResponse(
                      fileId = fileId,
                      originalName = originalName,
                      fileSize = fileContent.length,
                      fileType = fileType,
                      uploadTime = LocalDateTime.now(),
                      success = true,
                      message = "文件上传成功"
                    ))
                  }.recover {
                    case ex =>
                      // 数据库保存失败，删除已保存的文件
                      Files.deleteIfExists(filePath)
                      Left(s"数据库保存失败: ${ex.getMessage}")
                  }
                  
                case Failure(ex) =>
                  Future.successful(Left(s"文件保存失败: ${ex.getMessage}"))
              }
          }
        }
    }
  }
  
  // 文件下载
  def downloadFile(fileId: String, accessUserId: Option[String], accessUserType: Option[String]): Future[Either[String, FileDownloadResponse]] = {
    db.getFileInfoById(fileId).flatMap {
      case None => Future.successful(Left("文件不存在"))
      case Some(fileInfo) =>
        val filePath = Paths.get(fileInfo.filePath)
        
        if (!Files.exists(filePath)) {
          Future.successful(Left("文件不存在于存储系统中"))
        } else {
          Try {
            val fileContent = Files.readAllBytes(filePath)
            
            // 记录访问日志
            val accessLog = FileAccessLog(
              logId = 0,
              fileId = fileId,
              accessUserId = accessUserId,
              accessUserType = accessUserType,
              accessTime = LocalDateTime.now(),
              accessType = "download",
              clientIp = None,
              userAgent = None,
              success = true,
              errorMessage = None
            )
            
            db.insertAccessLog(accessLog)
            db.updateFileAccessCount(fileId)
            
            FileDownloadResponse(
              fileId = fileId,
              originalName = fileInfo.originalName,
              fileContent = fileContent,
              fileSize = fileInfo.fileSize,
              mimeType = fileInfo.mimeType.getOrElse("application/octet-stream"),
              success = true,
              message = "文件下载成功"
            )
          } match {
            case Success(response) => Future.successful(Right(response))
            case Failure(ex) => Future.successful(Left(s"文件读取失败: ${ex.getMessage}"))
          }
        }
    }
  }
  
  // 图片代理服务
  def getImageProxy(imageUrl: String): Future[Either[String, Array[Byte]]] = {
    // 这里可以实现图片URL代理功能
    // 如果是本地文件，直接读取；如果是外部URL，下载并缓存
    if (imageUrl.startsWith("http")) {
      // 外部URL代理
      Future.successful(Left("暂不支持外部URL代理"))
    } else {
      // 本地文件路径
      val filePath = Paths.get(storageBasePath, imageUrl)
      if (Files.exists(filePath)) {
        Try {
          Files.readAllBytes(filePath)
        } match {
          case Success(content) => Future.successful(Right(content))
          case Failure(ex) => Future.successful(Left(s"图片读取失败: ${ex.getMessage}"))
        }
      } else {
        Future.successful(Left("图片文件不存在"))
      }
    }
  }
  
  // 成绩导出
  def exportScores(examId: String, format: String): Future[Either[String, FileExportResponse]] = {
    // 这里应该调用成绩统计服务获取数据，然后生成Excel或PDF文件
    val exportId = UUID.randomUUID().toString
    val fileName = s"exam_${examId}_scores_${System.currentTimeMillis()}.${format}"
    val filePath = Paths.get(storageBasePath, "exports", fileName)
    
    // 模拟数据导出（实际应该从成绩服务获取数据）
    format.toLowerCase match {
      case "excel" => generateExcelFile(examId, filePath)
      case "pdf" => generatePdfFile(examId, filePath)
      case _ => Future.successful(Left("不支持的导出格式"))
    }
  }
  
  // 生成Excel文件
  private def generateExcelFile(examId: String, filePath: java.nio.file.Path): Future[Either[String, FileExportResponse]] = {
    // 这里应该使用Apache POI等库生成Excel文件
    // 目前创建一个简单的CSV文件作为示例
    val csvContent = """学号,姓名,总分,排名
2024001,张三,95,1
2024002,李四,92,2
2024003,王五,88,3
"""
    
    Try {
      Files.write(filePath, csvContent.getBytes("UTF-8"))
      FileExportResponse(
        fileId = UUID.randomUUID().toString,
        fileName = filePath.getFileName.toString,
        fileSize = csvContent.getBytes("UTF-8").length,
        exportTime = LocalDateTime.now(),
        success = true,
        message = "Excel导出成功"
      )
    } match {
      case Success(response) => Future.successful(Right(response))
      case Failure(ex) => Future.successful(Left(s"Excel生成失败: ${ex.getMessage}"))
    }
  }
  
  // 生成PDF文件
  private def generatePdfFile(examId: String, filePath: java.nio.file.Path): Future[Either[String, FileExportResponse]] = {
    // 这里应该使用iText等库生成PDF文件
    // 目前创建一个简单的文本文件作为示例
    val pdfContent = s"考试成绩报告\n考试ID: $examId\n生成时间: ${LocalDateTime.now()}\n"
    
    Try {
      Files.write(filePath, pdfContent.getBytes("UTF-8"))
      FileExportResponse(
        fileId = UUID.randomUUID().toString,
        fileName = filePath.getFileName.toString,
        fileSize = pdfContent.getBytes("UTF-8").length,
        exportTime = LocalDateTime.now(),
        success = true,
        message = "PDF导出成功"
      )
    } match {
      case Success(response) => Future.successful(Right(response))
      case Failure(ex) => Future.successful(Left(s"PDF生成失败: ${ex.getMessage}"))
    }
  }
  
  // 获取仪表盘统计数据
  def getDashboardStats(): Future[Either[String, DashboardStats]] = {
    db.getDashboardStats().map {
      case Some(stats) => Right(stats)
      case None => Left("无法获取统计数据")
    }.recover {
      case ex => Left(s"获取统计数据失败: ${ex.getMessage}")
    }
  }
  
  // 文件验证
  private def validateFile(originalName: String, fileContent: Array[Byte]): Either[String, Unit] = {
    // 检查文件大小
    if (fileContent.length > maxFileSize) {
      return Left(s"文件大小超过限制 (${maxFileSize} bytes)")
    }
    
    // 检查文件扩展名
    val fileExtension = extractFileExtension(originalName).toLowerCase
    if (!allowedFileTypes.contains(fileExtension)) {
      return Left(s"不支持的文件类型: $fileExtension")
    }
    
    // 检查文件内容（简单的文件头检查）
    if (fileContent.length < 10) {
      return Left("文件内容不完整")
    }
    
    Right(())
  }
  
  // 提取文件扩展名
  private def extractFileExtension(fileName: String): String = {
    val lastDotIndex = fileName.lastIndexOf('.')
    if (lastDotIndex > 0 && lastDotIndex < fileName.length - 1) {
      fileName.substring(lastDotIndex + 1)
    } else {
      ""
    }
  }
  
  // 生成存储文件名
  private def generateStoredName(fileId: String, fileType: String): String = {
    s"${fileId}.${fileType}"
  }
  
  // 计算文件哈希值
  private def calculateFileHash(fileContent: Array[Byte]): String = {
    val md = MessageDigest.getInstance("SHA-256")
    md.digest(fileContent).map("%02x".format(_)).mkString
  }
  
  // 检测MIME类型
  private def detectMimeType(fileName: String): String = {
    val extension = extractFileExtension(fileName).toLowerCase
    extension match {
      case "jpg" | "jpeg" => "image/jpeg"
      case "png" => "image/png"
      case "gif" => "image/gif"
      case "pdf" => "application/pdf"
      case "doc" => "application/msword"
      case "docx" => "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
      case "xls" => "application/vnd.ms-excel"
      case "xlsx" => "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
      case "txt" => "text/plain"
      case "csv" => "text/csv"
      case "zip" => "application/zip"
      case _ => "application/octet-stream"
    }
  }
  
  // 清理临时文件
  def cleanupTempFiles(): Future[Unit] = {
    Future {
      val tempDir = new File(storageBasePath, "temp")
      if (tempDir.exists()) {
        tempDir.listFiles().foreach { file =>
          if (file.isFile && System.currentTimeMillis() - file.lastModified() > 24 * 60 * 60 * 1000) {
            file.delete()
          }
        }
      }
    }
  }
  
  // 获取用户文件列表
  def getUserFiles(userId: String, userType: String): Future[List[FileInfo]] = {
    db.getFilesByUserId(userId, userType)
  }
  
  // 获取考试相关文件
  def getExamFiles(examId: String): Future[List[FileInfo]] = {
    db.getFilesByExamId(examId)
  }
  
  // 删除文件
  def deleteFile(
    fileId: String,
    requestUserId: Option[String],
    requestUserType: Option[String]
  ): Future[Either[String, String]] = {
    db.getFileInfo(fileId).flatMap {
      case Some(fileInfo) =>
        // 检查权限（可以根据业务逻辑添加更严格的权限控制）
        val canDelete = requestUserType match {
          case Some("admin") => true
          case Some("coach") => fileInfo.uploadUserId == requestUserId
          case Some("student") => fileInfo.uploadUserId == requestUserId
          case _ => false
        }
        
        if (canDelete) {
          // 软删除：更新数据库状态
          val updatedFileInfo = fileInfo.copy(
            fileStatus = "deleted",
            updatedAt = LocalDateTime.now()
          )
          
          db.updateFileInfo(updatedFileInfo).map { _ =>
            // 记录访问日志
            db.logFileAccess(FileAccessLog(
              logId = 0,
              fileId = fileId,
              accessUserId = requestUserId,
              accessUserType = requestUserType,
              accessTime = LocalDateTime.now(),
              accessType = "delete",
              clientIp = None,
              userAgent = None,
              success = true,
              errorMessage = None
            ))
            
            Right("File deleted successfully")
          }.recover {
            case ex => Left(s"Failed to delete file: ${ex.getMessage}")
          }
        } else {
          Future.successful(Left("Permission denied"))
        }
      case None =>
        Future.successful(Left("File not found"))
    }
  }
  
  // 获取文件信息
  def getFileInfo(
    fileId: String,
    requestUserId: Option[String],
    requestUserType: Option[String]
  ): Future[Either[String, FileInfo]] = {
    db.getFileInfo(fileId).map {
      case Some(fileInfo) =>
        // 检查权限
        val canView = requestUserType match {
          case Some("admin") => true
          case Some("grader") => true
          case Some("coach") => true
          case Some("student") => fileInfo.uploadUserId == requestUserId || fileInfo.fileStatus == "active"
          case _ => false
        }
        
        if (canView) {
          Right(fileInfo)
        } else {
          Left("Permission denied")
        }
      case None =>
        Left("File not found")
    }
  }
}
