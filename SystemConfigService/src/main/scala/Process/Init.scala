package Process

import cats.effect.IO
import Database.DatabaseManager
import Config.{ServerConfig, Constants}
import Services.{SystemConfigService, SystemConfigServiceImpl}
import Controllers.SystemConfigController
import org.slf4j.LoggerFactory

object Init {
  private val logger = LoggerFactory.getLogger("Init")

  def init(config: ServerConfig): IO[SystemConfigController] = {
    for {
      _ <- IO(logger.info("开始初始化系统配置服务..."))
      
      // 初始化数据库连接
      _ <- DatabaseManager.initialize(config.toDatabaseConfig)
      _ <- IO(logger.info("数据库连接池初始化完成"))
      
      // 执行数据库健康检查
      healthCheck <- DatabaseManager.healthCheck()
      _ <- if (healthCheck) {
        IO(logger.info("数据库健康检查通过"))
      } else {
        IO.raiseError(new RuntimeException("数据库健康检查失败"))
      }
      
      // 创建服务实例
      systemConfigService = new SystemConfigServiceImpl()
      
      // 创建控制器
      systemConfigController = new SystemConfigController(systemConfigService)
      
      _ <- IO(logger.info("系统配置服务初始化完成"))
    } yield systemConfigController
  }
}
