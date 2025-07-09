package Services

import cats.effect.IO
import io.circe.*
import io.circe.generic.auto.*
import io.circe.parser.*
import Models.*
import Database.{DatabaseManager, SqlParameter}
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ScoreStatisticsService {
  private val logger = LoggerFactory.getLogger("ScoreStatisticsService")

  // 学生成绩查看和仪表板API实现

  /**
   * 获取学生考试成绩
   * GET /api/student/exams/{examId}/score
   */
  def getStudentExamScore(examId: Int, studentId: Int): IO[Option[StudentScoreResponse]] = {
    val sql = """
      SELECT 
        es.exam_id,
        es.student_id,
        es.total_score,
        es.rank_position,
        es.percentile,
        es.question_scores,
        'Mock Exam' as exam_title,
        es.created_at as exam_date
      FROM exam_scores es
      WHERE es.exam_id = ? AND es.student_id = ?
    """
    
    val params = List(
      SqlParameter("int", examId),
      SqlParameter("int", studentId)
    )
    
    DatabaseManager.executeQueryOptional(sql, params).map { optJson =>
      optJson.map { json =>
        StudentScoreResponse(
          examId = DatabaseManager.decodeFieldUnsafe[Int](json, "exam_id"),
          studentId = DatabaseManager.decodeFieldUnsafe[Int](json, "student_id"),
          totalScore = DatabaseManager.decodeFieldUnsafe[Double](json, "total_score"),
          rank = DatabaseManager.decodeFieldUnsafe[Int](json, "rank_position"),
          percentile = DatabaseManager.decodeFieldUnsafe[Double](json, "percentile"),
          questionScores = DatabaseManager.decodeFieldOptional[String](json, "question_scores")
            .map(s => parse(s).getOrElse(Json.obj()).as[Map[String, Double]].getOrElse(Map.empty))
            .getOrElse(Map.empty),
          examTitle = DatabaseManager.decodeFieldUnsafe[String](json, "exam_title"),
          examDate = DatabaseManager.decodeFieldUnsafe[String](json, "exam_date")
        )
      }
    }
  }

  /**
   * 获取学生考试排名
   * GET /api/student/exams/{examId}/ranking
   */
  def getStudentExamRanking(examId: Int, studentId: Int): IO[Option[StudentRankingResponse]] = {
    val sql = """
      SELECT 
        es.exam_id,
        es.student_id,
        es.rank_position,
        es.total_score,
        es.percentile,
        (SELECT COUNT(*) FROM exam_scores WHERE exam_id = es.exam_id) as total_participants,
        'Mock Exam' as exam_title
      FROM exam_scores es
      WHERE es.exam_id = ? AND es.student_id = ?
    """
    
    val params = List(
      SqlParameter("int", examId),
      SqlParameter("int", studentId)
    )
    
    DatabaseManager.executeQueryOptional(sql, params).map { optJson =>
      optJson.map { json =>
        StudentRankingResponse(
          examId = DatabaseManager.decodeFieldUnsafe[Int](json, "exam_id"),
          studentId = DatabaseManager.decodeFieldUnsafe[Int](json, "student_id"),
          rank = DatabaseManager.decodeFieldUnsafe[Int](json, "rank_position"),
          totalScore = DatabaseManager.decodeFieldUnsafe[Double](json, "total_score"),
          percentile = DatabaseManager.decodeFieldUnsafe[Double](json, "percentile"),
          totalParticipants = DatabaseManager.decodeFieldUnsafe[Int](json, "total_participants"),
          examTitle = DatabaseManager.decodeFieldUnsafe[String](json, "exam_title")
        )
      }
    }
  }

  /**
   * 获取学生历史成绩
   * GET /api/student/scores
   */
  def getStudentScores(studentId: Int): IO[List[StudentScoreResponse]] = {
    val sql = """
      SELECT 
        es.exam_id,
        es.student_id,
        es.total_score,
        es.rank_position,
        es.percentile,
        es.question_scores,
        'Mock Exam ' || es.exam_id as exam_title,
        es.created_at as exam_date
      FROM exam_scores es
      WHERE es.student_id = ?
      ORDER BY es.created_at DESC
    """
    
    val params = List(SqlParameter("int", studentId))
    
    DatabaseManager.executeQuery(sql, params).map { jsonList =>
      jsonList.map { json =>
        StudentScoreResponse(
          examId = DatabaseManager.decodeFieldUnsafe[Int](json, "exam_id"),
          studentId = DatabaseManager.decodeFieldUnsafe[Int](json, "student_id"),
          totalScore = DatabaseManager.decodeFieldUnsafe[Double](json, "total_score"),
          rank = DatabaseManager.decodeFieldUnsafe[Int](json, "rank_position"),
          percentile = DatabaseManager.decodeFieldUnsafe[Double](json, "percentile"),
          questionScores = DatabaseManager.decodeFieldOptional[String](json, "question_scores")
            .map(s => parse(s).getOrElse(Json.obj()).as[Map[String, Double]].getOrElse(Map.empty))
            .getOrElse(Map.empty),
          examTitle = DatabaseManager.decodeFieldUnsafe[String](json, "exam_title"),
          examDate = DatabaseManager.decodeFieldUnsafe[String](json, "exam_date")
        )
      }
    }
  }

  /**
   * 获取学生仪表板统计数据
   * GET /api/student/dashboard/stats
   */
  def getStudentDashboardStats(studentId: Int): IO[DashboardStats] = {
    val sql = """
      SELECT 
        ss.total_exams,
        ss.total_exams as total_scores,
        ss.average_score,
        ss.improvement_trend,
        (SELECT MIN(rank_position) FROM exam_scores WHERE student_id = ss.student_id) as best_rank
      FROM student_statistics ss
      WHERE ss.student_id = ?
    """
    
    val params = List(SqlParameter("int", studentId))
    
    DatabaseManager.executeQueryOptional(sql, params).map { optJson =>
      optJson.map { json =>
        DashboardStats(
          totalExams = DatabaseManager.decodeFieldUnsafe[Int](json, "total_exams"),
          totalScores = DatabaseManager.decodeFieldUnsafe[Int](json, "total_scores"),
          averageScore = DatabaseManager.decodeFieldUnsafe[Double](json, "average_score"),
          improvementRate = DatabaseManager.decodeFieldUnsafe[Double](json, "improvement_trend"),
          rankingPosition = DatabaseManager.decodeFieldOptional[Int](json, "best_rank"),
          recentActivity = List.empty,
          performanceMetrics = PerformanceMetrics(
            strongSubjects = List.empty,
            weakSubjects = List.empty
          )
        )
      }.getOrElse(DashboardStats(0, 0, 0.0, 0.0, None, List.empty, PerformanceMetrics()))
    }
  }

  // 教练成绩管理和仪表板API实现

  /**
   * 获取教练成绩概览
   * GET /api/coach/grades/overview
   */
  def getCoachGradesOverview(coachId: Int): IO[CoachGradesOverview] = {
    val sql = """
      SELECT 
        cs.total_students,
        cs.total_exams,
        cs.average_student_score,
        cs.best_student_score,
        cs.class_performance
      FROM coach_statistics cs
      WHERE cs.coach_id = ?
    """
    
    val params = List(SqlParameter("int", coachId))
    
    DatabaseManager.executeQueryOptional(sql, params).map { optJson =>
      optJson.map { json =>
        CoachGradesOverview(
          totalStudents = DatabaseManager.decodeFieldUnsafe[Int](json, "total_students"),
          totalExams = DatabaseManager.decodeFieldUnsafe[Int](json, "total_exams"),
          averageClassScore = DatabaseManager.decodeFieldUnsafe[Double](json, "average_student_score"),
          bestPerformingStudent = Some(BestPerformingStudent(
            score = DatabaseManager.decodeFieldUnsafe[Double](json, "best_student_score"),
            name = "Top Student"
          )),
          recentExamResults = List.empty,
          performanceTrends = PerformanceTrends("improving")
        )
      }.getOrElse(CoachGradesOverview(0, 0, 0.0, None, List.empty, PerformanceTrends("stable")))
    }
  }

  /**
   * 获取教练成绩详情
   * GET /api/coach/grades/details
   */
  def getCoachGradesDetails(coachId: Int): IO[CoachGradesDetails] = {
    IO.pure(CoachGradesDetails(
      students = List.empty,
      examResults = List.empty,
      statisticalAnalysis = StatisticalAnalysis(0.0),
      performanceComparison = PerformanceComparison("average")
    ))
  }

  /**
   * 获取教练学生成绩
   * GET /api/coach/students/scores
   */
  def getCoachStudentsScores(coachId: Int): IO[List[CoachStudentScoreResponse]] = {
    IO.pure(List.empty)
  }

  /**
   * 获取教练查看学生成绩
   * GET /api/coach/students/{studentId}/exams/{examId}/score
   */
  def getCoachStudentExamScore(coachId: Int, studentId: Int, examId: Int): IO[Option[StudentScoreResponse]] = {
    // 复用学生成绩查询逻辑
    getStudentExamScore(examId, studentId)
  }

  /**
   * 获取教练仪表板统计数据
   * GET /api/coach/dashboard/stats
   */
  def getCoachDashboardStats(coachId: Int): IO[DashboardStats] = {
    val sql = """
      SELECT 
        cs.total_students,
        cs.total_exams,
        cs.average_student_score,
        cs.best_student_score
      FROM coach_statistics cs
      WHERE cs.coach_id = ?
    """
    
    val params = List(SqlParameter("int", coachId))
    
    DatabaseManager.executeQueryOptional(sql, params).map { optJson =>
      optJson.map { json =>
        DashboardStats(
          totalExams = DatabaseManager.decodeFieldUnsafe[Int](json, "total_exams"),
          totalScores = DatabaseManager.decodeFieldUnsafe[Int](json, "total_students"),
          averageScore = DatabaseManager.decodeFieldUnsafe[Double](json, "average_student_score"),
          improvementRate = 0.0,
          rankingPosition = None,
          recentActivity = List.empty,
          performanceMetrics = PerformanceMetrics(
            bestScore = Some(DatabaseManager.decodeFieldUnsafe[Double](json, "best_student_score")),
            totalStudents = Some(DatabaseManager.decodeFieldUnsafe[Int](json, "total_students"))
          )
        )
      }.getOrElse(DashboardStats(0, 0, 0.0, 0.0, None, List.empty, PerformanceMetrics()))
    }
  }

  // 阅卷员统计和仪表板API实现

  /**
   * 获取阅卷员统计数据（简化版，不含平均分）
   * GET /api/grader/statistics
   */
  def getGraderStatistics(graderId: Int): IO[GraderStatisticsResponse] = {
    val sql = """
      SELECT 
        gs.total_graded,
        gs.grading_accuracy,
        gs.grading_speed
      FROM grader_statistics gs
      WHERE gs.grader_id = ?
    """
    
    val params = List(SqlParameter("int", graderId))
    
    DatabaseManager.executeQueryOptional(sql, params).map { optJson =>
      optJson.map { json =>
        GraderStatisticsResponse(
          totalGraded = DatabaseManager.decodeFieldUnsafe[Int](json, "total_graded"),
          gradingAccuracy = DatabaseManager.decodeFieldUnsafe[Double](json, "grading_accuracy"),
          gradingSpeed = DatabaseManager.decodeFieldUnsafe[Double](json, "grading_speed")
        )
      }.getOrElse(GraderStatisticsResponse(
        totalGraded = 0,
        gradingAccuracy = 0.0,
        gradingSpeed = 0.0
      ))
    }
  }

  /**
   * 获取阅卷员仪表板统计数据（简化版）
   * GET /api/grader/dashboard/stats
   */
  def getGraderDashboardStats(graderId: Int): IO[GraderDashboardStats] = {
    // 由于我们需要跨服务数据，这里暂时返回模拟数据
    // 实际应该从GradingService查询阅卷任务统计
    val mockStats = GraderDashboardStats(
      totalTasks = 15,        // 总任务数
      completedTasks = 8,     // 已完成任务数
      pendingTasks = 5,       // 待处理任务数
      totalScores = 850,      // 总分数（示例）
      recentActivities = List(
        RecentActivity("grading", "完成数学考试第1题阅卷", LocalDateTime.now().toString),
        RecentActivity("grading", "完成语文考试第2题阅卷", LocalDateTime.now().minusHours(1).toString),
        RecentActivity("grading", "开始物理考试第1题阅卷", LocalDateTime.now().minusHours(2).toString)
      )
    )
    
    IO.pure(mockStats)
  }

  /**
   * 获取阅卷员历史记录
   * GET /api/grader/history
   */
  def getGraderHistory(graderId: Int): IO[GraderHistoryResponse] = {
    val sql = """
      SELECT 
        gs.grader_id,
        gs.total_graded,
        gs.grading_history,
        gs.grading_accuracy,
        gs.grading_speed
      FROM grader_statistics gs
      WHERE gs.grader_id = ?
    """
    
    val params = List(SqlParameter("int", graderId))
    
    DatabaseManager.executeQueryOptional(sql, params).map { optJson =>
      optJson.map { json =>
        GraderHistoryResponse(
          graderId = DatabaseManager.decodeFieldUnsafe[Int](json, "grader_id"),
          totalGraded = DatabaseManager.decodeFieldUnsafe[Int](json, "total_graded"),
          recentGradingTasks = List.empty,
          gradingStatistics = GradingStatisticsData(
            accuracy = DatabaseManager.decodeFieldUnsafe[Double](json, "grading_accuracy"),
            speed = DatabaseManager.decodeFieldUnsafe[Double](json, "grading_speed")
          ),
          performanceMetrics = PerformanceMetrics(
            totalGraded = Some(DatabaseManager.decodeFieldUnsafe[Int](json, "total_graded"))
          )
        )
      }.getOrElse(GraderHistoryResponse(
        graderId = graderId,
        totalGraded = 0,
        recentGradingTasks = List.empty,
        gradingStatistics = GradingStatisticsData(0.0, 0.0),
        performanceMetrics = PerformanceMetrics()
      ))
    }
  }

  // 管理员仪表板API实现

  /**
   * 获取管理员仪表板统计数据
   * GET /api/admin/dashboard/stats
   */
  def getAdminDashboardStats(): IO[DashboardStats] = {
    val sql = """
      SELECT 
        ss.total_users,
        ss.total_students,
        ss.total_coaches,
        ss.total_graders,
        ss.total_exams,
        ss.total_submissions
      FROM system_statistics ss
      WHERE ss.stat_date = CURRENT_DATE
    """
    
    DatabaseManager.executeQueryOptional(sql).map { optJson =>
      optJson.map { json =>
        DashboardStats(
          totalExams = DatabaseManager.decodeFieldUnsafe[Int](json, "total_exams"),
          totalScores = DatabaseManager.decodeFieldUnsafe[Int](json, "total_submissions"),
          averageScore = 0.0,
          improvementRate = 0.0,
          rankingPosition = None,
          recentActivity = List.empty,
          performanceMetrics = PerformanceMetrics(
            totalUsers = Some(DatabaseManager.decodeFieldUnsafe[Int](json, "total_users")),
            totalStudents = Some(DatabaseManager.decodeFieldUnsafe[Int](json, "total_students")),
            totalCoaches = Some(DatabaseManager.decodeFieldUnsafe[Int](json, "total_coaches")),
            totalGraders = Some(DatabaseManager.decodeFieldUnsafe[Int](json, "total_graders")),
            totalExams = Some(DatabaseManager.decodeFieldUnsafe[Int](json, "total_exams")),
            totalSubmissions = Some(DatabaseManager.decodeFieldUnsafe[Int](json, "total_submissions"))
          )
        )
      }.getOrElse(DashboardStats(0, 0, 0.0, 0.0, None, List.empty, PerformanceMetrics()))
    }
  }

  // 辅助方法：更新统计数据
  def updateExamStatistics(examId: Int): IO[Unit] = {
    val sql = """
      INSERT INTO exam_statistics (exam_id, total_submissions, average_score, highest_score, lowest_score)
      SELECT 
        ?, 
        COUNT(*) as total_submissions,
        AVG(total_score) as average_score,
        MAX(total_score) as highest_score,
        MIN(total_score) as lowest_score
      FROM exam_scores 
      WHERE exam_id = ?
      ON CONFLICT (exam_id) DO UPDATE SET
        total_submissions = EXCLUDED.total_submissions,
        average_score = EXCLUDED.average_score,
        highest_score = EXCLUDED.highest_score,
        lowest_score = EXCLUDED.lowest_score,
        updated_at = CURRENT_TIMESTAMP
    """
    
    val params = List(
      SqlParameter("int", examId),
      SqlParameter("int", examId)
    )
    
    DatabaseManager.executeUpdate(sql, params).map(_ => ())
  }

  def updateStudentStatistics(studentId: Int): IO[Unit] = {
    val sql = """
      INSERT INTO student_statistics (student_id, total_exams, average_score, best_score, worst_score)
      SELECT 
        ?, 
        COUNT(*) as total_exams,
        AVG(total_score) as average_score,
        MAX(total_score) as best_score,
        MIN(total_score) as worst_score
      FROM exam_scores 
      WHERE student_id = ?
      ON CONFLICT (student_id) DO UPDATE SET
        total_exams = EXCLUDED.total_exams,
        average_score = EXCLUDED.average_score,
        best_score = EXCLUDED.best_score,
        worst_score = EXCLUDED.worst_score,
        updated_at = CURRENT_TIMESTAMP
    """
    
    val params = List(
      SqlParameter("int", studentId),
      SqlParameter("int", studentId)
    )
    
    DatabaseManager.executeUpdate(sql, params).map(_ => ())
  }
}
