package Controllers

import cats.effect.IO
import cats.implicits.*
import io.circe.*
import io.circe.syntax.*
// Only use semiauto to avoid conflicts
import io.circe.generic.semiauto.{deriveEncoder, deriveDecoder}
import org.http4s.{Headers, HttpRoutes, Request, Response, Status}
import org.http4s.dsl.io.*
import org.http4s.circe.CirceEntityCodec.*
import org.slf4j.LoggerFactory
import Models.*
import Services.*
import java.time.LocalDateTime
import java.util.Base64

// Explicit Circe Encoders for Option types and model classes
object CirceEncoders {
  // Import datetime codecs
  import Models.CustomCodecs._
  // Encoder for Option[Nothing] (handles Left errors)
  implicit val encodeOptionNothing: Encoder[Option[Nothing]] = new Encoder[Option[Nothing]] {
    final def apply(a: Option[Nothing]): Json = Json.Null
  }
  
  // Generic encoder for all Option types
  implicit def encodeOptionNull[A](implicit encoder: Encoder[A]): Encoder[Option[A]] = new Encoder[Option[A]] {
    final def apply(a: Option[A]): Json = a match {
      case Some(value) => encoder(value)
      case None => Json.Null
    }
  }

  // Custom encoder for ApiResponse with Nothing type (for error responses)
  implicit val encodeApiResponseNothing: Encoder[ApiResponse[Nothing]] = new Encoder[ApiResponse[Nothing]] {
    final def apply(a: ApiResponse[Nothing]): Json = Json.obj(
      ("success", Json.fromBoolean(a.success)),
      ("data", Json.Null),
      ("message", a.message.fold(Json.Null)(m => Json.fromString(m)))
    )
  }
  
  // Custom encoder for Null
  implicit val encodeNull: Encoder[Null] = new Encoder[Null] {
    final def apply(a: Null): Json = Json.Null
  }
  
  // SubmissionStatus encoder (needed for ExamSubmission)
  implicit val submissionStatusEncoder: Encoder[SubmissionStatus] = SubmissionStatus.encoder
  implicit val submissionStatusDecoder: Decoder[SubmissionStatus] = SubmissionStatus.decoder
  
  // Model encoders and decoders
  implicit val submissionAnswerEncoder: Encoder[SubmissionAnswer] = deriveEncoder[SubmissionAnswer]
  implicit val submissionAnswerDecoder: Decoder[SubmissionAnswer] = deriveDecoder[SubmissionAnswer]
  
  implicit val examSubmissionEncoder: Encoder[ExamSubmission] = deriveEncoder[ExamSubmission]
  implicit val examSubmissionDecoder: Decoder[ExamSubmission] = deriveDecoder[ExamSubmission]
  
  implicit val studentAnswerRequestEncoder: Encoder[StudentAnswerRequest] = deriveEncoder[StudentAnswerRequest]
  implicit val studentAnswerRequestDecoder: Decoder[StudentAnswerRequest] = deriveDecoder[StudentAnswerRequest]
  
  implicit val studentSubmitRequestEncoder: Encoder[StudentSubmitRequest] = deriveEncoder[StudentSubmitRequest]
  implicit val studentSubmitRequestDecoder: Decoder[StudentSubmitRequest] = deriveDecoder[StudentSubmitRequest]
  
  implicit val coachSubmitRequestEncoder: Encoder[CoachSubmitRequest] = deriveEncoder[CoachSubmitRequest]
  implicit val coachSubmitRequestDecoder: Decoder[CoachSubmitRequest] = deriveDecoder[CoachSubmitRequest]
  
  implicit val coachFileUploadRequestEncoder: Encoder[CoachFileUploadRequest] = deriveEncoder[CoachFileUploadRequest]
  implicit val coachFileUploadRequestDecoder: Decoder[CoachFileUploadRequest] = deriveDecoder[CoachFileUploadRequest]
  
  // Generic ApiResponse encoders
  implicit def apiResponseEncoder[T](implicit encoder: Encoder[T]): Encoder[ApiResponse[T]] = new Encoder[ApiResponse[T]] {
    final def apply(a: ApiResponse[T]): Json = Json.obj(
      ("success", Json.fromBoolean(a.success)),
      ("data", a.data.fold(Json.Null)(d => encoder(d))),
      ("message", a.message.fold(Json.Null)(m => Json.fromString(m)))
    )
  }
  
  // Explicit instances for common types
  implicit val listExamSubmissionEncoder: Encoder[List[ExamSubmission]] = 
    Encoder.encodeList[ExamSubmission]
  
  implicit val apiResponseExamSubmissionEncoder: Encoder[ApiResponse[ExamSubmission]] = 
    apiResponseEncoder[ExamSubmission]
    
  implicit val apiResponseListExamSubmissionEncoder: Encoder[ApiResponse[List[ExamSubmission]]] = 
    apiResponseEncoder[List[ExamSubmission]]
  
  implicit val mapStringEncoder: Encoder[Map[String, String]] = 
    Encoder.encodeMap[String, String]
    
  implicit val apiResponseMapStringEncoder: Encoder[ApiResponse[Map[String, String]]] = 
    apiResponseEncoder[Map[String, String]]
    
  // Use the encoder from GradingProgress
  implicit val gradingProgressEncoder: Encoder[GradingProgress] = GradingProgress.encoder
  implicit val apiResponseGradingProgressEncoder: Encoder[ApiResponse[GradingProgress]] = 
    apiResponseEncoder[GradingProgress]
}

class SubmissionController(
  submissionService: SubmissionService
) {
  import CirceEncoders._
  
  private val logger = LoggerFactory.getLogger("SubmissionController")

  // CORS 支持
  private val corsHeaders = Headers(
    "Access-Control-Allow-Origin" -> "*",
    "Access-Control-Allow-Methods" -> "GET, POST, PUT, DELETE, OPTIONS",
    "Access-Control-Allow-Headers" -> "Content-Type, Authorization"
  )

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    // CORS 预检请求
    case req @ OPTIONS -> _ =>
      Ok().map(_.withHeaders(corsHeaders))

    // 学生自主提交答案
    case req @ POST -> Root / "api" / "student" / "exams" / examId / "submit" =>
      handleStudentSubmit(examId, req).map(_.withHeaders(corsHeaders))

    // 学生获取提交记录
    case req @ GET -> Root / "api" / "student" / "exams" / examId / "submission" =>
      handleGetStudentSubmission(examId, req).map(_.withHeaders(corsHeaders))

    // 教练查看代管学生提交
    case req @ GET -> Root / "api" / "coach" / "exams" / examId / "submissions" =>
      handleGetCoachSubmissions(examId, req).map(_.withHeaders(corsHeaders))

    // 教练代理非独立学生提交答卷
    case req @ POST -> Root / "api" / "coach" / "exams" / examId / "upload-answer" =>
      handleCoachUploadAnswer(examId, req).map(_.withHeaders(corsHeaders))

    // 阅卷员查看具体提交
    case req @ GET -> Root / "api" / "grader" / "submissions" / submissionId =>
      handleGetSubmissionForGrading(submissionId, req).map(_.withHeaders(corsHeaders))

    // 阅卷进度查看
    case req @ GET -> Root / "api" / "grader" / "exams" / examId / "progress" =>
      handleGetGradingProgress(examId, req).map(_.withHeaders(corsHeaders))

    // 健康检查
    case GET -> Root / "health" =>
      Ok("OK").map(_.withHeaders(corsHeaders))
  }

  // 处理学生自主提交
  private def handleStudentSubmit(examId: String, req: Request[IO]): IO[Response[IO]] = {
    // Make decoders visible in this scope
    import CirceEncoders.{
      studentSubmitRequestDecoder,
      examSubmissionEncoder, 
      apiResponseExamSubmissionEncoder, 
      encodeApiResponseNothing
    }
    
    (for {
      token <- extractToken(req)
      submitReq <- req.as[StudentSubmitRequest]
      result <- submissionService.submitAnswers(examId, submitReq, token)
      response <- result match {
        case Right(submission) =>
          Ok(ApiResponse.success(submission, "答案提交成功").asJson)
        case Left(error) =>
          BadRequest(ApiResponse.error(error).asJson)
      }
    } yield response).handleErrorWith { error =>
      logger.error("学生提交处理失败", error)
      BadRequest(ApiResponse.error(s"提交失败: ${error.getMessage}").asJson)
    }
  }

  // 处理获取学生提交记录
  private def handleGetStudentSubmission(examId: String, req: Request[IO]): IO[Response[IO]] = {
    // Make encoders visible in this scope
    import CirceEncoders.{
      examSubmissionEncoder, 
      apiResponseExamSubmissionEncoder, 
      mapStringEncoder, 
      apiResponseMapStringEncoder,
      encodeApiResponseNothing
    }
    
    (for {
      token <- extractToken(req)
      result <- submissionService.getStudentSubmission(examId, token)
      response <- result match {
        case Right(Some(submission)) =>
          Ok(ApiResponse.success(submission, "获取提交记录成功").asJson)
        case Right(None) =>
          // Create a success response with an empty object instead of null
          val emptyData = Map[String, String]()
          Ok(ApiResponse.success(emptyData, "未找到提交记录").asJson)
        case Left(error) =>
          BadRequest(ApiResponse.error(error).asJson)
      }
    } yield response).handleErrorWith { error =>
      logger.error("获取学生提交记录失败", error)
      BadRequest(ApiResponse.error(s"获取失败: ${error.getMessage}").asJson)
    }
  }

  // 处理教练获取提交记录
  private def handleGetCoachSubmissions(examId: String, req: Request[IO]): IO[Response[IO]] = {
    // Make encoders visible in this scope
    import CirceEncoders.{
      examSubmissionEncoder,
      listExamSubmissionEncoder,
      apiResponseListExamSubmissionEncoder,
      encodeApiResponseNothing
    }
    
    (for {
      token <- extractToken(req)
      studentUsername = req.uri.query.params.get("studentUsername")
      result <- submissionService.getCoachStudentSubmissions(examId, studentUsername, token)
      response <- result match {
        case Right(submissions) =>
          Ok(ApiResponse.success(submissions, "获取提交记录成功").asJson)
        case Left(error) =>
          BadRequest(ApiResponse.error(error).asJson)
      }
    } yield response).handleErrorWith { error =>
      logger.error("获取教练提交记录失败", error)
      BadRequest(ApiResponse.error(s"获取失败: ${error.getMessage}").asJson)
    }
  }

  // 处理教练代理上传答案
  private def handleCoachUploadAnswer(examId: String, req: Request[IO]): IO[Response[IO]] = {
    // Make encoders and decoders visible in this scope
    import CirceEncoders.{
      coachFileUploadRequestDecoder,
      mapStringEncoder,
      apiResponseMapStringEncoder,
      encodeApiResponseNothing
    }
    
    (for {
      token <- extractToken(req)
      uploadRequest <- req.as[CoachFileUploadRequest]
      result <- processCoachUpload(examId, uploadRequest, token)
      response <- result match {
        case Right(imageUrl) =>
          Ok(ApiResponse.success(Map("imageUrl" -> imageUrl), "上传成功").asJson)
        case Left(error) =>
          BadRequest(ApiResponse.error(error).asJson)
      }
    } yield response).handleErrorWith { error =>
      logger.error("教练上传答案失败", error)
      BadRequest(ApiResponse.error(s"上传失败: ${error.getMessage}").asJson)
    }
  }

  // 处理阅卷员获取提交详情
  private def handleGetSubmissionForGrading(submissionId: String, req: Request[IO]): IO[Response[IO]] = {
    // Make encoders visible in this scope
    import CirceEncoders.{
      examSubmissionEncoder,
      apiResponseExamSubmissionEncoder,
      encodeApiResponseNothing
    }
    
    (for {
      token <- extractToken(req)
      result <- submissionService.getSubmissionForGrading(submissionId, token)
      response <- result match {
        case Right(submission) =>
          Ok(ApiResponse.success(submission, "获取提交详情成功").asJson)
        case Left(error) =>
          BadRequest(ApiResponse.error(error).asJson)
      }
    } yield response).handleErrorWith { error =>
      logger.error("获取提交详情失败", error)
      BadRequest(ApiResponse.error(s"获取失败: ${error.getMessage}").asJson)
    }
  }

  // 处理阅卷进度查询
  private def handleGetGradingProgress(examId: String, req: Request[IO]): IO[Response[IO]] = {
    // Make encoders visible in this scope
    import CirceEncoders.{
      gradingProgressEncoder, 
      apiResponseGradingProgressEncoder,
      encodeApiResponseNothing
    }
    
    (for {
      token <- extractToken(req)
      result <- submissionService.getGradingProgress(examId, token)
      response <- result match {
        case Right(progress) =>
          val resp = ApiResponse.success(progress, "获取阅卷进度成功")
          Ok(resp.asJson)
        case Left(error) =>
          BadRequest(ApiResponse.error(error).asJson)
      }
    } yield response).handleErrorWith { error =>
      logger.error("获取阅卷进度失败", error)
      BadRequest(ApiResponse.error(s"获取失败: ${error.getMessage}").asJson)
    }
  }

  // 处理教练文件上传
  private def processCoachUpload(examId: String, uploadRequest: CoachFileUploadRequest, token: String): IO[Either[String, String]] = {
    for {
      // 验证文件格式和大小
      validationResult <- IO {
        val fileExtension = uploadRequest.fileName.split("\\.").lastOption.getOrElse("")
        if (!List("jpg", "jpeg", "png", "pdf").contains(fileExtension.toLowerCase)) {
          Left("不支持的文件格式")
        } else {
          try {
            val fileBytes = Base64.getDecoder.decode(uploadRequest.fileContent)
            if (fileBytes.length > 10485760) { // 10MB
              Left("文件大小超过限制")
            } else {
              Right(fileBytes)
            }
          } catch {
            case _: Exception => Left("文件内容解码失败")
          }
        }
      }
      
      result <- validationResult match {
        case Right(fileBytes) =>
          // 调用文件存储服务上传文件
          submissionService.uploadAnswerFile(
            examId = examId,
            questionNumber = uploadRequest.questionNumber,
            studentUsername = uploadRequest.studentUsername,
            fileName = uploadRequest.fileName,
            fileContent = fileBytes,
            token = token
          )
        case Left(error) => IO.pure(Left(error))
      }
    } yield result
  }

  // 提取认证Token
  private def extractToken(req: Request[IO]): IO[String] = {
    req.headers.get[org.http4s.headers.Authorization] match {
      case Some(auth) => 
        val tokenValue = auth.credentials.toString.replace("Bearer ", "")
        IO.pure(tokenValue)
      case None => IO.raiseError(new RuntimeException("缺少认证Token"))
    }
  }

  // 获取文件扩展名
  private def getFileExtension(filename: String): String = {
    val lastDot = filename.lastIndexOf('.')
    if (lastDot > 0 && lastDot < filename.length - 1) {
      filename.substring(lastDot + 1).toLowerCase
    } else {
      "jpg" // 默认扩展名
    }
  }
}
