package Process

import cats.effect.IO
import Database.{DatabaseManager, SqlParameter}
import Config.{ServerConfig, Constants}
import Services.*
import Controllers.AuthController
import org.slf4j.LoggerFactory
import java.nio.file.{Files, Paths}
import java.util.UUID

object Init {
  private val logger = LoggerFactory.getLogger("Init")

  def init(config: ServerConfig): IO[AuthController] = {
    for {
      _ <- IO(logger.info("开始初始化用户认证服务..."))
      
      // 初始化数据库连接
      _ <- DatabaseManager.initialize(config.toDatabaseConfig)
      _ <- IO(logger.info("数据库连接池初始化完成"))
      
      // 执行数据库初始化
      _ <- initializeDatabase()
      _ <- IO(logger.info("数据库表初始化完成"))
      
      // 创建服务实例
      regionServiceClient = new RegionServiceClientImpl("http://localhost:3007")  // RegionMS 的地址
      userService = new UserServiceImpl(regionServiceClient)
      adminService = new AdminServiceImpl()
      tokenService = new TokenServiceImpl(config)
      authService = new AuthService(userService, adminService, tokenService, regionServiceClient)
      
      // 创建控制器
      authController = new AuthController(authService)
      
      _ <- IO(logger.info("用户认证服务初始化完成"))
    } yield authController
  }

  private def initializeDatabase(): IO[Unit] = {
    for {
      _ <- IO(logger.info("开始初始化数据库表..."))
      _ <- createSchema()
      _ <- createTables()
      _ <- insertInitialData()
      _ <- IO(logger.info("数据库表初始化完成"))
    } yield ()
  }

  private def createSchema(): IO[Unit] = {
    val sql = "CREATE SCHEMA IF NOT EXISTS authservice"
    DatabaseManager.executeUpdate(sql, List.empty).map(_ => ())
  }

  private def createTables(): IO[Unit] = {
    val createTablesSql = List(
      // 省份表
      """CREATE TABLE IF NOT EXISTS authservice.province_table (
          province_id VARCHAR NOT NULL PRIMARY KEY,
          name TEXT NOT NULL,
          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      )""",
      
      // 学校表  
      """CREATE TABLE IF NOT EXISTS authservice.school_table (
          school_id VARCHAR NOT NULL PRIMARY KEY,
          province_id VARCHAR NOT NULL REFERENCES authservice.province_table(province_id),
          name TEXT NOT NULL,
          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      )""",
      
      // 用户表
      """CREATE TABLE IF NOT EXISTS authservice.user_table (
          user_id VARCHAR NOT NULL PRIMARY KEY,
          username TEXT NOT NULL UNIQUE,
          phone VARCHAR(20),
          password_hash TEXT NOT NULL,
          salt TEXT NOT NULL,
          role TEXT NOT NULL,
          status TEXT NOT NULL DEFAULT 'PENDING',
          province_id VARCHAR REFERENCES authservice.province_table(province_id),
          school_id VARCHAR REFERENCES authservice.school_table(school_id),
          avatar_url TEXT,
          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
          updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      )""",
      
      // 管理员表
      """CREATE TABLE IF NOT EXISTS authservice.admin_table (
          admin_id VARCHAR NOT NULL PRIMARY KEY,
          username TEXT NOT NULL UNIQUE,
          password_hash TEXT NOT NULL,
          salt TEXT NOT NULL,
          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      )""",
      
      // Token黑名单表
      """CREATE TABLE IF NOT EXISTS authservice.token_blacklist_table (
          token_id VARCHAR NOT NULL PRIMARY KEY,
          token_hash TEXT NOT NULL UNIQUE,
          expired_at TIMESTAMP NOT NULL,
          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      )""",
      
      // 登录日志表
      """CREATE TABLE IF NOT EXISTS authservice.login_log_table (
          log_id VARCHAR NOT NULL PRIMARY KEY,
          user_id VARCHAR,
          admin_id VARCHAR,
          login_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
          ip_address INET,
          user_agent TEXT,
          login_result TEXT NOT NULL
      )"""
    )
    
    // 创建索引
    val createIndexesSql = List(
      "CREATE INDEX IF NOT EXISTS idx_user_table_username ON authservice.user_table(username)",
      "CREATE INDEX IF NOT EXISTS idx_user_table_role ON authservice.user_table(role)",
      "CREATE INDEX IF NOT EXISTS idx_user_table_status ON authservice.user_table(status)",
      "CREATE INDEX IF NOT EXISTS idx_admin_table_username ON authservice.admin_table(username)",
      "CREATE INDEX IF NOT EXISTS idx_token_blacklist_expired_at ON authservice.token_blacklist_table(expired_at)",
      "CREATE INDEX IF NOT EXISTS idx_login_log_user_id ON authservice.login_log_table(user_id)",
      "CREATE INDEX IF NOT EXISTS idx_login_log_admin_id ON authservice.login_log_table(admin_id)",
      "CREATE INDEX IF NOT EXISTS idx_login_log_time ON authservice.login_log_table(login_time)"
    )
    
    val allSql = createTablesSql ++ createIndexesSql
    
    for {
      _ <- allSql.foldLeft(IO.unit) { (acc, sql) =>
        acc.flatMap(_ => DatabaseManager.executeUpdate(sql, List.empty).map(_ => ()))
      }
    } yield ()
  }

  private def insertInitialData(): IO[Unit] = {
    for {
      _ <- insertDefaultAdmin()
      // 移除模拟数据初始化，只保留管理员
    } yield ()
  }

  private def insertDefaultAdmin(): IO[Unit] = {
    // 使用前端计算的哈希值 (admin123 + 盐值的SHA-256哈希)
    // 前端提供的正确哈希值
    val adminHash = "0bb6a396f8c6c7f133f426e8ad6931b91f1d208a265acb66900ece3ca082aa66"
    // 使用固定的UUID作为默认管理员ID
    val adminId = "550e8400-e29b-41d4-a716-446655440000"
    
    for {
      // 先清理可能存在的无效UUID记录
      _ <- DatabaseManager.executeUpdate("DELETE FROM authservice.admin_table WHERE admin_id = 'admin-001'", List.empty)
      
      // 插入或更新管理员记录
      _ <- DatabaseManager.executeUpdate("""
        INSERT INTO authservice.admin_table (admin_id, username, password_hash, salt) VALUES (?, ?, ?, ?) 
        ON CONFLICT (username) DO UPDATE SET 
          admin_id = EXCLUDED.admin_id,
          password_hash = EXCLUDED.password_hash,
          salt = EXCLUDED.salt
      """, List(
        SqlParameter("String", adminId),
        SqlParameter("String", "admin"),
        SqlParameter("String", adminHash),
        SqlParameter("String", Constants.SALT_VALUE)
      ))
    } yield ()
  }
}
