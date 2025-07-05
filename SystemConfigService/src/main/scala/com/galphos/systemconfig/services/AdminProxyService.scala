package com.galphos.systemconfig.services

import cats.effect.IO
import cats.implicits._
import com.galphos.systemconfig.models.{Admin, CreateAdminRequest, UpdateAdminRequest, ResetPasswordRequest}
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
 * 管理员代理服务
 * 将 SystemConfigService 的管理员操作代理到 UserManagementService
 * 确保所有管理员数据使用统一的存储
 */
class AdminProxyService(userManagementServiceUrl: String) {
  private val logger = Slf4jLogger.getLogger[IO]
  private val httpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .build()

  // 获取所有管理员
  def getAllAdmins(token: String): IO[List[Admin]] = {
    val request = HttpRequest.newBuilder()
      .uri(URI.create(s"$userManagementServiceUrl/api/admin/system/admins"))
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
                  case Some(admins) =>
                    admins.flatMap(convertToAdmin)
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
      logger.error(error)("获取管理员列表失败") *> IO.pure(List.empty)
    }
  }

  // 创建管理员
  def createAdmin(adminRequest: CreateAdminRequest, token: String): IO[Option[Admin]] = {
    val requestBody = Json.obj(
      "username" -> Json.fromString(adminRequest.username),
      "password" -> Json.fromString(adminRequest.password),
      "role" -> Json.fromString(adminRequest.role.getOrElse("admin")),
      "name" -> Json.Null, // 明确设置为null
      "avatarUrl" -> Json.Null // 明确设置为null
    ).noSpaces

    val request = HttpRequest.newBuilder()
      .uri(URI.create(s"$userManagementServiceUrl/api/admin/system/admins"))
      .header("Authorization", s"Bearer $token")
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(requestBody))
      .build()

    for {
      _ <- logger.info(s"创建管理员请求: URL=${userManagementServiceUrl}/api/admin/system/admins, 请求体=$requestBody")
      result <- IO.blocking {
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        val statusCode = response.statusCode()
        val responseBody = response.body()
        
        (statusCode, responseBody)
      }.flatMap { case (statusCode, responseBody) =>
        logger.info(s"创建管理员响应: 状态码=$statusCode, 响应体=$responseBody") *>
        IO {
          if (statusCode == 200 || statusCode == 201) {
            parse(responseBody) match {
              case Right(json) =>
                val cursor = json.hcursor
                cursor.downField("success").as[Boolean] match {
                  case Right(true) =>
                    cursor.downField("data").as[Json].toOption.flatMap(convertToAdmin)
                  case _ => 
                    None
                }
              case Left(parseError) => 
                None
            }
          } else {
            None
          }
        }
      }
      _ <- result match {
        case Some(admin) => 
          logger.info(s"创建管理员成功: ${admin.username}")
        case None => 
          logger.error(s"创建管理员失败: ${adminRequest.username}")
      }
    } yield result
  }.handleErrorWith { error =>
    logger.error(error)(s"创建管理员失败: ${adminRequest.username}") *> IO.pure(None)
  }

  // 更新管理员
  def updateAdmin(adminId: Long, updateRequest: UpdateAdminRequest, token: String): IO[Option[Admin]] = {
    val requestBody = Json.obj(
      "role" -> updateRequest.role.map(Json.fromString).getOrElse(Json.Null),
      "status" -> Json.fromString(if (updateRequest.isSuperAdmin.getOrElse(false)) "active" else "active")
    ).noSpaces

    val request = HttpRequest.newBuilder()
      .uri(URI.create(s"$userManagementServiceUrl/api/admin/system/admins/$adminId"))
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
                cursor.downField("data").as[Json].toOption.flatMap(convertToAdmin)
              case _ => None
            }
          case Left(_) => None
        }
      } else {
        None
      }
    }.handleErrorWith { error =>
      logger.error(error)(s"更新管理员失败，ID: $adminId") *> IO.pure(None)
    }
  }

  // 删除管理员
  def deleteAdmin(adminId: Long, token: String): IO[Boolean] = {
    val request = HttpRequest.newBuilder()
      .uri(URI.create(s"$userManagementServiceUrl/api/admin/system/admins/$adminId"))
      .header("Authorization", s"Bearer $token")
      .header("Content-Type", "application/json")
      .DELETE()
      .build()

    IO.blocking {
      val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
      response.statusCode() == 200 || response.statusCode() == 204
    }.handleErrorWith { error =>
      logger.error(error)(s"删除管理员失败，ID: $adminId") *> IO.pure(false)
    }
  }

  // 重置管理员密码
  def resetPassword(adminId: Long, resetRequest: ResetPasswordRequest, token: String): IO[Boolean] = {
    val requestBody = Json.obj(
      "password" -> Json.fromString(resetRequest.password)
    ).noSpaces

    val request = HttpRequest.newBuilder()
      .uri(URI.create(s"$userManagementServiceUrl/api/admin/system/admins/$adminId/password"))
      .header("Authorization", s"Bearer $token")
      .header("Content-Type", "application/json")
      .PUT(HttpRequest.BodyPublishers.ofString(requestBody))
      .build()

    IO.blocking {
      val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
      response.statusCode() == 200
    }.handleErrorWith { error =>
      logger.error(error)(s"重置密码失败，ID: $adminId") *> IO.pure(false)
    }
  }

  // 将 UserManagementService 的管理员数据转换为前端期望的格式
  private def convertToAdmin(json: Json): Option[Admin] = {
    val cursor = json.hcursor
    for {
      idStr <- cursor.downField("id").as[String].toOption
      username <- cursor.downField("username").as[String].toOption
      role <- cursor.downField("role").as[String].toOption.orElse(Some("admin"))
      status <- cursor.downField("status").as[String].toOption.orElse(Some("active"))
    } yield {
      // 将UUID字符串直接作为ID使用，前端期望字符串格式
      val createdAt = cursor.downField("createdAt").as[String].toOption
        .flatMap(s => if (s.nonEmpty && s != "null") scala.util.Try(java.time.ZonedDateTime.parse(s)).toOption else None)
      val lastLogin = cursor.downField("lastLoginAt").as[String].toOption
        .flatMap(s => if (s.nonEmpty && s != "null") scala.util.Try(java.time.ZonedDateTime.parse(s)).toOption else None)
      val avatar = cursor.downField("avatar").as[String].toOption
        .orElse(cursor.downField("avatarUrl").as[String].toOption)
      
      Admin(
        adminId = Some(idStr.hashCode.toLong.abs), // 保持兼容性，但前端主要使用字符串ID
        username = username,
        passwordHash = None,
        role = role,
        isSuperAdmin = role.contains("super_admin"),
        createdAt = createdAt,
        updatedAt = createdAt,
        lastLogin = lastLogin
      )
    }
  }
}
