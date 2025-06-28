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
  def getUsersByRole(role: UserRole, status: Option[UserStatus] = None): IO[List[ApprovedUser]]
  // 新增：获取学生注册申请（只包含有教练关联的）
  def getStudentRegistrationRequests(): IO[List[StudentRegistrationRequest]]
  
  // 个人资料管理相关方法
  def getUserProfile(username: String): IO[Option[UserProfile]]
  def updateUserProfile(username: String, request: UpdateProfileRequest): IO[Unit]
  def getAdminProfile(username: String): IO[Option[AdminProfile]]
  def updateAdminProfile(username: String, request: UpdateAdminProfileRequest): IO[Unit]
  def changeUserPassword(username: String, request: ChangePasswordRequest): IO[Unit]
}

class UserManagementServiceImpl() extends UserManagementService {
  private val logger = LoggerFactory.getLogger("UserManagementService")
  private val schemaName = "authservice"  // 使用固定的schema名称

  override def getPendingUsers(): IO[List[PendingUser]] = {
    // 首先查询所有用户来验证数据库连接和数据
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

    val params = List(SqlParameter("String", Constants.USER_STATUS_PENDING))

    for {
      _ <- IO(logger.info(s"查询待审核用户，状态参数: ${Constants.USER_STATUS_PENDING}"))
      
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
        u.updated_at as approvedAt,
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
      SET status = ?, updated_at = NOW()
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
        u.created_at as approvedAt,
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
        u.created_at as approvedAt,
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
    // 构建动态更新SQL
    val updateFields = scala.collection.mutable.ListBuffer[String]()
    val sqlParams = scala.collection.mutable.ListBuffer[SqlParameter]()

    request.phone.foreach { phone =>
      updateFields += "phone = ?"
      sqlParams += SqlParameter("String", phone)
    }

    request.province.foreach { province =>
      // 需要先查找省份ID
      updateFields += "province_id = (SELECT province_id FROM authservice.province_table WHERE name = ?)"
      sqlParams += SqlParameter("String", province)
    }

    request.school.foreach { school =>
      // 需要先查找学校ID
      updateFields += "school_id = (SELECT school_id FROM authservice.school_table WHERE name = ?)"
      sqlParams += SqlParameter("String", school)
    }

    request.avatarUrl.foreach { avatarUrl =>
      updateFields += "avatar_url = ?"
      sqlParams += SqlParameter("String", avatarUrl)
    }

    if (updateFields.isEmpty) {
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
    // 密码修改需要验证当前密码并更新新密码
    if (request.newPassword != request.confirmPassword) {
      IO.raiseError(new RuntimeException("新密码与确认密码不匹配"))
    } else {
      // 这里需要调用认证服务的密码修改功能
      // 由于我们在用户管理服务中，可能需要通过HTTP调用认证服务
      // 或者直接访问数据库进行密码验证和更新
      IO.raiseError(new RuntimeException("密码修改功能需要与认证服务集成"))
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
    
    // 直接解码字符串格式的审核时间
    val approvedAtOpt = DatabaseManager.decodeFieldOptional[String](json, "approvedat").flatMap { str =>
      if (str.nonEmpty && str != "null") {
        try {
          logger.info(s"解析approvedAt字符串: '$str'")
          Some(LocalDateTime.parse(str))
        } catch {
          case e: Exception =>
            logger.error(s"解析approvedAt失败: ${e.getMessage}, 原始值: '$str'")
            None
        }
      } else {
        logger.info("approvedAt字段为空或null，返回None")
        None
      }
    }
    
    // 解码最后登录时间
    val lastLoginAtOpt = DatabaseManager.decodeFieldOptional[String](json, "lastloginat").flatMap { str =>
      if (str.nonEmpty && str != "null") {
        try {
          Some(LocalDateTime.parse(str))
        } catch {
          case e: Exception =>
            logger.error(s"解析lastLoginAt失败: ${e.getMessage}, 原始值: '$str'")
            None
        }
      } else {
        None
      }
    }
    
    logger.info(s"解码approvedAt字段结果: $approvedAtOpt")
    
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
}
