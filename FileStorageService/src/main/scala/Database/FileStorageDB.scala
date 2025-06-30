package Database

import java.sql.{Connection, DriverManager, PreparedStatement, ResultSet, SQLException, Timestamp}
import scala.util.{Try, Success, Failure}
import scala.concurrent.{Future, ExecutionContext}
import java.util.UUID
import java.time.LocalDateTime
import Config.ServerConfig
import Models._

class FileStorageDB(config: ServerConfig)(implicit ec: ExecutionContext) {
  
  private val jdbcUrl = config.jdbcUrl
  private val username = config.username
  private val password = config.password
  
  // 连接池配置
  private val maxPoolSize = config.maximumPoolSize
  private val connectionLiveMinutes = config.connectionLiveMinutes
  
  // 获取数据库连接
  private def getConnection: Connection = {
    try {
      Class.forName("org.postgresql.Driver")
      val connection = DriverManager.getConnection(jdbcUrl, username, password)
      connection.setAutoCommit(false)
      connection
    } catch {
      case e: SQLException =>
        println(s"数据库连接失败: ${e.getMessage}")
        throw e
      case e: ClassNotFoundException =>
        println(s"PostgreSQL驱动未找到: ${e.getMessage}")
        throw e
    }
  }
  
  // 执行查询
  private def executeQuery[T](sql: String, params: Any*)(mapper: ResultSet => T): Future[List[T]] = {
    Future {
      var connection: Connection = null
      var stmt: PreparedStatement = null
      var rs: ResultSet = null
      
      try {
        connection = getConnection
        stmt = connection.prepareStatement(sql)
        
        // 设置参数
        params.zipWithIndex.foreach { case (param, index) =>
          param match {
            case s: String => stmt.setString(index + 1, s)
            case i: Int => stmt.setInt(index + 1, i)
            case l: Long => stmt.setLong(index + 1, l)
            case t: Timestamp => stmt.setTimestamp(index + 1, t)
            case b: Boolean => stmt.setBoolean(index + 1, b)
            case null => stmt.setNull(index + 1, java.sql.Types.NULL)
            case other => stmt.setObject(index + 1, other)
          }
        }
        
        rs = stmt.executeQuery()
        var results = List.empty[T]
        
        while (rs.next()) {
          results = mapper(rs) :: results
        }
        
        connection.commit()
        results.reverse
        
      } catch {
        case e: SQLException =>
          if (connection != null) connection.rollback()
          println(s"查询执行失败: ${e.getMessage}")
          throw e
      } finally {
        if (rs != null) rs.close()
        if (stmt != null) stmt.close()
        if (connection != null) connection.close()
      }
    }
  }
  
  // 执行更新
  private def executeUpdate(sql: String, params: Any*): Future[Int] = {
    Future {
      var connection: Connection = null
      var stmt: PreparedStatement = null
      
      try {
        connection = getConnection
        stmt = connection.prepareStatement(sql)
        
        // 设置参数
        params.zipWithIndex.foreach { case (param, index) =>
          param match {
            case s: String => stmt.setString(index + 1, s)
            case i: Int => stmt.setInt(index + 1, i)
            case l: Long => stmt.setLong(index + 1, l)
            case t: Timestamp => stmt.setTimestamp(index + 1, t)
            case b: Boolean => stmt.setBoolean(index + 1, b)
            case null => stmt.setNull(index + 1, java.sql.Types.NULL)
            case other => stmt.setObject(index + 1, other)
          }
        }
        
        val result = stmt.executeUpdate()
        connection.commit()
        result
        
      } catch {
        case e: SQLException =>
          if (connection != null) connection.rollback()
          println(s"更新执行失败: ${e.getMessage}")
          throw e
      } finally {
        if (stmt != null) stmt.close()
        if (connection != null) connection.close()
      }
    }
  }
  
  // ResultSet映射器
  private def mapFileInfo(rs: ResultSet): FileInfo = {
    FileInfo(
      fileId = rs.getString("file_id"),
      originalName = rs.getString("original_name"),
      storedName = rs.getString("stored_name"),
      filePath = rs.getString("file_path"),
      fileSize = rs.getLong("file_size"),
      fileType = rs.getString("file_type"),
      mimeType = Option(rs.getString("mime_type")),
      uploadUserId = Option(rs.getString("upload_user_id")),
      uploadUserType = Option(rs.getString("upload_user_type")),
      uploadTime = rs.getTimestamp("upload_time").toLocalDateTime,
      fileStatus = rs.getString("file_status"),
      accessCount = rs.getInt("access_count"),
      downloadCount = rs.getInt("download_count"),
      lastAccessTime = Option(rs.getTimestamp("last_access_time")).map(_.toLocalDateTime),
      description = Option(rs.getString("description")),
      fileHash = Option(rs.getString("file_hash")),
      relatedExamId = Option(rs.getString("related_exam_id")),
      relatedSubmissionId = Option(rs.getString("related_submission_id")),
      createdAt = rs.getTimestamp("created_at").toLocalDateTime,
      updatedAt = rs.getTimestamp("updated_at").toLocalDateTime
    )
  }
  
  private def mapFileAccessLog(rs: ResultSet): FileAccessLog = {
    FileAccessLog(
      logId = rs.getInt("log_id"),
      fileId = rs.getString("file_id"),
      accessUserId = Option(rs.getString("access_user_id")),
      accessUserType = Option(rs.getString("access_user_type")),
      accessTime = rs.getTimestamp("access_time").toLocalDateTime,
      accessType = rs.getString("access_type"),
      clientIp = Option(rs.getString("client_ip")),
      userAgent = Option(rs.getString("user_agent")),
      success = rs.getBoolean("success"),
      errorMessage = Option(rs.getString("error_message"))
    )
  }
  
  private def mapDashboardStats(rs: ResultSet): DashboardStats = {
    DashboardStats(
      totalFiles = rs.getInt("total_files"),
      totalSize = rs.getLong("total_size"),
      activeFiles = rs.getInt("active_files"),
      deletedFiles = rs.getInt("deleted_files"),
      todayUploads = rs.getInt("today_uploads"),
      weekUploads = rs.getInt("week_uploads"),
      monthUploads = rs.getInt("month_uploads")
    )
  }
  
  // 文件信息 CRUD 操作
  def insertFileInfo(fileInfo: FileInfo): Future[Int] = {
    val sql = """
      INSERT INTO files (
        file_id, original_name, stored_name, file_path, file_size, file_type, 
        mime_type, upload_user_id, upload_user_type, upload_time, file_status,
        access_count, download_count, last_access_time, description, file_hash, 
        related_exam_id, related_submission_id, created_at, updated_at
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """
    
    executeUpdate(sql,
      fileInfo.fileId,
      fileInfo.originalName,
      fileInfo.storedName,
      fileInfo.filePath,
      fileInfo.fileSize,
      fileInfo.fileType,
      fileInfo.mimeType.orNull,
      fileInfo.uploadUserId.orNull,
      fileInfo.uploadUserType.orNull,
      Timestamp.valueOf(fileInfo.uploadTime),
      fileInfo.fileStatus,
      fileInfo.accessCount,
      fileInfo.downloadCount,
      fileInfo.lastAccessTime.map(Timestamp.valueOf).orNull,
      fileInfo.description.orNull,
      fileInfo.fileHash.orNull,
      fileInfo.relatedExamId.orNull,
      fileInfo.relatedSubmissionId.orNull,
      Timestamp.valueOf(fileInfo.createdAt),
      Timestamp.valueOf(fileInfo.updatedAt)
    )
  }
  
  def getFileInfoById(fileId: String): Future[Option[FileInfo]] = {
    val sql = "SELECT * FROM files WHERE file_id = ? AND file_status = 'active'"
    executeQuery(sql, fileId)(mapFileInfo).map(_.headOption)
  }
  
  def getFilesByUserId(userId: String, userType: String): Future[List[FileInfo]] = {
    val sql = """
      SELECT * FROM files 
      WHERE upload_user_id = ? AND upload_user_type = ? AND file_status = 'active'
      ORDER BY upload_time DESC
    """
    executeQuery(sql, userId, userType)(mapFileInfo)
  }
  
  def getFilesByExamId(examId: String): Future[List[FileInfo]] = {
    val sql = """
      SELECT * FROM files 
      WHERE related_exam_id = ? AND file_status = 'active'
      ORDER BY upload_time DESC
    """
    executeQuery(sql, examId)(mapFileInfo)
  }
  
  def updateFileAccessCount(fileId: String): Future[Int] = {
    val sql = """
      UPDATE files 
      SET access_count = access_count + 1, last_access_time = CURRENT_TIMESTAMP 
      WHERE file_id = ?
    """
    executeUpdate(sql, fileId)
  }
  
  def deleteFile(fileId: String): Future[Int] = {
    val sql = "UPDATE files SET file_status = 'deleted' WHERE file_id = ?"
    executeUpdate(sql, fileId)
  }
  
  def updateFileInfo(fileInfo: FileInfo): Future[Int] = {
    val sql = """
      UPDATE files SET 
        original_name = ?, stored_name = ?, file_path = ?, file_size = ?, 
        file_type = ?, mime_type = ?, upload_user_id = ?, upload_user_type = ?, 
        upload_time = ?, file_status = ?, description = ?, file_hash = ?, 
        related_exam_id = ?, related_submission_id = ?, last_access_time = ?, 
        access_count = ?, download_count = ?, created_at = ?, updated_at = ?
      WHERE file_id = ?
    """
    
    executeUpdate(sql,
      fileInfo.originalName,
      fileInfo.storedName,
      fileInfo.filePath,
      fileInfo.fileSize,
      fileInfo.fileType,
      fileInfo.mimeType.orNull,
      fileInfo.uploadUserId.orNull,
      fileInfo.uploadUserType.orNull,
      Timestamp.valueOf(fileInfo.uploadTime),
      fileInfo.fileStatus,
      fileInfo.description.orNull,
      fileInfo.fileHash.orNull,
      fileInfo.relatedExamId.orNull,
      fileInfo.relatedSubmissionId.orNull,
      fileInfo.lastAccessTime.map(Timestamp.valueOf).orNull,
      fileInfo.accessCount,
      fileInfo.downloadCount,
      Timestamp.valueOf(fileInfo.createdAt),
      Timestamp.valueOf(fileInfo.updatedAt),
      fileInfo.fileId
    )
  }
  
  // 文件访问日志
  def insertAccessLog(log: FileAccessLog): Future[Int] = {
    val sql = """
      INSERT INTO file_access_log (
        file_id, access_user_id, access_user_type, access_time, access_type,
        client_ip, user_agent, success, error_message
      ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    """
    
    executeUpdate(sql,
      log.fileId,
      log.accessUserId.orNull,
      log.accessUserType.orNull,
      Timestamp.valueOf(log.accessTime),
      log.accessType,
      log.clientIp.orNull,
      log.userAgent.orNull,
      log.success,
      log.errorMessage.orNull
    )
  }

  // logFileAccess 别名方法
  def logFileAccess(log: FileAccessLog): Future[Int] = insertAccessLog(log)
  
  // 统计数据
  def getDashboardStats(): Future[Option[DashboardStats]] = {
    val sql = "SELECT * FROM v_file_dashboard"
    executeQuery(sql)(mapDashboardStats).map(_.headOption)
  }
  
  // 检查文件是否存在
  def fileExists(fileHash: String): Future[Option[FileInfo]] = {
    val sql = "SELECT * FROM files WHERE file_hash = ? AND file_status = 'active'"
    executeQuery(sql, fileHash)(mapFileInfo).map(_.headOption)
  }
  
  // 获取文件信息
  def getFileInfo(fileId: String): Future[Option[FileInfo]] = {
    val sql = """
      SELECT file_id, original_name, stored_name, file_path, file_size, file_type, 
             mime_type, upload_user_id, upload_user_type, upload_time, file_status,
             access_count, download_count, last_access_time, description, file_hash,
             related_exam_id, related_submission_id, created_at, updated_at
      FROM files 
      WHERE file_id = ? AND file_status = 'active'
    """
    executeQuery(sql, fileId) { rs =>
      FileInfo(
        fileId = rs.getString("file_id"),
        originalName = rs.getString("original_name"),
        storedName = rs.getString("stored_name"),
        filePath = rs.getString("file_path"),
        fileSize = rs.getLong("file_size"),
        fileType = rs.getString("file_type"),
        mimeType = Option(rs.getString("mime_type")),
        uploadUserId = Option(rs.getString("upload_user_id")),
        uploadUserType = Option(rs.getString("upload_user_type")),
        uploadTime = rs.getTimestamp("upload_time").toLocalDateTime,
        fileStatus = rs.getString("file_status"),
        accessCount = rs.getInt("access_count"),
        downloadCount = rs.getInt("download_count"),
        lastAccessTime = Option(rs.getTimestamp("last_access_time")).map(_.toLocalDateTime),
        description = Option(rs.getString("description")),
        fileHash = Option(rs.getString("file_hash")),
        relatedExamId = Option(rs.getString("related_exam_id")),
        relatedSubmissionId = Option(rs.getString("related_submission_id")),
        createdAt = rs.getTimestamp("created_at").toLocalDateTime,
        updatedAt = rs.getTimestamp("updated_at").toLocalDateTime
      )
    }.map(_.headOption)
  }

  // 清理过期文件
  def cleanupExpiredFiles(daysBefore: Int): Future[Int] = {
    val sql = """
      UPDATE files 
      SET file_status = 'deleted' 
      WHERE upload_time < CURRENT_TIMESTAMP - INTERVAL ? DAY
      AND file_status = 'active'
    """
    executeUpdate(sql, s"$daysBefore")
  }
  
  // 数据库连接测试
  def testConnection(): Future[Boolean] = {
    Future {
      var connection: Connection = null
      try {
        connection = getConnection
        !connection.isClosed
      } catch {
        case _: Exception => false
      } finally {
        if (connection != null) connection.close()
      }
    }
  }
}
