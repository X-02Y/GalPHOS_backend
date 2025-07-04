import doobie._
import doobie.implicits._
import doobie.util.transactor.Transactor
import doobie.postgres._
import doobie.postgres.implicits._
import cats.effect._
import java.time.ZonedDateTime
import java.sql.Timestamp
import java.util.Properties

// 导入我们的Meta实例
import com.galphos.systemconfig.db.DoobieMeta._
import com.galphos.systemconfig.db.DatabaseSupport._

// 测试程序
object TestZonedDateTime extends IOApp {
  // 创建一个简单的测试事务，使用正确的参数顺序和属性
  val xa: Transactor[IO] = {
    val props = new Properties()
    props.setProperty("user", "db")
    props.setProperty("password", "root")
    
    Transactor.fromDriverManager[IO](
      "org.postgresql.Driver",
      "jdbc:postgresql://localhost:5432/galphos_systemconfig",
      props,
      None  // LogHandler
    )
  }

  // 测试ZonedDateTime映射
  def testZonedDateTime: IO[Unit] = {
    val now = ZonedDateTime.now()
    
    // 测试查询
    val query = sql"SELECT CURRENT_TIMESTAMP".query[ZonedDateTime].unique
    
    // 使用 transact 将 ConnectionIO 转换为 IO
    val program = for {
      timestamp <- query.transact(xa)
      _ <- IO.println(s"数据库时间: $timestamp")
      _ <- IO.println(s"当前时间: $now")
      _ <- IO.println(s"时间差: ${timestamp.toInstant.toEpochMilli - now.toInstant.toEpochMilli} ms")
    } yield ()
    
    program
  }
  
  override def run(args: List[String]): IO[ExitCode] = 
    testZonedDateTime.as(ExitCode.Success)
}
