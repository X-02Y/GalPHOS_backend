package com.galphos.systemconfig.services

import cats.effect.IO
import cats.implicits._
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
    val requestBody = Map(
      "username" -> adminRequest.username,
      "password" -> adminRequest.password,
      "role" -> adminRequest.role.getOrElse("admin")
    ).asJson.noSpaces

    val request = HttpRequest.newBuilder()
      .uri(URI.create(s"$userManagementServiceUrl/api/admin/system/admins"))
      .header("Authorization", s"Bearer $token")
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(requestBody))
      .build()

    IO.blocking {
      val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
      if (response.statusCode() == 200 || response.statusCode() == 201) {
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
      logger.error(error)(s"创建管理员失败: ${adminRequest.username}") *> IO.pure(None)
    }
  }

  // 更新管理员
  def updateAdmin(adminId: Long, updateRequest: UpdateAdminRequest, token: String): IO[Option[Admin]] = {
    val requestBody = Map(
      "role" -> updateRequest.role,
      "status" -> (if (updateRequest.isSuperAdmin.getOrElse(false)) "active" else "active")
    ).filter(_._2.isDefined).mapValues(_.get).asJson.noSpaces

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
    val requestBody = Map(
      "password" -> resetRequest.password
    ).asJson.noSpaces

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

  // 将 UserManagementService 的管理员数据转换为 SystemConfigService 的 Admin 模型
  private def convertToAdmin(json: Json): Option[Admin] = {
    val cursor = json.hcursor
    for {
      id <- cursor.downField("id").as[String].toOption.flatMap(s => scala.util.Try(s.toLong).toOption)
      username <- cursor.downField("username").as[String].toOption
      role <- cursor.downField("role").as[String].toOption
      status <- cursor.downField("status").as[String].toOption
    } yield {
      val isSuperAdmin = role.contains("super_admin")
      val createdAt = cursor.downField("createdAt").as[String].toOption
        .flatMap(s => scala.util.Try(java.time.ZonedDateTime.parse(s)).toOption)
      val lastLogin = cursor.downField("lastLoginAt").as[String].toOption
        .flatMap(s => scala.util.Try(java.time.ZonedDateTime.parse(s)).toOption)
      
      Admin(
        adminId = Some(id),
        username = username,
        passwordHash = None, // 不需要密码哈希
        role = role,
        isSuperAdmin = isSuperAdmin,
        createdAt = createdAt,
        updatedAt = createdAt, // 使用相同的时间
        lastLogin = lastLogin
      )
    }
  }
}
