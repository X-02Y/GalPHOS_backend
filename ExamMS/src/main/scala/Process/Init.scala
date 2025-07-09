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
    logger.info("跳过创建默认数据（已移除模拟数据）...")
    IO.unit
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
