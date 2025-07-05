package com.galphos.systemconfig.services

import cats.effect.IO
import cats.implicits._
import com.galphos.systemconfig.models.{Admin, CreateAdminRequest, UpdateAdminRequest, ResetPasswordRequest}
import com.galphos.systemconfig.models.Models._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

/**
 * 管理员代理服务
 * 直接使用 AdminService 操作数据库，确保管理员数据的一致性
 */
class AdminProxyService(adminService: AdminService) {
  private val logger = Slf4jLogger.getLogger[IO]

  // 获取所有管理员
  def getAllAdmins(token: String): IO[List[Admin]] = {
    logger.info("获取所有管理员列表") *> adminService.getAllAdmins
  }

  // 创建管理员
  def createAdmin(adminRequest: CreateAdminRequest, token: String): IO[Option[Admin]] = {
    logger.info(s"创建管理员: ${adminRequest.username}") *> adminService.createAdmin(adminRequest)
  }

  // 更新管理员 - 直接使用UUID
  def updateAdmin(adminId: String, updateRequest: UpdateAdminRequest, token: String): IO[Option[Admin]] = {
    logger.info(s"更新管理员，UUID: $adminId") *> adminService.updateAdmin(adminId, updateRequest)
  }

  // 删除管理员 - 直接使用UUID
  def deleteAdmin(adminId: String, token: String): IO[Boolean] = {
    logger.info(s"删除管理员，UUID: $adminId") *> adminService.deleteAdmin(adminId)
  }

  // 重置管理员密码 - 直接使用UUID
  def resetPassword(adminId: String, resetRequest: ResetPasswordRequest, token: String): IO[Boolean] = {
    logger.info(s"重置管理员密码，UUID: $adminId") *> adminService.resetPassword(adminId, resetRequest)
  }
}
