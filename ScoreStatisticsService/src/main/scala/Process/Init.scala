package Process

import cats.effect.IO
import Database.{DatabaseManager, SqlParameter}
import Config.ServerConfig
import Services.*
import Controllers.ScoreStatisticsController
import org.slf4j.LoggerFactory
import java.nio.file.{Files, Paths}
import java.util.UUID

object Init {
  private val logger = LoggerFactory.getLogger("Init")

  def init(config: ServerConfig): IO[ScoreStatisticsController] = {
    for {
      _ <- IO(logger.info("开始初始化成绩统计服务..."))
      
      // 初始化数据库连接
      _ <- DatabaseManager.initialize(config.toDatabaseConfig)
      _ <- IO(logger.info("数据库连接池初始化完成"))
      
      // 执行数据库初始化
      _ <- initializeDatabase()
      _ <- IO(logger.info("数据库表初始化完成"))
      
      // 创建服务实例
      scoreService = new ScoreStatisticsService()
      
      // 创建控制器
      scoreController = new ScoreStatisticsController(scoreService)
      
      _ <- IO(logger.info("成绩统计服务初始化完成"))
    } yield scoreController
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
    val createSchemaSql = "CREATE SCHEMA IF NOT EXISTS scorestatistics"
    DatabaseManager.executeUpdate(createSchemaSql).map(_ => ())
  }

  private def createTables(): IO[Unit] = {
    val createTablesSql = """
      -- 考试成绩表
      CREATE TABLE IF NOT EXISTS exam_scores (
          id SERIAL PRIMARY KEY,
          exam_id INTEGER NOT NULL,
          student_id INTEGER NOT NULL,
          total_score DECIMAL(5,2) DEFAULT 0.00,
          question_scores JSONB DEFAULT '{}',
          rank_position INTEGER DEFAULT 0,
          percentile DECIMAL(5,2) DEFAULT 0.00,
          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
          updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
          UNIQUE(exam_id, student_id)
      );

      -- 考试统计表
      CREATE TABLE IF NOT EXISTS exam_statistics (
          id SERIAL PRIMARY KEY,
          exam_id INTEGER NOT NULL UNIQUE,
          total_submissions INTEGER DEFAULT 0,
          average_score DECIMAL(5,2) DEFAULT 0.00,
          highest_score DECIMAL(5,2) DEFAULT 0.00,
          lowest_score DECIMAL(5,2) DEFAULT 0.00,
          median_score DECIMAL(5,2) DEFAULT 0.00,
          pass_rate DECIMAL(5,2) DEFAULT 0.00,
          difficulty_analysis JSONB DEFAULT '{}',
          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
          updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      );

      -- 学生统计表
      CREATE TABLE IF NOT EXISTS student_statistics (
          id SERIAL PRIMARY KEY,
          student_id INTEGER NOT NULL UNIQUE,
          total_exams INTEGER DEFAULT 0,
          average_score DECIMAL(5,2) DEFAULT 0.00,
          best_score DECIMAL(5,2) DEFAULT 0.00,
          worst_score DECIMAL(5,2) DEFAULT 0.00,
          improvement_trend DECIMAL(5,2) DEFAULT 0.00,
          strong_subjects JSONB DEFAULT '[]',
          weak_subjects JSONB DEFAULT '[]',
          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
          updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      );

      -- 教练统计表
      CREATE TABLE IF NOT EXISTS coach_statistics (
          id SERIAL PRIMARY KEY,
          coach_id INTEGER NOT NULL UNIQUE,
          total_students INTEGER DEFAULT 0,
          total_exams INTEGER DEFAULT 0,
          average_student_score DECIMAL(5,2) DEFAULT 0.00,
          best_student_score DECIMAL(5,2) DEFAULT 0.00,
          class_performance JSONB DEFAULT '{}',
          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
          updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      );

      -- 阅卷员统计表
      CREATE TABLE IF NOT EXISTS grader_statistics (
          id SERIAL PRIMARY KEY,
          grader_id INTEGER NOT NULL UNIQUE,
          total_graded INTEGER DEFAULT 0,
          grading_accuracy DECIMAL(5,2) DEFAULT 0.00,
          grading_speed DECIMAL(5,2) DEFAULT 0.00,
          grading_history JSONB DEFAULT '[]',
          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
          updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      );

      -- 系统统计表
      CREATE TABLE IF NOT EXISTS system_statistics (
          id SERIAL PRIMARY KEY,
          stat_date DATE NOT NULL UNIQUE,
          total_users INTEGER DEFAULT 0,
          total_students INTEGER DEFAULT 0,
          total_coaches INTEGER DEFAULT 0,
          total_graders INTEGER DEFAULT 0,
          total_exams INTEGER DEFAULT 0,
          total_submissions INTEGER DEFAULT 0,
          system_metrics JSONB DEFAULT '{}',
          created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      );
    """
    
    DatabaseManager.executeUpdate(createTablesSql).map(_ => ())
  }

  private def insertInitialData(): IO[Unit] = {
    val insertSql = """
      INSERT INTO system_statistics (stat_date, total_users, total_students, total_coaches, total_graders, total_exams, total_submissions)
      VALUES (CURRENT_DATE, 100, 80, 10, 5, 20, 150) ON CONFLICT (stat_date) DO NOTHING;
      
      INSERT INTO student_statistics (student_id, total_exams, average_score, best_score, worst_score)
      VALUES (1, 5, 85.5, 95.0, 70.0) ON CONFLICT (student_id) DO NOTHING;
      
      INSERT INTO coach_statistics (coach_id, total_students, total_exams, average_student_score, best_student_score)
      VALUES (1, 10, 8, 82.3, 95.0) ON CONFLICT (coach_id) DO NOTHING;
      
      INSERT INTO grader_statistics (grader_id, total_graded, grading_accuracy, grading_speed)
      VALUES (1, 150, 95.5, 12.5) ON CONFLICT (grader_id) DO NOTHING;
      
      INSERT INTO exam_scores (exam_id, student_id, total_score, rank_position, percentile)
      VALUES (1, 1, 85.5, 5, 75.0) ON CONFLICT (exam_id, student_id) DO NOTHING;
    """
    
    DatabaseManager.executeUpdate(insertSql).map(_ => ())
  }
}
