package Models

import io.circe.{Decoder, Encoder}
import io.circe.generic.auto.*
import java.time.LocalDateTime

// 性能指标数据结构
case class PerformanceMetrics(
  strongSubjects: List[String] = List.empty,
  weakSubjects: List[String] = List.empty,
  bestScore: Option[Double] = None,
  totalStudents: Option[Int] = None,
  totalGraded: Option[Int] = None,
  accuracy: Option[Double] = None,
  totalUsers: Option[Int] = None,
  totalCoaches: Option[Int] = None,
  totalGraders: Option[Int] = None,
  totalExams: Option[Int] = None,
  totalSubmissions: Option[Int] = None
)

// 最佳表现学生数据结构
case class BestPerformingStudent(
  score: Double,
  name: String
)

// 表现趋势数据结构
case class PerformanceTrends(
  trend: String
)

// 统计分析数据结构
case class StatisticalAnalysis(
  averageScore: Double
)

// 表现比较数据结构
case class PerformanceComparison(
  comparison: String
)

// 阅卷统计数据结构
case class GradingStatisticsData(
  accuracy: Double,
  speed: Double
)

// 最近活动数据结构
case class RecentActivity(
  activityType: String,
  description: String,
  timestamp: String
)

// 阅卷任务数据结构
case class GradingTask(
  taskId: Int,
  examId: Int,
  submissionId: Int,
  gradedAt: String,
  score: Double
)

// 考试成绩模型
case class ExamScore(
  id: Option[Int] = None,
  examId: Int,
  studentId: Int,
  totalScore: Double,
  questionScores: Map[String, Double] = Map.empty,
  rankPosition: Int = 0,
  percentile: Double = 0.0,
  createdAt: Option[LocalDateTime] = None,
  updatedAt: Option[LocalDateTime] = None
)

// 考试统计模型
case class ExamStatistics(
  id: Option[Int] = None,
  examId: Int,
  totalSubmissions: Int,
  averageScore: Double,
  highestScore: Double,
  lowestScore: Double,
  medianScore: Double,
  passRate: Double,
  difficultyAnalysis: Map[String, Any] = Map.empty,
  createdAt: Option[LocalDateTime] = None,
  updatedAt: Option[LocalDateTime] = None
)

// 学生统计模型
case class StudentStatistics(
  id: Option[Int] = None,
  studentId: Int,
  totalExams: Int,
  averageScore: Double,
  bestScore: Double,
  worstScore: Double,
  improvementTrend: Double,
  strongSubjects: List[String] = List.empty,
  weakSubjects: List[String] = List.empty,
  createdAt: Option[LocalDateTime] = None,
  updatedAt: Option[LocalDateTime] = None
)

// 教练统计模型
case class CoachStatistics(
  id: Option[Int] = None,
  coachId: Int,
  totalStudents: Int,
  totalExams: Int,
  averageStudentScore: Double,
  bestStudentScore: Double,
  classPerformance: Map[String, Any] = Map.empty,
  createdAt: Option[LocalDateTime] = None,
  updatedAt: Option[LocalDateTime] = None
)

// 阅卷员统计模型
case class GraderStatistics(
  id: Option[Int] = None,
  graderId: Int,
  totalGraded: Int,
  gradingAccuracy: Double,
  gradingSpeed: Double,
  gradingHistory: List[Map[String, Any]] = List.empty,
  createdAt: Option[LocalDateTime] = None,
  updatedAt: Option[LocalDateTime] = None
)

// 系统统计模型
case class SystemStatistics(
  id: Option[Int] = None,
  statDate: String,
  totalUsers: Int,
  totalStudents: Int,
  totalCoaches: Int,
  totalGraders: Int,
  totalExams: Int,
  totalSubmissions: Int,
  systemMetrics: Map[String, Any] = Map.empty,
  createdAt: Option[LocalDateTime] = None
)

// 仪表板统计数据模型
case class DashboardStats(
  totalExams: Int,
  totalScores: Int,
  averageScore: Double,
  improvementRate: Double,
  rankingPosition: Option[Int] = None,
  recentActivity: List[RecentActivity] = List.empty,
  performanceMetrics: PerformanceMetrics = PerformanceMetrics()
)

// 阅卷员仪表板统计数据模型（匹配前端期望的数据结构）
case class GraderDashboardStats(
  totalTasks: Int,
  completedTasks: Int,
  pendingTasks: Int,
  totalScores: Int,
  recentActivities: List[RecentActivity] = List.empty
)

// 学生成绩响应模型
case class StudentScoreResponse(
  examId: Int,
  studentId: Int,
  totalScore: Double,
  rank: Int,
  percentile: Double,
  questionScores: Map[String, Double],
  examTitle: String,
  examDate: String
)

// 学生排名响应模型
case class StudentRankingResponse(
  examId: Int,
  studentId: Int,
  rank: Int,
  totalScore: Double,
  percentile: Double,
  totalParticipants: Int,
  examTitle: String
)

// 教练成绩概览响应模型
case class CoachGradesOverview(
  totalStudents: Int,
  totalExams: Int,
  averageClassScore: Double,
  bestPerformingStudent: Option[BestPerformingStudent] = None,
  recentExamResults: List[RecentActivity] = List.empty,
  performanceTrends: PerformanceTrends = PerformanceTrends("stable")
)

// 教练成绩详情响应模型
case class CoachGradesDetails(
  students: List[RecentActivity],
  examResults: List[RecentActivity],
  statisticalAnalysis: StatisticalAnalysis,
  performanceComparison: PerformanceComparison
)

// 阅卷员历史记录响应模型
case class GraderHistoryResponse(
  graderId: Int,
  totalGraded: Int,
  recentGradingTasks: List[GradingTask],
  gradingStatistics: GradingStatisticsData,
  performanceMetrics: PerformanceMetrics
)

// 简化的阅卷员统计响应模型（用于API返回）
case class GraderStatisticsResponse(
  totalGraded: Int,
  gradingAccuracy: Double,
  gradingSpeed: Double
)

// 教练学生成绩响应模型
case class CoachStudentScoreResponse(
  studentId: Int,
  studentName: String,
  examId: Int,
  examTitle: String,
  score: Double,
  rank: Int,
  submitTime: String
)

// API响应包装器
case class ApiResponse[T](
  success: Boolean,
  data: Option[T] = None,
  message: String = "",
  error: Option[String] = None
)

// 分页响应模型
case class PagedResponse[T](
  data: List[T],
  total: Int,
  page: Int,
  pageSize: Int,
  totalPages: Int
)
