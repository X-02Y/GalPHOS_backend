package com.galphos.systemconfig.services

import cats.effect.IO
import cats.implicits._
import com.galphos.systemconfig.models.{User, CreateUserRequest, UpdateUserRequest, UserApprovalRequest}
import com.galphos.systemconfig.models.Models._
import io.circe._
import io.circe.parser._
import io.circe.generic.auto._
import io.circe.syntax._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.net.URI
import java.time.Duration

/**
 * 用户代理服务
 * 将 SystemConfigService 的用户操作代理到 UserManagementService
 * 确保所有用户数据使用统一的存储
 */
class UserProxyService(userManagementServiceUrl: String) {
  private val logger = Slf4jLogger.getLogger[IO]
  private val httpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .build()

  // 获取待审核用户列表
  def getPendingUsers(token: String): IO[List[User]] = {
    val request = HttpRequest.newBuilder()
      .uri(URI.create(s"$userManagementServiceUrl/api/admin/users/pending"))
      .header("Authorization", s"Bearer $token")
      .header("Content-Type", "application/json")
      .GET()
      .build()

    IO.blocking {
      val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
      if (response.statusCode() == 200) {
        parse(response.body()) match {
          case Right(json) =>
            val cursor = json.hcursor
            cursor.downField("success").as[Boolean] match {
              case Right(true) =>
                cursor.downField("data").as[List[Json]].toOption match {
                  case Some(users) =>
                    users.flatMap(convertToUser)
                  case None => List.empty
                }
              case _ => List.empty
            }
          case Left(_) => List.empty
        }
      } else {
        List.empty
      }
    }.handleErrorWith { error =>
      logger.error(error)("获取待审核用户列表失败") *> IO.pure(List.empty)
    }
  }

  // 获取已审核用户列表
  def getApprovedUsers(token: String, page: Option[Int] = None, limit: Option[Int] = None, 
                      role: Option[String] = None, status: Option[String] = None): IO[List[User]] = {
    val queryParams = scala.collection.mutable.ListBuffer[String]()
    page.foreach(p => queryParams += s"page=$p")
    limit.foreach(l => queryParams += s"limit=$l")
    role.foreach(r => queryParams += s"role=$r")
    status.foreach(s => queryParams += s"status=$s")
    
    val queryString = if (queryParams.nonEmpty) "?" + queryParams.mkString("&") else ""
    val url = s"$userManagementServiceUrl/api/admin/users/approved$queryString"
    
    val request = HttpRequest.newBuilder()
      .uri(URI.create(url))
      .header("Authorization", s"Bearer $token")
      .header("Content-Type", "application/json")
      .GET()
      .build()

    IO.blocking {
      val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
      if (response.statusCode() == 200) {
        parse(response.body()) match {
          case Right(json) =>
            val cursor = json.hcursor
            cursor.downField("success").as[Boolean] match {
              case Right(true) =>
                cursor.downField("data").downField("items").as[List[Json]].toOption match {
                  case Some(users) =>
                    users.flatMap(convertToUser)
                  case None => List.empty
                }
              case _ => List.empty
            }
          case Left(_) => List.empty
        }
      } else {
        List.empty
      }
    }.handleErrorWith { error =>
      logger.error(error)("获取已审核用户列表失败") *> IO.pure(List.empty)
    }
  }

  // 审核用户申请
  def approveUser(approvalRequest: UserApprovalRequest, token: String): IO[Boolean] = {
    val requestBody = Json.obj(
      "userId" -> Json.fromString(approvalRequest.userId),
      "action" -> Json.fromString(approvalRequest.action),
      "reason" -> approvalRequest.reason.map(Json.fromString).getOrElse(Json.Null)
    ).noSpaces

    val request = HttpRequest.newBuilder()
      .uri(URI.create(s"$userManagementServiceUrl/api/admin/users/approve"))
      .header("Authorization", s"Bearer $token")
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(requestBody))
      .build()

    for {
      _ <- logger.info(s"审核用户请求: URL=${userManagementServiceUrl}/api/admin/users/approve, 请求体=$requestBody")
      result <- IO.blocking {
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        val statusCode = response.statusCode()
        val responseBody = response.body()
        
        (statusCode, responseBody)
      }.flatMap { case (statusCode, responseBody) =>
        logger.info(s"审核用户响应: 状态码=$statusCode, 响应体=$responseBody") *>
        IO {
          statusCode == 200 || statusCode == 201
        }
      }
      _ <- if (result) {
        logger.info(s"审核用户成功: ${approvalRequest.userId}")
      } else {
        logger.error(s"审核用户失败: ${approvalRequest.userId}")
      }
    } yield result
  }.handleErrorWith { error =>
    logger.error(error)(s"审核用户失败: ${approvalRequest.userId}") *> IO.pure(false)
  }

  // 更新用户状态
  def updateUserStatus(userId: String, status: String, token: String): IO[Boolean] = {
    val requestBody = Json.obj(
      "userId" -> Json.fromString(userId),
      "status" -> Json.fromString(status)
    ).noSpaces

    val request = HttpRequest.newBuilder()
      .uri(URI.create(s"$userManagementServiceUrl/api/admin/users/status"))
      .header("Authorization", s"Bearer $token")
      .header("Content-Type", "application/json")
      .PUT(HttpRequest.BodyPublishers.ofString(requestBody))
      .build()

    IO.blocking {
      val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
      response.statusCode() == 200
    }.handleErrorWith { error =>
      logger.error(error)(s"更新用户状态失败，ID: $userId") *> IO.pure(false)
    }
  }

  // 删除用户
  def deleteUser(userId: String, token: String): IO[Boolean] = {
    val request = HttpRequest.newBuilder()
      .uri(URI.create(s"$userManagementServiceUrl/api/admin/users/$userId"))
      .header("Authorization", s"Bearer $token")
      .header("Content-Type", "application/json")
      .DELETE()
      .build()

    IO.blocking {
      val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
      response.statusCode() == 200 || response.statusCode() == 204
    }.handleErrorWith { error =>
      logger.error(error)(s"删除用户失败，ID: $userId") *> IO.pure(false)
    }
  }

  // 获取单个用户信息
  def getUserById(userId: String, token: String): IO[Option[User]] = {
    val request = HttpRequest.newBuilder()
      .uri(URI.create(s"$userManagementServiceUrl/api/admin/users/$userId"))
      .header("Authorization", s"Bearer $token")
      .header("Content-Type", "application/json")
      .GET()
      .build()

    IO.blocking {
      val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
      if (response.statusCode() == 200) {
        parse(response.body()) match {
          case Right(json) =>
            val cursor = json.hcursor
            cursor.downField("success").as[Boolean] match {
              case Right(true) =>
                cursor.downField("data").as[Json].toOption.flatMap(convertToUser)
              case _ => None
            }
          case Left(_) => None
        }
      } else {
        None
      }
    }.handleErrorWith { error =>
      logger.error(error)(s"获取用户信息失败，ID: $userId") *> IO.pure(None)
    }
  }

  // 更新用户信息
  def updateUser(userId: String, updateRequest: UpdateUserRequest, token: String): IO[Option[User]] = {
    val requestBody = Json.obj(
      "phone" -> updateRequest.phone.map(Json.fromString).getOrElse(Json.Null),
      "role" -> updateRequest.role.map(Json.fromString).getOrElse(Json.Null),
      "status" -> updateRequest.status.map(Json.fromString).getOrElse(Json.Null),
      "province" -> updateRequest.province.map(Json.fromString).getOrElse(Json.Null),
      "school" -> updateRequest.school.map(Json.fromString).getOrElse(Json.Null),
      "avatarUrl" -> updateRequest.avatarUrl.map(Json.fromString).getOrElse(Json.Null)
    ).noSpaces

    val request = HttpRequest.newBuilder()
      .uri(URI.create(s"$userManagementServiceUrl/api/admin/users/$userId"))
      .header("Authorization", s"Bearer $token")
      .header("Content-Type", "application/json")
      .PUT(HttpRequest.BodyPublishers.ofString(requestBody))
      .build()

    IO.blocking {
      val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
      if (response.statusCode() == 200) {
        parse(response.body()) match {
          case Right(json) =>
            val cursor = json.hcursor
            cursor.downField("success").as[Boolean] match {
              case Right(true) =>
                cursor.downField("data").as[Json].toOption.flatMap(convertToUser)
              case _ => None
            }
          case Left(_) => None
        }
      } else {
        None
      }
    }.handleErrorWith { error =>
      logger.error(error)(s"更新用户信息失败，ID: $userId") *> IO.pure(None)
    }
  }

  // 将 UserManagementService 的用户数据转换为 SystemConfigService 的 User 模型
  private def convertToUser(json: Json): Option[User] = {
    val cursor = json.hcursor
    for {
      id <- cursor.downField("id").as[String].toOption
      username <- cursor.downField("username").as[String].toOption
      role <- cursor.downField("role").as[String].toOption
      status <- cursor.downField("status").as[String].toOption
    } yield {
      val phone = cursor.downField("phone").as[String].toOption
      val province = cursor.downField("province").as[String].toOption
      val school = cursor.downField("school").as[String].toOption
      val avatarUrl = cursor.downField("avatarUrl").as[String].toOption
      val createdAt = cursor.downField("appliedAt").as[String].toOption
        .orElse(cursor.downField("approvedAt").as[String].toOption)
        .flatMap(s => scala.util.Try(java.time.ZonedDateTime.parse(s)).toOption)
      val lastLogin = cursor.downField("lastLoginAt").as[String].toOption
        .flatMap(s => scala.util.Try(java.time.ZonedDateTime.parse(s)).toOption)
      
      User(
        userId = Some(id),
        username = username,
        phone = phone,
        role = role,
        status = status,
        province = province,
        school = school,
        avatarUrl = avatarUrl,
        createdAt = createdAt,
        updatedAt = createdAt, // 使用相同的时间
        lastLogin = lastLogin
      )
    }
  }
}
