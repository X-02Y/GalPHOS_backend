package Services

import cats.effect.IO
import cats.implicits.*
import Database.{DatabaseManager, SqlParameter}
import Models.*
import Config.Constants
import io.circe.*
import io.circe.syntax.*
import org.slf4j.LoggerFactory
import java.util.UUID
import java.time.LocalDateTime

trait UserService {
  def findUserByUsername(username: String): IO[Option[User]]
  def findUserById(userId: String): IO[Option[User]]
  def createUser(registerReq: RegisterRequest, hashedPassword: String): IO[String]
  def updateLastLoginTime(userId: String): IO[Unit]
  def getUserInfo(username: String, role: String): IO[UserInfo]
}

class UserServiceImpl extends UserService {
  private val logger = LoggerFactory.getLogger("UserService")
  private val schemaName = "authservice"

  override def findUserByUsername(username: String): IO[Option[User]] = {
    val sql = s"""
      SELECT user_id, username, password_hash, salt, role, status, phone, province_id, school_id, avatar_url, created_at, updated_at
      FROM $schemaName.user_table
      WHERE username = ?
    """.stripMargin
    
    val params = List(SqlParameter("String", username))
    
    for {
      resultOpt <- DatabaseManager.executeQueryOptional(sql, params)
      user <- resultOpt match {
        case Some(json) => IO.pure(Some(jsonToUser(json)))
        case None => IO.pure(None)
      }
    } yield user
  }

  override def findUserById(userId: String): IO[Option[User]] = {
    val sql = s"""
      SELECT user_id, username, password_hash, salt, role, status, phone, province_id, school_id, avatar_url, created_at, updated_at
      FROM $schemaName.user_table
      WHERE user_id = ?
    """.stripMargin
    
    val params = List(SqlParameter("String", userId))
    
    for {
      resultOpt <- DatabaseManager.executeQueryOptional(sql, params)
      user <- resultOpt match {
        case Some(json) => IO.pure(Some(jsonToUser(json)))
        case None => IO.pure(None)
      }
    } yield user
  }

  override def createUser(registerReq: RegisterRequest, hashedPassword: String): IO[String] = {
    val userId = UUID.randomUUID().toString
    val salt = Constants.SALT_VALUE
    val role = mapRoleStringToEnum(registerReq.role)
    val status = UserStatus.Pending.value
    
    val sql = s"""
      INSERT INTO $schemaName.user_table (user_id, username, phone, password_hash, salt, role, status, province_id, school_id, created_at, updated_at)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
    """.stripMargin
    
    val params = List(
      SqlParameter("String", userId),
      SqlParameter("String", registerReq.username),
      SqlParameter("String", registerReq.phone),
      SqlParameter("String", hashedPassword),
      SqlParameter("String", salt),
      SqlParameter("String", role),
      SqlParameter("String", status),
      SqlParameter("String", registerReq.province.orNull),
      SqlParameter("String", registerReq.school.orNull)
    )
    
    for {
      _ <- DatabaseManager.executeUpdate(sql, params)
      _ = logger.info(s"创建用户成功: ${registerReq.username}")
    } yield userId
  }

  override def updateLastLoginTime(userId: String): IO[Unit] = {
    val sql = s"""
      UPDATE $schemaName.user_table 
      SET updated_at = NOW() 
      WHERE user_id = ?
    """.stripMargin
    
    val params = List(SqlParameter("String", userId))
    
    for {
      _ <- DatabaseManager.executeUpdate(sql, params)
      _ = logger.info(s"更新用户最后登录时间: $userId")
    } yield ()
  }

  override def getUserInfo(username: String, role: String): IO[UserInfo] = {
    val sql = s"""
      SELECT u.username, u.role, p.name as province_name, s.name as school_name, u.avatar_url
      FROM $schemaName.user_table u
      LEFT JOIN $schemaName.province_table p ON u.province_id = p.province_id
      LEFT JOIN $schemaName.school_table s ON u.school_id = s.school_id
      WHERE u.username = ?
    """.stripMargin
    
    val params = List(SqlParameter("String", username))
    
    for {
      resultOpt <- DatabaseManager.executeQueryOptional(sql, params)
      userInfo <- resultOpt match {
        case Some(json) => 
          val dbRole = DatabaseManager.decodeFieldUnsafe[String](json, "role")
          // 转换角色为API要求的小写英文格式
          val apiRole = dbRole match {
            case "学生角色" => "student"
            case "教练角色" => "coach"
            case "阅卷者角色" => "grader"
            case _ => "student" // 默认值
          }
          IO.pure(UserInfo(
            username = DatabaseManager.decodeFieldUnsafe[String](json, "username"),
            role = Some(apiRole),
            province = json.hcursor.downField("province_name").as[String].toOption,
            school = json.hcursor.downField("school_name").as[String].toOption,
            avatar = json.hcursor.downField("avatar_url").as[String].toOption
          ))
        case None => 
          IO.raiseError(new RuntimeException(s"用户不存在: $username"))
      }
    } yield userInfo
  }

  private def jsonToUser(json: Json): User = {
    User(
      userID = DatabaseManager.decodeFieldUnsafe[String](json, "user_id"),
      username = DatabaseManager.decodeFieldUnsafe[String](json, "username"),
      passwordHash = DatabaseManager.decodeFieldUnsafe[String](json, "password_hash"),
      salt = DatabaseManager.decodeFieldUnsafe[String](json, "salt"),
      role = UserRole.fromString(DatabaseManager.decodeFieldUnsafe[String](json, "role")),
      status = UserStatus.fromString(DatabaseManager.decodeFieldUnsafe[String](json, "status")),
      phone = json.hcursor.downField("phone").as[String].toOption,
      provinceId = json.hcursor.downField("province_id").as[String].toOption,
      schoolId = json.hcursor.downField("school_id").as[String].toOption,
      avatarUrl = json.hcursor.downField("avatar_url").as[String].toOption
    )
  }

  private def mapRoleStringToEnum(role: String): String = role.toLowerCase match {
    case "student" => UserRole.Student.value
    case "coach" => UserRole.Coach.value
    case "grader" => UserRole.Grader.value
    case _ => throw new IllegalArgumentException(s"未知角色: $role")
  }
}
