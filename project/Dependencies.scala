// GalPHOS Backend 版本配置文件
// 统一管理所有依赖版本

import sbt._

object Versions {
  // Scala 版本
  val scala = "3.4.2"
  
  // SBT 版本 
  val sbt = "1.9.7"
  
  // Java 版本
  val java = "21"
  
  // 核心依赖版本
  val cats = "2.10.0"
  val catsEffect = "3.5.4"
  val http4s = "0.23.26"
  val circe = "0.14.6"
  val doobie = "1.0.0-RC5"
  val logback = "1.4.14"
  val postgresql = "42.7.2"
  
  // 测试依赖版本
  val scalatest = "3.2.18"
  val testcontainers = "0.41.3"
}

object Dependencies {
  import Versions._
  
  // Cats 生态
  val cats = "org.typelevel" %% "cats-core" % Versions.cats
  val catsEffect = "org.typelevel" %% "cats-effect" % Versions.catsEffect
  
  // HTTP4S 生态
  val http4sCore = "org.http4s" %% "http4s-core" % http4s
  val http4sDsl = "org.http4s" %% "http4s-dsl" % http4s
  val http4sEmberServer = "org.http4s" %% "http4s-ember-server" % http4s
  val http4sEmberClient = "org.http4s" %% "http4s-ember-client" % http4s
  val http4sCirce = "org.http4s" %% "http4s-circe" % http4s
  
  // Circe JSON
  val circeCore = "io.circe" %% "circe-core" % circe
  val circeGeneric = "io.circe" %% "circe-generic" % circe
  val circeParser = "io.circe" %% "circe-parser" % circe
  
  // Doobie 数据库
  val doobieCore = "org.tpolecat" %% "doobie-core" % doobie
  val doobieHikari = "org.tpolecat" %% "doobie-hikari" % doobie
  val doobiePostgres = "org.tpolecat" %% "doobie-postgres" % doobie
  
  // 数据库驱动
  val postgresql = "org.postgresql" % "postgresql" % Versions.postgresql
  
  // 日志
  val logback = "ch.qos.logback" % "logback-classic" % Versions.logback
  val log4cats = "org.typelevel" %% "log4cats-core" % "2.7.0"
  val log4catsSlf4j = "org.typelevel" %% "log4cats-slf4j" % "2.7.0"
  
  // JWT 库
  val jwtCore = "com.github.jwt-scala" %% "jwt-core" % "10.0.1"
  val jwtCirce = "com.github.jwt-scala" %% "jwt-circe" % "10.0.1"
  
  // STTP 客户端
  val sttpCore = "com.softwaremill.sttp.client3" %% "core" % "3.10.3"
  val sttpAsyncHttpClient = "com.softwaremill.sttp.client3" %% "async-http-client-backend-cats" % "3.10.3"
  val sttpFs2 = "com.softwaremill.sttp.client3" %% "fs2" % "3.10.3"
  val sttpCirce = "com.softwaremill.sttp.client3" %% "circe" % "3.10.3"
  
  // FS2 库
  val fs2Core = "co.fs2" %% "fs2-core" % "3.11.0"
  val fs2Io = "co.fs2" %% "fs2-io" % "3.11.0"
  
  // 其他工具库
  val jodaTime = "joda-time" % "joda-time" % "2.12.7"
  val jgit = "org.eclipse.jgit" % "org.eclipse.jgit" % "5.13.0.202109080827-r"
  val hikariCP = "com.zaxxer" % "HikariCP" % "5.1.0"
  val circeYaml = "io.circe" %% "circe-yaml" % "1.15.0"
  
  // Jackson JSON
  val jacksonDatabind = "com.fasterxml.jackson.core" % "jackson-databind" % "2.15.2"
  val jacksonScala = "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.15.2"
  
  // 配置管理
  val typesafeConfig = "com.typesafe" % "config" % "1.4.3"
  
  // SLF4J
  val slf4jApi = "org.slf4j" % "slf4j-api" % "2.0.9"
  
  // 测试工具
  val scalatestPlusMockito = "org.scalatestplus" %% "mockito-4-6" % "3.2.15.0" % Test
  
  // 测试
  val scalatest = "org.scalatest" %% "scalatest" % Versions.scalatest % Test
  val testcontainers = "com.dimafeng" %% "testcontainers-scala-scalatest" % Versions.testcontainers % Test
  val testcontainersPostgres = "com.dimafeng" %% "testcontainers-scala-postgresql" % Versions.testcontainers % Test
  
  // 认证服务依赖
  val authServiceDeps = Seq(
    cats,
    catsEffect,
    http4sCore,
    http4sDsl,
    http4sEmberServer,
    http4sEmberClient,
    http4sCirce,
    circeCore,
    circeGeneric,
    circeParser,
    circeYaml,
    doobieCore,
    doobieHikari,
    doobiePostgres,
    postgresql,
    logback,
    log4cats,
    log4catsSlf4j,
    jwtCore,
    jwtCirce,
    sttpCore,
    sttpAsyncHttpClient,
    sttpFs2,
    sttpCirce,
    fs2Core,
    fs2Io,
    jodaTime,
    jgit,
    hikariCP,
    jacksonDatabind,
    jacksonScala,
    scalatest,
    testcontainers,
    testcontainersPostgres
  )
  
  // 用户管理服务依赖
  val userManagementServiceDeps = Seq(
    cats,
    catsEffect,
    http4sCore,
    http4sDsl,
    http4sEmberServer,
    http4sEmberClient,
    http4sCirce,
    circeCore,
    circeGeneric,
    circeParser,
    doobieCore,
    doobieHikari,
    doobiePostgres,
    postgresql,
    hikariCP,
    logback,
    slf4jApi,
    jwtCirce,
    typesafeConfig,
    scalatest,
    scalatestPlusMockito,
    testcontainers,
    testcontainersPostgres
  )
}
