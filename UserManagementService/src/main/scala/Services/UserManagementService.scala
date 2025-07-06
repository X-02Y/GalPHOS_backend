package Services

import Models.*
import Database.{DatabaseManager, SqlParameter}
import cats.effect.IO
import cats.implicits.*
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID
import Config.Constants

trait UserManagementService {
  def getPendingUsers(): IO[List[PendingUser]]
  def getApprovedUsers(params: QueryParams): IO[PaginatedResponse[ApprovedUser]]
  def approveUser(request: UserApprovalRequest): IO[Unit]
  def updateUserStatus(request: UserStatusUpdateRequest): IO[Unit]
  def deleteUser(userId: String): IO[Unit]
  def getUserById(userId: String): IO[Option[ApprovedUser]]
  def getUserByUsername(username: String): IO[Option[User]]
  def updateUserById(userId: String, request: UpdateUserRequest): IO[Unit]
  def getUsersByRole(role: UserRole, status: Option[UserStatus] = None): IO[List[ApprovedUser]]
  // 新增：获取学生注册申请（只包含有教练关联的）
  def getStudentRegistrationRequests(): IO[List[StudentRegistrationRequest]]
  def reviewStudentRegistration(requestId: String, request: ReviewRegistrationRequest): IO[Unit]
  def approveStudentRegistration(request: StudentRegistrationApprovalRequest): IO[Unit]
  
  // 个人资料管理相关方法
  def getUserProfile(username: String): IO[Option[UserProfile]]
  def updateUserProfile(username: String, request: UpdateProfileRequest): IO[Unit]
  def getAdminProfile(username: String): IO[Option[AdminProfile]]
  def updateAdminProfile(username: String, request: UpdateAdminProfileRequest): IO[Unit]
  def changeUserPassword(username: String, request: ChangePasswordRequest): IO[Unit]
  
  // 系统管理员管理相关方法
  def getSystemAdmins(): IO[List[AdminProfile]]
  def getSystemAdminById(adminId: String): IO[Option[AdminProfile]]
  def createSystemAdmin(request: CreateSystemAdminRequest): IO[String]
  def updateSystemAdmin(adminId: String, request: UpdateSystemAdminRequest): IO[Unit]
  def deleteSystemAdmin(adminId: String): IO[Unit]
  def resetSystemAdminPassword(adminId: String, newPassword: String): IO[Unit]
  
  // 区域变更相关方法
  def createRegionChangeRequest(username: String, request: RegionChangeRequest): IO[String]
  def getUserRegionChangeRequests(username: String): IO[List[RegionChangeRequestRecord]]
  
  // 阅卷员密码修改方法（使用不同的参数结构）
  def changeGraderPassword(username: String, request: ChangeGraderPasswordRequest): IO[Unit]
  
  // 管理员密码修改方法
  def changeAdminPassword(username: String, currentPassword: String, newPassword: String): IO[Unit]
  
  // 文件上传相关方法
  def uploadAvatar(username: String, userType: String, fileName: String, fileData: Array[Byte], mimeType: String): IO[FileOperationResponse]
  def uploadAnswerImage(username: String, fileName: String, fileData: Array[Byte], mimeType: String, examId: Option[String] = None, questionNumber: Option[Int] = None): IO[FileOperationResponse]
  def uploadDocument(username: String, userType: String, fileName: String, fileData: Array[Byte], mimeType: String, description: Option[String] = None): IO[FileOperationResponse]
  
  // 获取用户头像（支持返回base64或URL）
  def getUserAvatar(username: String, format: String = "url"): IO[Option[String]]
}

class UserManagementServiceImpl() extends UserManagementService {
  private val logger = LoggerFactory.getLogger("UserManagementService")
  private val schemaName = "authservice"  // 使用固定的schema名称
  private val config = Config.ConfigLoader.loadConfig() // 添加config变量
  private val regionClient = RegionServiceClient(config.regionServiceUrl.getOrElse("http://localhost:3007"))

  override def getPendingUsers(): IO[List[PendingUser]] = {
    val testSql = s"""
      SELECT u.username, u.status, COUNT(*) as count
      FROM authservice.user_table u
      GROUP BY u.username, u.status
      ORDER BY u.username
    """.stripMargin

    val sql = s"""
      SELECT 
        u.user_id as id,
        u.username,
        u.phone,
        u.role,
        u.province_id,
        u.school_id,
        COALESCE(u.created_at, CURRENT_TIMESTAMP) as appliedAt,
        u.status
      FROM authservice.user_table u
      WHERE u.status = ?
      ORDER BY u.created_at DESC
    """.stripMargin

    val params = List(SqlParameter("String", "PENDING"))

    for {
      _ <- IO(logger.info(s"查询待审核用户，状态参数: PENDING"))
      // 先执行测试查询
      testResults <- DatabaseManager.executeQuery(testSql, List.empty)
      _ <- IO(logger.info(s"数据库中所有用户状态统计: ${testResults.length} 条记录"))
      _ <- IO(testResults.foreach(json => {
        val username = DatabaseManager.decodeFieldOptional[String](json, "username").getOrElse("unknown")
        val status = DatabaseManager.decodeFieldOptional[String](json, "status").getOrElse("unknown")
        val count = DatabaseManager.decodeFieldOptional[Int](json, "count").getOrElse(0)
        logger.info(s"用户: $username, 状态: '$status', 数量: $count")
      }))
      
      // 然后执行原查询
      results <- DatabaseManager.executeQuery(sql, params)
      _ <- IO(logger.info(s"查询到 ${results.length} 条待审核用户记录"))
      // 添加原始数据日志
      _ <- IO(results.headOption.foreach(json => logger.info(s"第一条记录原始数据: $json")))
      
      // 转换用户数据，使用内部API获取省份学校名称
      usersIO <- results.traverse(json => convertToPendingUserWithRegionAsync(json))
      _ <- IO(logger.info(s"转换后的用户数量: ${usersIO.length}"))
    } yield usersIO
  }

  override def getApprovedUsers(params: QueryParams): IO[PaginatedResponse[ApprovedUser]] = {
    val page = params.page.getOrElse(1)
    val limit = math.min(params.limit.getOrElse(Constants.DEFAULT_PAGE_SIZE), Constants.MAX_PAGE_SIZE)
    val offset = (page - 1) * limit

    // 构建WHERE条件
    val whereConditions = scala.collection.mutable.ListBuffer[String]()
    val sqlParams = scala.collection.mutable.ListBuffer[SqlParameter]()
    
    whereConditions += "u.status IN (?, ?)"
    sqlParams += SqlParameter("String", Constants.USER_STATUS_ACTIVE)
    sqlParams += SqlParameter("String", Constants.USER_STATUS_DISABLED)

    params.role.foreach { role =>
      whereConditions += "u.role = ?"
      sqlParams += SqlParameter("String", role)
    }

    params.status.foreach { status =>
      whereConditions += "u.status = ?"
      sqlParams += SqlParameter("String", status.toUpperCase)
    }

    params.search.foreach { search =>
      whereConditions += "u.username ILIKE ?"
      sqlParams += SqlParameter("String", s"%$search%")
    }

    val whereClause = if (whereConditions.nonEmpty) 
      s"WHERE ${whereConditions.mkString(" AND ")}" 
    else ""

    // 查询总数
    val countSql = s"""
      SELECT COUNT(*) as total
      FROM authservice.user_table u
      $whereClause
    """.stripMargin

    // 查询数据
    val dataSql = s"""
      SELECT 
        u.user_id as id,
        u.username,
        u.phone,
        u.role,
        u.province_id,
        u.school_id,
        u.status,
        u.approved_at as approved_at,
        u.updated_at as lastLoginAt,
        u.avatar_url as avatarUrl
      FROM authservice.user_table u
      $whereClause
      ORDER BY u.created_at DESC
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
      
      // 使用内部API转换用户数据
      usersIO <- dataResults.traverse(json => convertToApprovedUserWithRegionAsync(json))
    } yield PaginatedResponse(usersIO, total, page, limit)
  }

  override def approveUser(request: UserApprovalRequest): IO[Unit] = {
    val newStatus = request.action match {
      case Constants.APPROVAL_ACTION_APPROVE => Constants.USER_STATUS_ACTIVE
      case Constants.APPROVAL_ACTION_REJECT => Constants.USER_STATUS_DISABLED
      case _ => throw new IllegalArgumentException(s"无效的审核操作: ${request.action}")
    }

    val sql = s"""
      UPDATE authservice.user_table 
      SET status = ?, approved_at = NOW(), updated_at = NOW()
      WHERE user_id = ?
    """.stripMargin

    val params = List(
      SqlParameter("String", newStatus),
      SqlParameter("String", request.userId)
    )

    for {
      rowsAffected <- DatabaseManager.executeUpdate(sql, params)
      _ <- if (rowsAffected == 0) {
        IO.raiseError(new RuntimeException(s"用户不存在或更新失败: ${request.userId}"))
      } else {
        IO.unit
      }
      _ = logger.info(s"用户审核完成: userId=${request.userId}, action=${request.action}, newStatus=$newStatus")
    } yield ()
  }

  override def updateUserStatus(request: UserStatusUpdateRequest): IO[Unit] = {
    val sql = s"""
      UPDATE authservice.user_table 
      SET status = ?, updated_at = NOW()
      WHERE user_id = ?
    """.stripMargin

    val params = List(
      SqlParameter("String", request.status.toUpperCase),
      SqlParameter("String", request.userId)
    )

    for {
      rowsAffected <- DatabaseManager.executeUpdate(sql, params)
      _ <- if (rowsAffected == 0) {
        IO.raiseError(new RuntimeException(s"用户不存在或更新失败: ${request.userId}"))
      } else {
        IO.unit
      }
      _ = logger.info(s"用户状态更新完成: userId=${request.userId}, status=${request.status}")
    } yield ()
  }

  override def deleteUser(userId: String): IO[Unit] = {
    val sql = s"""
      DELETE FROM authservice.user_table 
      WHERE user_id = ?
    """.stripMargin

    val params = List(SqlParameter("String", userId))

    for {
      rowsAffected <- DatabaseManager.executeUpdate(sql, params)
      _ <- if (rowsAffected == 0) {
        IO.raiseError(new RuntimeException(s"用户不存在或删除失败: $userId"))
      } else {
        IO.unit
      }
      _ = logger.info(s"用户删除完成: userId=$userId")
    } yield ()
  }

  override def getUserById(userId: String): IO[Option[ApprovedUser]] = {
    val sql = s"""
      SELECT 
        u.user_id as id,
        u.username,
        u.phone,
        u.role,
        u.province_id,
        u.school_id,
        u.status,
        u.approved_at as approved_at,
        u.updated_at as lastLoginAt,
        u.avatar_url as avatarUrl
      FROM authservice.user_table u
      WHERE u.user_id = ?
    """.stripMargin

    val params = List(SqlParameter("String", userId))

    for {
      result <- DatabaseManager.executeQueryOptional(sql, params)
      userOpt <- result match {
        case Some(json) => convertToApprovedUserWithRegionAsync(json).map(Some(_))
        case None => IO.pure(None)
      }
    } yield userOpt
  }

  override def getUserByUsername(username: String): IO[Option[User]] = {
    val sql = s"""
      SELECT 
        u.user_id,
        u.username,
        u.password_hash,
        u.salt,
        u.role,
        u.status,
        u.phone,
        u.province_id,
        u.school_id,
        u.avatar_url,
        u.created_at,
        u.updated_at
      FROM authservice.user_table u
      WHERE u.username = ?
    """.stripMargin

    val params = List(SqlParameter("String", username))

    for {
      result <- DatabaseManager.executeQueryOptional(sql, params)
      userOpt = result.map(convertToUser)
    } yield userOpt
  }

  private def convertToUser(json: io.circe.Json): User = {
    User(
      userID = DatabaseManager.decodeFieldUnsafe[String](json, "user_id"),
      username = DatabaseManager.decodeFieldUnsafe[String](json, "username"),
      passwordHash = DatabaseManager.decodeFieldUnsafe[String](json, "password_hash"),
      salt = DatabaseManager.decodeFieldUnsafe[String](json, "salt"),
      role = UserRole.fromString(DatabaseManager.decodeFieldUnsafe[String](json, "role")),
      status = UserStatus.fromString(DatabaseManager.decodeFieldUnsafe[String](json, "status")),
      phone = DatabaseManager.decodeFieldOptional[String](json, "phone"),
      provinceId = DatabaseManager.decodeFieldOptional[String](json, "province_id"),
      schoolId = DatabaseManager.decodeFieldOptional[String](json, "school_id"),
      avatarUrl = DatabaseManager.decodeFieldOptional[String](json, "avatar_url"),
      createdAt = DatabaseManager.decodeFieldOptional[LocalDateTime](json, "created_at"),
      updatedAt = DatabaseManager.decodeFieldOptional[LocalDateTime](json, "updated_at")
    )
  }

  override def getUsersByRole(role: UserRole, status: Option[UserStatus] = None): IO[List[ApprovedUser]] = {
    val baseConditions = List("u.role = ?")
    val baseParams = List(SqlParameter("String", role.value))

    val (conditions, params) = status match {
      case Some(s) => 
        (baseConditions :+ "u.status = ?", baseParams :+ SqlParameter("String", s.value))
      case None => 
        (baseConditions, baseParams)
    }

    val whereClause = conditions.mkString(" AND ")

    val sql = s"""
      SELECT 
        u.user_id as id,
        u.username,
        u.phone,
        u.role,
        u.province_id,
        u.school_id,
        u.status,
        u.approved_at as approved_at,
        u.updated_at as lastLoginAt,
        u.avatar_url as avatarUrl
      FROM authservice.user_table u
      WHERE $whereClause
      ORDER BY u.username
    """.stripMargin

    for {
      results <- DatabaseManager.executeQuery(sql, params)
      users <- results.traverse(json => convertToApprovedUserWithRegionAsync(json))
    } yield users
  }

  // 新增：获取学生注册申请（只包含有教练关联的）
  override def getStudentRegistrationRequests(): IO[List[StudentRegistrationRequest]] = {
    val sql = s"""
      SELECT 
        id,
        username,
        province,
        school,
        coach_username,
        reason,
        status,
        created_at,
        reviewed_by,
        reviewed_at,
        review_note
      FROM authservice.user_registration_requests
      WHERE coach_username IS NOT NULL AND coach_username != ''
      ORDER BY created_at DESC
    """.stripMargin

    for {
      results <- DatabaseManager.executeQuery(sql, List.empty)
      requests = results.map(convertToStudentRegistrationRequest)
      _ = logger.info(s"查询到 ${requests.length} 条有教练关联的学生注册申请")
    } yield requests
  }.handleErrorWith { error =>
    logger.error(s"获取学生注册申请失败: ${error.getMessage}")
    IO.pure(List.empty)
  }

  // 个人资料管理相关方法实现
  override def getUserProfile(username: String): IO[Option[UserProfile]] = {
    val sql = s"""
      SELECT 
        u.user_id as id,
        u.username,
        u.phone,
        u.role,
        u.province_id,
        u.school_id,
        u.avatar_url as avatarUrl,
        u.created_at as createdAt,
        u.updated_at as lastLoginAt
      FROM authservice.user_table u
      WHERE u.username = ?
    """.stripMargin

    val params = List(SqlParameter("String", username))

    for {
      result <- DatabaseManager.executeQueryOptional(sql, params)
      userProfileOpt <- result match {
        case Some(json) => convertToUserProfileWithRegionAsync(json).map(Some(_))
        case None => IO.pure(None)
      }
    } yield userProfileOpt
  }

  override def updateUserProfile(username: String, request: UpdateProfileRequest): IO[Unit] = {
    for {
      // 如果要更新用户名，先检查新用户名的唯一性
      _ <- request.username match {
        case Some(newUsername) if newUsername != username =>
          checkUsernameExists(newUsername).flatMap {
            case true => IO.raiseError(new RuntimeException(s"用户名已存在: $newUsername"))
            case false => IO.unit
          }
        case _ => IO.unit
      }
      
      // 构建动态更新SQL
      updateFields = scala.collection.mutable.ListBuffer[String]()
      sqlParams = scala.collection.mutable.ListBuffer[SqlParameter]()
      
      // 用户名更新
      _ = request.username.foreach { newUsername =>
        updateFields += "username = ?"
        sqlParams += SqlParameter("String", newUsername)
      }
      
      // 手机号更新
      _ = request.phone.foreach { phone =>
        updateFields += "phone = ?"
        sqlParams += SqlParameter("String", phone)
      }

      // 注意：省份和学校的更新需要通过RegionMS API获取对应的ID
      // 暂时移除直接的省份学校更新功能，需要前端传递ID而非名称
      _ = request.province.foreach { _ =>
        logger.warn("省份更新功能暂时不可用，需要通过RegionMS API获取对应的ID")
      }

      _ = request.school.foreach { _ =>
        logger.warn("学校更新功能暂时不可用，需要通过RegionMS API获取对应的ID")
      }

      // 头像更新（支持前端的avatar字段和后端的avatarUrl字段）
      avatarToUpdate = request.avatar.orElse(request.avatarUrl)
      _ = avatarToUpdate.foreach { avatarUrl =>
        updateFields += "avatar_url = ?"
        sqlParams += SqlParameter("String", avatarUrl)
      }

      // 执行更新
      _ <- if (updateFields.isEmpty) {
        IO.unit // 没有需要更新的字段
      } else {
        updateFields += "updated_at = NOW()"
        sqlParams += SqlParameter("String", username)

        val sql = s"""
          UPDATE authservice.user_table 
          SET ${updateFields.mkString(", ")}
          WHERE username = ?
        """.stripMargin

        for {
          rowsAffected <- DatabaseManager.executeUpdate(sql, sqlParams.toList)
          _ <- if (rowsAffected == 0) {
            IO.raiseError(new RuntimeException(s"用户不存在或更新失败: $username"))
          } else {
            IO.unit
          }
          _ = logger.info(s"用户资料更新完成: username=$username")
        } yield ()
      }
    } yield ()
  }

  // 辅助方法：检查用户名是否已存在
  private def checkUsernameExists(username: String): IO[Boolean] = {
    val sql = s"""
      SELECT COUNT(*) as count
      FROM authservice.user_table
      WHERE username = ?
    """.stripMargin

    val params = List(SqlParameter("String", username))

    for {
      results <- DatabaseManager.executeQuery(sql, params)
      exists <- results.headOption match {
        case Some(json: io.circe.Json) =>
          val count = DatabaseManager.decodeFieldUnsafe[Int](json, "count")
          IO.pure(count > 0)
        case None =>
          IO.pure(false)
      }
    } yield exists
  }

  // 辅助方法：检查管理员用户名是否已存在
  private def checkAdminUsernameExists(username: String): IO[Boolean] = {
    val sql = s"""
      SELECT COUNT(*) as count
      FROM authservice.admin_table
      WHERE username = ?
    """.stripMargin

    val params = List(SqlParameter("String", username))
    
    logger.info(s"检查管理员用户名是否存在: $username")

    for {
      results <- DatabaseManager.executeQuery(sql, params).handleErrorWith { error =>
        logger.error(s"检查用户名查询失败: ${error.getMessage}", error)
        if (error.getMessage.contains("relation") && error.getMessage.contains("does not exist")) {
          IO.raiseError(new RuntimeException("数据库表 authservice.admin_table 不存在"))
        } else {
          IO.raiseError(new RuntimeException(s"数据库查询失败: ${error.getMessage}"))
        }
      }
      
      exists <- results.headOption match {
        case Some(json: io.circe.Json) =>
          val count = DatabaseManager.decodeFieldUnsafe[Int](json, "count")
          logger.info(s"用户名 $username 的查询结果: count=$count")
          IO.pure(count > 0)
        case None =>
          logger.warn(s"用户名检查查询无结果: $username")
          IO.pure(false)
      }
    } yield exists
  }.handleErrorWith { error =>
    logger.error(s"检查用户名存在性失败: ${error.getMessage}", error)
    IO.raiseError(error)
  }

  override def getAdminProfile(username: String): IO[Option[AdminProfile]] = {
    val sql = s"""
      SELECT 
        admin_id as id,
        username,
        status,
        role,
        avatar_url,
        created_at as createdAt,
        last_login_at as lastLoginAt
      FROM authservice.admin_table
      WHERE username = ?
    """.stripMargin

    val params = List(SqlParameter("String", username))

    for {
      result <- DatabaseManager.executeQueryOptional(sql, params)
    } yield result.map(convertToAdminProfile)
  }

  override def updateAdminProfile(username: String, request: UpdateAdminProfileRequest): IO[Unit] = {
    // 管理员资料更新，支持用户名和头像更新
    val updateFields = scala.collection.mutable.ListBuffer[String]()
    val sqlParams = scala.collection.mutable.ListBuffer[SqlParameter]()

    // 如果要更新用户名，先检查新用户名的唯一性
    val usernameValidation = request.username match {
      case Some(newUsername) if newUsername != username =>
        checkAdminUsernameExists(newUsername).flatMap {
          case true => IO.raiseError(new RuntimeException(s"用户名已存在: $newUsername"))
          case false => 
            updateFields += "username = ?"
            sqlParams += SqlParameter("String", newUsername)
            IO.unit
        }
      case Some(_) => IO.unit // 用户名未变更
      case None => IO.unit // 不更新用户名
    }

    // 处理头像更新 - 支持base64和URL两种格式
    // 优先使用 avatar 字段（base64），然后是 avatarUrl 字段
    val avatarToUpdate = request.avatar.orElse(request.avatarUrl)
    
    avatarToUpdate.foreach { avatarUrl =>
      if (avatarUrl.startsWith("data:image/")) {
        // 如果是base64格式，直接存储
        updateFields += "avatar_url = ?"
        sqlParams += SqlParameter("String", avatarUrl)
        logger.info(s"管理员 $username 头像更新为base64格式")
      } else if (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://")) {
        // 如果是完整URL，直接存储
        updateFields += "avatar_url = ?"
        sqlParams += SqlParameter("String", avatarUrl)
        logger.info(s"管理员 $username 头像更新为URL格式: $avatarUrl")
      } else {
        // 如果是不完整的URL，添加协议
        val completeUrl = s"http://$avatarUrl"
        updateFields += "avatar_url = ?"
        sqlParams += SqlParameter("String", completeUrl)
        logger.info(s"管理员 $username 头像URL已补全协议: $completeUrl")
      }
    }

    for {
      _ <- usernameValidation
      _ <- if (updateFields.isEmpty) {
        IO.unit // 没有需要更新的字段
      } else {
        sqlParams += SqlParameter("String", username)

        val sql = s"""
          UPDATE authservice.admin_table 
          SET ${updateFields.mkString(", ")}
          WHERE username = ?
        """.stripMargin

        for {
          rowsAffected <- DatabaseManager.executeUpdate(sql, sqlParams.toList)
          _ <- if (rowsAffected == 0) {
            IO.raiseError(new RuntimeException(s"管理员不存在或更新失败: $username"))
          } else {
            IO.unit
          }
          _ = logger.info(s"管理员资料更新完成: username=$username")
        } yield ()
      }
    } yield ()
  }

  override def changeUserPassword(username: String, request: ChangePasswordRequest): IO[Unit] = {
    // 验证密码长度
    if (request.newPassword.length < 6) {
      IO.raiseError(new RuntimeException("新密码长度不能少于6位"))
    } else {
      // 这里直接更新数据库中的密码
      // 注意：前端已经对密码进行了哈希处理，所以这里直接存储
      val sql = s"""
        UPDATE authservice.user_table 
        SET password_hash = ?, updated_at = NOW()
        WHERE username = ?
      """.stripMargin

      val params = List(
        SqlParameter("String", request.newPassword), // 前端传来的已经是哈希后的密码
        SqlParameter("String", username)
      )

      for {
        // 首先验证当前密码
        currentUser <- DatabaseManager.executeQueryOptional(
          "SELECT password_hash FROM authservice.user_table WHERE username = ?",
          List(SqlParameter("String", username))
        )
        _ <- currentUser match {
          case Some(user) =>
            val currentHash = DatabaseManager.decodeFieldUnsafe[String](user, "password_hash")
            if (currentHash == request.oldPassword) {
              // 当前密码正确，更新为新密码
              for {
                rowsAffected <- DatabaseManager.executeUpdate(sql, params)
                _ <- if (rowsAffected == 0) {
                  IO.raiseError(new RuntimeException(s"用户不存在或更新失败: $username"))
                } else {
                  IO.unit
                }
              } yield ()
            } else {
              IO.raiseError(new RuntimeException("当前密码不正确"))
            }
          case None =>
            IO.raiseError(new RuntimeException(s"用户不存在: $username"))
        }
        _ = logger.info(s"用户密码修改成功: username=$username")
      } yield ()
    }
  }

  override def changeGraderPassword(username: String, request: ChangeGraderPasswordRequest): IO[Unit] = {
    // 验证密码长度
    if (request.newPassword.length < 6) {
      IO.raiseError(new RuntimeException("新密码长度不能少于6位"))
    } else {
      // 这里直接更新数据库中的密码
      // 注意：前端已经对密码进行了哈希处理，所以这里直接存储
      val sql = s"""
        UPDATE authservice.user_table 
        SET password_hash = ?, updated_at = NOW()
        WHERE username = ?
      """.stripMargin

      val params = List(
        SqlParameter("String", request.newPassword), // 前端传来的已经是哈希后的密码
        SqlParameter("String", username)
      )

      for {
        // 首先验证当前密码
        currentUser <- DatabaseManager.executeQueryOptional(
          "SELECT password_hash FROM authservice.user_table WHERE username = ?",
          List(SqlParameter("String", username))
        )
        _ <- currentUser match {
          case Some(user) =>
            val currentHash = DatabaseManager.decodeFieldUnsafe[String](user, "password_hash")
            if (currentHash == request.currentPassword) {
              // 当前密码正确，更新为新密码
              for {
                rowsAffected <- DatabaseManager.executeUpdate(sql, params)
                _ <- if (rowsAffected == 0) {
                  IO.raiseError(new RuntimeException(s"用户不存在或更新失败: $username"))
                } else {
                  IO.unit
                }
              } yield ()
            } else {
              IO.raiseError(new RuntimeException("当前密码不正确"))
            }
          case None =>
            IO.raiseError(new RuntimeException(s"用户不存在: $username"))
        }
        _ = logger.info(s"阅卷员密码修改成功: username=$username")
      } yield ()
    }
  }
  
  override def changeAdminPassword(username: String, currentPassword: String, newPassword: String): IO[Unit] = {
    // 验证密码长度
    if (newPassword.length < 6) {
      IO.raiseError(new RuntimeException("新密码长度不能少于6位"))
    } else {
      // 这里直接更新数据库中的密码
      // 注意：前端已经对密码进行了哈希处理，所以这里直接存储
      val sql = s"""
        UPDATE authservice.user_table 
        SET password_hash = ?, updated_at = NOW()
        WHERE username = ? AND role = 'admin'
      """.stripMargin

      val params = List(
        SqlParameter("String", newPassword), // 前端传来的已经是哈希后的密码
        SqlParameter("String", username)
      )

      for {
        // 首先验证当前密码
        currentUser <- DatabaseManager.executeQueryOptional(
          "SELECT password_hash FROM authservice.user_table WHERE username = ? AND role = 'admin'",
          List(SqlParameter("String", username))
        )
        _ <- currentUser match {
          case Some(user) =>
            val currentHash = DatabaseManager.decodeFieldUnsafe[String](user, "password_hash")
            if (currentHash == currentPassword) {
              // 当前密码正确，更新为新密码
              for {
                rowsAffected <- DatabaseManager.executeUpdate(sql, params)
                _ <- if (rowsAffected == 0) {
                  IO.raiseError(new RuntimeException(s"管理员不存在或更新失败: $username"))
                } else {
                  IO.unit
                }
              } yield ()
            } else {
              IO.raiseError(new RuntimeException("当前密码不正确"))
            }
          case None =>
            IO.raiseError(new RuntimeException(s"管理员不存在: $username"))
        }
        _ = logger.info(s"管理员密码修改成功: username=$username")
      } yield ()
    }
  }

  // ===================== 系统管理员管理方法 =====================

  override def getSystemAdmins(): IO[List[AdminProfile]] = {
    val sql = s"""
      SELECT 
        admin_id as id,
        username,
        status,
        role,
        name,
        avatar_url,
        created_at as createdAt,
        last_login_at as lastLoginAt
      FROM authservice.admin_table
      ORDER BY created_at DESC
    """.stripMargin

    for {
      _ <- IO(logger.info("开始查询管理员列表"))
      results <- DatabaseManager.executeQuery(sql, List.empty).handleErrorWith { error =>
        logger.error(s"查询管理员列表失败: ${error.getMessage}", error)
        IO.raiseError(error)
      }
      _ <- IO(logger.info(s"查询到 ${results.length} 条管理员记录"))
      _ <- IO(results.headOption.foreach(json => logger.info(s"第一条管理员记录: $json")))
      adminProfiles = results.map(convertToAdminProfile)
      _ <- IO(logger.info(s"转换后的管理员数量: ${adminProfiles.length}"))
      _ <- IO(adminProfiles.headOption.foreach(profile => logger.info(s"第一个管理员资料: $profile")))
    } yield adminProfiles
  }

  def getSystemAdminById(adminId: String): IO[Option[AdminProfile]] = {
    val sql = s"""
      SELECT 
        admin_id as id,
        username,
        status,
        role,
        name,
        avatar_url,
        created_at as createdAt,
        last_login_at as lastLoginAt
      FROM authservice.admin_table
      WHERE admin_id = ?
    """.stripMargin

    for {
      results <- DatabaseManager.executeQuery(sql, List(SqlParameter("String", adminId)))
    } yield results.headOption.map(convertToAdminProfile)
  }

  override def createSystemAdmin(request: CreateSystemAdminRequest): IO[String] = {
    val adminId = java.util.UUID.randomUUID().toString
    val salt = "GalPHOS_2025_SALT" // 使用统一的盐值
    
    logger.info(s"开始创建管理员: username=${request.username}, role=${request.role}")
    logger.info(s"请求详情: password长度=${request.password.length}, name=${request.name}, avatarUrl=${request.avatarUrl}")
    
    // 验证输入参数
    for {
      _ <- if (request.username.trim.isEmpty) {
        logger.error("用户名不能为空")
        IO.raiseError(new RuntimeException("用户名不能为空"))
      } else if (request.password.trim.isEmpty) {
        logger.error("密码不能为空")
        IO.raiseError(new RuntimeException("密码不能为空"))
      } else if (!Set("admin", "super_admin").contains(request.role)) {
        logger.error(s"无效的角色: ${request.role}")
        IO.raiseError(new RuntimeException(s"无效的角色: ${request.role}，只支持admin或super_admin"))
      } else {
        logger.info("输入参数验证通过")
        IO.unit
      }
      
      // 验证用户名唯一性
      exists <- checkAdminUsernameExists(request.username).handleErrorWith { error =>
        logger.error(s"检查用户名唯一性失败: ${error.getMessage}", error)
        IO.raiseError(new RuntimeException(s"检查用户名唯一性失败: ${error.getMessage}"))
      }
      
      _ <- if (exists) {
        logger.warn(s"管理员用户名已存在: ${request.username}")
        IO.raiseError(new RuntimeException(s"管理员用户名已存在: ${request.username}"))
      } else {
        logger.info(s"用户名检查通过: ${request.username}")
        IO.unit
      }
      
      // 插入新管理员
      sql = """
        INSERT INTO authservice.admin_table (
          admin_id, username, password_hash, salt, role, status, name, avatar_url, created_at
        ) VALUES (?, ?, ?, ?, ?, 'active', ?, ?, CURRENT_TIMESTAMP)
      """.stripMargin
      
      params = List(
        SqlParameter("String", adminId),
        SqlParameter("String", request.username),
        SqlParameter("String", request.password), // 前端已经哈希过的密码
        SqlParameter("String", salt),
        SqlParameter("String", request.role),
        SqlParameter("String", request.name.getOrElse("")),
        SqlParameter("String", request.avatarUrl.getOrElse(""))
      )
      
      _ = logger.info(s"执行插入SQL: $sql")
      _ = logger.info(s"参数: adminId=$adminId, username=${request.username}, role=${request.role}, name=${request.name}")
      
      // 先测试数据库连接
      _ <- DatabaseManager.executeQuery("SELECT 1 as test", List.empty).handleErrorWith { error =>
        logger.error(s"数据库连接测试失败: ${error.getMessage}", error)
        IO.raiseError(new RuntimeException(s"数据库连接失败: ${error.getMessage}"))
      }
      
      _ = logger.info("数据库连接测试成功")
      
      rowsAffected <- DatabaseManager.executeUpdate(sql, params).handleErrorWith { error =>
        logger.error(s"数据库插入失败: ${error.getMessage}", error)
        // 检查是否是表不存在的错误
        if (error.getMessage.contains("relation") && error.getMessage.contains("does not exist")) {
          IO.raiseError(new RuntimeException(s"数据库表不存在: authservice.admin_table"))
        } else if (error.getMessage.contains("duplicate key")) {
          IO.raiseError(new RuntimeException(s"用户名已存在: ${request.username}"))
        } else {
          IO.raiseError(new RuntimeException(s"数据库插入失败: ${error.getMessage}"))
        }
      }
      
      _ <- if (rowsAffected == 0) {
        logger.error("插入管理员失败: 影响行数为0")
        IO.raiseError(new RuntimeException("创建管理员失败: 数据库插入影响行数为0"))
      } else {
        logger.info(s"管理员插入成功: 影响行数=$rowsAffected")
        IO.unit
      }
      _ = logger.info(s"创建管理员成功: adminId=$adminId, username=${request.username}")
    } yield adminId
  }.handleErrorWith { error =>
    logger.error(s"创建管理员失败: ${error.getMessage}", error)
    IO.raiseError(error)
  }

  override def updateSystemAdmin(adminId: String, request: UpdateSystemAdminRequest): IO[Unit] = {
    val updateFields = scala.collection.mutable.ListBuffer[String]()
    val sqlParams = scala.collection.mutable.ListBuffer[SqlParameter]()

    // 检查用户名唯一性
    val usernameValidation = request.username match {
      case Some(newUsername) =>
        checkAdminUsernameExists(newUsername).flatMap {
          case true => 
            // 检查是否是当前用户自己的用户名
            DatabaseManager.executeQueryOptional(
              "SELECT username FROM authservice.admin_table WHERE admin_id = ?",
              List(SqlParameter("String", adminId))
            ).flatMap {
              case Some(currentRecord) =>
                val currentUsername = DatabaseManager.decodeFieldUnsafe[String](currentRecord, "username")
                if (currentUsername == newUsername) {
                  IO.unit // 是自己的用户名，允许
                } else {
                  IO.raiseError(new RuntimeException(s"用户名已存在: $newUsername"))
                }
              case None =>
                IO.raiseError(new RuntimeException("管理员不存在"))
            }
          case false =>
            updateFields += "username = ?"
            sqlParams += SqlParameter("String", newUsername)
            IO.unit
        }
      case None => IO.unit
    }

    // 构建更新字段
    request.role.foreach { role =>
      updateFields += "role = ?"
      sqlParams += SqlParameter("String", role)
    }
    
    request.status.foreach { status =>
      updateFields += "status = ?"
      sqlParams += SqlParameter("String", status)
    }
    
    request.name.foreach { name =>
      updateFields += "name = ?"
      sqlParams += SqlParameter("String", name)
    }
    
    request.avatarUrl.foreach { avatarUrl =>
      updateFields += "avatar_url = ?"
      sqlParams += SqlParameter("String", avatarUrl)
    }

    for {
      _ <- usernameValidation
      _ <- if (updateFields.isEmpty) {
        IO.unit
      } else {
        sqlParams += SqlParameter("String", adminId)
        
        val sql = s"""
          UPDATE authservice.admin_table 
          SET ${updateFields.mkString(", ")}
          WHERE admin_id = ?
        """.stripMargin

        for {
          rowsAffected <- DatabaseManager.executeUpdate(sql, sqlParams.toList)
          _ <- if (rowsAffected == 0) {
            IO.raiseError(new RuntimeException("管理员不存在或更新失败"))
          } else {
            IO.unit
          }
          _ = logger.info(s"更新管理员成功: adminId=$adminId")
        } yield ()
      }
    } yield ()
  }

  override def deleteSystemAdmin(adminId: String): IO[Unit] = {
    for {
      // 检查是否是超级管理员
      admin <- DatabaseManager.executeQueryOptional(
        "SELECT role FROM authservice.admin_table WHERE admin_id = ?",
        List(SqlParameter("String", adminId))
      )
      _ <- admin match {
        case Some(adminRecord) =>
          val role = DatabaseManager.decodeFieldUnsafe[String](adminRecord, "role")
          if (role == "super_admin") {
            IO.raiseError(new RuntimeException("不能删除超级管理员"))
          } else {
            IO.unit
          }
        case None =>
          IO.raiseError(new RuntimeException("管理员不存在"))
      }
      
      // 删除管理员
      sql = "DELETE FROM authservice.admin_table WHERE admin_id = ?"
      params = List(SqlParameter("String", adminId))
      
      rowsAffected <- DatabaseManager.executeUpdate(sql, params)
      _ <- if (rowsAffected == 0) {
        IO.raiseError(new RuntimeException("管理员不存在或删除失败"))
      } else {
        IO.unit
      }
      _ = logger.info(s"删除管理员成功: adminId=$adminId")
    } yield ()
  }

  override def resetSystemAdminPassword(adminId: String, newPassword: String): IO[Unit] = {
    if (newPassword.length < 6) {
      IO.raiseError(new RuntimeException("新密码长度不能少于6位"))
    } else {
      val sql = """
        UPDATE authservice.admin_table 
        SET password_hash = ?
        WHERE admin_id = ?
      """.stripMargin

      val params = List(
        SqlParameter("String", newPassword), // 前端传来的已经是哈希后的密码
        SqlParameter("String", adminId)
      )

      for {
        rowsAffected <- DatabaseManager.executeUpdate(sql, params)
        _ <- if (rowsAffected == 0) {
          IO.raiseError(new RuntimeException("管理员不存在或密码重置失败"))
        } else {
          IO.unit
        }
        _ = logger.info(s"重置管理员密码成功: adminId=$adminId")
      } yield ()
    }
  }

  // ===================== 辅助方法 =====================
  private def convertToStudentRegistrationRequest(json: io.circe.Json): StudentRegistrationRequest = {
    StudentRegistrationRequest(
      id = DatabaseManager.decodeFieldUnsafe[String](json, "id"),
      username = DatabaseManager.decodeFieldUnsafe[String](json, "username"),
      province = DatabaseManager.decodeFieldUnsafe[String](json, "province"),
      school = DatabaseManager.decodeFieldUnsafe[String](json, "school"),
      coachUsername = DatabaseManager.decodeFieldOptional[String](json, "coach_username"),
      reason = DatabaseManager.decodeFieldOptional[String](json, "reason"),
      status = DatabaseManager.decodeFieldUnsafe[String](json, "status"),
      createdAt = DatabaseManager.decodeFieldOptional[LocalDateTime](json, "created_at").getOrElse(LocalDateTime.now()),
      reviewedBy = DatabaseManager.decodeFieldOptional[String](json, "reviewed_by"),
      reviewedAt = DatabaseManager.decodeFieldOptional[LocalDateTime](json, "reviewed_at"),
      reviewNote = DatabaseManager.decodeFieldOptional[String](json, "review_note")
    )
  }

  // 辅助转换方法
  private def convertToUserProfile(json: io.circe.Json): UserProfile = {
    UserProfile(
      id = DatabaseManager.decodeFieldUnsafe[String](json, "id"),
      username = DatabaseManager.decodeFieldUnsafe[String](json, "username"),
      phone = DatabaseManager.decodeFieldOptional[String](json, "phone"),
      role = UserRole.fromString(DatabaseManager.decodeFieldUnsafe[String](json, "role")).value,
      province = DatabaseManager.decodeFieldOptional[String](json, "province"),
      school = DatabaseManager.decodeFieldOptional[String](json, "school"),
      avatarUrl = DatabaseManager.decodeFieldOptional[String](json, "avatarurl"),
      createdAt = DatabaseManager.decodeFieldOptional[LocalDateTime](json, "createdat").map(_.toString),
      lastLoginAt = DatabaseManager.decodeFieldOptional[LocalDateTime](json, "lastloginat").map(_.toString)
    )
  }

  private def convertToAdminProfile(json: io.circe.Json): AdminProfile = {
    val avatarUrl = DatabaseManager.decodeFieldOptional[String](json, "avatar_url")
    
    // 根据数据库中存储的格式，决定返回avatar(base64)还是avatarUrl(URL)
    val (finalAvatarUrl, finalAvatar) = avatarUrl match {
      case Some(url) if url.startsWith("data:image/") =>
        // 如果是base64格式，返回到avatar字段
        (None, Some(url))
      case Some(url) =>
        // 如果是URL格式，返回到avatarUrl字段
        (Some(url), None)
      case None =>
        (None, None)
    }
    
    // 处理角色字段：确保符合前端期望的格式
    val roleValue = DatabaseManager.decodeFieldOptional[String](json, "role").getOrElse("admin")
    val normalizedRole = roleValue match {
      case "super_admin" => "super_admin"
      case _ => "admin"
    }
    
    // 处理状态字段：确保符合前端期望的格式
    val statusValue = DatabaseManager.decodeFieldOptional[String](json, "status").getOrElse("active")
    val normalizedStatus = statusValue.toLowerCase match {
      case "disabled" => "disabled"
      case _ => "active"
    }
    
    AdminProfile(
      id = DatabaseManager.decodeFieldUnsafe[String](json, "id"),
      username = DatabaseManager.decodeFieldUnsafe[String](json, "username"),
      createdAt = DatabaseManager.decodeFieldOptional[LocalDateTime](json, "createdat").map(_.toString),
      lastLoginAt = DatabaseManager.decodeFieldOptional[LocalDateTime](json, "lastloginat").map(_.toString),
      avatarUrl = finalAvatarUrl,
      avatar = finalAvatar,
      status = Some(normalizedStatus),
      role = Some(normalizedRole)
    )
  }

  // ===================== 新增的方法实现 =====================

  override def updateUserById(userId: String, request: UpdateUserRequest): IO[Unit] = {
    val updates = scala.collection.mutable.ListBuffer[String]()
    val params = scala.collection.mutable.ListBuffer[SqlParameter]()

    request.phone.foreach { phone =>
      updates += "phone = ?"
      params += SqlParameter("String", phone)
    }
    
    request.role.foreach { role =>
      updates += "role = ?"
      params += SqlParameter("String", role)
    }
    
    request.status.foreach { status =>
      updates += "status = ?"
      params += SqlParameter("String", status)
    }
    
    request.avatarUrl.foreach { avatarUrl =>
      updates += "avatar_url = ?"
      params += SqlParameter("String", avatarUrl)
    }

    if (updates.isEmpty) {
      IO.raiseError(new RuntimeException("没有需要更新的字段"))
    } else {
      updates += "updated_at = CURRENT_TIMESTAMP"
      params += SqlParameter("String", userId)

      val sql = s"""
        UPDATE authservice.user_table 
        SET ${updates.mkString(", ")}
        WHERE user_id = ?
      """.stripMargin

      for {
        rowsAffected <- DatabaseManager.executeUpdate(sql, params.toList)
        _ <- if (rowsAffected == 0) {
          IO.raiseError(new RuntimeException(s"用户不存在或更新失败: $userId"))
        } else {
          IO.unit
        }
      } yield ()
    }
  }

  override def reviewStudentRegistration(requestId: String, request: ReviewRegistrationRequest): IO[Unit] = {
    val sql = s"""
      UPDATE authservice.student_registrations 
      SET status = ?, reviewed_at = CURRENT_TIMESTAMP, review_note = ?
      WHERE id = ?
    """.stripMargin

    val status = if (request.action == "approve") "approved" else "rejected"
    val params = List(
      SqlParameter("String", status),
      SqlParameter("String", request.adminComment.getOrElse("")),
      SqlParameter("String", requestId)
    )

    for {
      rowsAffected <- DatabaseManager.executeUpdate(sql, params)
      _ <- if (rowsAffected == 0) {
        IO.raiseError(new RuntimeException(s"注册申请不存在或审核失败: $requestId"))
      } else {
        IO.unit
      }
    } yield ()
  }

  override def approveStudentRegistration(request: StudentRegistrationApprovalRequest): IO[Unit] = {
    val action = request.action.toLowerCase match {
      case "approve" => "approved"
      case "reject" => "rejected"
      case _ => throw new IllegalArgumentException(s"Invalid action: ${request.action}")
    }

    val placeholders = request.requestIds.map(_ => "?").mkString(",")
    val sql = s"""
      UPDATE authservice.student_registrations 
      SET status = ?, reviewed_at = CURRENT_TIMESTAMP, review_note = ?
      WHERE id IN ($placeholders)
    """.stripMargin

    val params = List(
      SqlParameter("String", action),
      SqlParameter("String", request.adminComment.getOrElse(""))
    ) ++ request.requestIds.map(id => SqlParameter("String", id))

    for {
      rowsAffected <- DatabaseManager.executeUpdate(sql, params)
      _ <- if (rowsAffected != request.requestIds.length) {
        IO(logger.warn(s"批量审核学生注册申请：期望更新 ${request.requestIds.length} 条，实际更新 $rowsAffected 条"))
      } else {
        IO.unit
      }
      _ <- IO(logger.info(s"批量审核学生注册申请完成：$action，更新了 $rowsAffected 条记录"))
    } yield ()
  }

  override def createRegionChangeRequest(username: String, request: RegionChangeRequest): IO[String] = {
    val requestId = UUID.randomUUID().toString
    
    val sql = s"""
      INSERT INTO authservice.region_change_requests 
      (id, user_id, username, role, current_province, current_school, 
       requested_province, requested_school, reason, status, created_at)
      SELECT ?, u.user_id, u.username, u.role, 
             '', '',  -- 当前省份和学校名称暂时为空，需要通过RegionMS API获取
             ?, ?, ?, 'pending', CURRENT_TIMESTAMP
      FROM authservice.user_table u
      WHERE u.username = ?
    """.stripMargin

    val params = List(
      SqlParameter("String", requestId),
      SqlParameter("String", request.province),
      SqlParameter("String", request.school),
      SqlParameter("String", request.reason),
      SqlParameter("String", username)
    )

    for {
      rowsAffected <- DatabaseManager.executeUpdate(sql, params)
      _ <- if (rowsAffected == 0) {
        IO.raiseError(new RuntimeException(s"用户不存在或创建区域变更申请失败: $username"))
      } else {
        IO.unit
      }
    } yield requestId
  }

  override def getUserRegionChangeRequests(username: String): IO[List[RegionChangeRequestRecord]] = {
    val sql = s"""
      SELECT id, user_id, username, role, current_province, current_school,
             requested_province, requested_school, reason, status, created_at,
             processed_at, processed_by, admin_comment
      FROM authservice.region_change_requests
      WHERE username = ?
      ORDER BY created_at DESC
    """.stripMargin

    val params = List(SqlParameter("String", username))

    for {
      results <- DatabaseManager.executeQuery(sql, params)
    } yield results.map(convertToRegionChangeRequestRecord)
  }

  private def convertToRegionChangeRequestRecord(json: io.circe.Json): RegionChangeRequestRecord = {
    RegionChangeRequestRecord(
      id = DatabaseManager.decodeFieldUnsafe[String](json, "id"),
      userId = DatabaseManager.decodeFieldUnsafe[String](json, "user_id"),
      username = DatabaseManager.decodeFieldUnsafe[String](json, "username"),
      role = DatabaseManager.decodeFieldUnsafe[String](json, "role"),
      currentProvince = DatabaseManager.decodeFieldUnsafe[String](json, "current_province"),
      currentSchool = DatabaseManager.decodeFieldUnsafe[String](json, "current_school"),
      requestedProvince = DatabaseManager.decodeFieldUnsafe[String](json, "requested_province"),
      requestedSchool = DatabaseManager.decodeFieldUnsafe[String](json, "requested_school"),
      reason = DatabaseManager.decodeFieldUnsafe[String](json, "reason"),
      status = DatabaseManager.decodeFieldUnsafe[String](json, "status"),
      createdAt = DatabaseManager.decodeFieldUnsafe[LocalDateTime](json, "created_at"),
      processedAt = DatabaseManager.decodeFieldOptional[LocalDateTime](json, "processed_at"),
      processedBy = DatabaseManager.decodeFieldOptional[String](json, "processed_by"),
      adminComment = DatabaseManager.decodeFieldOptional[String](json, "admin_comment")
    )
  }

  // 文件上传相关方法实现
  private lazy val fileStorageClient = Utils.FileStorageClient(config)

  override def uploadAvatar(username: String, userType: String, fileName: String, fileData: Array[Byte], mimeType: String): IO[FileOperationResponse] = {
    for {
      // 获取用户ID
      userIdOpt <- getUserIdByUsername(username)
      userId = userIdOpt.getOrElse(username) // 如果找不到用户ID，使用username作为fallback
      
      // 上传文件到 FileStorageService
      uploadResult <- fileStorageClient.uploadFile(
        fileName = fileName,
        fileData = fileData,
        mimeType = mimeType,
        uploadUserId = userId,
        uploadUserType = userType,
        category = "avatar",
        description = Some(s"${userType}头像")
      )
      
      result <- if (uploadResult.success) {
        val fileId = uploadResult.fileId.getOrElse("")
        // 修正：使用正确的文件访问URL格式，添加协议
        val baseUrl = s"http://${config.fileStorageService.host}:${config.fileStorageService.port}"
        val fileUrl = userType match {
          case "admin" => s"$baseUrl/api/admin/files/download/$fileId"
          case "student" => s"$baseUrl/api/student/files/download/$fileId"
          case "coach" => s"$baseUrl/api/coach/files/download/$fileId"
          case "grader" => s"$baseUrl/api/grader/files/download/$fileId"
          case _ => s"$baseUrl/api/student/files/download/$fileId" // 默认为student
        }
        
        logger.info(s"生成头像URL: $fileUrl (用户类型: $userType, 文件ID: $fileId)")
        
        // 更新用户头像URL
        updateUserAvatarUrl(username, fileUrl).map { _ =>
          FileOperationResponse(
            success = true,
            message = "头像上传成功",
            fileUrl = Some(fileUrl),
            fileId = uploadResult.fileId
          )
        }
      } else {
        IO.pure(FileOperationResponse(
          success = false,
          message = uploadResult.error.getOrElse("上传失败")
        ))
      }
    } yield result
  }

  override def uploadAnswerImage(username: String, fileName: String, fileData: Array[Byte], mimeType: String, examId: Option[String], questionNumber: Option[Int]): IO[FileOperationResponse] = {
    for {
      userIdOpt <- getUserIdByUsername(username)
      userId = userIdOpt.getOrElse(username)
      
      uploadResult <- fileStorageClient.uploadFile(
        fileName = fileName,
        fileData = fileData,
        mimeType = mimeType,
        uploadUserId = userId,
        uploadUserType = "student",
        category = "answer-image",
        relatedId = examId,
        description = questionNumber.map(q => s"题目${q}答题图片")
      )
      
      result <- IO.pure(
        if (uploadResult.success) {
          val fileId = uploadResult.fileId.getOrElse("")
          val fileUrl = s"${config.fileStorageService.host}:${config.fileStorageService.port}/files/${fileId}"
          FileOperationResponse(
            success = true,
            message = "答题图片上传成功",
            fileUrl = Some(fileUrl),
            fileId = uploadResult.fileId
          )
        } else {
          FileOperationResponse(
            success = false,
            message = uploadResult.error.getOrElse("上传失败")
          )
        }
      )
    } yield result
  }

  override def uploadDocument(username: String, userType: String, fileName: String, fileData: Array[Byte], mimeType: String, description: Option[String]): IO[FileOperationResponse] = {
    for {
      userIdOpt <- getUserIdByUsername(username)
      userId = userIdOpt.getOrElse(username)
      
      uploadResult <- fileStorageClient.uploadFile(
        fileName = fileName,
        fileData = fileData,
        mimeType = mimeType,
        uploadUserId = userId,
        uploadUserType = userType,
        category = "document",
        description = description
      )
      
      result <- IO.pure(
        if (uploadResult.success) {
          val fileId = uploadResult.fileId.getOrElse("")
          val fileUrl = s"${config.fileStorageService.host}:${config.fileStorageService.port}/files/${fileId}"
          FileOperationResponse(
            success = true,
            message = "文档上传成功",
            fileUrl = Some(fileUrl),
            fileId = uploadResult.fileId
          )
        } else {
          FileOperationResponse(
            success = false,
            message = uploadResult.error.getOrElse("上传失败")
          )
        }
      )
    } yield result
  }

  // 获取用户头像（支持返回base64或URL）
  override def getUserAvatar(username: String, format: String = "url"): IO[Option[String]] = {
    for {
      // 获取用户资料获取avatarUrl
      profileOpt <- getUserProfile(username)
      result <- profileOpt match {
        case Some(profile) if profile.avatarUrl.isDefined =>
          val avatarUrl = profile.avatarUrl.get
          if (format == "base64" && avatarUrl.contains("files/download/")) {
            // 从FileStorageService获取文件并转换为base64
            val fileId = avatarUrl.split("/").last
            fileStorageClient.downloadFileAsBase64(fileId, username, "student")
              .map(base64 => Some(s"data:image/jpeg;base64,$base64"))
              .handleErrorWith(_ => IO.pure(Some(avatarUrl))) // 降级为URL
          } else {
            IO.pure(Some(avatarUrl))
          }
        case _ => IO.pure(None)
      }
    } yield result
  }

  // 辅助方法：通过用户名获取用户ID
  private def getUserIdByUsername(username: String): IO[Option[String]] = {
    val sql = s"""
      SELECT user_id
      FROM authservice.user_table
      WHERE username = ? AND status = 'ACTIVE'
    """.stripMargin

    val params = List(SqlParameter("String", username))

    DatabaseManager.executeQuery(sql, params).map { results =>
      results.headOption.map(DatabaseManager.decodeFieldUnsafe[String](_, "user_id"))
    }.handleErrorWith { error =>
      logger.warn(s"获取用户ID失败: ${error.getMessage}")
      IO.pure(None)
    }
  }

  // 辅助方法：更新用户头像URL
  private def updateUserAvatarUrl(username: String, avatarUrl: String): IO[Unit] = {
    val sql = s"""
      UPDATE authservice.user_table
      SET avatar_url = ?, updated_at = CURRENT_TIMESTAMP
      WHERE username = ?
    """.stripMargin

    val params = List(
      SqlParameter("String", avatarUrl),
      SqlParameter("String", username)
    )

    DatabaseManager.executeUpdate(sql, params).map(_ => ()).handleErrorWith { error =>
      logger.error(s"更新用户头像URL失败: ${error.getMessage}", error)
      IO.raiseError(error)
    }
  }

  // 使用RegionMS内部API根据ID获取省份和学校名称的辅助方法
  private def getRegionNamesByIds(provinceId: String, schoolId: String, username: String): IO[Option[(String, String)]] = {
    regionClient.getProvinceAndSchoolNamesByIds(provinceId, schoolId).map {
      case Right(response) =>
        logger.info(s"用户 $username: 通过内部API获取到省份='${response.provinceName}', 学校='${response.schoolName}'")
        Some((response.provinceName, response.schoolName))
      case Left(error) =>
        logger.warn(s"用户 $username: 内部API调用失败: $error")
        None
    }.handleErrorWith { ex =>
      logger.error(s"用户 $username: 调用RegionMS内部API异常: ${ex.getMessage}", ex)
      IO.pure(None)
    }
  }

  // 新的异步转换方法 - 使用RegionMS内部API
  private def convertToPendingUserWithRegionAsync(json: io.circe.Json): IO[PendingUser] = {
    try {
      val roleStr = DatabaseManager.decodeFieldUnsafe[String](json, "role")
      val provinceId = DatabaseManager.decodeFieldOptional[String](json, "province_id")
      val schoolId = DatabaseManager.decodeFieldOptional[String](json, "school_id")
      val username = DatabaseManager.decodeFieldUnsafe[String](json, "username")
      
      // 如果有省份ID和学校ID，使用内部API获取名称
      val regionNamesIO = (provinceId, schoolId) match {
        case (Some(pId), Some(sId)) =>
          logger.info(s"用户 $username: 使用内部API获取地区信息 - 省份ID: $pId, 学校ID: $sId")
          getRegionNamesByIds(pId, sId, username)
        case _ =>
          logger.warn(s"用户 $username: 省份ID或学校ID为空 - 省份ID: $provinceId, 学校ID: $schoolId")
          IO.pure(None)
      }
      
      regionNamesIO.map { regionNamesOpt =>
        val (provinceName, schoolName) = regionNamesOpt match {
          case Some((pName, sName)) => (Some(pName), Some(sName))
          case None => (None, None)
        }
        
        logger.info(s"转换待审核用户 $username，角色: '$roleStr', 省份: $provinceName, 学校: $schoolName")
        
        PendingUser(
          id = DatabaseManager.decodeFieldUnsafe[String](json, "id"),
          username = username,
          phone = DatabaseManager.decodeFieldOptional[String](json, "phone"),
          role = UserRole.fromString(roleStr),
          province = provinceName,
          school = schoolName,
          appliedAt = DatabaseManager.decodeFieldOptional[LocalDateTime](json, "appliedAt").getOrElse(LocalDateTime.now()),
          status = UserStatus.fromString(DatabaseManager.decodeFieldUnsafe[String](json, "status"))
        )
      }
    } catch {
      case e: Exception =>
        logger.error(s"转换待审核用户失败: ${e.getMessage}, JSON: $json")
        IO.raiseError(e)
    }
  }

  // 新的异步转换方法 - 使用RegionMS内部API转换ApprovedUser
  private def convertToApprovedUserWithRegionAsync(json: io.circe.Json): IO[ApprovedUser] = {
    try {
      val provinceId = DatabaseManager.decodeFieldOptional[String](json, "province_id")
      val schoolId = DatabaseManager.decodeFieldOptional[String](json, "school_id")
      val username = DatabaseManager.decodeFieldUnsafe[String](json, "username")
      
      // 如果有省份ID和学校ID，使用内部API获取名称
      val regionNamesIO = (provinceId, schoolId) match {
        case (Some(pId), Some(sId)) =>
          logger.info(s"用户 $username: 使用内部API获取地区信息 - 省份ID: $pId, 学校ID: $sId")
          getRegionNamesByIds(pId, sId, username)
        case _ =>
          logger.warn(s"用户 $username: 省份ID或学校ID为空 - 省份ID: $provinceId, 学校ID: $schoolId")
          IO.pure(None)
      }
      
      regionNamesIO.map { regionNamesOpt =>
        val (provinceName, schoolName) = regionNamesOpt match {
          case Some((pName, sName)) => (Some(pName), Some(sName))
          case None => (None, None)
        }
        
        logger.info(s"转换已审核用户 $username，省份: $provinceName, 学校: $schoolName")
        
        // 处理时间字段
        val approvedAtOpt = DatabaseManager.decodeFieldOptional[String](json, "approved_at").flatMap { timeStr =>
          try {
            val formattedTimeStr = if (timeStr.contains(" ")) {
              val parts = timeStr.split("\\.")
              if (parts.length == 2) {
                val microseconds = parts(1).take(6).padTo(6, '0')
                s"${parts(0).replace(" ", "T")}.$microseconds"
              } else {
                timeStr.replace(" ", "T")
              }
            } else {
              timeStr
            }
            Some(LocalDateTime.parse(formattedTimeStr))
          } catch {
            case e: Exception =>
              logger.warn(s"解析审核时间失败: $timeStr, 错误: ${e.getMessage}")
              None
          }
        }

        val lastLoginAtOpt = DatabaseManager.decodeFieldOptional[String](json, "lastloginat").flatMap { timeStr =>
          try {
            val formattedTimeStr = if (timeStr.contains(" ")) {
              val parts = timeStr.split("\\.")
              if (parts.length == 2) {
                val microseconds = parts(1).take(6).padTo(6, '0')
                s"${parts(0).replace(" ", "T")}.$microseconds"
              } else {
                timeStr.replace(" ", "T")
              }
            } else {
              timeStr
            }
            Some(LocalDateTime.parse(formattedTimeStr))
          } catch {
            case e: Exception =>
              logger.warn(s"解析最后登录时间失败: $timeStr, 错误: ${e.getMessage}")
              None
          }
        }

        ApprovedUser(
          id = DatabaseManager.decodeFieldUnsafe[String](json, "id"),
          username = username,
          phone = DatabaseManager.decodeFieldOptional[String](json, "phone"),
          role = UserRole.fromString(DatabaseManager.decodeFieldUnsafe[String](json, "role")),
          province = provinceName,
          school = schoolName,
          status = UserStatus.fromString(DatabaseManager.decodeFieldUnsafe[String](json, "status")),
          approvedAt = approvedAtOpt,
          lastLoginAt = lastLoginAtOpt,
          avatarUrl = DatabaseManager.decodeFieldOptional[String](json, "avatarurl")
        )
      }
    } catch {
      case e: Exception =>
        logger.error(s"转换已审核用户失败: ${e.getMessage}, JSON: $json")
        IO.raiseError(e)
    }
  }

  // 新的异步转换方法 - 使用RegionMS内部API转换UserProfile
  private def convertToUserProfileWithRegionAsync(json: io.circe.Json): IO[UserProfile] = {
    try {
      val provinceId = DatabaseManager.decodeFieldOptional[String](json, "province_id")
      val schoolId = DatabaseManager.decodeFieldOptional[String](json, "school_id")
      val username = DatabaseManager.decodeFieldUnsafe[String](json, "username")
      
      // 如果有省份ID和学校ID，使用内部API获取名称
      val regionNamesIO = (provinceId, schoolId) match {
        case (Some(pId), Some(sId)) =>
          logger.info(s"用户 $username: 使用内部API获取地区信息 - 省份ID: $pId, 学校ID: $sId")
          getRegionNamesByIds(pId, sId, username)
        case _ =>
          logger.warn(s"用户 $username: 省份ID或学校ID为空 - 省份ID: $provinceId, 学校ID: $schoolId")
          IO.pure(None)
      }
      
      regionNamesIO.map { regionNamesOpt =>
        val (provinceName, schoolName) = regionNamesOpt match {
          case Some((pName, sName)) => (Some(pName), Some(sName))
          case None => (None, None)
        }
        
        logger.info(s"转换用户资料 $username，省份: $provinceName, 学校: $schoolName")
        
        UserProfile(
          id = DatabaseManager.decodeFieldUnsafe[String](json, "id"),
          username = username,
          phone = DatabaseManager.decodeFieldOptional[String](json, "phone"),
          role = UserRole.fromString(DatabaseManager.decodeFieldUnsafe[String](json, "role")).value,
          province = provinceName,
          school = schoolName,
          avatarUrl = DatabaseManager.decodeFieldOptional[String](json, "avatarurl"),
          createdAt = DatabaseManager.decodeFieldOptional[LocalDateTime](json, "createdat").map(_.toString),
          lastLoginAt = DatabaseManager.decodeFieldOptional[LocalDateTime](json, "lastloginat").map(_.toString)
        )
      }
    } catch {
      case e: Exception =>
        logger.error(s"转换用户资料失败: ${e.getMessage}, JSON: $json")
        IO.raiseError(e)
    }
  }
}