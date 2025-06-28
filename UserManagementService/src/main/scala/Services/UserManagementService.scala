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
}

class UserManagementServiceImpl() extends UserManagementService {
  private val logger = LoggerFactory.getLogger("UserManagementService")
  private val schemaName = "authservice"  // 使用固定的schema名称

  override def getPendingUsers(): IO[List[PendingUser]] = {
    val sql = s"""
      SELECT 
        u.user_id as id,
        u.username,
        u.phone,
        u.role,
        p.name as province,
        s.name as school,
        u.created_at as appliedAt,
        u.status
      FROM authservice.user_table u
      LEFT JOIN authservice.province_table p ON u.province_id = p.province_id
      LEFT JOIN authservice.school_table s ON u.school_id = s.school_id
      WHERE u.status = ?
      ORDER BY u.created_at DESC
    """.stripMargin

    val params = List(SqlParameter("String", Constants.USER_STATUS_PENDING))

    for {
      results <- DatabaseManager.executeQuery(sql, params)
      users = results.map(convertToPendingUser)
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
        u.created_at as approvedAt,
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

  private def convertToPendingUser(json: io.circe.Json): PendingUser = {
    PendingUser(
      id = DatabaseManager.decodeFieldUnsafe[String](json, "id"),
      username = DatabaseManager.decodeFieldUnsafe[String](json, "username"),
      phone = DatabaseManager.decodeFieldOptional[String](json, "phone"),
      role = UserRole.fromString(DatabaseManager.decodeFieldUnsafe[String](json, "role")),
      province = DatabaseManager.decodeFieldOptional[String](json, "province"),
      school = DatabaseManager.decodeFieldOptional[String](json, "school"),
      appliedAt = DatabaseManager.decodeFieldUnsafe[LocalDateTime](json, "appliedAt"),
      status = UserStatus.fromString(DatabaseManager.decodeFieldUnsafe[String](json, "status"))
    )
  }

  private def convertToApprovedUser(json: io.circe.Json): ApprovedUser = {
    ApprovedUser(
      id = DatabaseManager.decodeFieldUnsafe[String](json, "id"),
      username = DatabaseManager.decodeFieldUnsafe[String](json, "username"),
      phone = DatabaseManager.decodeFieldOptional[String](json, "phone"),
      role = UserRole.fromString(DatabaseManager.decodeFieldUnsafe[String](json, "role")),
      province = DatabaseManager.decodeFieldOptional[String](json, "province"),
      school = DatabaseManager.decodeFieldOptional[String](json, "school"),
      status = UserStatus.fromString(DatabaseManager.decodeFieldUnsafe[String](json, "status")),
      approvedAt = DatabaseManager.decodeFieldOptional[LocalDateTime](json, "approvedAt"),
      lastLoginAt = DatabaseManager.decodeFieldOptional[LocalDateTime](json, "lastLoginAt"),
      avatarUrl = DatabaseManager.decodeFieldOptional[String](json, "avatarUrl")
    )
  }
}
