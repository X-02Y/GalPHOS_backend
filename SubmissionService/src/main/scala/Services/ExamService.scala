package Services

import cats.effect.IO
import cats.implicits.*
import sttp.client3.*
import sttp.client3.circe.*
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend
import io.circe.generic.auto.*
import org.slf4j.LoggerFactory
import Config.ServerConfig
import Models.*

class ExamService(config: ServerConfig) {
  private val logger = LoggerFactory.getLogger("ExamService")
  private val backend = AsyncHttpClientCatsBackend[IO]()
  private val examServiceUrl = config.examServiceUrl

  def getExamInfo(examId: String, token: String): IO[Either[String, ExamInfo]] = {
    val request = basicRequest
      .get(uri"$examServiceUrl/api/admin/exams/$examId")
      .header("Authorization", s"Bearer $token")
      .response(asJson[ApiResponse[ExamInfo]])

    backend.flatMap { implicit b =>
      request.send(b).map(_.body match {
        case Right(response) if response.success =>
          response.data match {
            case Some(examInfo) => Right(examInfo)
            case None => Left("考试信息不存在")
          }
        case Right(response) => Left(response.message.getOrElse("获取考试信息失败"))
        case Left(error) => Left(s"考试服务通信失败: ${error.getMessage}")
      })
    }.handleErrorWith { error =>
      logger.error(s"获取考试信息失败: examId=$examId", error)
      IO.pure(Left(s"考试服务不可用: ${error.getMessage}"))
    }
  }

  def validateExamAccess(examId: String, userRole: String, token: String): IO[Either[String, Boolean]] = {
    getExamInfo(examId, token).map {
      case Right(examInfo) =>
        // 检查考试状态和用户权限
        userRole match {
          case "student" =>
            if (examInfo.status == "published") Right(true)
            else Left("考试未发布或已结束")
          case "coach" | "grader" => Right(true)
          case _ => Left("无权限访问此考试")
        }
      case Left(error) => Left(error)
    }
  }
}
