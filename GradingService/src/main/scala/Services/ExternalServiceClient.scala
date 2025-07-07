package Services

import Models.*
import Models.Implicits.given
import cats.effect.{IO, Resource}
import cats.implicits.*
import io.circe.*
import io.circe.syntax.*
import io.circe.parser.*
import io.circe.generic.auto.*
import org.http4s.*
import org.http4s.client.*
import org.http4s.ember.client.*
import org.http4s.circe.*
import org.http4s.headers.*
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

class ExternalServiceClient {
  private val logger = LoggerFactory.getLogger("ExternalServiceClient")
  
  // 从配置读取服务地址
  private val examMSBaseUrl = sys.env.getOrElse("EXAM_MS_URL", "http://localhost:8081")
  private val fileStorageBaseUrl = sys.env.getOrElse("FILE_STORAGE_URL", "http://localhost:8083")
  
  // HTTP客户端
  private def createClient: Resource[IO, Client[IO]] = {
    EmberClientBuilder.default[IO].build
  }

  // 调用ExamMS获取考试信息
  def getExamById(examId: Long): IO[Option[ExamData]] = {
    logger.info(s"调用ExamMS获取考试信息: examId=$examId")
    
    createClient.use { client =>
      val url = s"$examMSBaseUrl/api/exams/$examId"
      
      client.expect[String](url).flatMap { responseBody =>
        decode[ExternalApiResponse[ExamData]](responseBody) match {
          case Right(apiResponse) if apiResponse.success =>
            IO.pure(apiResponse.data)
          case Right(apiResponse) =>
            logger.warn(s"ExamMS返回失败响应: ${apiResponse.message}")
            IO.pure(None)
          case Left(parseError) =>
            logger.error(s"解析ExamMS响应失败: ${parseError.getMessage}")
            IO.pure(None)
        }
      }.handleErrorWith { error =>
        logger.error(s"调用ExamMS失败: ${error.getMessage}", error)
        IO.pure(None)
      }
    }
  }

  // 调用ExamMS获取考试列表
  def getAvailableExams(): IO[List[ExamData]] = {
    logger.info("调用ExamMS获取可用考试列表")
    
    createClient.use { client =>
      val url = s"$examMSBaseUrl/api/exams?status=grading"
      
      client.expect[String](url).flatMap { responseBody =>
        decode[ExternalApiResponse[List[ExamData]]](responseBody) match {
          case Right(apiResponse) if apiResponse.success =>
            IO.pure(apiResponse.data.getOrElse(List.empty))
          case Right(apiResponse) =>
            logger.warn(s"ExamMS返回失败响应: ${apiResponse.message}")
            IO.pure(List.empty)
          case Left(parseError) =>
            logger.error(s"解析ExamMS响应失败: ${parseError.getMessage}")
            IO.pure(List.empty)
        }
      }.handleErrorWith { error =>
        logger.error(s"调用ExamMS失败: ${error.getMessage}", error)
        IO.pure(List.empty)
      }
    }
  }

  // 调用FileStorageService获取图片数据
  def getImagesByExam(examId: Long, studentId: Option[Long] = None, questionNumber: Option[Int] = None): IO[List[FileStorageImage]] = {
    logger.info(s"调用FileStorageService获取图片: examId=$examId, studentId=$studentId, questionNumber=$questionNumber")
    
    createClient.use { client =>
      val baseUrl = s"$fileStorageBaseUrl/api/files/images"
      val queryParams = buildQueryParams(examId, studentId, questionNumber)
      val url = if (queryParams.nonEmpty) s"$baseUrl?$queryParams" else baseUrl
      
      client.expect[String](url).flatMap { responseBody =>
        decode[ExternalApiResponse[List[FileStorageImage]]](responseBody) match {
          case Right(apiResponse) if apiResponse.success =>
            IO.pure(apiResponse.data.getOrElse(List.empty))
          case Right(apiResponse) =>
            logger.warn(s"FileStorageService返回失败响应: ${apiResponse.message}")
            IO.pure(List.empty)
          case Left(parseError) =>
            logger.error(s"解析FileStorageService响应失败: ${parseError.getMessage}")
            IO.pure(List.empty)
        }
      }.handleErrorWith { error =>
        logger.error(s"调用FileStorageService失败: ${error.getMessage}", error)
        IO.pure(List.empty)
      }
    }
  }

  // 构建查询参数
  private def buildQueryParams(examId: Long, studentId: Option[Long], questionNumber: Option[Int]): String = {
    val params = scala.collection.mutable.ListBuffer[String]()
    
    params += s"examId=$examId"
    studentId.foreach(id => params += s"studentId=$id")
    questionNumber.foreach(qn => params += s"questionNumber=$qn")
    
    params.mkString("&")
  }

  // 获取图片的实际内容（Base64编码）
  def getImageContent(imageUrl: String): IO[Option[String]] = {
    logger.info(s"获取图片内容: $imageUrl")
    
    createClient.use { client =>
      client.get(imageUrl) { response =>
        if (response.status.isSuccess) {
          response.body.compile.toList.map { bytes =>
            val base64 = java.util.Base64.getEncoder.encodeToString(bytes.toArray)
            Some(base64)
          }
        } else {
          logger.warn(s"获取图片失败，状态码: ${response.status.code}")
          IO.pure(None)
        }
      }
    }.handleErrorWith { error =>
      logger.error(s"获取图片内容失败: ${error.getMessage}", error)
      IO.pure(None)
    }
  }
}

// Circe编解码器
object ExternalServiceClient {
  import io.circe.generic.semiauto.*
  
  implicit val examDataEncoder: Encoder[ExamData] = deriveEncoder[ExamData]
  implicit val examDataDecoder: Decoder[ExamData] = deriveDecoder[ExamData]
  
  implicit val fileStorageImageEncoder: Encoder[FileStorageImage] = deriveEncoder[FileStorageImage]
  implicit val fileStorageImageDecoder: Decoder[FileStorageImage] = deriveDecoder[FileStorageImage]
  
  implicit def externalApiResponseEncoder[T: Encoder]: Encoder[ExternalApiResponse[T]] = deriveEncoder[ExternalApiResponse[T]]
  implicit def externalApiResponseDecoder[T: Decoder]: Decoder[ExternalApiResponse[T]] = deriveDecoder[ExternalApiResponse[T]]
}
