package Services

import cats.effect.IO
import cats.implicits.*
import org.http4s.*
import org.http4s.client.Client
import org.http4s.circe.*
import org.http4s.headers.*
import io.circe.generic.auto.*
import io.circe.parser.*
import io.circe.syntax.*
import Models.*
import Database.*
import Config.ServiceConfig
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID

trait SubmissionService {
  def submitExamAnswers(examId: String, userId: String, request: StudentSubmissionRequest): IO[Either[ServiceError, ExamSubmission]]
  def getStudentSubmission(examId: String, studentUsername: String): IO[Either[ServiceError, ExamSubmission]]
  def coachProxySubmission(examId: String, coachId: String, request: CoachSubmissionRequest): IO[Either[ServiceError, ExamSubmission]]
  def getCoachManagedSubmissions(examId: String, coachId: String, studentUsername: Option[String]): IO[Either[ServiceError, List[ExamSubmission]]]
  def getSubmissionForGrader(submissionId: String): IO[Either[ServiceError, ExamSubmission]]
  def getGradingProgress(examId: String, graderId: String): IO[Either[ServiceError, GradingProgress]]
}

class SubmissionServiceImpl(
  submissionDAO: SubmissionDAO,
  answerDAO: AnswerDAO,
  authService: AuthService,
  config: ServiceConfig,
  client: Client[IO]
) extends SubmissionService {
  private val logger = LoggerFactory.getLogger("SubmissionServiceImpl")

  override def submitExamAnswers(examId: String, userId: String, request: StudentSubmissionRequest): IO[Either[ServiceError, ExamSubmission]] = {
    for {
      // Validate user and get user info
      userInfoResult <- authService.getUserInfo(userId)
      userInfo <- userInfoResult match {
        case Right(info) => IO.pure(info)
        case Left(error) => IO.raiseError(new RuntimeException(error.message))
      }

      // Check if user is an independent student
      _ <- if (userInfo.role != "student" || userInfo.isIndependent.contains(false)) {
        IO.raiseError(new RuntimeException("Only independent students can submit directly"))
      } else IO.unit

      // Validate exam exists and is active (call to ExamService)
      examValid <- validateExamExists(examId)
      _ <- if (!examValid) IO.raiseError(new RuntimeException("Exam not found or not active")) else IO.unit

      // Check if submission already exists
      existingSubmissions <- submissionDAO.getSubmissionsByExamAndStudent(examId, userInfo.username)
      
      submissionResult <- existingSubmissions.headOption match {
        case Some(existing) =>
          // Update existing submission
          updateSubmission(existing.id, request.answers)
        case None =>
          // Create new submission
          createNewSubmission(examId, userInfo, request.answers)
      }
    } yield Right(submissionResult)
  }.handleError { error =>
    logger.error(s"Error submitting exam answers: ${error.getMessage}")
    Left(ServiceError.internalError(error.getMessage))
  }

  override def getStudentSubmission(examId: String, studentUsername: String): IO[Either[ServiceError, ExamSubmission]] = {
    for {
      submissions <- submissionDAO.getSubmissionsByExamAndStudent(examId, studentUsername)
      result <- submissions.headOption match {
        case Some(submission) =>
          buildExamSubmission(submission).map(Right(_))
        case None =>
          IO.pure(Left(ServiceError.notFound("No submission found")))
      }
    } yield result
  }.handleError { error =>
    logger.error(s"Error getting student submission: ${error.getMessage}")
    Left(ServiceError.internalError(error.getMessage))
  }

  override def coachProxySubmission(examId: String, coachId: String, request: CoachSubmissionRequest): IO[Either[ServiceError, ExamSubmission]] = {
    for {
      // Validate coach-student relationship
      hasRelation <- authService.checkCoachStudentRelation(coachId, request.studentUsername)
      _ <- if (!hasRelation) {
        IO.raiseError(new RuntimeException("Coach does not manage this student"))
      } else IO.unit

      // Get student info
      userInfoResult <- authService.getUserInfo(coachId) // This will be used to get student ID later
      
      // Validate exam exists
      examValid <- validateExamExists(examId)
      _ <- if (!examValid) IO.raiseError(new RuntimeException("Exam not found or not active")) else IO.unit

      // Check if submission already exists
      existingSubmissions <- submissionDAO.getSubmissionsByExamAndStudent(examId, request.studentUsername)
      
      submissionResult <- existingSubmissions.headOption match {
        case Some(existing) =>
          updateSubmission(existing.id, request.answers, Some(coachId))
        case None =>
          createNewProxySubmission(examId, request.studentUsername, coachId, request.answers)
      }
    } yield Right(submissionResult)
  }.handleError { error =>
    logger.error(s"Error with coach proxy submission: ${error.getMessage}")
    Left(ServiceError.internalError(error.getMessage))
  }

  override def getCoachManagedSubmissions(examId: String, coachId: String, studentUsername: Option[String]): IO[Either[ServiceError, List[ExamSubmission]]] = {
    for {
      submissions <- studentUsername match {
        case Some(username) =>
          // Check coach-student relationship first
          for {
            hasRelation <- authService.checkCoachStudentRelation(coachId, username)
            result <- if (hasRelation) {
              submissionDAO.getSubmissionsByExamAndStudent(examId, username)
            } else {
              IO.pure(List.empty[SubmissionEntity])
            }
          } yield result
        case None =>
          // Get all submissions for students managed by this coach
          // This would require additional logic to get all coach's students
          submissionDAO.getSubmissionsByExam(examId).map(_.filter(_.submittedBy.contains(coachId)))
      }
      
      examSubmissions <- submissions.traverse(buildExamSubmission)
    } yield Right(examSubmissions)
  }.handleError { error =>
    logger.error(s"Error getting coach managed submissions: ${error.getMessage}")
    Left(ServiceError.internalError(error.getMessage))
  }

  override def getSubmissionForGrader(submissionId: String): IO[Either[ServiceError, ExamSubmission]] = {
    for {
      submissionOpt <- submissionDAO.getSubmissionById(submissionId)
      submission <- submissionOpt match {
        case Some(sub) => IO.pure(sub)
        case None => IO.raiseError(new RuntimeException("Submission not found"))
      }
      examSubmission <- buildExamSubmission(submission)
    } yield Right(examSubmission)
  }.handleError { error =>
    logger.error(s"Error getting submission for grader: ${error.getMessage}")
    Left(ServiceError.internalError(error.getMessage))
  }

  override def getGradingProgress(examId: String, graderId: String): IO[Either[ServiceError, GradingProgress]] = {
    for {
      // Get all submissions for the exam
      allSubmissions <- submissionDAO.getSubmissionsByExam(examId)
      
      // Get grader's completed tasks
      graderSubmissions <- submissionDAO.getSubmissionsByGrader(graderId)
      graderTasksForExam = graderSubmissions.count(_.examId == examId)
      
      // Calculate progress
      totalSubmissions = allSubmissions.length
      gradedSubmissions = allSubmissions.count(_.status == "graded")
      pendingSubmissions = totalSubmissions - gradedSubmissions
      progress = if (totalSubmissions > 0) (gradedSubmissions.toDouble / totalSubmissions * 100) else 0.0
      
      // Get exam info (title) - this would require calling ExamService
      examTitle <- getExamTitle(examId).map(_.getOrElse(s"Exam $examId"))
      
      gradingProgress = GradingProgress(
        examId = examId,
        examTitle = examTitle,
        totalSubmissions = totalSubmissions,
        gradedSubmissions = gradedSubmissions,
        pendingSubmissions = pendingSubmissions,
        myCompletedTasks = graderTasksForExam,
        progress = progress
      )
    } yield Right(gradingProgress)
  }.handleError { error =>
    logger.error(s"Error getting grading progress: ${error.getMessage}")
    Left(ServiceError.internalError(error.getMessage))
  }

  // Helper methods

  private def createNewSubmission(examId: String, userInfo: UserInfo, answers: List[AnswerSubmission]): IO[ExamSubmission] = {
    for {
      submissionId <- IO.pure(UUID.randomUUID().toString)
      
      submissionEntity = SubmissionEntity(
        id = submissionId,
        examId = examId,
        studentId = userInfo.id,
        studentUsername = userInfo.username,
        submittedAt = LocalDateTime.now(),
        status = "submitted"
      )
      
      _ <- submissionDAO.createSubmission(submissionEntity)
      _ <- createAnswerEntities(submissionId, answers)
      
      result <- buildExamSubmission(submissionEntity)
    } yield result
  }

  private def createNewProxySubmission(examId: String, studentUsername: String, coachId: String, answers: List[AnswerSubmission]): IO[ExamSubmission] = {
    for {
      submissionId <- IO.pure(UUID.randomUUID().toString)
      
      // Get student ID (simplified - in real implementation, call UserService)
      studentId <- IO.pure(UUID.randomUUID().toString) // This should be retrieved from UserService
      
      submissionEntity = SubmissionEntity(
        id = submissionId,
        examId = examId,
        studentId = studentId,
        studentUsername = studentUsername,
        submittedAt = LocalDateTime.now(),
        status = "submitted",
        submittedBy = Some(coachId)
      )
      
      _ <- submissionDAO.createSubmission(submissionEntity)
      _ <- createAnswerEntities(submissionId, answers)
      
      result <- buildExamSubmission(submissionEntity)
    } yield result
  }

  private def updateSubmission(submissionId: String, answers: List[AnswerSubmission], submittedBy: Option[String] = None): IO[ExamSubmission] = {
    for {
      // Delete existing answers
      _ <- answerDAO.deleteAnswersBySubmission(submissionId)
      
      // Create new answers
      _ <- createAnswerEntities(submissionId, answers)
      
      // Get updated submission
      submissionOpt <- submissionDAO.getSubmissionById(submissionId)
      submission <- submissionOpt match {
        case Some(sub) => IO.pure(sub)
        case None => IO.raiseError(new RuntimeException("Submission not found after update"))
      }
      
      result <- buildExamSubmission(submission)
    } yield result
  }

  private def createAnswerEntities(submissionId: String, answers: List[AnswerSubmission]): IO[Unit] = {
    answers.traverse { answer =>
      val answerEntity = AnswerEntity(
        id = UUID.randomUUID().toString,
        submissionId = submissionId,
        questionId = s"q${answer.questionNumber}", // Simplified
        questionNumber = answer.questionNumber,
        imageUrl = Some(answer.imageUrl),
        uploadTime = Some(answer.uploadTime),
        maxScore = 10.0 // Default - should be retrieved from exam questions
      )
      answerDAO.createAnswer(answerEntity)
    }.void
  }

  private def buildExamSubmission(submission: SubmissionEntity): IO[ExamSubmission] = {
    for {
      answers <- answerDAO.getAnswersBySubmission(submission.id)
      examAnswers = answers.map { answer =>
        ExamAnswer(
          questionId = answer.questionId,
          questionNumber = answer.questionNumber,
          answer = answer.answer,
          score = answer.score,
          maxScore = answer.maxScore,
          comments = answer.comments,
          imageUrl = answer.imageUrl,
          uploadTime = answer.uploadTime,
          graderId = answer.graderId,
          graderName = answer.graderName,
          gradedAt = answer.gradedAt
        )
      }
    } yield ExamSubmission(
      id = submission.id,
      examId = submission.examId,
      studentId = submission.studentId,
      studentUsername = submission.studentUsername,
      answers = examAnswers,
      submittedAt = submission.submittedAt,
      status = submission.status match {
        case "submitted" => SubmissionStatus.Submitted
        case "grading" => SubmissionStatus.Grading
        case "graded" => SubmissionStatus.Graded
        case _ => SubmissionStatus.Submitted
      },
      totalScore = submission.totalScore,
      maxScore = submission.maxScore,
      gradedAt = submission.gradedAt,
      gradedBy = submission.gradedBy,
      feedback = submission.feedback
    )
  }

  private def validateExamExists(examId: String): IO[Boolean] = {
    // Call to ExamService to validate exam exists and is active
    val uri = Uri.unsafeFromString(s"http://${config.externalServices.examService.host}:${config.externalServices.examService.port}/api/internal/exams/$examId/validate")
    
    val request = Request[IO](
      method = Method.GET,
      uri = uri,
      headers = Headers(
        Authorization(Credentials.Token(AuthScheme.Bearer, config.jwtSecret))
      )
    )

    client.expect[String](request).attempt.flatMap {
      case Right(response) =>
        IO {
          decode[ApiResponse[Boolean]](response) match {
            case Right(apiResponse) => apiResponse.success && apiResponse.data.getOrElse(false)
            case Left(_) => false
          }
        }
      case Left(error) =>
        logger.warn(s"Failed to validate exam: $error")
        IO.pure(false)
    }
  }

  private def getExamTitle(examId: String): IO[Option[String]] = {
    // Call to ExamService to get exam title
    val uri = Uri.unsafeFromString(s"http://${config.externalServices.examService.host}:${config.externalServices.examService.port}/api/internal/exams/$examId")
    
    val request = Request[IO](
      method = Method.GET,
      uri = uri,
      headers = Headers(
        Authorization(Credentials.Token(AuthScheme.Bearer, config.jwtSecret))
      )
    )

    client.expect[String](request).attempt.flatMap {
      case Right(response) =>
        IO {
          decode[ApiResponse[ExamInfo]](response) match {
            case Right(apiResponse) if apiResponse.success =>
              apiResponse.data.map(_.title)
            case _ => None
          }
        }
      case Left(error) =>
        logger.warn(s"Failed to get exam title: $error")
        IO.pure(None)
    }
  }
}
