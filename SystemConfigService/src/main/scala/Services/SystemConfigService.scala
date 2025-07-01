package Services

import Models.*
import Database.{DatabaseManager, SqlParameter}
import Config.Constants
import cats.effect.IO
import cats.implicits.*
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID
import at.favre.lib.crypto.bcrypt.BCrypt

// 系统管理员服务trait
trait SystemAdminService {
  def getSystemAdmins(params: QueryParams): IO[PaginatedResponse[SystemAdmin]]
  def getSystemAdminById(adminId: String): IO[Option[SystemAdmin]]
  def createSystemAdmin(request: CreateAdminRequest): IO[String]
  def updateSystemAdmin(adminId: String, request: UpdateAdminRequest): IO[Unit]
  def deleteSystemAdmin(adminId: String): IO[Unit]
  def changeAdminPassword(adminId: String, request: ChangeAdminPasswordRequest): IO[Unit]
  def resetAdminPassword(adminId: String, newPassword: String): IO[Unit]
}

// 系统设置服务trait
trait SystemSettingsService {
  def getSystemSettings(): IO[SystemSettings]
  def updateSystemSettings(request: UpdateSystemSettingsRequest): IO[Unit]
  def getPublicSettings(): IO[SystemSettings]
}



// 综合系统配置服务trait
trait SystemConfigService extends SystemAdminService with SystemSettingsService

// 系统管理员服务实现
class SystemAdminServiceImpl extends SystemAdminService {
  private val logger = LoggerFactory.getLogger("SystemAdminService")
  private val schemaName = "systemconfig"

  override def getSystemAdmins(params: QueryParams): IO[PaginatedResponse[SystemAdmin]] = {
    val page = params.page.getOrElse(1)
    val limit = math.min(params.limit.getOrElse(Constants.DEFAULT_PAGE_SIZE), Constants.MAX_PAGE_SIZE)
    val offset = (page - 1) * limit

    // 构建WHERE条件
    val whereConditions = scala.collection.mutable.ListBuffer[String]()
    val sqlParams = scala.collection.mutable.ListBuffer[SqlParameter]()

    params.search.foreach { search =>
      whereConditions += "(username ILIKE ? OR name ILIKE ?)"
      sqlParams += SqlParameter("String", s"%$search%")
      sqlParams += SqlParameter("String", s"%$search%")
    }

    val whereClause = if (whereConditions.nonEmpty) 
      s"WHERE ${whereConditions.mkString(" AND ")}" 
    else ""

    // 查询总数
    val countSql = s"""
      SELECT COUNT(*) as total
      FROM systemconfig.system_admins
      $whereClause
    """.stripMargin

    // 查询数据
    val dataSql = s"""
      SELECT 
        admin_id, username, role, status, name, email, phone, 
        avatar_url, created_at, updated_at, last_login_at
      FROM systemconfig.system_admins
      $whereClause
      ORDER BY created_at DESC
      LIMIT ? OFFSET ?
    """.stripMargin

    val dataParams = sqlParams.toList ++ List(
      SqlParameter("Int", limit),
      SqlParameter("Int", offset)
    )

    for {
      totalResult <- DatabaseManager.executeQueryOptional(countSql, sqlParams.toList)
      total = totalResult.map(DatabaseManager.decodeFieldUnsafe[Int](_, "total")).getOrElse(0)
      
      dataResults <- DatabaseManager.executeQuery(dataSql, dataParams)
      admins = dataResults.map(convertToSystemAdmin)
    } yield PaginatedResponse(admins, total, page, limit).withTotalPages
  }

  override def getSystemAdminById(adminId: String): IO[Option[SystemAdmin]] = {
    val sql = s"""
      SELECT 
        admin_id, username, role, status, name, email, phone, 
        avatar_url, created_at, updated_at, last_login_at
      FROM systemconfig.system_admins
      WHERE admin_id = ?
    """.stripMargin

    val params = List(SqlParameter("String", adminId))

    DatabaseManager.executeQueryOptional(sql, params).map(_.map(convertToSystemAdmin))
  }

  override def createSystemAdmin(request: CreateAdminRequest): IO[String] = {
    for {
      // 检查用户名唯一性
      _ <- checkUsernameExists(request.username).flatMap {
        case true => IO.raiseError(new RuntimeException(s"用户名已存在: ${request.username}"))
        case false => IO.unit
      }
      
      // 生成ID和密码哈希
      adminId = UUID.randomUUID().toString
      passwordHash = BCrypt.withDefaults().hashToString(12, request.password.toCharArray)
      
      // 插入数据库
      sql = s"""
        INSERT INTO systemconfig.system_admins 
        (admin_id, username, password_hash, salt, role, status, name, email, phone, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
      """.stripMargin
      
      params = List(
        SqlParameter("String", adminId),
        SqlParameter("String", request.username),
        SqlParameter("String", passwordHash),
        SqlParameter("String", Constants.PASSWORD_SALT),
        SqlParameter("String", request.role),
        SqlParameter("String", Constants.ADMIN_STATUS_ACTIVE),
        SqlParameter("String", request.name.orNull),
        SqlParameter("String", request.email.orNull),
        SqlParameter("String", request.phone.orNull)
      )
      
      _ <- DatabaseManager.executeUpdate(sql, params)
      _ = logger.info(s"系统管理员创建成功: adminId=$adminId, username=${request.username}")
    } yield adminId
  }

  override def updateSystemAdmin(adminId: String, request: UpdateAdminRequest): IO[Unit] = {
    val updates = scala.collection.mutable.ListBuffer[String]()
    val params = scala.collection.mutable.ListBuffer[SqlParameter]()

    request.name.foreach { name =>
      updates += "name = ?"
      params += SqlParameter("String", name)
    }
    
    request.email.foreach { email =>
      updates += "email = ?"
      params += SqlParameter("String", email)
    }
    
    request.phone.foreach { phone =>
      updates += "phone = ?"
      params += SqlParameter("String", phone)
    }
    
    request.avatarUrl.foreach { avatarUrl =>
      updates += "avatar_url = ?"
      params += SqlParameter("String", avatarUrl)
    }
    
    request.status.foreach { status =>
      updates += "status = ?"
      params += SqlParameter("String", status)
    }

    if (updates.isEmpty) {
      IO.raiseError(new RuntimeException("没有需要更新的字段"))
    } else {
      updates += "updated_at = CURRENT_TIMESTAMP"
      params += SqlParameter("String", adminId)

      val sql = s"""
        UPDATE systemconfig.system_admins 
        SET ${updates.mkString(", ")}
        WHERE admin_id = ?
      """.stripMargin

      for {
        rowsAffected <- DatabaseManager.executeUpdate(sql, params.toList)
        _ <- if (rowsAffected == 0) {
          IO.raiseError(new RuntimeException(s"系统管理员不存在或更新失败: $adminId"))
        } else {
          IO.unit
        }
        _ = logger.info(s"系统管理员更新成功: adminId=$adminId")
      } yield ()
    }
  }

  override def deleteSystemAdmin(adminId: String): IO[Unit] = {
    val sql = s"""
      DELETE FROM systemconfig.system_admins 
      WHERE admin_id = ? AND role != 'super_admin'
    """.stripMargin

    val params = List(SqlParameter("String", adminId))

    for {
      rowsAffected <- DatabaseManager.executeUpdate(sql, params)
      _ <- if (rowsAffected == 0) {
        IO.raiseError(new RuntimeException(s"系统管理员不存在、删除失败或为超级管理员: $adminId"))
      } else {
        IO.unit
      }
      _ = logger.info(s"系统管理员删除成功: adminId=$adminId")
    } yield ()
  }

  override def changeAdminPassword(adminId: String, request: ChangeAdminPasswordRequest): IO[Unit] = {
    if (request.newPassword.length < Constants.MIN_PASSWORD_LENGTH) {
      IO.raiseError(new RuntimeException("新密码长度不能少于6位"))
    } else {
      for {
        // 验证当前密码
        currentAdmin <- DatabaseManager.executeQueryOptional(
          "SELECT password_hash FROM systemconfig.system_admins WHERE admin_id = ?",
          List(SqlParameter("String", adminId))
        )
        _ <- currentAdmin match {
          case Some(admin) =>
            val currentHash = DatabaseManager.decodeFieldUnsafe[String](admin, "password_hash")
            if (BCrypt.verifyer().verify(request.currentPassword.toCharArray, currentHash).verified) {
              // 当前密码正确，更新为新密码
              val newPasswordHash = BCrypt.withDefaults().hashToString(12, request.newPassword.toCharArray)
              val sql = s"""
                UPDATE systemconfig.system_admins 
                SET password_hash = ?, updated_at = CURRENT_TIMESTAMP
                WHERE admin_id = ?
              """.stripMargin
              
              val params = List(
                SqlParameter("String", newPasswordHash),
                SqlParameter("String", adminId)
              )
              
              for {
                rowsAffected <- DatabaseManager.executeUpdate(sql, params)
                _ <- if (rowsAffected == 0) {
                  IO.raiseError(new RuntimeException(s"管理员不存在或更新失败: $adminId"))
                } else {
                  IO.unit
                }
              } yield ()
            } else {
              IO.raiseError(new RuntimeException("当前密码不正确"))
            }
          case None =>
            IO.raiseError(new RuntimeException(s"管理员不存在: $adminId"))
        }
        _ = logger.info(s"管理员密码修改成功: adminId=$adminId")
      } yield ()
    }
  }

  override def resetAdminPassword(adminId: String, newPassword: String): IO[Unit] = {
    if (newPassword.length < Constants.MIN_PASSWORD_LENGTH) {
      IO.raiseError(new RuntimeException("新密码长度不能少于6位"))
    } else {
      // 直接重置密码，无需验证当前密码（管理员重置其他管理员密码）
      val newPasswordHash = BCrypt.withDefaults().hashToString(12, newPassword.toCharArray)
      val sql = s"""
        UPDATE systemconfig.system_admins 
        SET password_hash = ?, updated_at = CURRENT_TIMESTAMP
        WHERE admin_id = ?
      """.stripMargin
      
      val params = List(
        SqlParameter("String", newPasswordHash),
        SqlParameter("String", adminId)
      )
      
      for {
        rowsAffected <- DatabaseManager.executeUpdate(sql, params)
        _ <- if (rowsAffected == 0) {
          IO.raiseError(new RuntimeException(s"管理员不存在或更新失败: $adminId"))
        } else {
          IO.unit
        }
        _ = logger.info(s"管理员密码重置成功: adminId=$adminId")
      } yield ()
    }
  }

  // 辅助方法：检查用户名是否已存在
  private def checkUsernameExists(username: String): IO[Boolean] = {
    val sql = s"""
      SELECT COUNT(*) as count
      FROM systemconfig.system_admins
      WHERE username = ?
    """.stripMargin

    val params = List(SqlParameter("String", username))

    DatabaseManager.executeQuery(sql, params).map { results =>
      results.headOption.map(DatabaseManager.decodeFieldUnsafe[Int](_, "count")).getOrElse(0) > 0
    }
  }

  // 辅助方法：转换为SystemAdmin
  private def convertToSystemAdmin(json: io.circe.Json): SystemAdmin = {
    SystemAdmin(
      adminId = DatabaseManager.decodeFieldUnsafe[String](json, "admin_id"),
      username = DatabaseManager.decodeFieldUnsafe[String](json, "username"),
      role = DatabaseManager.decodeFieldUnsafe[String](json, "role"),
      status = DatabaseManager.decodeFieldUnsafe[String](json, "status"),
      name = DatabaseManager.decodeFieldOptional[String](json, "name"),
      email = DatabaseManager.decodeFieldOptional[String](json, "email"),
      phone = DatabaseManager.decodeFieldOptional[String](json, "phone"),
      avatarUrl = DatabaseManager.decodeFieldOptional[String](json, "avatar_url"),
      createdAt = DatabaseManager.decodeFieldOptional[LocalDateTime](json, "created_at"),
      updatedAt = DatabaseManager.decodeFieldOptional[LocalDateTime](json, "updated_at"),
      lastLoginAt = DatabaseManager.decodeFieldOptional[LocalDateTime](json, "last_login_at")
    )
  }


}

// 系统设置服务实现
class SystemSettingsServiceImpl extends SystemSettingsService {
  private val logger = LoggerFactory.getLogger("SystemSettingsService")
  private val schemaName = "systemconfig"

  override def getSystemSettings(): IO[SystemSettings] = {
    val sql = s"""
      SELECT setting_key, setting_value, setting_type
      FROM systemconfig.system_settings
      ORDER BY setting_key
    """.stripMargin

    for {
      settingsResult <- DatabaseManager.executeQuery(sql, List.empty)
      settings = convertToSystemSettings(settingsResult)
    } yield settings
  }

  override def updateSystemSettings(request: UpdateSystemSettingsRequest): IO[Unit] = {
    logger.info(s"收到系统设置更新请求: $request")
    
    val updates = List(
      // 只保留与前端接口匹配的字段
      request.systemName.map(v => (Constants.SETTING_KEY_SYSTEM_TITLE, v)),
      request.version.map(v => (Constants.SETTING_KEY_SYSTEM_VERSION, v)),
      request.buildTime.map(v => (Constants.SETTING_KEY_BUILD_TIME, v)),
      request.announcementEnabled.map(v => (Constants.SETTING_KEY_ANNOUNCEMENT_ENABLED, v.toString))
    ).flatten

    logger.info(s"需要更新的设置: $updates")

    if (updates.isEmpty) {
      IO.raiseError(new RuntimeException("没有需要更新的设置"))
    } else {
      val updateTasks = updates.map { case (key, value) =>
        val sql = s"""
          UPDATE systemconfig.system_settings 
          SET setting_value = ?, updated_at = CURRENT_TIMESTAMP
          WHERE setting_key = ?
        """.stripMargin
        
        val params = List(
          SqlParameter("String", value),
          SqlParameter("String", key)
        )
        
        DatabaseManager.executeUpdate(sql, params)
      }
      
      for {
        _ <- updateTasks.sequence
        _ = logger.info("系统设置更新成功")
      } yield ()
    }
  }

  override def getPublicSettings(): IO[SystemSettings] = {
    val sql = s"""
      SELECT setting_key, setting_value, setting_type
      FROM systemconfig.system_settings
      WHERE is_public = true
      ORDER BY setting_key
    """.stripMargin

    for {
      settingsResult <- DatabaseManager.executeQuery(sql, List.empty)
      settings = convertToSystemSettings(settingsResult)
    } yield settings
  }

  // 辅助方法：转换为SystemSettings
  private def convertToSystemSettings(settingsJson: List[io.circe.Json]): SystemSettings = {
    val settingsMap = settingsJson.map { json =>
      val key = DatabaseManager.decodeFieldUnsafe[String](json, "setting_key")
      val value = DatabaseManager.decodeFieldUnsafe[String](json, "setting_value")
      key -> value
    }.toMap

    val announcementEnabled = settingsMap.get(Constants.SETTING_KEY_ANNOUNCEMENT_ENABLED).forall(_.toBoolean)

    SystemSettings(
      announcementEnabled = announcementEnabled,
      systemName = settingsMap.getOrElse(Constants.SETTING_KEY_SYSTEM_TITLE, "GalPHOS"),
      version = settingsMap.getOrElse(Constants.SETTING_KEY_SYSTEM_VERSION, "1.3.0"),
      buildTime = settingsMap.getOrElse(Constants.SETTING_KEY_BUILD_TIME, java.time.LocalDateTime.now().toString)
    )
  }
}



// 系统配置综合服务实现（组合所有服务）
class SystemConfigServiceImpl extends SystemConfigService {
  private val adminService = new SystemAdminServiceImpl()
  private val settingsService = new SystemSettingsServiceImpl()

  // 系统管理员相关方法 - 委托给adminService
  override def getSystemAdmins(params: QueryParams): IO[PaginatedResponse[SystemAdmin]] = 
    adminService.getSystemAdmins(params)
  
  override def getSystemAdminById(adminId: String): IO[Option[SystemAdmin]] = 
    adminService.getSystemAdminById(adminId)
  
  override def createSystemAdmin(request: CreateAdminRequest): IO[String] = 
    adminService.createSystemAdmin(request)
  
  override def updateSystemAdmin(adminId: String, request: UpdateAdminRequest): IO[Unit] = 
    adminService.updateSystemAdmin(adminId, request)
  
  override def deleteSystemAdmin(adminId: String): IO[Unit] = 
    adminService.deleteSystemAdmin(adminId)
  
  override def changeAdminPassword(adminId: String, request: ChangeAdminPasswordRequest): IO[Unit] = 
    adminService.changeAdminPassword(adminId, request)

  override def resetAdminPassword(adminId: String, newPassword: String): IO[Unit] = 
    adminService.resetAdminPassword(adminId, newPassword)

  // 系统设置相关方法 - 委托给settingsService
  override def getSystemSettings(): IO[SystemSettings] = 
    settingsService.getSystemSettings()
  
  override def updateSystemSettings(request: UpdateSystemSettingsRequest): IO[Unit] = 
    settingsService.updateSystemSettings(request)
  
  override def getPublicSettings(): IO[SystemSettings] = 
    settingsService.getPublicSettings()
}
