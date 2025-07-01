package Services

import cats.effect.IO
import cats.implicits.*
import org.slf4j.LoggerFactory
import Database.{DatabaseManager, SqlParameter}
import Models.*
import java.time.LocalDateTime
import java.util.UUID

class SubmissionService(
  authService: AuthService,
  examService: ExamService,
  fileStorageService: FileStorageService
) {
  private val logger = LoggerFactory.getLogger("SubmissionService")

  // 学生自主提交答案
  def submitAnswers(examId: String, request: StudentSubmitRequest, token: String): IO[Either[String, ExamSubmission]] = {
    for {
      userInfo <- authService.extractUserFromToken(token)
      examAccess <- userInfo match {
        case Right(user) if user.role == "student" => 
          examService.validateExamAccess(examId, user.role, token)
        case Right(_) => IO.pure(Left("只有学生可以自主提交答案"))
        case Left(error) => IO.pure(Left(error))
      }
      result <- (userInfo, examAccess) match {
        case (Right(user), Right(true)) => 
          createOrUpdateSubmission(examId, user.username, request.answers, isProxy = false, None)
        case (Right(_), Left(error)) => IO.pure(Left(error))
        case (Left(error), _) => IO.pure(Left(error))
        case _ => IO.pure(Left("提交失败"))
      }
    } yield result
  }

  // 教练代理提交答案
  def coachSubmitAnswers(examId: String, request: CoachSubmitRequest, token: String): IO[Either[String, ExamSubmission]] = {
    for {
      userInfo <- authService.extractUserFromToken(token)
      examAccess <- userInfo match {
        case Right(user) if user.role == "coach" => 
          examService.validateExamAccess(examId, user.role, token)
        case Right(_) => IO.pure(Left("只有教练可以代理提交答案"))
        case Left(error) => IO.pure(Left(error))
      }
      result <- (userInfo, examAccess) match {
        case (Right(user), Right(true)) => 
          createOrUpdateSubmission(examId, request.studentUsername, request.answers, isProxy = true, Some(user.username))
        case (Right(_), Left(error)) => IO.pure(Left(error))
        case (Left(error), _) => IO.pure(Left(error))
        case _ => IO.pure(Left("代理提交失败"))
      }
    } yield result
  }

  // 获取学生提交记录
  def getStudentSubmission(examId: String, token: String): IO[Either[String, Option[ExamSubmission]]] = {
    for {
      userInfo <- authService.extractUserFromToken(token)
      result <- userInfo match {
        case Right(user) => getSubmissionByUsername(examId, user.username)
        case Left(error) => IO.pure(Left(error))
      }
    } yield result
  }

  // 教练获取管理学生的提交记录
  def getCoachStudentSubmissions(examId: String, studentUsername: Option[String], token: String): IO[Either[String, List[ExamSubmission]]] = {
    for {
      userInfo <- authService.extractUserFromToken(token)
      result <- userInfo match {
        case Right(user) if user.role == "coach" => 
          studentUsername match {
            case Some(username) => getSubmissionByUsername(examId, username).map {
              case Right(Some(submission)) => Right(List(submission))
              case Right(None) => Right(List.empty[ExamSubmission])
              case Left(error) => Left(error)
            }
            case None => getSubmissionsByCoach(examId, user.username)
          }
        case Right(_) => IO.pure(Left("只有教练可以查看管理学生的提交记录"))
        case Left(error) => IO.pure(Left(error))
      }
    } yield result
  }

  // 阅卷员获取提交详情
  def getSubmissionForGrading(submissionId: String, token: String): IO[Either[String, ExamSubmission]] = {
    for {
      userInfo <- authService.extractUserFromToken(token)
      result <- userInfo match {
        case Right(user) if user.role == "grader" => getSubmissionById(submissionId)
        case Right(_) => IO.pure(Left("只有阅卷员可以查看提交详情"))
        case Left(error) => IO.pure(Left(error))
      }
    } yield result
  }

  // 阅卷员获取考试阅卷进度
  def getGradingProgress(examId: String, token: String): IO[Either[String, GradingProgress]] = {
    for {
      userInfo <- authService.extractUserFromToken(token)
      result <- userInfo match {
        case Right(user) if user.role == "grader" => calculateGradingProgress(examId)
        case Right(_) => IO.pure(Left("只有阅卷员可以查看阅卷进度"))
        case Left(error) => IO.pure(Left(error))
      }
    } yield result
  }

  // 上传答案图片
  def uploadAnswerImage(
    examId: String, 
    questionNumber: Int, 
    fileContent: Array[Byte], 
    originalName: String, 
    fileType: String,
    studentUsername: Option[String],
    token: String
  ): IO[Either[String, String]] = {
    for {
      userInfo <- authService.extractUserFromToken(token)
      examAccess <- userInfo match {
        case Right(user) => examService.validateExamAccess(examId, user.role, token)
        case Left(error) => IO.pure(Left(error))
      }
      result <- (userInfo, examAccess) match {
        case (Right(user), Right(true)) =>
          val uploadUserId = user.username
          val uploadUserType = user.role
          val targetStudent = if (user.role == "coach") studentUsername.getOrElse(user.username) else user.username
          val description = if (user.role == "coach") s"教练${user.username}代理学生${targetStudent}上传答案" else s"学生${user.username}上传答案"
          
          // 验证文件
          if (!fileStorageService.validateFileType(fileType)) {
            IO.pure(Left("不支持的文件类型"))
          } else if (!fileStorageService.validateFileSize(fileContent.length)) {
            IO.pure(Left("文件大小超出限制"))
          } else {
            fileStorageService.uploadFile(
              fileContent, originalName, fileType, uploadUserId, uploadUserType, 
              examId, description, token
            ).map(_.map(_.fileUrl))
          }
        case (Right(_), Left(error)) => IO.pure(Left(error))
        case (Left(error), _) => IO.pure(Left(error))
      }
    } yield result
  }

  // 上传答案文件（新版本，使用 uploadAnswerFile 方法名）
  def uploadAnswerFile(
    examId: String,
    questionNumber: Int,
    studentUsername: String,
    fileName: String,
    fileContent: Array[Byte],
    token: String
  ): IO[Either[String, String]] = {
    for {
      userInfo <- authService.extractUserFromToken(token)
      result <- userInfo match {
        case Right(user) if user.role == "coach" =>
          // 教练代理上传
          for {
            // 验证学生是否由此教练代管
            studentInfo <- authService.validateStudentCoachRelation(studentUsername, user.username, token)
            uploadResult <- studentInfo match {
              case Right(_) =>
                // 上传文件到文件存储服务
                fileStorageService.uploadFile(
                  fileContent,
                  fileName, 
                  "answer", 
                  studentUsername,
                  "student",
                  examId,
                  s"问题 $questionNumber 的答案",
                  token
                ).flatMap {
                  case Right(fileData) =>
                    // 记录答案到数据库
                    saveAnswerToDatabase(examId, questionNumber, studentUsername, fileId = Some(fileData.fileId))
                      .map(_.map(_ => fileData.fileId))
                  case Left(error) => IO.pure(Left(s"文件上传失败: $error"))
                }
              case Left(error) => IO.pure(Left(error))
            }
          } yield uploadResult
        case Right(user) if user.role == "student" && user.username == studentUsername =>
          // 学生上传自己的答案
          // 验证学生是否有参加考试的权限
          examService.validateExamAccess(examId, user.role, token).flatMap {
            case Right(true) =>
              fileStorageService.uploadFile(
                fileContent,
                fileName, 
                "answer", 
                user.username,
                "student",
                examId,
                s"问题 $questionNumber 的答案",
                token
              ).flatMap {
                case Right(fileData) =>
                  // 记录答案到数据库
                  saveAnswerToDatabase(examId, questionNumber, user.username, fileId = Some(fileData.fileId))
                    .map(_.map(_ => fileData.fileId))
                case Left(error) => IO.pure(Left(s"文件上传失败: $error"))
              }
            case Right(false) => IO.pure(Left("无权访问此考试"))
            case Left(error) => IO.pure(Left(error))
          }
        case Right(user) if user.role == "student" => 
          IO.pure(Left("学生只能上传自己的答案"))
        case Right(_) => IO.pure(Left("只有教练和学生可以上传答案"))
      }
    } yield result
  }

  // 保存答案到数据库
  private def saveAnswerToDatabase(
    examId: String, 
    questionNumber: Int, 
    studentUsername: String, 
    fileId: Option[String]
  ): IO[Either[String, Unit]] = {
    // 先检查是否存在提交记录
    val checkSql = """
      SELECT id FROM exam_submissions 
      WHERE exam_id = ? AND student_username = ?
    """
    val checkParams = List(
      SqlParameter("uuid", examId),
      SqlParameter("string", studentUsername)
    )
    
    DatabaseManager.executeQuery(checkSql, checkParams).flatMap { results =>
      if (results.isEmpty) {
        // 如果不存在提交记录，先创建提交记录
        val submissionId = UUID.randomUUID()
        val submissionTime = LocalDateTime.now()
        
        val createSubmissionSql = """
          INSERT INTO exam_submissions (id, exam_id, student_id, student_username, submission_time, status)
          VALUES (?, ?, ?, ?, ?, ?)
        """
        val createSubmissionParams = List(
          SqlParameter("uuid", submissionId),
          SqlParameter("uuid", examId),
          SqlParameter("string", studentUsername),
          SqlParameter("string", studentUsername),
          SqlParameter("timestamp", submissionTime),
          SqlParameter("string", "in_progress")
        )
        
        DatabaseManager.executeUpdate(createSubmissionSql, createSubmissionParams).flatMap { _ =>
          // 插入答案记录
          insertAnswer(submissionId, questionNumber, fileId)
        }
      } else {
        // 已存在提交记录，获取ID然后更新或插入答案
        val submissionId = UUID.fromString(DatabaseManager.decodeFieldUnsafe[String](results.head, "id"))
        
        // 检查该问题是否已有答案
        val checkAnswerSql = """
          SELECT submission_id FROM submission_answers 
          WHERE submission_id = ? AND question_number = ?
        """
        val checkAnswerParams = List(
          SqlParameter("uuid", submissionId),
          SqlParameter("int", questionNumber)
        )
        
        DatabaseManager.executeQuery(checkAnswerSql, checkAnswerParams).flatMap { answerResults =>
          if (answerResults.isEmpty) {
            // 不存在该问题的答案，插入新答案
            insertAnswer(submissionId, questionNumber, fileId)
          } else {
            // 更新已有答案
            val updateSql = """
              UPDATE submission_answers 
              SET answer_image_url = ?, upload_time = ? 
              WHERE submission_id = ? AND question_number = ?
            """
            val updateParams = List(
              SqlParameter("string", fileId.orNull),
              SqlParameter("timestamp", LocalDateTime.now()),
              SqlParameter("uuid", submissionId),
              SqlParameter("int", questionNumber)
            )
            
            DatabaseManager.executeUpdate(updateSql, updateParams).map { rowsAffected =>
              if (rowsAffected > 0) Right(()) else Left("更新答案失败")
            }.handleErrorWith { error =>
              logger.error(s"更新答案失败: ${error.getMessage}", error)
              IO.pure(Left(s"数据库更新错误: ${error.getMessage}"))
            }
          }
        }
      }
    }.handleErrorWith { error =>
      logger.error(s"保存答案到数据库失败: ${error.getMessage}", error)
      IO.pure(Left(s"数据库操作错误: ${error.getMessage}"))
    }
  }
  
  // 插入新答案辅助方法
  private def insertAnswer(
    submissionId: UUID,
    questionNumber: Int,
    fileId: Option[String]
  ): IO[Either[String, Unit]] = {
    val insertSql = """
      INSERT INTO submission_answers (submission_id, question_number, answer_image_url, upload_time)
      VALUES (?, ?, ?, ?)
    """
    val insertParams = List(
      SqlParameter("uuid", submissionId),
      SqlParameter("int", questionNumber),
      SqlParameter("string", fileId.orNull),
      SqlParameter("timestamp", LocalDateTime.now())
    )
    
    DatabaseManager.executeUpdate(insertSql, insertParams).map { rowsAffected =>
      if (rowsAffected > 0) Right(()) else Left("插入答案失败")
    }.handleErrorWith { error =>
      logger.error(s"插入答案失败: ${error.getMessage}", error)
      IO.pure(Left(s"数据库插入错误: ${error.getMessage}"))
    }
  }

  // 私有方法：创建或更新提交记录
  private def createOrUpdateSubmission(
    examId: String, 
    studentUsername: String, 
    answers: List[StudentAnswerRequest],
    isProxy: Boolean,
    coachId: Option[String]
  ): IO[Either[String, ExamSubmission]] = {
    val examUuid = UUID.fromString(examId)
    val submissionTime = LocalDateTime.now()
    
    // 检查是否已存在提交记录
    getSubmissionByUsername(examId, studentUsername).flatMap {
      case Right(Some(existingSubmission)) =>
        // 更新现有提交
        updateSubmissionAnswers(existingSubmission.id, answers).map {
          case Right(_) => Right(existingSubmission.copy(
            submissionTime = submissionTime,
            answers = answers.map(a => SubmissionAnswer(
              questionNumber = a.questionNumber,
              answerImageUrl = Some(a.imageUrl),
              uploadTime = a.uploadTime
            ))
          ))
          case Left(error) => Left(error)
        }
      case Right(None) =>
        // 创建新提交记录
        createNewSubmission(examUuid, studentUsername, answers, isProxy, coachId, submissionTime)
      case Left(error) => IO.pure(Left(error))
    }
  }

  private def createNewSubmission(
    examId: UUID,
    studentUsername: String,
    answers: List[StudentAnswerRequest],
    isProxy: Boolean,
    coachId: Option[String],
    submissionTime: LocalDateTime
  ): IO[Either[String, ExamSubmission]] = {
    val submissionId = UUID.randomUUID()
    val sql = """
      INSERT INTO exam_submissions (id, exam_id, student_id, student_username, coach_id, is_proxy_submission, submission_time, status)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    """
    val params = List(
      SqlParameter("uuid", submissionId),
      SqlParameter("uuid", examId),
      SqlParameter("string", studentUsername),
      SqlParameter("string", studentUsername),
      SqlParameter("string", coachId.orNull),
      SqlParameter("boolean", isProxy),
      SqlParameter("timestamp", submissionTime),
      SqlParameter("string", "submitted")
    )

    DatabaseManager.executeUpdate(sql, params).flatMap { rowsAffected =>
      if (rowsAffected > 0) {
        // 插入答案详情
        insertSubmissionAnswers(submissionId, answers).map { _ =>
          Right(ExamSubmission(
            id = submissionId.toString,
            examId = examId.toString,
            studentId = studentUsername,
            studentUsername = studentUsername,
            coachId = coachId,
            isProxySubmission = isProxy,
            submissionTime = submissionTime,
            status = SubmissionStatus.Submitted,
            answers = answers.map(a => SubmissionAnswer(
              questionNumber = a.questionNumber,
              answerImageUrl = Some(a.imageUrl),
              uploadTime = a.uploadTime
            ))
          ))
        }
      } else {
        IO.pure(Left("创建提交记录失败"))
      }
    }.handleErrorWith { error =>
      logger.error("创建提交记录失败", error)
      IO.pure(Left(s"数据库操作失败: ${error.getMessage}"))
    }
  }

  private def insertSubmissionAnswers(submissionId: UUID, answers: List[StudentAnswerRequest]): IO[Unit] = {
    val insertOps = answers.map { answer =>
      val sql = """
        INSERT INTO submission_answers (submission_id, question_number, answer_image_url, upload_time)
        VALUES (?, ?, ?, ?)
      """
      val params = List(
        SqlParameter("uuid", submissionId),
        SqlParameter("int", answer.questionNumber),
        SqlParameter("string", answer.imageUrl),
        SqlParameter("timestamp", answer.uploadTime)
      )
      DatabaseManager.executeUpdate(sql, params)
    }

    insertOps.sequence.map(_ => ())
  }

  private def updateSubmissionAnswers(submissionId: String, answers: List[StudentAnswerRequest]): IO[Either[String, Unit]] = {
    val submissionUuid = UUID.fromString(submissionId)
    
    // 删除旧答案
    val deleteSql = "DELETE FROM submission_answers WHERE submission_id = ?"
    val deleteParams = List(SqlParameter("uuid", submissionUuid))
    
    DatabaseManager.executeUpdate(deleteSql, deleteParams).flatMap { _ =>
      // 插入新答案
      insertSubmissionAnswers(submissionUuid, answers).map(_ => Right(()))
    }.handleErrorWith { error =>
      logger.error("更新提交答案失败", error)
      IO.pure(Left(s"更新答案失败: ${error.getMessage}"))
    }
  }

  private def getSubmissionByUsername(examId: String, studentUsername: String): IO[Either[String, Option[ExamSubmission]]] = {
    val sql = """
      SELECT s.*, a.question_number, a.answer_image_url, a.upload_time, a.score, a.max_score, a.grader_feedback
      FROM exam_submissions s
      LEFT JOIN submission_answers a ON s.id = a.submission_id
      WHERE s.exam_id = ? AND s.student_username = ?
      ORDER BY a.question_number
    """
    val params = List(
      SqlParameter("uuid", examId),
      SqlParameter("string", studentUsername)
    )

    DatabaseManager.executeQuery(sql, params).map { results =>
      if (results.isEmpty) {
        Right(None)
      } else {
        try {
          val firstRow = results.head
          val submissionId = DatabaseManager.decodeFieldUnsafe[String](firstRow, "id")
          val examIdStr = DatabaseManager.decodeFieldUnsafe[String](firstRow, "exam_id")
          val studentId = DatabaseManager.decodeFieldUnsafe[String](firstRow, "student_id")
          val studentUsernameDb = DatabaseManager.decodeFieldUnsafe[String](firstRow, "student_username")
          val coachId = DatabaseManager.decodeFieldOptional[String](firstRow, "coach_id")
          val isProxy = DatabaseManager.decodeFieldUnsafe[Boolean](firstRow, "is_proxy_submission")
          val submissionTime = DatabaseManager.decodeFieldUnsafe[LocalDateTime](firstRow, "submission_time")
          val status = DatabaseManager.decodeFieldUnsafe[String](firstRow, "status")
          val totalScore = DatabaseManager.decodeFieldOptional[BigDecimal](firstRow, "total_score")
          val maxScore = DatabaseManager.decodeFieldOptional[BigDecimal](firstRow, "max_score")
          val feedback = DatabaseManager.decodeFieldOptional[String](firstRow, "feedback")

          // 处理答案
          val answers = results.filter(row => 
            DatabaseManager.decodeFieldOptional[Int](row, "question_number").isDefined
          ).map { row =>
            SubmissionAnswer(
              questionNumber = DatabaseManager.decodeFieldUnsafe[Int](row, "question_number"),
              answerImageUrl = DatabaseManager.decodeFieldOptional[String](row, "answer_image_url"),
              uploadTime = DatabaseManager.decodeFieldUnsafe[LocalDateTime](row, "upload_time"),
              score = DatabaseManager.decodeFieldOptional[BigDecimal](row, "score"),
              maxScore = DatabaseManager.decodeFieldOptional[BigDecimal](row, "max_score"),
              graderFeedback = DatabaseManager.decodeFieldOptional[String](row, "grader_feedback")
            )
          }

          Right(Some(ExamSubmission(
            id = submissionId,
            examId = examIdStr,
            studentId = studentId,
            studentUsername = studentUsernameDb,
            coachId = coachId,
            isProxySubmission = isProxy,
            submissionTime = submissionTime,
            status = SubmissionStatus.fromString(status),
            totalScore = totalScore,
            maxScore = maxScore,
            feedback = feedback,
            answers = answers
          )))
        } catch {
          case e: Exception =>
            logger.error("解析提交记录失败", e)
            Left(s"数据解析失败: ${e.getMessage}")
        }
      }
    }.handleErrorWith { error =>
      logger.error(s"查询提交记录失败: examId=$examId, student=$studentUsername", error)
      IO.pure(Left(s"查询失败: ${error.getMessage}"))
    }
  }

  private def getSubmissionById(submissionId: String): IO[Either[String, ExamSubmission]] = {
    val sql = """
      SELECT s.*, a.question_number, a.answer_image_url, a.upload_time, a.score, a.max_score, a.grader_feedback
      FROM exam_submissions s
      LEFT JOIN submission_answers a ON s.id = a.submission_id
      WHERE s.id = ?
      ORDER BY a.question_number
    """
    val params = List(SqlParameter("uuid", submissionId))

    DatabaseManager.executeQuery(sql, params).map { results =>
      if (results.isEmpty) {
        Left("提交记录不存在")
      } else {
        try {
          val firstRow = results.head
          val examId = DatabaseManager.decodeFieldUnsafe[String](firstRow, "exam_id")
          val studentId = DatabaseManager.decodeFieldUnsafe[String](firstRow, "student_id")
          val studentUsername = DatabaseManager.decodeFieldUnsafe[String](firstRow, "student_username")
          val coachId = DatabaseManager.decodeFieldOptional[String](firstRow, "coach_id")
          val isProxy = DatabaseManager.decodeFieldUnsafe[Boolean](firstRow, "is_proxy_submission")
          val submissionTime = DatabaseManager.decodeFieldUnsafe[LocalDateTime](firstRow, "submission_time")
          val status = DatabaseManager.decodeFieldUnsafe[String](firstRow, "status")
          val totalScore = DatabaseManager.decodeFieldOptional[BigDecimal](firstRow, "total_score")
          val maxScore = DatabaseManager.decodeFieldOptional[BigDecimal](firstRow, "max_score")
          val feedback = DatabaseManager.decodeFieldOptional[String](firstRow, "feedback")

          val answers = results.filter(row => 
            DatabaseManager.decodeFieldOptional[Int](row, "question_number").isDefined
          ).map { row =>
            SubmissionAnswer(
              questionNumber = DatabaseManager.decodeFieldUnsafe[Int](row, "question_number"),
              answerImageUrl = DatabaseManager.decodeFieldOptional[String](row, "answer_image_url"),
              uploadTime = DatabaseManager.decodeFieldUnsafe[LocalDateTime](row, "upload_time"),
              score = DatabaseManager.decodeFieldOptional[BigDecimal](row, "score"),
              maxScore = DatabaseManager.decodeFieldOptional[BigDecimal](row, "max_score"),
              graderFeedback = DatabaseManager.decodeFieldOptional[String](row, "grader_feedback")
            )
          }

          Right(ExamSubmission(
            id = submissionId,
            examId = examId,
            studentId = studentId,
            studentUsername = studentUsername,
            coachId = coachId,
            isProxySubmission = isProxy,
            submissionTime = submissionTime,
            status = SubmissionStatus.fromString(status),
            totalScore = totalScore,
            maxScore = maxScore,
            feedback = feedback,
            answers = answers
          ))
        } catch {
          case e: Exception =>
            logger.error("解析提交记录失败", e)
            Left(s"数据解析失败: ${e.getMessage}")
        }
      }
    }.handleErrorWith { error =>
      logger.error(s"查询提交记录失败: submissionId=$submissionId", error)
      IO.pure(Left(s"查询失败: ${error.getMessage}"))
    }
  }

  private def getSubmissionsByCoach(examId: String, coachUsername: String): IO[Either[String, List[ExamSubmission]]] = {
    val sql = """
      SELECT s.*, a.question_number, a.answer_image_url, a.upload_time, a.score, a.max_score, a.grader_feedback
      FROM exam_submissions s
      LEFT JOIN submission_answers a ON s.id = a.submission_id
      WHERE s.exam_id = ? AND s.coach_id = ?
      ORDER BY s.student_username, a.question_number
    """
    val params = List(
      SqlParameter("uuid", examId),
      SqlParameter("string", coachUsername)
    )

    DatabaseManager.executeQuery(sql, params).map { results =>
      try {
        val submissionsMap = results.groupBy(row => 
          DatabaseManager.decodeFieldUnsafe[String](row, "id")
        ).map { case (submissionId, rows) =>
          val firstRow = rows.head
          val examIdStr = DatabaseManager.decodeFieldUnsafe[String](firstRow, "exam_id")
          val studentId = DatabaseManager.decodeFieldUnsafe[String](firstRow, "student_id")
          val studentUsername = DatabaseManager.decodeFieldUnsafe[String](firstRow, "student_username")
          val coachId = DatabaseManager.decodeFieldOptional[String](firstRow, "coach_id")
          val isProxy = DatabaseManager.decodeFieldUnsafe[Boolean](firstRow, "is_proxy_submission")
          val submissionTime = DatabaseManager.decodeFieldUnsafe[LocalDateTime](firstRow, "submission_time")
          val status = DatabaseManager.decodeFieldUnsafe[String](firstRow, "status")
          val totalScore = DatabaseManager.decodeFieldOptional[BigDecimal](firstRow, "total_score")
          val maxScore = DatabaseManager.decodeFieldOptional[BigDecimal](firstRow, "max_score")
          val feedback = DatabaseManager.decodeFieldOptional[String](firstRow, "feedback")

          val answers = rows.filter(row => 
            DatabaseManager.decodeFieldOptional[Int](row, "question_number").isDefined
          ).map { row =>
            SubmissionAnswer(
              questionNumber = DatabaseManager.decodeFieldUnsafe[Int](row, "question_number"),
              answerImageUrl = DatabaseManager.decodeFieldOptional[String](row, "answer_image_url"),
              uploadTime = DatabaseManager.decodeFieldUnsafe[LocalDateTime](row, "upload_time"),
              score = DatabaseManager.decodeFieldOptional[BigDecimal](row, "score"),
              maxScore = DatabaseManager.decodeFieldOptional[BigDecimal](row, "max_score"),
              graderFeedback = DatabaseManager.decodeFieldOptional[String](row, "grader_feedback")
            )
          }

          ExamSubmission(
            id = submissionId,
            examId = examIdStr,
            studentId = studentId,
            studentUsername = studentUsername,
            coachId = coachId,
            isProxySubmission = isProxy,
            submissionTime = submissionTime,
            status = SubmissionStatus.fromString(status),
            totalScore = totalScore,
            maxScore = maxScore,
            feedback = feedback,
            answers = answers
          )
        }.toList

        Right(submissionsMap)
      } catch {
        case e: Exception =>
          logger.error("解析教练提交记录失败", e)
          Left(s"数据解析失败: ${e.getMessage}")
      }
    }.handleErrorWith { error =>
      logger.error(s"查询教练提交记录失败: examId=$examId, coach=$coachUsername", error)
      IO.pure(Left(s"查询失败: ${error.getMessage}"))
    }
  }

  private def calculateGradingProgress(examId: String): IO[Either[String, GradingProgress]] = {
    val sql = """
      SELECT 
        COUNT(*) as total_submissions,
        COUNT(CASE WHEN status = 'graded' THEN 1 END) as graded_submissions,
        AVG(CASE WHEN total_score IS NOT NULL THEN total_score END) as average_score
      FROM exam_submissions 
      WHERE exam_id = ?
    """
    val params = List(SqlParameter("uuid", examId))

    DatabaseManager.executeQuery(sql, params).map { results =>
      if (results.isEmpty) {
        Right(GradingProgress(examId, 0, 0, None))
      } else {
        try {
          val row = results.head
          val totalSubmissions = DatabaseManager.decodeFieldUnsafe[Long](row, "total_submissions").toInt
          val gradedSubmissions = DatabaseManager.decodeFieldUnsafe[Long](row, "graded_submissions").toInt
          val averageScore = DatabaseManager.decodeFieldOptional[BigDecimal](row, "average_score")

          Right(GradingProgress(
            examId = examId,
            totalSubmissions = totalSubmissions,
            gradedSubmissions = gradedSubmissions,
            averageScore = averageScore
          ))
        } catch {
          case e: Exception =>
            logger.error("计算阅卷进度失败", e)
            Left(s"数据处理失败: ${e.getMessage}")
        }
      }
    }.handleErrorWith { error =>
      logger.error(s"查询阅卷进度失败: examId=$examId", error)
      IO.pure(Left(s"查询失败: ${error.getMessage}"))
    }
  }
}
