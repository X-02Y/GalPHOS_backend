package com.galphos.systemconfig.services

import cats.effect.IO
import cats.implicits._
import com.galphos.systemconfig.models.{Admin, User, CreateAdminRequest, CreateUserRequest, UpdateAdminRequest, UpdateUserRequest, UserApprovalRequest, ResetPasswordRequest, AllUsersResponse}
import com.galphos.systemconfig.models.Models._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

/**
 * 统一数据代理服务
 * 提供统一的接口访问用户和管理员数据
 * 确保SystemConfigService可以通过相同的方式访问所有用户数据
 */
class UnifiedDataProxyService(
  adminProxyService: AdminProxyService,
  userProxyService: UserProxyService
) {
  private val logger = Slf4jLogger.getLogger[IO]

  // ================= 管理员相关操作 =================
  
  def getAllAdmins(token: String): IO[List[Admin]] = {
    adminProxyService.getAllAdmins(token)
  }

  def createAdmin(adminRequest: CreateAdminRequest, token: String): IO[Option[Admin]] = {
    adminProxyService.createAdmin(adminRequest, token)
  }

  def updateAdmin(adminId: String, updateRequest: UpdateAdminRequest, token: String): IO[Option[Admin]] = {
    adminProxyService.updateAdmin(adminId, updateRequest, token)
  }

  def deleteAdmin(adminId: String, token: String): IO[Boolean] = {
    adminProxyService.deleteAdmin(adminId, token)
  }

  def resetAdminPassword(adminId: String, resetRequest: ResetPasswordRequest, token: String): IO[Boolean] = {
    adminProxyService.resetPassword(adminId, resetRequest, token)
  }

  // ================= 用户相关操作 =================
  
  def getPendingUsers(token: String): IO[List[User]] = {
    userProxyService.getPendingUsers(token)
  }

  def getApprovedUsers(token: String, page: Option[Int] = None, limit: Option[Int] = None, 
                      role: Option[String] = None, status: Option[String] = None): IO[List[User]] = {
    userProxyService.getApprovedUsers(token, page, limit, role, status)
  }

  def getUserById(userId: String, token: String): IO[Option[User]] = {
    userProxyService.getUserById(userId, token)
  }

  def approveUser(approvalRequest: UserApprovalRequest, token: String): IO[Boolean] = {
    userProxyService.approveUser(approvalRequest, token)
  }

  def updateUserStatus(userId: String, status: String, token: String): IO[Boolean] = {
    userProxyService.updateUserStatus(userId, status, token)
  }

  def updateUser(userId: String, updateRequest: UpdateUserRequest, token: String): IO[Option[User]] = {
    userProxyService.updateUser(userId, updateRequest, token)
  }

  def deleteUser(userId: String, token: String): IO[Boolean] = {
    userProxyService.deleteUser(userId, token)
  }

  // ================= 统一查询操作 =================
  
  /**
   * 获取所有用户（包括管理员和普通用户）
   * 提供统一的用户视图
   */
  def getAllUsers(token: String, includeAdmins: Boolean = true): IO[AllUsersResponse] = {
    for {
      _ <- logger.info("开始获取所有用户数据")
      regularUsers <- getApprovedUsers(token)
      admins <- if (includeAdmins) getAllAdmins(token) else IO.pure(List.empty[Admin])
      _ <- logger.info(s"获取到 ${regularUsers.length} 个普通用户，${admins.length} 个管理员")
    } yield AllUsersResponse(
      users = regularUsers,
      admins = admins,
      totalUsers = regularUsers.length,
      totalAdmins = admins.length,
      total = regularUsers.length + admins.length
    )
  }

  /**
   * 按角色获取用户
   */
  def getUsersByRole(role: String, token: String): IO[List[User]] = {
    role.toLowerCase match {
      case "admin" | "super_admin" =>
        // 管理员角色，转换为User格式返回
        for {
          admins <- getAllAdmins(token)
          users = admins.map(adminToUser)
        } yield users
      case _ =>
        // 普通用户角色
        getApprovedUsers(token, role = Some(role))
    }
  }

  /**
   * 按状态获取用户
   */
  def getUsersByStatus(status: String, token: String): IO[List[User]] = {
    status.toLowerCase match {
      case "pending" =>
        getPendingUsers(token)
      case _ =>
        getApprovedUsers(token, status = Some(status))
    }
  }

  /**
   * 搜索用户（按用户名）
   */
  def searchUsers(query: String, token: String, includeAdmins: Boolean = true): IO[List[User]] = {
    for {
      allUsersResponse <- getAllUsers(token, includeAdmins)
      filteredUsers = allUsersResponse.users.filter(_.username.toLowerCase.contains(query.toLowerCase))
      filteredAdmins = if (includeAdmins) {
        allUsersResponse.admins
          .filter(_.username.toLowerCase.contains(query.toLowerCase))
          .map(adminToUser)
      } else List.empty
    } yield filteredUsers ++ filteredAdmins
  }

  // ================= 辅助方法 =================
  
  /**
   * 将管理员转换为用户格式（用于统一视图）
   */
  private def adminToUser(admin: Admin): User = {
    User(
      userId = admin.id,
      username = admin.username,
      phone = None, // 管理员通常不存储电话号码
      role = admin.role,
      status = if (admin.isSuperAdmin) "super_admin" else "admin",
      province = None,
      school = None,
      avatarUrl = None,
      createdAt = admin.createdAt,
      updatedAt = admin.updatedAt,
      lastLogin = admin.lastLogin
    )
  }
}
