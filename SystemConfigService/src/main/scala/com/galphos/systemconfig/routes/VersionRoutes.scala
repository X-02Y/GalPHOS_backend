package com.galphos.systemconfig.routes

import cats.effect.IO
import com.galphos.systemconfig.services.VersionService
import com.galphos.systemconfig.models._
import org.http4s.HttpRoutes
import org.http4s.dsl.io._
import io.circe.syntax._
import org.http4s.circe.CirceEntityCodec._

class VersionRoutes(versionService: VersionService) {
  import Models._ // 导入从Models对象定义的编解码器
  
  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    // 系统版本信息
    case GET -> Root / "system" / "version" =>
      versionService.getCurrentVersion.flatMap {
        case Some(version) => Ok(version)
        case None => NotFound(ErrorResponse("版本信息不可用"))
      }.handleErrorWith(err => 
        InternalServerError(ErrorResponse(s"获取版本信息失败: ${err.getMessage}"))
      )
  }
}
