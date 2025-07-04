package com.galphos.systemconfig.routes

import cats.effect.IO
import io.circe.Json
import io.circe.syntax._
import org.http4s.HttpRoutes
import org.http4s.dsl.io._
import org.http4s.circe._

// 健康检查路由
class HealthRoutes {
  
  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "health" =>
      val healthInfo = Json.obj(
        "status" -> "UP".asJson,
        "service" -> "SystemConfigService".asJson,
        "time" -> System.currentTimeMillis().asJson
      )
      Ok(healthInfo)
  }
}
