package Controllers

import cats.effect.IO
import cats.syntax.either.*
import cats.implicits.*
import io.circe.*
import io.circe.generic.auto.*
import io.circe.syntax.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.io.*
import org.http4s.headers.*
import org.http4s.server.Router
import org.typelevel.ci.CIString
import org.slf4j.LoggerFactory
import Services.ScoreStatisticsService
import Models.*

class ScoreStatisticsController(scoreService: ScoreStatisticsService) {
  
  private val logger = LoggerFactory.getLogger("ScoreStatisticsController")

  // 隐式转换
  implicit val entityEncoder: EntityEncoder[IO, Json] = jsonEncoderOf[Json]
  implicit val entityDecoder: EntityDecoder[IO, Json] = jsonOf[IO, Json]

  // 学生成绩相关路由
  private val studentRoutes = HttpRoutes.of[IO] {
    
    // OPTIONS 请求处理
    case OPTIONS -> _ => 
      Ok().map(_.withHeaders(Headers(
        Header.Raw(CIString("Access-Control-Allow-Origin"), "*"),
        Header.Raw(CIString("Access-Control-Allow-Methods"), "GET, POST, PUT, DELETE, OPTIONS"),
        Header.Raw(CIString("Access-Control-Allow-Headers"), "Content-Type, Authorization")
      )))
    
    // GET /api/student/exams/{examId}/score
    case GET -> Root / "student" / "exams" / IntVar(examId) / "score" => 
      val studentId = 1 // 从JWT token中获取
      scoreService.getStudentExamScore(examId, studentId).flatMap {
        case Some(score) => Ok(ApiResponse(success = true, data = Some(score)).asJson)
        case None => NotFound(ApiResponse[String](success = false, message = "成绩未找到").asJson)
      }.handleErrorWith { error =>
        logger.error(s"获取学生考试成绩失败: examId=$examId, studentId=$studentId", error)
        InternalServerError(ApiResponse[String](success = false, message = "服务器内部错误").asJson)
      }

    // GET /api/student/exams/{examId}/ranking
    case GET -> Root / "student" / "exams" / IntVar(examId) / "ranking" => 
      val studentId = 1 // 从JWT token中获取
      scoreService.getStudentExamRanking(examId, studentId).flatMap {
        case Some(ranking) => Ok(ApiResponse(success = true, data = Some(ranking)).asJson)
        case None => NotFound(ApiResponse[String](success = false, message = "排名信息未找到").asJson)
      }.handleErrorWith { error =>
        logger.error(s"获取学生考试排名失败: examId=$examId, studentId=$studentId", error)
        InternalServerError(ApiResponse[String](success = false, message = "服务器内部错误").asJson)
      }

    // GET /api/student/scores
    case GET -> Root / "student" / "scores" => 
      val studentId = 1 // 从JWT token中获取
      scoreService.getStudentScores(studentId).flatMap { scores =>
        Ok(ApiResponse(success = true, data = Some(scores)).asJson)
      }.handleErrorWith { error =>
        logger.error(s"获取学生历史成绩失败: studentId=$studentId", error)
        InternalServerError(ApiResponse[String](success = false, message = "服务器内部错误").asJson)
      }

    // GET /api/student/dashboard/stats
    case GET -> Root / "student" / "dashboard" / "stats" => 
      val studentId = 1 // 从JWT token中获取
      scoreService.getStudentDashboardStats(studentId).flatMap { stats =>
        Ok(ApiResponse(success = true, data = Some(stats)).asJson)
      }.handleErrorWith { error =>
        logger.error(s"获取学生仪表板统计失败: studentId=$studentId", error)
        InternalServerError(ApiResponse[String](success = false, message = "服务器内部错误").asJson)
      }
  }

  // 教练成绩相关路由
  private val coachRoutes = HttpRoutes.of[IO] {
    
    // GET /api/coach/grades/overview
    case GET -> Root / "coach" / "grades" / "overview" => 
      val coachId = 1 // 从JWT token中获取
      scoreService.getCoachGradesOverview(coachId).flatMap { overview =>
        Ok(ApiResponse(success = true, data = Some(overview)).asJson)
      }.handleErrorWith { error =>
        logger.error(s"获取教练成绩概览失败: coachId=$coachId", error)
        InternalServerError(ApiResponse[String](success = false, message = "服务器内部错误").asJson)
      }

    // GET /api/coach/grades/details
    case GET -> Root / "coach" / "grades" / "details" => 
      val coachId = 1 // 从JWT token中获取
      scoreService.getCoachGradesDetails(coachId).flatMap { details =>
        Ok(ApiResponse(success = true, data = Some(details)).asJson)
      }.handleErrorWith { error =>
        logger.error(s"获取教练成绩详情失败: coachId=$coachId", error)
        InternalServerError(ApiResponse[String](success = false, message = "服务器内部错误").asJson)
      }

    // GET /api/coach/students/scores
    case GET -> Root / "coach" / "students" / "scores" => 
      val coachId = 1 // 从JWT token中获取
      scoreService.getCoachStudentsScores(coachId).flatMap { scores =>
        Ok(ApiResponse(success = true, data = Some(scores)).asJson)
      }.handleErrorWith { error =>
        logger.error(s"获取教练学生成绩失败: coachId=$coachId", error)
        InternalServerError(ApiResponse[String](success = false, message = "服务器内部错误").asJson)
      }

    // GET /api/coach/students/{studentId}/exams/{examId}/score
    case GET -> Root / "coach" / "students" / IntVar(studentId) / "exams" / IntVar(examId) / "score" => 
      val coachId = 1 // 从JWT token中获取
      scoreService.getCoachStudentExamScore(coachId, studentId, examId).flatMap {
        case Some(score) => Ok(ApiResponse(success = true, data = Some(score)).asJson)
        case None => NotFound(ApiResponse[String](success = false, message = "成绩未找到").asJson)
      }.handleErrorWith { error =>
        logger.error(s"获取教练查看学生成绩失败: coachId=$coachId, studentId=$studentId, examId=$examId", error)
        InternalServerError(ApiResponse[String](success = false, message = "服务器内部错误").asJson)
      }

    // GET /api/coach/dashboard/stats
    case GET -> Root / "coach" / "dashboard" / "stats" => 
      val coachId = 1 // 从JWT token中获取
      scoreService.getCoachDashboardStats(coachId).flatMap { stats =>
        Ok(ApiResponse(success = true, data = Some(stats)).asJson)
      }.handleErrorWith { error =>
        logger.error(s"获取教练仪表板统计失败: coachId=$coachId", error)
        InternalServerError(ApiResponse[String](success = false, message = "服务器内部错误").asJson)
      }
  }

  // 阅卷员统计相关路由
  private val graderRoutes = HttpRoutes.of[IO] {
    
    // GET /api/grader/statistics
    case GET -> Root / "grader" / "statistics" => 
      val graderId = 1 // 从JWT token中获取
      scoreService.getGraderStatistics(graderId).flatMap { stats =>
        Ok(ApiResponse(success = true, data = Some(stats)).asJson)
      }.handleErrorWith { error =>
        logger.error(s"获取阅卷员统计失败: graderId=$graderId", error)
        InternalServerError(ApiResponse[String](success = false, message = "服务器内部错误").asJson)
      }

    // GET /api/grader/dashboard/stats
    case GET -> Root / "grader" / "dashboard" / "stats" => 
      val graderId = 1 // 从JWT token中获取
      scoreService.getGraderDashboardStats(graderId).flatMap { stats =>
        Ok(ApiResponse(success = true, data = Some(stats)).asJson)
      }.handleErrorWith { error =>
        logger.error(s"获取阅卷员仪表板统计失败: graderId=$graderId", error)
        InternalServerError(ApiResponse[String](success = false, message = "服务器内部错误").asJson)
      }

    // GET /api/grader/history
    case GET -> Root / "grader" / "history" => 
      val graderId = 1 // 从JWT token中获取
      scoreService.getGraderHistory(graderId).flatMap { history =>
        Ok(ApiResponse(success = true, data = Some(history)).asJson)
      }.handleErrorWith { error =>
        logger.error(s"获取阅卷员历史记录失败: graderId=$graderId", error)
        InternalServerError(ApiResponse[String](success = false, message = "服务器内部错误").asJson)
      }
  }

  // 管理员统计相关路由
  private val adminRoutes = HttpRoutes.of[IO] {
    
    // GET /api/admin/dashboard/stats
    case GET -> Root / "admin" / "dashboard" / "stats" => 
      scoreService.getAdminDashboardStats().flatMap { stats =>
        Ok(ApiResponse(success = true, data = Some(stats)).asJson)
      }.handleErrorWith { error =>
        logger.error("获取管理员仪表板统计失败", error)
        InternalServerError(ApiResponse[String](success = false, message = "服务器内部错误").asJson)
      }
  }

  // 健康检查路由
  private val healthRoutes = HttpRoutes.of[IO] {
    case OPTIONS -> _ => 
      Ok().map(_.withHeaders(Headers(
        Header.Raw(CIString("Access-Control-Allow-Origin"), "*"),
        Header.Raw(CIString("Access-Control-Allow-Methods"), "GET, POST, PUT, DELETE, OPTIONS"),
        Header.Raw(CIString("Access-Control-Allow-Headers"), "Content-Type, Authorization")
      )))
      
    case GET -> Root / "health" => 
      Ok(Json.obj(
        "status" -> Json.fromString("ok"),
        "service" -> Json.fromString("ScoreStatisticsService"),
        "timestamp" -> Json.fromString(java.time.LocalDateTime.now().toString)
      ))
  }

  // 组合所有路由
  val routes: HttpRoutes[IO] = Router(
    "/api" -> (studentRoutes <+> coachRoutes <+> graderRoutes <+> adminRoutes),
    "/" -> healthRoutes
  )

  // 添加CORS支持
  def routesWithCORS: HttpRoutes[IO] = {
    val corsHeaders = List(
      Header.Raw(CIString("Access-Control-Allow-Origin"), "*"),
      Header.Raw(CIString("Access-Control-Allow-Methods"), "GET, POST, PUT, DELETE, OPTIONS"),
      Header.Raw(CIString("Access-Control-Allow-Headers"), "Content-Type, Authorization")
    )

    routes.map { response =>
      response.copy(headers = response.headers ++ Headers(corsHeaders))
    }
  }
}
