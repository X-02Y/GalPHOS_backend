package Controllers

import cats.effect.IO
import cats.implicits.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import org.slf4j.LoggerFactory

class ExamManagementController(
  studentController: StudentController,
  coachController: CoachController,
  graderController: GraderController,
  adminController: AdminController
) {
  private val logger = LoggerFactory.getLogger("ExamManagementController")

  // CORS 支持
  private val corsHeaders = Headers(
    "Access-Control-Allow-Origin" -> "*",
    "Access-Control-Allow-Methods" -> "GET, POST, PUT, DELETE, OPTIONS",
    "Access-Control-Allow-Headers" -> "Content-Type, Authorization"
  )

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    // 健康检查
    case GET -> Root / "health" =>
      Ok("Exam Management Service is running").map(_.withHeaders(corsHeaders))

    // 服务信息
    case GET -> Root / "info" =>
      Ok("""
        {
          "service": "Exam Management Service",
          "version": "1.0.0",
          "port": 3003,
          "status": "running"
        }
      """).map(_.withHeaders(corsHeaders))

  } <+> studentController.routes <+> coachController.routes <+> graderController.routes <+> adminController.routes
}
