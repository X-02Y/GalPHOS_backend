package Services

import Models.*
import cats.effect.IO
import Database.{DatabaseManager, SqlParameter}
import Config.Constants
import org.slf4j.{Logger, LoggerFactory}
import io.circe.Json
import java.util.UUID

trait AdminService {
  def findAdminByUsername(username: String): IO[Option[Admin]]
  def findAdminById(adminId: String): IO[Option[Admin]]
}

class AdminServiceImpl extends AdminService {
  private val logger = LoggerFactory.getLogger("AdminService")
  private val schemaName = "authservice"

  override def findAdminByUsername(username: String): IO[Option[Admin]] = {
    val sql = s"""
      SELECT admin_id, username, password_hash, salt, role, status, name, avatar_url, created_at, last_login_at
      FROM $schemaName.admin_table
      WHERE username = ?
    """.stripMargin
    
    val params = List(SqlParameter("String", username))
    
    for {
      resultOpt <- DatabaseManager.executeQueryOptional(sql, params)
      admin <- resultOpt match {
        case Some(json) => IO.pure(Some(jsonToAdmin(json)))
        case None => IO.pure(None)
      }
    } yield admin
  }

  override def findAdminById(adminId: String): IO[Option[Admin]] = {
    val sql = s"""
      SELECT admin_id, username, password_hash, salt, role, status, name, avatar_url, created_at, last_login_at
      FROM $schemaName.admin_table
      WHERE admin_id = ?
    """.stripMargin
    
    val params = List(SqlParameter("String", adminId))
    
    for {
      resultOpt <- DatabaseManager.executeQueryOptional(sql, params)
      admin <- resultOpt match {
        case Some(json) => IO.pure(Some(jsonToAdmin(json)))
        case None => IO.pure(None)
      }
    } yield admin
  }

  private def jsonToAdmin(json: Json): Admin = {
    Admin(
      adminID = DatabaseManager.decodeFieldUnsafe[String](json, "admin_id"),
      username = DatabaseManager.decodeFieldUnsafe[String](json, "username"),
      passwordHash = DatabaseManager.decodeFieldUnsafe[String](json, "password_hash"),
      salt = DatabaseManager.decodeFieldUnsafe[String](json, "salt"),
      role = DatabaseManager.decodeFieldOptional[String](json, "role").getOrElse("admin"),
      status = DatabaseManager.decodeFieldOptional[String](json, "status").getOrElse("active"),
      name = DatabaseManager.decodeFieldOptional[String](json, "name"),
      avatarUrl = DatabaseManager.decodeFieldOptional[String](json, "avatar_url"),
      createdAt = DatabaseManager.decodeFieldOptional[java.time.LocalDateTime](json, "created_at"),
      lastLoginAt = DatabaseManager.decodeFieldOptional[java.time.LocalDateTime](json, "last_login_at")
    )
  }
}
