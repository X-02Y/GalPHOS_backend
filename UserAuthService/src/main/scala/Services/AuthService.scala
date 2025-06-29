package Services

import Models.*
import cats.effect.IO
import org.slf4j.{Logger, LoggerFactory}
import java.util.UUID

class AuthService(
  userService: UserService,
  adminService: AdminService,
  tokenService: TokenService,
  regionServiceClient: RegionServiceClient
) {
  private val logger = LoggerFactory.getLogger("AuthService")

  // 用户登录
  def login(loginReq: LoginRequest): IO[Either[String, (UserInfo, String)]] = {
    for {
      userOpt <- userService.findUserByUsername(loginReq.username)
      result <- userOpt match {
        case Some(user) =>
          // 前端已经发送哈希后的密码，直接比较
          if (loginReq.password == user.passwordHash) {
            if (roleMatches(loginReq.role, user.role)) {
              if (user.status == UserStatus.Active) {
                for {
                  _ <- userService.updateLastLoginTime(user.userID)
                  token <- tokenService.generateToken(UUID.fromString(user.userID))
                  userInfo <- userService.getUserInfo(user.username, user.role.value)
                } yield Right((userInfo, token))
              } else {
                IO.pure(Left("账户未激活或已被禁用"))
              }
            } else {
              IO.pure(Left("角色不匹配"))
            }
          } else {
            IO.pure(Left("密码错误"))
          }
        case None =>
          IO.pure(Left("用户不存在"))
      }
    } yield result
  }.handleErrorWith { error =>
    logger.error(s"用户登录失败: ${error.getMessage}")
    IO.pure(Left(s"登录失败: ${error.getMessage}"))
  }

  // 管理员登录
  def adminLogin(loginReq: AdminLoginRequest): IO[Either[String, (UserInfo, String)]] = {
    for {
      adminOpt <- adminService.findAdminByUsername(loginReq.username)
      result <- adminOpt match {
        case Some(admin) =>
          // 前端已经发送哈希后的密码，直接比较
          if (loginReq.password == admin.passwordHash) {
            for {
              _ <- IO(logger.info(s"管理员登录验证成功，开始生成token: adminID=${admin.adminID}"))
              token <- tokenService.generateToken(UUID.fromString(admin.adminID), isAdmin = true)
              _ <- IO(logger.info(s"Token生成成功: ${token.take(20)}..."))
              userInfo = UserInfo(username = admin.username, `type` = Some("admin"))
              _ <- IO(logger.info(s"返回用户信息: ${userInfo}"))
            } yield Right((userInfo, token))
          } else {
            IO.pure(Left("管理员密码错误"))
          }
        case None =>
          IO.pure(Left("管理员账户不存在"))
      }
    } yield result
  }.handleErrorWith { error =>
    logger.error(s"管理员登录失败: ${error.getMessage}")
    IO.pure(Left(s"管理员登录失败: ${error.getMessage}"))
  }

  // 用户注册
  def register(registerReq: RegisterRequest): IO[Either[String, String]] = {
    for {
      // 添加调试日志
      _ <- IO(logger.info(s"收到注册请求 - 角色: '${registerReq.role}', 用户名: ${registerReq.username}"))
      // 验证密码一致性
      _ <- if (registerReq.password != registerReq.confirmPassword) {
        IO.raiseError(new IllegalArgumentException("两次密码输入不一致"))
      } else IO.unit
      
      // 检查用户名是否已存在
      existingUser <- userService.findUserByUsername(registerReq.username)
      result <- existingUser match {
        case Some(_) => IO.pure(Left("用户名已存在"))
        case None =>
          // 前端已经发送哈希后的密码，直接保存
          for {
            userId <- userService.createUser(registerReq, registerReq.password)
            _ = logger.info(s"用户注册成功: ${registerReq.username}, ID: $userId")
          } yield Right("注册申请已提交，等待管理员审核")
      }
    } yield result
  }.handleErrorWith { error =>
    logger.error(s"用户注册失败: ${error.getMessage}")
    IO.pure(Left(s"注册失败: ${error.getMessage}"))
  }

  // Token验证
  def validateToken(token: String): IO[Either[String, UserInfo]] = {
    for {
      validationResult <- tokenService.validateToken(token)
      result <- validationResult match {
        case Right((userId, isAdmin)) =>
          if (isAdmin) {
            for {
              adminOpt <- adminService.findAdminById(userId.toString)
              userInfo <- adminOpt match {
                case Some(admin) =>
                  IO.pure(Right(UserInfo(username = admin.username, `type` = Some("admin"))))
                case None =>
                  IO.pure(Left("管理员不存在"))
              }
            } yield userInfo
          } else {
            for {
              userOpt <- userService.findUserById(userId.toString)
              userInfo <- userOpt match {
                case Some(user) =>
                  if (user.status == UserStatus.Active) {
                    for {
                      fullUserInfo <- userService.getUserInfo(user.username, user.role.value)
                    } yield Right(fullUserInfo)
                  } else {
                    IO.pure(Left("账户未激活或已被禁用"))
                  }
                case None =>
                  IO.pure(Left("用户不存在"))
              }
            } yield userInfo
          }
        case Left(error) =>
          IO.pure(Left(error))
      }
    } yield result
  }

  // 用户登出
  def logout(token: String): IO[Either[String, Unit]] = {
    for {
      _ <- tokenService.addTokenToBlacklist(token)
    } yield Right(())
  }.handleErrorWith { error =>
    logger.error(s"用户登出失败: ${error.getMessage}")
    IO.pure(Left(s"登出失败: ${error.getMessage}"))
  }

  // 角色匹配检查
  private def roleMatches(requestRole: String, userRole: UserRole): Boolean = {
    requestRole.toLowerCase match {
      case "student" => userRole == UserRole.Student
      case "coach" => userRole == UserRole.Coach
      case "grader" => userRole == UserRole.Grader
      case _ => false
    }
  }
}
