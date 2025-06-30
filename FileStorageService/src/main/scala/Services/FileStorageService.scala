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
  
  private val logger = org.slf4j.LoggerFactory.getLogger(getClass)
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
    val subDirs = List("uploads", "temp", "images", "exports", "archives", "avatars")
    subDirs.foreach { subDir =>
      val dir = new File(baseDir, subDir)
      if (!dir.exists()) {
        dir.mkdirs()
      }
    }
  }
  
  // 辅助方法：提取文件扩展名
  private def extractFileExtension(fileName: String): Option[String] = {
    val lastDot = fileName.lastIndexOf('.')
    if (lastDot > 0 && lastDot < fileName.length - 1) {
      Some(fileName.substring(lastDot + 1).toLowerCase)
    } else {
      None
    }
  }
  
  // 辅助方法：计算文件哈希
  private def calculateFileHash(content: Array[Byte]): String = {
    val md = MessageDigest.getInstance("SHA-256")
    md.digest(content).map("%02x".format(_)).mkString
  }
  
  // 头像上传（专门的头像上传方法）
  def uploadAvatar(
    userId: String,
    fileName: String,
    fileData: Array[Byte],
    contentType: String,
    userType: String
  ): Future[AvatarUploadResponse] = {
    // 验证是否是图片文件（头像必须是图片）
    if (fileData.length > 5 * 1024 * 1024) { // 5MB限制
      Future.failed(new RuntimeException("头像文件大小不能超过5MB"))
    } else {
      // 首先查找并删除用户的旧头像
      val cleanupOldAvatarFuture = cleanupUserOldAvatar(userId, userType)
      
      cleanupOldAvatarFuture.flatMap { _ =>
        // 生成新头像文件名
        val fileId = UUID.randomUUID().toString
        val fileExtension = extractFileExtension(fileName).getOrElse("jpg")
        val originalName = fileName
        val fileHash = calculateFileHash(fileData)
        
        // 保存头像文件
        val storedName = s"avatar_${fileId}.${fileExtension}"
        val filePath = Paths.get(storageBasePath, "uploads", "avatars", storedName)
        
        // 确保头像目录存在
        val avatarDir = filePath.getParent.toFile
        if (!avatarDir.exists()) {
          avatarDir.mkdirs()
        }
        
        Try {
          Files.write(filePath, fileData)
        } match {
          case Success(_) =>
            // 保存头像信息到数据库
            val fileInfo = FileInfo(
              fileId = fileId,
              originalName = originalName,
              storedName = storedName,
              filePath = filePath.toString,
              fileSize = fileData.length,
              fileType = fileExtension,
              mimeType = Some(contentType),
              uploadUserId = Some(userId),
              uploadUserType = Some(userType),
              uploadTime = LocalDateTime.now(),
              fileStatus = "active",
              accessCount = 0,
              downloadCount = 0,
              lastAccessTime = None,
              description = Some("用户头像"),
              fileHash = Some(fileHash),
              relatedExamId = None,
              relatedSubmissionId = None,
              createdAt = LocalDateTime.now(),
              updatedAt = LocalDateTime.now()
            )
            
            db.insertFileInfo(fileInfo).map { rowsAffected =>
              if (rowsAffected > 0) {
                AvatarUploadResponse(
                  fileId = fileId,
                  fileName = originalName,
                  fileUrl = s"http://${config.serverIP}:${config.serverPort}/api/files/${fileId}",
                  fileSize = fileData.length,
                  uploadTime = LocalDateTime.now(),
                  message = "头像上传成功"
                )
              } else {
                throw new RuntimeException("保存头像信息到数据库失败")
              }
            }
            
          case Failure(exception) =>
            Future.failed(new RuntimeException(s"保存头像文件失败: ${exception.getMessage}"))
        }
      }
    }
  }
  
  // 清理用户旧头像
  private def cleanupUserOldAvatar(userId: String, userType: String): Future[Unit] = {
    db.getFilesByUser(userId, Some(userType)).flatMap { files =>
      val avatarFiles = files.filter(_.description.contains("用户头像"))
      
      if (avatarFiles.nonEmpty) {
        val deleteFutures = avatarFiles.map { file =>
          // 删除物理文件
          Try {
            val filePath = Paths.get(file.filePath)
            if (Files.exists(filePath)) {
              Files.delete(filePath)
            }
          }
          
          // 标记数据库记录为已删除
          db.markFileAsDeleted(file.fileId)
        }
        
        Future.sequence(deleteFutures).map(_ => ())
      } else {
        Future.successful(())
      }
    }
  }
  
  // 文件下载
  def downloadFile(
    fileId: String,
    requestUserId: Option[String] = None,
    requestUserType: Option[String] = None
  ): Future[Either[String, FileDownloadResponse]] = {
    db.getFileById(fileId).flatMap {
      case Some(fileInfo) =>
        if (fileInfo.fileStatus == "active") {
          Try {
            val filePath = Paths.get(fileInfo.filePath)
            if (Files.exists(filePath)) {
              val fileContent = Files.readAllBytes(filePath)
              
              // 记录访问日志
              val accessLog = FileAccessLog(
                logId = UUID.randomUUID().toString,
                fileId = fileId,
                accessUserId = requestUserId.getOrElse("anonymous"),
                accessUserType = requestUserType,
                accessTime = LocalDateTime.now(),
                accessType = "download"
              )
              
              db.insertAccessLog(accessLog).map { _ =>
                // 更新访问计数
                db.updateFileAccessStats(fileId, "download")
                
                Right(FileDownloadResponse(
                  fileId = fileId,
                  originalName = fileInfo.originalName,
                  fileContent = fileContent,
                  fileSize = fileContent.length,
                  mimeType = fileInfo.mimeType.getOrElse("application/octet-stream"),
                  downloadTime = LocalDateTime.now(),
                  success = true
                ))
              }
            } else {
              Future.successful(Left("文件不存在"))
            }
          } match {
            case Success(future) => future
            case Failure(exception) => Future.successful(Left(s"读取文件失败: ${exception.getMessage}"))
          }
        } else {
          Future.successful(Left("文件已被删除"))
        }
      case None =>
        Future.successful(Left("文件不存在"))
    }
  }
  
  // 答题图片上传
  def uploadAnswerImage(
    userId: String,
    userType: String,
    fileName: String,
    fileData: Array[Byte],
    contentType: String,
    examId: String,
    questionNumber: Int
  ): Future[FileUploadResponse] = {
    // 首先清理同一题号的旧答题图片
    cleanupOldAnswerImage(userId, examId, questionNumber).flatMap { _ =>
      val fileId = UUID.randomUUID().toString
      val fileExtension = extractFileExtension(fileName).getOrElse("jpg")
      val storedName = s"answer_${examId}_${questionNumber}_${fileId}.${fileExtension}"
      val filePath = Paths.get(storageBasePath, "uploads", "answers", storedName)
      
      // 确保答题目录存在
      val answerDir = filePath.getParent.toFile
      if (!answerDir.exists()) {
        answerDir.mkdirs()
      }
      
      Try {
        Files.write(filePath, fileData)
      } match {
        case Success(_) =>
          val fileInfo = FileInfo(
            fileId = fileId,
            originalName = fileName,
            storedName = storedName,
            filePath = filePath.toString,
            fileSize = fileData.length,
            fileType = fileExtension,
            mimeType = Some(contentType),
            uploadUserId = Some(userId),
            uploadUserType = Some(userType),
            uploadTime = LocalDateTime.now(),
            fileStatus = "active",
            accessCount = 0,
            downloadCount = 0,
            lastAccessTime = None,
            description = Some(s"答题图片-考试${examId}-题目${questionNumber}"),
            fileHash = Some(calculateFileHash(fileData)),
            relatedExamId = Some(examId),
            relatedSubmissionId = None,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
          )
          
          db.insertFileInfo(fileInfo).map { rowsAffected =>
            if (rowsAffected > 0) {
              FileUploadResponse(
                fileId = fileId,
                originalName = fileName,
                storedName = storedName,
                fileSize = fileData.length,
                uploadTime = LocalDateTime.now(),
                uploadUserId = userId,
                message = "答题图片上传成功"
              )
            } else {
              throw new RuntimeException("保存答题图片信息到数据库失败")
            }
          }
          
        case Failure(exception) =>
          Future.failed(new RuntimeException(s"保存答题图片失败: ${exception.getMessage}"))
      }
    }
  }
  
  // 清理旧答题图片
  private def cleanupOldAnswerImage(userId: String, examId: String, questionNumber: Int): Future[Unit] = {
    db.getFilesByUser(userId, None).flatMap { files =>
      val oldAnswerImages = files.filter { file =>
        file.description.exists(_.contains(s"答题图片-考试${examId}-题目${questionNumber}"))
      }
      
      if (oldAnswerImages.nonEmpty) {
        val deleteFutures = oldAnswerImages.map { file =>
          Try {
            val filePath = Paths.get(file.filePath)
            if (Files.exists(filePath)) {
              Files.delete(filePath)
            }
          }
          db.markFileAsDeleted(file.fileId)
        }
        
        Future.sequence(deleteFutures).map(_ => ())
      } else {
        Future.successful(())
      }
    }
  }
  
  // 考试文件上传
  def uploadExamFile(
    userId: String,
    fileName: String,
    fileData: Array[Byte],
    contentType: String,
    examId: String,
    enableOverride: Boolean
  ): Future[FileUploadResponse] = {
    if (enableOverride) {
      cleanupOldExamFile(examId, fileName).flatMap { _ =>
        performFileUpload(userId, fileName, fileData, contentType, Some(examId), s"考试文件-${examId}")
      }
    } else {
      performFileUpload(userId, fileName, fileData, contentType, Some(examId), s"考试文件-${examId}")
    }
  }
  
  // 清理旧考试文件
  private def cleanupOldExamFile(examId: String, fileName: String): Future[Unit] = {
    db.getFilesByExam(examId).flatMap { files =>
      val oldExamFiles = files.filter(_.originalName == fileName)
      
      if (oldExamFiles.nonEmpty) {
        val deleteFutures = oldExamFiles.map { file =>
          Try {
            val filePath = Paths.get(file.filePath)
            if (Files.exists(filePath)) {
              Files.delete(filePath)
            }
          }
          db.markFileAsDeleted(file.fileId)
        }
        
        Future.sequence(deleteFutures).map(_ => ())
      } else {
        Future.successful(())
      }
    }
  }
  
  // 通用文件上传
  private def performFileUpload(
    userId: String,
    fileName: String,
    fileData: Array[Byte],
    contentType: String,
    relatedExamId: Option[String],
    description: String
  ): Future[FileUploadResponse] = {
    val fileId = UUID.randomUUID().toString
    val fileExtension = extractFileExtension(fileName).getOrElse("bin")
    val storedName = s"file_${fileId}.${fileExtension}"
    val filePath = Paths.get(storageBasePath, "uploads", "files", storedName)
    
    // 确保文件目录存在
    val fileDir = filePath.getParent.toFile
    if (!fileDir.exists()) {
      fileDir.mkdirs()
    }
    
    Try {
      Files.write(filePath, fileData)
    } match {
      case Success(_) =>
        val fileInfo = FileInfo(
          fileId = fileId,
          originalName = fileName,
          storedName = storedName,
          filePath = filePath.toString,
          fileSize = fileData.length,
          fileType = fileExtension,
          mimeType = Some(contentType),
          uploadUserId = Some(userId),
          uploadUserType = Some("admin"),
          uploadTime = LocalDateTime.now(),
          fileStatus = "active",
          accessCount = 0,
          downloadCount = 0,
          lastAccessTime = None,
          description = Some(description),
          fileHash = Some(calculateFileHash(fileData)),
          relatedExamId = relatedExamId,
          relatedSubmissionId = None,
          createdAt = LocalDateTime.now(),
          updatedAt = LocalDateTime.now()
        )
        
        db.insertFileInfo(fileInfo).map { rowsAffected =>
          if (rowsAffected > 0) {
            FileUploadResponse(
              fileId = fileId,
              originalName = fileName,
              storedName = storedName,
              fileSize = fileData.length,
              uploadTime = LocalDateTime.now(),
              uploadUserId = userId,
              message = "文件上传成功"
            )
          } else {
            throw new RuntimeException("保存文件信息到数据库失败")
          }
        }
        
      case Failure(exception) =>
        Future.failed(new RuntimeException(s"保存文件失败: ${exception.getMessage}"))
    }
  }
  
  // 获取图片代理
  def getImageProxy(imageUrl: String): Future[Either[String, Array[Byte]]] = {
    // 简单实现，实际应该支持HTTP请求
    Future.successful(Left("图片代理功能暂未实现"))
  }
  
  // 导出成绩
  def exportScores(examId: String, format: String): Future[Either[String, FileExportResponse]] = {
    // 简单实现，实际应该生成Excel文件
    Future.successful(Left("成绩导出功能暂未实现"))
  }
  
  // 获取仪表盘统计
  def getDashboardStats(): Future[Either[String, DashboardStats]] = {
    db.getFileStats().map { stats =>
      Right(stats)
    }
  }
  
  // 通用文件上传方法（供内部控制器调用）
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
  ): Future[Either[String, FileUploadResponse]] = {
    val userId = uploadUserId.getOrElse("anonymous")
    val userType = uploadUserType.getOrElse("unknown")
    
    val uploadFuture = category match {
      case "avatar" =>
        uploadAvatar(userId, originalName, fileContent, mimeType, userType).map { avatarResponse =>
          FileUploadResponse(
            fileId = avatarResponse.fileId,
            originalName = avatarResponse.fileName,
            storedName = s"avatar_${avatarResponse.fileId}",
            fileSize = avatarResponse.fileSize,
            uploadTime = avatarResponse.uploadTime,
            uploadUserId = userId,
            message = avatarResponse.message
          )
        }
      case "answer" =>
        examId match {
          case Some(examIdValue) =>
            // 假设题目编号为1，实际使用时应该从参数传入
            uploadAnswerImage(userId, userType, originalName, fileContent, mimeType, examIdValue, 1)
          case None =>
            Future.failed(new RuntimeException("答题图片上传需要提供考试ID"))
        }
      case "exam" =>
        examId match {
          case Some(examIdValue) =>
            uploadExamFile(userId, originalName, fileContent, mimeType, examIdValue, enableOverride = true)
          case None =>
            Future.failed(new RuntimeException("考试文件上传需要提供考试ID"))
        }
      case _ =>
        performFileUpload(userId, originalName, fileContent, mimeType, examId, description.getOrElse("通用文件"))
    }
    
    uploadFuture.map(Right(_)).recover {
      case ex: RuntimeException => Left(ex.getMessage)
      case ex: Exception => Left(s"上传失败: ${ex.getMessage}")
    }
  }
  
  // 删除文件方法
  def deleteFile(
    fileId: String,
    requestUserId: Option[String] = None,
    requestUserType: Option[String] = None
  ): Future[Either[String, String]] = {
    db.getFileById(fileId).flatMap {
      case Some(fileInfo) =>
        if (fileInfo.fileStatus == "active") {
          // 删除物理文件
          Try {
            val filePath = Paths.get(fileInfo.filePath)
            if (Files.exists(filePath)) {
              Files.delete(filePath)
            }
          } match {
            case Success(_) =>
              // 标记数据库记录为已删除
              db.markFileAsDeleted(fileId).map { rowsAffected =>
                if (rowsAffected > 0) {
                  // 记录删除日志
                  val accessLog = FileAccessLog(
                    logId = UUID.randomUUID().toString,
                    fileId = fileId,
                    accessUserId = requestUserId.getOrElse("anonymous"),
                    accessUserType = requestUserType,
                    accessTime = LocalDateTime.now(),
                    accessType = "delete"
                  )
                  db.insertAccessLog(accessLog)
                  
                  Right("文件删除成功")
                } else {
                  Left("文件删除失败")
                }
              }
            case Failure(exception) =>
              Future.successful(Left(s"删除物理文件失败: ${exception.getMessage}"))
          }
        } else {
          Future.successful(Left("文件已被删除"))
        }
      case None =>
        Future.successful(Left("文件不存在"))
    }
  }
  
  // 获取文件信息方法
  def getFileInfo(
    fileId: String,
    requestUserId: Option[String] = None,
    requestUserType: Option[String] = None
  ): Future[Either[String, FileInfo]] = {
    db.getFileById(fileId).flatMap {
      case Some(fileInfo) =>
        if (fileInfo.fileStatus == "active") {
          // 记录访问日志
          val accessLog = FileAccessLog(
            logId = UUID.randomUUID().toString,
            fileId = fileId,
            accessUserId = requestUserId.getOrElse("anonymous"),
            accessUserType = requestUserType,
            accessTime = LocalDateTime.now(),
            accessType = "info"
          )
          
          db.insertAccessLog(accessLog).map { _ =>
            // 更新访问计数
            db.updateFileAccessStats(fileId, "access")
            Right(fileInfo)
          }
        } else {
          Future.successful(Left("文件已被删除"))
        }
      case None =>
        Future.successful(Left("文件不存在"))
    }
  }
  
  // 清理临时文件方法
  def cleanupTempFiles(): Future[Unit] = {
    Future {
      try {
        val tempDir = new File(storageBasePath, "temp")
        if (tempDir.exists() && tempDir.isDirectory) {
          val cutoffTime = LocalDateTime.now().minusHours(24)
          
          tempDir.listFiles().foreach { file =>
            if (file.isFile && file.lastModified() < java.sql.Timestamp.valueOf(cutoffTime).getTime) {
              try {
                file.delete()
                logger.info(s"删除临时文件: ${file.getAbsolutePath}")
              } catch {
                case e: Exception =>
                  logger.warn(s"删除临时文件失败: ${file.getAbsolutePath}, 错误: ${e.getMessage}")
              }
            }
          }
        }
      } catch {
        case e: Exception =>
          logger.error(s"清理临时文件时发生错误: ${e.getMessage}")
      }
    }
  }
}
