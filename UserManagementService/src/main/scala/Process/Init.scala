package Process

import cats.effect.IO
import Database.{DatabaseManager, SqlParameter}
import Config.ServiceConfig
import org.slf4j.LoggerFactory

object Init {
  private val logger = LoggerFactory.getLogger("UserManagementInit")

  def initializeDatabase(): IO[Unit] = {
    for {
      _ <- IO(logger.info("开始初始化用户管理服务数据库表..."))
      _ <- createSchema()
      _ <- createUserManagementTables()
      _ <- insertInitialData()
      _ <- IO(logger.info("用户管理服务数据库初始化完成"))
    } yield ()
  }

  private def createSchema(): IO[Unit] = {
    val sql = "CREATE SCHEMA IF NOT EXISTS authservice"
    DatabaseManager.executeUpdate(sql, List.empty).map(_ => ())
  }

  private def insertInitialData(): IO[Unit] = {
    // 插入一些基础的省份和学校数据用于测试
    val insertSql = List(
      """INSERT INTO authservice.province_table (province_id, name) VALUES ('beijing', '北京市') ON CONFLICT (province_id) DO NOTHING""",
      """INSERT INTO authservice.province_table (province_id, name) VALUES ('shanghai', '上海市') ON CONFLICT (province_id) DO NOTHING""",
      """INSERT INTO authservice.school_table (school_id, province_id, name) VALUES ('tsinghua', 'beijing', '清华大学') ON CONFLICT (school_id) DO NOTHING""",
      """INSERT INTO authservice.school_table (school_id, province_id, name) VALUES ('pku', 'beijing', '北京大学') ON CONFLICT (school_id) DO NOTHING"""
    )
    
    for {
      _ <- insertSql.foldLeft(IO.unit) { (acc, sql) =>
        acc.flatMap(_ => DatabaseManager.executeUpdate(sql, List.empty).map(_ => ()))
      }
    } yield ()
  }

  private def createUserManagementTables(): IO[Unit] = {
    val createTablesSql = List(
      // 先创建基础的省份表（如果不存在）
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
      
      // 用户表（如果认证服务没有创建）
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
      
      // 用户注册申请表
      """CREATE TABLE IF NOT EXISTS authservice.user_registration_requests (
          id VARCHAR NOT NULL PRIMARY KEY,
          username VARCHAR(100) NOT NULL,
          password_hash VARCHAR(255) NOT NULL,
          salt VARCHAR(255) NOT NULL,
          province VARCHAR(100) NOT NULL,
          school VARCHAR(100) NOT NULL,
          coach_username VARCHAR(100),
          reason TEXT,
          status VARCHAR(20) DEFAULT 'PENDING',
          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
          reviewed_by VARCHAR(100),
          reviewed_at TIMESTAMP,
          review_note TEXT,
          CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
      )""",
      
      // 教练管理的学生表
      """CREATE TABLE IF NOT EXISTS authservice.coach_managed_students (
          id VARCHAR NOT NULL PRIMARY KEY,
          coach_id VARCHAR NOT NULL,
          student_id VARCHAR NOT NULL,
          student_username VARCHAR(100) NOT NULL,
          student_name VARCHAR(100),
          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
          UNIQUE(coach_id, student_username)
      )""",
      
      // 用户状态变更日志表
      """CREATE TABLE IF NOT EXISTS authservice.user_status_change_log (
          id VARCHAR NOT NULL PRIMARY KEY,
          user_id VARCHAR NOT NULL,
          old_status VARCHAR(20),
          new_status VARCHAR(20) NOT NULL,
          changed_by VARCHAR(100) NOT NULL,
          reason TEXT,
          changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      )""",
      
      // 区域变更申请表
      """CREATE TABLE IF NOT EXISTS authservice.region_change_requests (
          id VARCHAR NOT NULL PRIMARY KEY,
          user_id VARCHAR NOT NULL,
          username VARCHAR(100) NOT NULL,
          current_province VARCHAR(100) NOT NULL,
          current_school VARCHAR(100) NOT NULL,
          requested_province VARCHAR(100) NOT NULL,
          requested_school VARCHAR(100) NOT NULL,
          reason TEXT,
          status VARCHAR(20) DEFAULT 'PENDING',
          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
          reviewed_by VARCHAR(100),
          reviewed_at TIMESTAMP,
          review_note TEXT,
          CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
      )"""
    )
    
    // 创建索引
    val createIndexesSql = List(
      "CREATE INDEX IF NOT EXISTS idx_user_table_username ON authservice.user_table(username)",
      "CREATE INDEX IF NOT EXISTS idx_user_table_role ON authservice.user_table(role)",
      "CREATE INDEX IF NOT EXISTS idx_user_table_status ON authservice.user_table(status)",
      "CREATE INDEX IF NOT EXISTS idx_user_registration_requests_status ON authservice.user_registration_requests(status)",
      "CREATE INDEX IF NOT EXISTS idx_user_registration_requests_username ON authservice.user_registration_requests(username)",
      "CREATE INDEX IF NOT EXISTS idx_coach_managed_students_coach_id ON authservice.coach_managed_students(coach_id)",
      "CREATE INDEX IF NOT EXISTS idx_coach_managed_students_student_username ON authservice.coach_managed_students(student_username)",
      "CREATE INDEX IF NOT EXISTS idx_user_status_change_log_user_id ON authservice.user_status_change_log(user_id)",
      "CREATE INDEX IF NOT EXISTS idx_region_change_requests_user_id ON authservice.region_change_requests(user_id)",
      "CREATE INDEX IF NOT EXISTS idx_region_change_requests_status ON authservice.region_change_requests(status)"
    )
    
    val allSql = createTablesSql ++ createIndexesSql
    
    for {
      _ <- allSql.foldLeft(IO.unit) { (acc, sql) =>
        acc.flatMap(_ => DatabaseManager.executeUpdate(sql, List.empty).map(_ => ()))
      }
    } yield ()
  }
}
