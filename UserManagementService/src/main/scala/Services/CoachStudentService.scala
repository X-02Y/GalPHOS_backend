package Services

import Models.*
import Database.{DatabaseManager, SqlParameter}
import cats.effect.IO
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

trait CoachStudentService {
  def getCoachStudentRelationships(params: QueryParams): IO[PaginatedResponse[CoachStudentRelationship]]
  def getCoachStudentStats(): IO[CoachStudentStats]
  def createCoachStudentRelationship(request: CreateCoachStudentRequest): IO[String]
  def deleteCoachStudentRelationship(relationshipId: String): IO[Unit]
  def getStudentsByCoach(coachId: String): IO[List[CoachStudentRelationship]]
}

class CoachStudentServiceImpl() extends CoachStudentService {
  private val logger = LoggerFactory.getLogger("CoachStudentService")
  private val schemaName = "authservice"  // 使用固定的schema名称

  override def getCoachStudentRelationships(params: QueryParams): IO[PaginatedResponse[CoachStudentRelationship]] = {
    val page = params.page.getOrElse(1)
    val limit = math.min(params.limit.getOrElse(20), 100)
    val offset = (page - 1) * limit

    // 构建WHERE条件
    val whereConditions = scala.collection.mutable.ListBuffer[String]()
    val sqlParams = scala.collection.mutable.ListBuffer[SqlParameter]()

    params.coachId.foreach { coachId =>
      whereConditions += "cms.coach_id = ?"
      sqlParams += SqlParameter("String", coachId)
    }

    val whereClause = if (whereConditions.nonEmpty) 
      s"WHERE ${whereConditions.mkString(" AND ")}" 
    else ""

    // 查询总数
    val countSql = s"""
      SELECT COUNT(*) as total
      FROM authservice.coach_managed_students cms
      $whereClause
    """.stripMargin

    // 查询数据
    val dataSql = s"""
      SELECT 
        cms.id,
        cms.coach_id,
        cu.username as coach_username,
        cms.student_id,
        cms.student_username,
        cms.student_name,
        cms.created_at
      FROM authservice.coach_managed_students cms
      LEFT JOIN authservice.user_table cu ON cms.coach_id = cu.user_id
      $whereClause
      ORDER BY cms.created_at DESC
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
      relationships = dataResults.map(convertToCoachStudentRelationship)
    } yield PaginatedResponse(relationships, total, page, limit)
  }

  override def getCoachStudentStats(): IO[CoachStudentStats] = {
    val sql = s"""
      SELECT 
        COUNT(DISTINCT cms.coach_id) as total_coaches,
        COUNT(cms.student_id) as total_managed_students
      FROM authservice.coach_managed_students cms
    """.stripMargin

    for {
      result <- DatabaseManager.executeQueryOptional(sql)
      stats = result.map { json =>
        val totalCoaches = DatabaseManager.decodeFieldUnsafe[Int](json, "total_coaches")
        val totalManagedStudents = DatabaseManager.decodeFieldUnsafe[Int](json, "total_managed_students")
        val averageStudentsPerCoach = if (totalCoaches > 0) 
          totalManagedStudents.toDouble / totalCoaches.toDouble 
        else 0.0
        
        CoachStudentStats(totalCoaches, totalManagedStudents, averageStudentsPerCoach)
      }.getOrElse(CoachStudentStats(0, 0, 0.0))
    } yield stats
  }

  override def createCoachStudentRelationship(request: CreateCoachStudentRequest): IO[String] = {
    // 首先验证教练存在且角色正确
    val validateCoachSql = s"""
      SELECT user_id FROM authservice.user_table 
      WHERE user_id = ? AND role = 'coach' AND status = 'ACTIVE'
    """.stripMargin

    val validateCoachParams = List(SqlParameter("String", request.coachId))

    // 检查关系是否已存在
    val checkExistsSql = s"""
      SELECT id FROM authservice.coach_managed_students 
      WHERE coach_id = ? AND student_username = ?
    """.stripMargin

    val checkExistsParams = List(
      SqlParameter("String", request.coachId),
      SqlParameter("String", request.studentUsername)
    )

    // 插入新关系
    val insertSql = s"""
      INSERT INTO authservice.coach_managed_students 
      (id, coach_id, student_id, student_username, student_name, created_at)
      VALUES (?, ?, ?, ?, ?, NOW())
    """.stripMargin

    val relationshipId = UUID.randomUUID().toString
    val studentId = UUID.randomUUID().toString // 为教练管理的学生生成独立ID

    val insertParams = List(
      SqlParameter("String", relationshipId),
      SqlParameter("String", request.coachId),
      SqlParameter("String", studentId),
      SqlParameter("String", request.studentUsername),
      SqlParameter("String", request.studentName)
    )

    for {
      // 验证教练
      coachExists <- DatabaseManager.executeQueryOptional(validateCoachSql, validateCoachParams)
      _ <- if (coachExists.isEmpty) {
        IO.raiseError(new RuntimeException(s"教练不存在或状态无效: ${request.coachId}"))
      } else IO.unit

      // 检查关系是否已存在
      relationshipExists <- DatabaseManager.executeQueryOptional(checkExistsSql, checkExistsParams)
      _ <- if (relationshipExists.nonEmpty) {
        IO.raiseError(new RuntimeException(s"教练学生关系已存在: ${request.coachId} -> ${request.studentUsername}"))
      } else IO.unit

      // 插入新关系
      _ <- DatabaseManager.executeUpdate(insertSql, insertParams)
      _ = logger.info(s"创建教练学生关系: relationshipId=$relationshipId, coachId=${request.coachId}, studentUsername=${request.studentUsername}")
    } yield relationshipId
  }

  override def deleteCoachStudentRelationship(relationshipId: String): IO[Unit] = {
    val sql = s"""
      DELETE FROM authservice.coach_managed_students 
      WHERE id = ?
    """.stripMargin

    val params = List(SqlParameter("String", relationshipId))

    for {
      rowsAffected <- DatabaseManager.executeUpdate(sql, params)
      _ <- if (rowsAffected == 0) {
        IO.raiseError(new RuntimeException(s"教练学生关系不存在: $relationshipId"))
      } else {
        IO.unit
      }
      _ = logger.info(s"删除教练学生关系: relationshipId=$relationshipId")
    } yield ()
  }

  override def getStudentsByCoach(coachId: String): IO[List[CoachStudentRelationship]] = {
    val sql = s"""
      SELECT 
        cms.id,
        cms.coach_id,
        cu.username as coach_username,
        cms.student_id,
        cms.student_username,
        cms.student_name,
        cms.created_at
      FROM authservice.coach_managed_students cms
      LEFT JOIN authservice.user_table cu ON cms.coach_id = cu.user_id
      WHERE cms.coach_id = ?
      ORDER BY cms.created_at DESC
    """.stripMargin

    val params = List(SqlParameter("String", coachId))

    for {
      results <- DatabaseManager.executeQuery(sql, params)
      relationships = results.map(convertToCoachStudentRelationship)
    } yield relationships
  }

  private def convertToCoachStudentRelationship(json: io.circe.Json): CoachStudentRelationship = {
    CoachStudentRelationship(
      id = DatabaseManager.decodeFieldUnsafe[String](json, "id"),
      coachId = DatabaseManager.decodeFieldUnsafe[String](json, "coach_id"),
      coachUsername = DatabaseManager.decodeFieldUnsafe[String](json, "coach_username"),
      coachName = DatabaseManager.decodeFieldOptional[String](json, "coach_name"),
      studentId = DatabaseManager.decodeFieldUnsafe[String](json, "student_id"),
      studentUsername = DatabaseManager.decodeFieldUnsafe[String](json, "student_username"),
      studentName = DatabaseManager.decodeFieldOptional[String](json, "student_name"),
      createdAt = DatabaseManager.decodeFieldUnsafe[LocalDateTime](json, "created_at")
    )
  }
}
