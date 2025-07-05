package Process

import cats.effect.IO
import cats.implicits.*
import Database.DatabaseUtils
import org.slf4j.LoggerFactory
import scala.io.Source
import java.io.File

object Init {
  private val logger = LoggerFactory.getLogger("Init")

  def initializeDatabase(): IO[Unit] = {
    logger.info("开始初始化数据库...")
    
    val sqlFile = new File("init_database.sql")
    if (!sqlFile.exists()) {
      logger.warn("数据库初始化脚本不存在，跳过数据库初始化")
      return IO.unit
    }

    val sqlContent = Source.fromFile(sqlFile).getLines().mkString("\n")
    val sqlStatements = sqlContent.split(";").map(_.trim).filter(_.nonEmpty)

    sqlStatements.toList.traverse { statement =>
      DatabaseUtils.executeUpdate(statement).handleErrorWith { error =>
        logger.warn(s"执行SQL语句失败: $statement", error)
        IO.pure(0)
      }
    }.map { results =>
      logger.info(s"数据库初始化完成，执行了 ${results.length} 个SQL语句")
    }.handleErrorWith { error =>
      logger.error("数据库初始化失败", error)
      IO.raiseError(error)
    }
  }

  def createDefaultData(): IO[Unit] = {
    logger.info("创建默认数据...")
    
    val defaultExamSql = """
      INSERT INTO exams (title, description, start_time, end_time, status, created_by, duration, total_questions, max_score, subject)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT DO NOTHING
    """

    val defaultExams = List(
      ("物理竞赛模拟考试", "2024年物理竞赛模拟考试", "2024-04-01 09:00:00", "2024-04-01 12:00:00", "draft", "admin", 180, 20, 100.0, "物理"),
      ("数学竞赛模拟考试", "2024年数学竞赛模拟考试", "2024-04-02 09:00:00", "2024-04-02 11:00:00", "draft", "admin", 120, 15, 75.0, "数学"),
      ("化学竞赛模拟考试", "2024年化学竞赛模拟考试", "2024-04-03 09:00:00", "2024-04-03 11:30:00", "draft", "admin", 150, 18, 90.0, "化学")
    )

    defaultExams.traverse { exam =>
      val params = List(exam._1, exam._2, exam._3, exam._4, exam._5, exam._6, exam._7, exam._8, exam._9, exam._10)
      DatabaseUtils.executeUpdate(defaultExamSql, params).handleErrorWith { error =>
        logger.warn(s"创建默认考试失败: ${exam._1}", error)
        IO.pure(0)
      }
    }.map { results =>
      logger.info(s"默认数据创建完成，创建了 ${results.count(_ > 0)} 个考试")
    }.handleErrorWith { error =>
      logger.error("创建默认数据失败", error)
      IO.raiseError(error)
    }
  }

  def checkDatabaseConnection(): IO[Boolean] = {
    logger.info("检查数据库连接...")
    
    val testSql = "SELECT 1"
    DatabaseUtils.executeQuery(testSql)(rs => rs.getInt(1)).map { results =>
      val connected = results.nonEmpty && results.head == 1
      if (connected) {
        logger.info("数据库连接正常")
      } else {
        logger.error("数据库连接异常")
      }
      connected
    }.handleErrorWith { error =>
      logger.error("数据库连接检查失败", error)
      IO.pure(false)
    }
  }

  def performStartupTasks(): IO[Unit] = {
    for {
      _ <- checkDatabaseConnection()
      _ <- initializeDatabase()
      _ <- createDefaultData()
      _ <- IO(logger.info("所有启动任务完成"))
    } yield ()
  }
}
