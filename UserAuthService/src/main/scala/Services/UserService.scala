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

class UserServiceImpl(regionServiceClient: RegionServiceClient) extends UserService {
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
    
    // 如果用户提供了省份和学校信息，需要先验证
    val validationIO = (registerReq.province, registerReq.school) match {
      case (Some(provinceId), Some(schoolId)) =>
        logger.info(s"验证省份和学校: provinceId=$provinceId, schoolId=$schoolId")
        regionServiceClient.validateProvinceAndSchool(provinceId, schoolId).flatMap {
          case Right(true) =>
            logger.info("省份和学校验证通过")
            IO.unit
          case Right(false) =>
            IO.raiseError(new RuntimeException("省份和学校验证失败"))
          case Left(error) =>
            logger.error(s"省份和学校验证失败: $error")
            IO.raiseError(new RuntimeException(s"省份和学校验证失败: $error"))
        }
      case (None, None) =>
        // 没有提供省份学校信息（如阅卷员），跳过验证
        logger.info("未提供省份学校信息，跳过验证")
        IO.unit
      case _ =>
        // 省份和学校信息不完整
        IO.raiseError(new RuntimeException("省份和学校信息必须同时提供或同时为空"))
    }
    
    for {
      _ <- validationIO  // 先进行验证
      // 验证通过后插入数据库
      sql = s"""
        INSERT INTO $schemaName.user_table (user_id, username, phone, password_hash, salt, role, status, province_id, school_id, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
      """.stripMargin
      
      params = List(
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
      
      _ = logger.info(s"创建用户参数 - userId: $userId, username: ${registerReq.username}, province: ${registerReq.province}, school: ${registerReq.school}")
      
      _ <- DatabaseManager.executeUpdate(sql, params).handleErrorWith { error =>
        logger.error(s"数据库插入失败: ${error.getMessage}", error)
        IO.raiseError(new RuntimeException(s"数据库插入失败: ${error.getMessage}"))
      }
      _ = logger.info(s"用户创建成功: $userId")
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
