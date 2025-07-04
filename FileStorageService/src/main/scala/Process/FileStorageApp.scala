package Process

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import scala.concurrent.ExecutionContext
import scala.util.{Success, Failure}
import scala.io.StdIn
import java.util.concurrent.Executors

import Config.ServerConfig
import Database.FileStorageDB
import Services.FileStorageService
import Controllers.FileStorageController

object FileStorageApp extends App {
  
  println("正在启动 FileStorageService...")
  
  // 创建ActorSystem
  implicit val system: ActorSystem = ActorSystem("file-storage-system")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  implicit val executionContext: ExecutionContext = system.dispatcher
  
  try {
    // 加载配置
    val config = ServerConfig.loadConfig()
    println(s"配置加载成功，服务将在 ${config.serverIP}:${config.serverPort} 启动")
    
    // 初始化数据库
    val database = new FileStorageDB(config)
    
    // 测试数据库连接
    database.testConnection().onComplete {
      case Success(connected) =>
        if (connected) {
          println("数据库连接测试成功")
        } else {
          println("警告：数据库连接测试失败")
        }
      case Failure(ex) =>
        println(s"数据库连接测试异常: ${ex.getMessage}")
    }
    
    // 初始化服务
    val fileStorageService = new FileStorageService(database, config)
    
    // 初始化控制器
    val controller = new FileStorageController(fileStorageService, config)
    
    // 启动服务器
    val serverBinding = controller.startServer()
    
    serverBinding.onComplete {
      case Success(binding) =>
        println(s"FileStorageService 成功启动！")
        println(s"服务地址: http://${binding.localAddress.getHostString}:${binding.localAddress.getPort}")
        println(s"健康检查: http://${binding.localAddress.getHostString}:${binding.localAddress.getPort}/health")
        println("\n外部API端点 (前端调用):")
        println("  GET  /api/student/files/download/{fileId}     - 学生文件下载")
        println("  GET  /api/grader/images?url={imageUrl}        - 阅卷员图片代理")
        println("  GET  /api/coach/exams/{examId}/ranking        - 教练考试排名导出")
        println("  POST /api/coach/exams/{examId}/scores/export  - 教练成绩导出")
        println("  GET  /api/coach/exams/{examId}/scores/statistics - 教练成绩统计")
        println("  GET  /api/coach/dashboard/stats               - 教练仪表盘统计")
        println("  GET  /api/admin/dashboard/stats               - 管理员仪表盘统计")
        println("  GET  /health                              - 健康检查")
        println("\n内部API端点 (微服务调用):")
        println("  POST /internal/upload                         - 文件上传")
        println("  POST /internal/download                       - 文件下载")
        println("  POST /internal/delete                         - 文件删除")
        println("  POST /internal/batch                          - 批量操作")
        println("  GET  /internal/info/{fileId}                  - 文件信息查询")
        println("  GET  /internal/health                         - 内部健康检查")
        println("\n配置信息:")
        println("文件存储路径: " + config.fileStoragePath)
        println("最大文件大小: " + config.maxFileSize + " bytes")
        println("支持文件类型: " + config.allowedFileTypes.mkString(", "))
        println("\n微服务通信说明:")
        println("- 前端文件上传请求会先到达对应的业务微服务")
        println("- 业务微服务调用 /internal/* 接口与FileStorageService通信")
        println("- 支持的业务微服务: ExamManagement(3003), Submission(3004), Grading(3005), UserManagement(3002)")
        println("\n按 ENTER 键停止服务...")
        
        // 启动清理任务
        startCleanupTasks(fileStorageService)
        
      case Failure(ex) =>
        println(s"服务启动失败: ${ex.getMessage}")
        ex.printStackTrace()
        system.terminate()
    }
    
    // 等待用户输入停止服务
    StdIn.readLine()
    
  } catch {
    case ex: Exception =>
      println(s"应用程序启动失败: ${ex.getMessage}")
      ex.printStackTrace()
  } finally {
    println("正在关闭 FileStorageService...")
    system.terminate()
  }
  
  // 启动定时清理任务
  private def startCleanupTasks(fileStorageService: FileStorageService): Unit = {
    val cleanupExecutor = Executors.newScheduledThreadPool(2)
    
    // 每天清理临时文件
    cleanupExecutor.scheduleAtFixedRate(
      new Runnable {
        override def run(): Unit = {
          println("执行临时文件清理任务...")
          fileStorageService.cleanupTempFiles().onComplete {
            case Success(_) => println("临时文件清理完成")
            case Failure(ex) => println(s"临时文件清理失败: ${ex.getMessage}")
          }
        }
      },
      24, // 初始延迟24小时
      24, // 每24小时执行一次
      java.util.concurrent.TimeUnit.HOURS
    )
    
    // 每小时更新统计数据
    cleanupExecutor.scheduleAtFixedRate(
      new Runnable {
        override def run(): Unit = {
          println("更新统计数据...")
          fileStorageService.getDashboardStats().onComplete {
            case Success(Right(stats)) =>
              println(s"统计数据更新完成: ${stats.totalFiles} 个文件, ${stats.totalSize} bytes")
            case Success(Left(error)) =>
              println(s"统计数据更新失败: $error")
            case Failure(ex) =>
              println(s"统计数据更新异常: ${ex.getMessage}")
          }
        }
      },
      1, // 初始延迟1小时
      1, // 每1小时执行一次
      java.util.concurrent.TimeUnit.HOURS
    )
    
    // 注册关闭钩子
    sys.addShutdownHook {
      cleanupExecutor.shutdown()
      println("清理任务调度器已关闭")
    }
  }
}
