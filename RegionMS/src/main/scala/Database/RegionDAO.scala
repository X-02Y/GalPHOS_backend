package Database

import cats.effect.IO
import Models.*
import java.time.OffsetDateTime
import java.util.UUID
import java.sql.ResultSet

class RegionDAO(db: DatabaseConnection) {
  
  // Helper method to map ResultSet to Province
  private def mapProvince(rs: ResultSet): Province = {
    Province(
      id = rs.getObject("id", classOf[UUID]),
      name = rs.getString("name"),
      createdAt = rs.getObject("created_at", classOf[OffsetDateTime]),
      updatedAt = rs.getObject("updated_at", classOf[OffsetDateTime])
    )
  }
  
  // Helper method to map ResultSet to School
  private def mapSchool(rs: ResultSet): School = {
    School(
      id = rs.getObject("id", classOf[UUID]),
      name = rs.getString("name"),
      provinceId = rs.getObject("province_id", classOf[UUID]),
      createdAt = rs.getObject("created_at", classOf[OffsetDateTime]),
      updatedAt = rs.getObject("updated_at", classOf[OffsetDateTime])
    )
  }
  
  // Helper method to map ResultSet to School with Province
  private def mapSchoolWithProvince(rs: ResultSet): School = {
    val province = if (rs.getObject("province_name") != null) {
      Some(Province(
        id = rs.getObject("province_id", classOf[UUID]),
        name = rs.getString("province_name"),
        createdAt = rs.getObject("province_created_at", classOf[OffsetDateTime]),
        updatedAt = rs.getObject("province_updated_at", classOf[OffsetDateTime])
      ))
    } else None
    
    School(
      id = rs.getObject("id", classOf[UUID]),
      name = rs.getString("name"),
      provinceId = rs.getObject("province_id", classOf[UUID]),
      province = province,
      createdAt = rs.getObject("created_at", classOf[OffsetDateTime]),
      updatedAt = rs.getObject("updated_at", classOf[OffsetDateTime])
    )
  }
  
  // Helper method to map ResultSet to RegionChangeRequest
  private def mapRegionChangeRequest(rs: ResultSet): RegionChangeRequest = {
    val currentProvince = if (rs.getObject("current_province_name") != null) {
      Some(Province(
        id = rs.getObject("current_province_id", classOf[UUID]),
        name = rs.getString("current_province_name"),
        createdAt = OffsetDateTime.now(), // These won't be used in responses
        updatedAt = OffsetDateTime.now()
      ))
    } else None
    
    val currentSchool = if (rs.getObject("current_school_name") != null) {
      Some(School(
        id = rs.getObject("current_school_id", classOf[UUID]),
        name = rs.getString("current_school_name"),
        provinceId = rs.getObject("current_province_id", classOf[UUID]),
        createdAt = OffsetDateTime.now(),
        updatedAt = OffsetDateTime.now()
      ))
    } else None
    
    val requestedProvince = Province(
      id = rs.getObject("requested_province_id", classOf[UUID]),
      name = rs.getString("requested_province_name"),
      createdAt = OffsetDateTime.now(),
      updatedAt = OffsetDateTime.now()
    )
    
    val requestedSchool = School(
      id = rs.getObject("requested_school_id", classOf[UUID]),
      name = rs.getString("requested_school_name"),
      provinceId = rs.getObject("requested_province_id", classOf[UUID]),
      createdAt = OffsetDateTime.now(),
      updatedAt = OffsetDateTime.now()
    )
    
    RegionChangeRequest(
      id = rs.getObject("id", classOf[UUID]),
      userId = rs.getObject("user_id", classOf[UUID]),
      userType = rs.getString("user_type"),
      currentProvinceId = Option(rs.getObject("current_province_id", classOf[UUID])),
      currentSchoolId = Option(rs.getObject("current_school_id", classOf[UUID])),
      requestedProvinceId = rs.getObject("requested_province_id", classOf[UUID]),
      requestedSchoolId = rs.getObject("requested_school_id", classOf[UUID]),
      reason = rs.getString("reason"),
      status = rs.getString("status"),
      reviewedBy = Option(rs.getObject("reviewed_by", classOf[UUID])),
      reviewNote = Option(rs.getString("review_note")),
      createdAt = rs.getObject("created_at", classOf[OffsetDateTime]),
      updatedAt = rs.getObject("updated_at", classOf[OffsetDateTime]),
      currentProvince = currentProvince,
      currentSchool = currentSchool,
      requestedProvince = Some(requestedProvince),
      requestedSchool = Some(requestedSchool)
    )
  }
  
  // Province operations
  def getAllProvinces(): IO[List[Province]] = {
    val sql = "SELECT id, name, created_at, updated_at FROM provinces ORDER BY name"
    db.executeQuery(sql)(mapProvince)
  }
  
  def getProvinceById(id: UUID): IO[Option[Province]] = {
    val sql = "SELECT id, name, created_at, updated_at FROM provinces WHERE id = ?"
    db.executeQuery(sql, List(id))(mapProvince).map(_.headOption)
  }
  
  def createProvince(name: String): IO[Option[Province]] = {
    val sql = "INSERT INTO provinces (name) VALUES (?) RETURNING id, name, created_at, updated_at"
    db.executeInsertWithReturn(sql, List(name), List("id", "name", "created_at", "updated_at"))(mapProvince)
  }
  
  def deleteProvince(id: UUID): IO[Int] = {
    val sql = "DELETE FROM provinces WHERE id = ?"
    db.executeUpdate(sql, List(id))
  }
  
  // School operations
  def getSchoolsByProvince(provinceId: UUID): IO[List[School]] = {
    val sql = """
      SELECT s.id, s.name, s.province_id, s.created_at, s.updated_at,
             p.name as province_name, p.created_at as province_created_at, p.updated_at as province_updated_at
      FROM schools s
      LEFT JOIN provinces p ON s.province_id = p.id
      WHERE s.province_id = ?
      ORDER BY s.name
    """
    db.executeQuery(sql, List(provinceId))(mapSchoolWithProvince)
  }
  
  def getAllSchools(): IO[List[School]] = {
    val sql = """
      SELECT s.id, s.name, s.province_id, s.created_at, s.updated_at,
             p.name as province_name, p.created_at as province_created_at, p.updated_at as province_updated_at
      FROM schools s
      LEFT JOIN provinces p ON s.province_id = p.id
      ORDER BY p.name, s.name
    """
    db.executeQuery(sql)(mapSchoolWithProvince)
  }
  
  def getSchoolById(id: UUID): IO[Option[School]] = {
    val sql = """
      SELECT s.id, s.name, s.province_id, s.created_at, s.updated_at,
             p.name as province_name, p.created_at as province_created_at, p.updated_at as province_updated_at
      FROM schools s
      LEFT JOIN provinces p ON s.province_id = p.id
      WHERE s.id = ?
    """
    db.executeQuery(sql, List(id))(mapSchoolWithProvince).map(_.headOption)
  }
  
  def createSchool(name: String, provinceId: UUID): IO[Option[School]] = {
    val sql = "INSERT INTO schools (name, province_id) VALUES (?, ?) RETURNING id, name, province_id, created_at, updated_at"
    db.executeInsertWithReturn(sql, List(name, provinceId), List("id", "name", "province_id", "created_at", "updated_at"))(mapSchool)
  }
  
  def updateSchool(id: UUID, name: String): IO[Int] = {
    val sql = "UPDATE schools SET name = ? WHERE id = ?"
    db.executeUpdate(sql, List(name, id))
  }
  
  def deleteSchool(id: UUID): IO[Int] = {
    val sql = "DELETE FROM schools WHERE id = ?"
    db.executeUpdate(sql, List(id))
  }
  
  // Region change request operations
  def createRegionChangeRequest(
    userId: UUID,
    userType: String,
    currentProvinceId: Option[UUID],
    currentSchoolId: Option[UUID],
    requestedProvinceId: UUID,
    requestedSchoolId: UUID,
    reason: String
  ): IO[Option[RegionChangeRequest]] = {
    val sql = """
      INSERT INTO region_change_requests 
      (user_id, user_type, current_province_id, current_school_id, requested_province_id, requested_school_id, reason)
      VALUES (?, ?, ?, ?, ?, ?, ?)
      RETURNING id, user_id, user_type, current_province_id, current_school_id, 
                requested_province_id, requested_school_id, reason, status, 
                reviewed_by, review_note, created_at, updated_at
    """
    
    db.executeInsertWithReturn(
      sql, 
      List(userId, userType, currentProvinceId, currentSchoolId, requestedProvinceId, requestedSchoolId, reason),
      List("id", "user_id", "user_type", "current_province_id", "current_school_id", 
           "requested_province_id", "requested_school_id", "reason", "status", 
           "reviewed_by", "review_note", "created_at", "updated_at")
    ) { rs =>
      RegionChangeRequest(
        id = rs.getObject("id", classOf[UUID]),
        userId = rs.getObject("user_id", classOf[UUID]),
        userType = rs.getString("user_type"),
        currentProvinceId = Option(rs.getObject("current_province_id", classOf[UUID])),
        currentSchoolId = Option(rs.getObject("current_school_id", classOf[UUID])),
        requestedProvinceId = rs.getObject("requested_province_id", classOf[UUID]),
        requestedSchoolId = rs.getObject("requested_school_id", classOf[UUID]),
        reason = rs.getString("reason"),
        status = rs.getString("status"),
        reviewedBy = Option(rs.getObject("reviewed_by", classOf[UUID])),
        reviewNote = Option(rs.getString("review_note")),
        createdAt = rs.getObject("created_at", classOf[OffsetDateTime]),
        updatedAt = rs.getObject("updated_at", classOf[OffsetDateTime])
      )
    }
  }
  
  def getRegionChangeRequestsByUser(userId: UUID): IO[List[RegionChangeRequest]] = {
    val sql = """
      SELECT rcr.id, rcr.user_id, rcr.user_type, rcr.current_province_id, rcr.current_school_id,
             rcr.requested_province_id, rcr.requested_school_id, rcr.reason, rcr.status,
             rcr.reviewed_by, rcr.review_note, rcr.created_at, rcr.updated_at,
             cp.name as current_province_name, cs.name as current_school_name,
             rp.name as requested_province_name, rs.name as requested_school_name
      FROM region_change_requests rcr
      LEFT JOIN provinces cp ON rcr.current_province_id = cp.id
      LEFT JOIN schools cs ON rcr.current_school_id = cs.id
      JOIN provinces rp ON rcr.requested_province_id = rp.id
      JOIN schools rs ON rcr.requested_school_id = rs.id
      WHERE rcr.user_id = ?
      ORDER BY rcr.created_at DESC
    """
    db.executeQuery(sql, List(userId))(mapRegionChangeRequest)
  }
  
  def getRegionChangeRequests(status: Option[String], page: Int, limit: Int): IO[List[RegionChangeRequest]] = {
    val baseCondition = status.map(_ => "WHERE rcr.status = ?").getOrElse("")
    val params = status.toList ++ List(limit, (page - 1) * limit)
    
    val sql = s"""
      SELECT rcr.id, rcr.user_id, rcr.user_type, rcr.current_province_id, rcr.current_school_id,
             rcr.requested_province_id, rcr.requested_school_id, rcr.reason, rcr.status,
             rcr.reviewed_by, rcr.review_note, rcr.created_at, rcr.updated_at,
             cp.name as current_province_name, cs.name as current_school_name,
             rp.name as requested_province_name, rs.name as requested_school_name
      FROM region_change_requests rcr
      LEFT JOIN provinces cp ON rcr.current_province_id = cp.id
      LEFT JOIN schools cs ON rcr.current_school_id = cs.id
      JOIN provinces rp ON rcr.requested_province_id = rp.id
      JOIN schools rs ON rcr.requested_school_id = rs.id
      $baseCondition
      ORDER BY rcr.created_at DESC
      LIMIT ? OFFSET ?
    """
    db.executeQuery(sql, params)(mapRegionChangeRequest)
  }
  
  def countRegionChangeRequests(status: Option[String]): IO[Int] = {
    val baseCondition = status.map(_ => "WHERE status = ?").getOrElse("")
    val params = status.toList
    
    val sql = s"SELECT COUNT(*) as count FROM region_change_requests $baseCondition"
    db.executeQuery(sql, params)(rs => rs.getInt("count")).map(_.headOption.getOrElse(0))
  }
  
  def updateRegionChangeRequestStatus(
    id: UUID,
    status: String,
    reviewedBy: UUID,
    reviewNote: Option[String]
  ): IO[Int] = {
    val sql = "UPDATE region_change_requests SET status = ?, reviewed_by = ?, review_note = ? WHERE id = ?"
    db.executeUpdate(sql, List(status, reviewedBy, reviewNote, id))
  }
  
  def getRegionChangeRequestById(id: UUID): IO[Option[RegionChangeRequest]] = {
    val sql = """
      SELECT rcr.id, rcr.user_id, rcr.user_type, rcr.current_province_id, rcr.current_school_id,
             rcr.requested_province_id, rcr.requested_school_id, rcr.reason, rcr.status,
             rcr.reviewed_by, rcr.review_note, rcr.created_at, rcr.updated_at,
             cp.name as current_province_name, cs.name as current_school_name,
             rp.name as requested_province_name, rs.name as requested_school_name
      FROM region_change_requests rcr
      LEFT JOIN provinces cp ON rcr.current_province_id = cp.id
      LEFT JOIN schools cs ON rcr.current_school_id = cs.id
      JOIN provinces rp ON rcr.requested_province_id = rp.id
      JOIN schools rs ON rcr.requested_school_id = rs.id
      WHERE rcr.id = ?
    """
    db.executeQuery(sql, List(id))(mapRegionChangeRequest).map(_.headOption)
  }
}
