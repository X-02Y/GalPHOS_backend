package Services

import Models.*
import Database.{DatabaseManager, SqlParameter}
import cats.effect.IO
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
  def updateUserById(userId: String, request: UpdateUserRequest): IO[Unit]
  def getUsersByRole(role: UserRole, status: Option[UserStatus] = None): IO[List[ApprovedUser]]
  // 新增：获取学生注册申请（只包含有教练关联的）
  def getStudentRegistrationRequests(): IO[List[StudentRegistrationRequest]]
  def reviewStudentRegistration(requestId: String, request: ReviewRegistrationRequest): IO[Unit]
  
  // 个人资料管理相关方法
  def getUserProfile(username: String): IO[Option[UserProfile]]
  def updateUserProfile(username: String, request: UpdateProfileRequest): IO[Unit]
  def getAdminProfile(username: String): IO[Option[AdminProfile]]
  def updateAdminProfile(username: String, request: UpdateAdminProfileRequest): IO[Unit]
  def changeUserPassword(username: String, request: ChangePasswordRequest): IO[Unit]
  
  // 区域变更相关方法
  def createRegionChangeRequest(username: String, request: RegionChangeRequest): IO[String]
  def getUserRegionChangeRequests(username: String): IO[List[RegionChangeRequestRecord]]
  
  // 阅卷员密码修改方法（使用不同的参数结构）
  def changeGraderPassword(username: String, request: ChangeGraderPasswordRequest): IO[Unit]
}

class UserManagementServiceImpl() extends UserManagementService {
  private val logger = LoggerFactory.getLogger("UserManagementService")
  private val schemaName = "authservice"  // 使用固定的schema名称

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
        p.name as province,
        s.name as school,
        COALESCE(u.created_at, CURRENT_TIMESTAMP) as appliedAt,
        u.status
      FROM authservice.user_table u
      LEFT JOIN authservice.province_table p ON u.province_id = p.province_id
      LEFT JOIN authservice.school_table s ON u.school_id = s.school_id
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
      users = results.map(convertToPendingUser)
      _ <- IO(logger.info(s"转换后的用户数量: ${users.length}"))
    } yield users
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
        p.name as province,
        s.name as school,
        u.status,
        u.approved_at as approved_at,
        u.updated_at as lastLoginAt,
        u.avatar_url as avatarUrl
      FROM authservice.user_table u
      LEFT JOIN authservice.province_table p ON u.province_id = p.province_id
      LEFT JOIN authservice.school_table s ON u.school_id = s.school_id
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
      users = dataResults.map(convertToApprovedUser)
    } yield PaginatedResponse(users, total, page, limit)
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
        p.name as province,
        s.name as school,
        u.status,
        u.approved_at as approved_at,
        u.updated_at as lastLoginAt,
        u.avatar_url as avatarUrl
      FROM authservice.user_table u
      LEFT JOIN authservice.province_table p ON u.province_id = p.province_id
      LEFT JOIN authservice.school_table s ON u.school_id = s.school_id
      WHERE u.user_id = ?
    """.stripMargin

    val params = List(SqlParameter("String", userId))

    for {
      result <- DatabaseManager.executeQueryOptional(sql, params)
    } yield result.map(convertToApprovedUser)
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
        p.name as province,
        s.name as school,
        u.status,
        u.approved_at as approved_at,
        u.updated_at as lastLoginAt,
        u.avatar_url as avatarUrl
      FROM authservice.user_table u
      LEFT JOIN authservice.province_table p ON u.province_id = p.province_id
      LEFT JOIN authservice.school_table s ON u.school_id = s.school_id
      WHERE $whereClause
      ORDER BY u.username
    """.stripMargin

    for {
      results <- DatabaseManager.executeQuery(sql, params)
      users = results.map(convertToApprovedUser)
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
        p.name as province,
        s.name as school,
        u.avatar_url as avatarUrl,
        u.created_at as createdAt,
        u.updated_at as lastLoginAt
      FROM authservice.user_table u
      LEFT JOIN authservice.province_table p ON u.province_id = p.province_id
      LEFT JOIN authservice.school_table s ON u.school_id = s.school_id
      WHERE u.username = ?
    """.stripMargin

    val params = List(SqlParameter("String", username))

    for {
      result <- DatabaseManager.executeQueryOptional(sql, params)
    } yield result.map(convertToUserProfile)
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

      // 省份更新
      _ = request.province.foreach { province =>
        updateFields += "province_id = (SELECT province_id FROM authservice.province_table WHERE name = ?)"
        sqlParams += SqlParameter("String", province)
      }

      // 学校更新
      _ = request.school.foreach { school =>
        updateFields += "school_id = (SELECT school_id FROM authservice.school_table WHERE name = ?)"
        sqlParams += SqlParameter("String", school)
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

  override def getAdminProfile(username: String): IO[Option[AdminProfile]] = {
    val sql = s"""
      SELECT 
        admin_id as id,
        username,
        created_at as createdAt,
        created_at as lastLoginAt
      FROM authservice.admin_table
      WHERE username = ?
    """.stripMargin

    val params = List(SqlParameter("String", username))

    for {
      result <- DatabaseManager.executeQueryOptional(sql, params)
    } yield result.map(convertToAdminProfile)
  }

  override def updateAdminProfile(username: String, request: UpdateAdminProfileRequest): IO[Unit] = {
    // 管理员资料更新比较简单，只允许更新头像等基本信息
    val updateFields = scala.collection.mutable.ListBuffer[String]()
    val sqlParams = scala.collection.mutable.ListBuffer[SqlParameter]()

    request.avatarUrl.foreach { avatarUrl =>
      updateFields += "avatar_url = ?"
      sqlParams += SqlParameter("String", avatarUrl)
    }

    if (updateFields.isEmpty) {
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

  // 转换方法
  private def convertToPendingUser(json: io.circe.Json): PendingUser = {
    try {
      val roleStr = DatabaseManager.decodeFieldUnsafe[String](json, "role")
      logger.info(s"转换待审核用户，角色字符串: '$roleStr'")
      
      PendingUser(
        id = DatabaseManager.decodeFieldUnsafe[String](json, "id"),
        username = DatabaseManager.decodeFieldUnsafe[String](json, "username"),
        phone = DatabaseManager.decodeFieldOptional[String](json, "phone"),
        role = UserRole.fromString(roleStr),
        province = DatabaseManager.decodeFieldOptional[String](json, "province"),
        school = DatabaseManager.decodeFieldOptional[String](json, "school"),
        appliedAt = DatabaseManager.decodeFieldOptional[LocalDateTime](json, "appliedAt").getOrElse(LocalDateTime.now()),
        status = UserStatus.fromString(DatabaseManager.decodeFieldUnsafe[String](json, "status"))
      )
    } catch {
      case e: Exception =>
        logger.error(s"转换待审核用户失败: ${e.getMessage}, JSON: $json")
        throw e
    }
  }

  private def convertToApprovedUser(json: io.circe.Json): ApprovedUser = {
    // 添加调试日志
    logger.info(s"转换ApprovedUser，JSON数据: $json")
    
    // 尝试先解码为字符串，然后转换为LocalDateTime
    val approvedAtOpt = DatabaseManager.decodeFieldOptional[String](json, "approved_at").flatMap { timeStr =>
      try {
        // 处理PostgreSQL的时间戳格式：2025-06-29 15:03:13.378494
        val formattedTimeStr = if (timeStr.contains(" ")) {
          // 替换空格为T，并处理微秒部分
          val parts = timeStr.split("\\.")
          if (parts.length == 2) {
            // 有微秒部分，截取到6位微秒
            val microseconds = parts(1).take(6).padTo(6, '0')
            s"${parts(0).replace(" ", "T")}.$microseconds"
          } else {
            // 没有微秒部分
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
    logger.info(s"解码approvedAt字段结果: $approvedAtOpt")
    
    // 解码最后登录时间
    val lastLoginAtOpt = DatabaseManager.decodeFieldOptional[String](json, "lastloginat").flatMap { timeStr =>
      try {
        // 处理PostgreSQL的时间戳格式：2025-06-29 15:03:13.378494
        val formattedTimeStr = if (timeStr.contains(" ")) {
          // 替换空格为T，并处理微秒部分
          val parts = timeStr.split("\\.")
          if (parts.length == 2) {
            // 有微秒部分，截取到6位微秒
            val microseconds = parts(1).take(6).padTo(6, '0')
            s"${parts(0).replace(" ", "T")}.$microseconds"
          } else {
            // 没有微秒部分
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
      username = DatabaseManager.decodeFieldUnsafe[String](json, "username"),
      phone = DatabaseManager.decodeFieldOptional[String](json, "phone"),
      role = UserRole.fromString(DatabaseManager.decodeFieldUnsafe[String](json, "role")),
      province = DatabaseManager.decodeFieldOptional[String](json, "province"),
      school = DatabaseManager.decodeFieldOptional[String](json, "school"),
      status = UserStatus.fromString(DatabaseManager.decodeFieldUnsafe[String](json, "status")),
      approvedAt = approvedAtOpt,
      lastLoginAt = lastLoginAtOpt,
      avatarUrl = DatabaseManager.decodeFieldOptional[String](json, "avatarurl")
    )
  }

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
    AdminProfile(
      id = DatabaseManager.decodeFieldUnsafe[String](json, "id"),
      username = DatabaseManager.decodeFieldUnsafe[String](json, "username"),
      createdAt = DatabaseManager.decodeFieldOptional[LocalDateTime](json, "createdat").map(_.toString),
      lastLoginAt = DatabaseManager.decodeFieldOptional[LocalDateTime](json, "lastloginat").map(_.toString)
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

  override def createRegionChangeRequest(username: String, request: RegionChangeRequest): IO[String] = {
    val requestId = UUID.randomUUID().toString
    
    val sql = s"""
      INSERT INTO authservice.region_change_requests 
      (id, user_id, username, role, current_province, current_school, 
       requested_province, requested_school, reason, status, created_at)
      SELECT ?, u.user_id, u.username, u.role, 
             COALESCE(p.name, ''), COALESCE(s.name, ''),
             ?, ?, ?, 'pending', CURRENT_TIMESTAMP
      FROM authservice.user_table u
      LEFT JOIN authservice.province_table p ON u.province_id = p.province_id
      LEFT JOIN authservice.school_table s ON u.school_id = s.school_id
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
}
