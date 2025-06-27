package Services

import Config.{ServerConfig, Constants}
import cats.effect.IO
import Database.{DatabaseManager, SqlParameter}
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}
import io.circe.*
import io.circe.syntax.*
import io.circe.parser.*
import org.slf4j.{Logger, LoggerFactory}
import java.time.{Clock, Instant}
import java.util.UUID
import scala.util.{Try, Success, Failure}

trait TokenService {
  def generateToken(userId: UUID, isAdmin: Boolean = false): IO[String]
  def validateToken(token: String): IO[Either[String, (UUID, Boolean)]]
  def addTokenToBlacklist(token: String): IO[Unit]
  def isTokenBlacklisted(token: String): IO[Boolean]
}

class TokenServiceImpl(config: ServerConfig) extends TokenService {
  private val logger = LoggerFactory.getLogger("TokenService")
  private val schemaName = "authservice"
  private val jwtSecret = Constants.JWT_SECRET
  private val expirationHours = Constants.JWT_EXPIRATION_HOURS

  override def generateToken(userId: UUID, isAdmin: Boolean = false): IO[String] = IO {
    val now = Instant.now(Clock.systemUTC)
    val expiration = now.plusSeconds(expirationHours * 3600)
    
    logger.info(s"生成Token: userId=$userId, isAdmin=$isAdmin")
    
    // 创建JWT标准的claims
    val claim = JwtClaim(
      subject = Some(userId.toString),
      expiration = Some(expiration.getEpochSecond),
      issuedAt = Some(now.getEpochSecond)
    ) + ("isAdmin", isAdmin.toString)
    
    logger.info(s"Token claim content: ${claim.content}")
    
    val token = Jwt.encode(claim, jwtSecret, JwtAlgorithm.HS256)
    logger.info(s"生成的Token: ${token.take(50)}...")
    token
  }

  override def validateToken(token: String): IO[Either[String, (UUID, Boolean)]] = {
    for {
      _ <- IO(logger.info(s"开始验证Token: ${token.take(20)}..."))
      isBlacklisted <- isTokenBlacklisted(token)
      result <- if (isBlacklisted) {
        IO(logger.warn("Token已被列入黑名单"))
        IO.pure(Left("Token已被撤销"))
      } else {
        IO {
          logger.info("Token未在黑名单中，开始解码...")
          Jwt.decode(token, jwtSecret, Seq(JwtAlgorithm.HS256)) match {
            case Success(decoded) =>
              logger.info(s"JWT解码成功，内容: ${decoded.content}")
              
              // 首先尝试从JWT标准字段中获取subject
              decoded.subject match {
                case Some(userIdStr) =>
                  logger.info(s"从JWT标准subject字段提取userId成功: $userIdStr")
                  // 解析自定义字段isAdmin
                  parse(decoded.content) match {
                    case Right(json) =>
                      val cursor = json.hcursor
                      cursor.downField("isAdmin").as[String] match {
                        case Right(isAdminStr) =>
                          logger.info(s"提取isAdmin字段成功: $isAdminStr")
                          val isAdmin = isAdminStr.toBoolean
                          Try(UUID.fromString(userIdStr)).toEither.left.map(_ => "Invalid user ID format") match {
                            case Right(userId) =>
                              logger.info(s"UUID转换成功: $userId")
                              Right((userId, isAdmin))
                            case Left(error) =>
                              logger.error(s"UUID转换失败: $error")
                              Left(error)
                          }
                        case Left(decodingError) =>
                          logger.error(s"提取isAdmin字段失败: ${decodingError.getMessage}")
                          Left(s"Token格式错误: 缺少isAdmin字段")
                      }
                    case Left(parsingError) =>
                      logger.error(s"JSON解析失败: ${parsingError.message}")
                      Left(s"Token内容解析失败: ${parsingError.message}")
                  }
                case None =>
                  logger.error("JWT token中缺少subject字段")
                  Left("Token格式错误: 缺少subject字段")
              }
            case Failure(jwtException) =>
              logger.error(s"JWT解码失败: ${jwtException.getMessage}")
              Left(s"Token验证失败: ${jwtException.getMessage}")
          }
        }
      }
    } yield result
  }

  override def addTokenToBlacklist(token: String): IO[Unit] = {
    val sql = s"""
      INSERT INTO $schemaName.token_blacklist_table (token_id, token_hash, expired_at)
      VALUES (?, ?, NOW() + INTERVAL '$expirationHours hours')
      ON CONFLICT (token_hash) DO NOTHING
    """.stripMargin
    
    val tokenId = UUID.randomUUID().toString
    val params = List(
      SqlParameter("String", tokenId),
      SqlParameter("String", token)
    )
    
    for {
      _ <- DatabaseManager.executeUpdate(sql, params)
      _ = logger.info(s"Token已添加到黑名单: ${token.take(20)}...")
    } yield ()
  }

  override def isTokenBlacklisted(token: String): IO[Boolean] = {
    val sql = s"""
      SELECT COUNT(*) as count
      FROM $schemaName.token_blacklist_table
      WHERE token_hash = ? AND expired_at > NOW()
    """.stripMargin
    
    val params = List(SqlParameter("String", token))
    
    for {
      resultOpt <- DatabaseManager.executeQueryOptional(sql, params)
      isBlacklisted <- resultOpt match {
        case Some(json) =>
          val count = DatabaseManager.decodeFieldUnsafe[Int](json, "count")
          IO.pure(count > 0)
        case None => IO.pure(false)
      }
    } yield isBlacklisted
  }
}
