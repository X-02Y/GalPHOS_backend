ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.4.2"

assembly / assemblyMergeStrategy := {
  case PathList("module-info.class") => MergeStrategy.discard
  case PathList("META-INF", "versions", xs @ _, "module-info.class") => MergeStrategy.discard
  case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.first

  // 保留 PostgresSQL 驱动服务文件
  case PathList("META-INF", "services", "java.sql.Driver") => MergeStrategy.concat

  // 以下是常见的其他冲突处理策略
  case x if x.endsWith("/module-info.class") => MergeStrategy.discard
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case "reference.conf" => MergeStrategy.concat
  case _ => MergeStrategy.first
}

assembly / mainClass := Some("com.galphos.systemconfig.SystemConfigServiceMain")
enablePlugins(JavaAppPackaging)

// 不发布源码和文档
Compile / packageDoc / publishArtifact := false
Compile / packageSrc / publishArtifact := false
Compile / run / fork := true

resolvers ++= Seq(
  "Aliyun Central" at "https://maven.aliyun.com/repository/central",
  "Huawei Mirror" at "https://repo.huaweicloud.com/repository/maven/",
  "Tsinghua Mirror" at "https://mirrors.tuna.tsinghua.edu.cn/maven-central/",
  // 官方 Maven Central 仓库
  Resolver.mavenCentral
)

Universal / packageBin / mappings ++= {
  val baseDir = baseDirectory.value
  Seq(
    baseDir / "server_config.json" -> "server_config.json",
  )
}

lazy val root = (project in file("."))
  .settings(
    name := "SystemConfigService",
    // 添加编码设置
    javacOptions ++= Seq("-encoding", "UTF-8"),
    scalacOptions ++= Seq("-encoding", "UTF-8", "-Xmax-inlines", "64"),
  )

val http4sVersion = "0.23.30"
val circeVersion = "0.14.10"
val doobieVersion = "1.0.0-RC5"
Compile / run / fork := true

libraryDependencies ++= Seq(

  // http4s 核心库
  "org.http4s" %% "http4s-dsl" % http4sVersion,
  "org.http4s" %% "http4s-ember-server" % http4sVersion,
  "org.http4s" %% "http4s-ember-client" % http4sVersion,
  "org.http4s" %% "http4s-circe" % http4sVersion,

  // Circe 库
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "io.circe" %% "circe-yaml" % "1.15.0",

  // Doobie 库（数据库访问）
  "org.tpolecat" %% "doobie-core" % doobieVersion,
  "org.tpolecat" %% "doobie-postgres" % doobieVersion,
  "org.tpolecat" %% "doobie-postgres-circe" % doobieVersion,
  "org.tpolecat" %% "doobie-hikari" % doobieVersion, // 连接池支持
  "org.tpolecat" %% "doobie-specs2" % doobieVersion % "test",
  "org.tpolecat" %% "doobie-refined" % doobieVersion, // For refined types
  "org.postgresql" % "postgresql" % "42.7.2", // 确保使用最新版本的 JDBC 驱动

  // 日志库
  "org.typelevel" %% "log4cats-core" % "2.7.0",
  "org.typelevel" %% "log4cats-slf4j" % "2.7.0",
  "ch.qos.logback" % "logback-classic" % "1.5.16",

  // STTP 客户端
  "com.softwaremill.sttp.client3" %% "core" % "3.10.3",
  "com.softwaremill.sttp.client3" %% "async-http-client-backend-cats" % "3.10.3",
  "com.softwaremill.sttp.client3" %% "fs2" % "3.10.3",
  "com.softwaremill.sttp.client3" %% "circe" % "3.10.3",

  // FS2 库
  "co.fs2" %% "fs2-core" % "3.11.0",
  "co.fs2" %% "fs2-io" % "3.11.0",

  // JWT 库
  "com.github.jwt-scala" %% "jwt-core" % "10.0.1",
  "com.github.jwt-scala" %% "jwt-circe" % "10.0.1",

  // 密码哈希
  "at.favre.lib" % "bcrypt" % "0.10.2",
  "org.mindrot" % "jbcrypt" % "0.4",

  // 其他库
  "joda-time" % "joda-time" % "2.12.7",
  "com.zaxxer" % "HikariCP" % "5.1.0",
  "org.postgresql" % "postgresql" % "42.7.2",
  "com.typesafe" % "config" % "1.4.3",
)

// 引入 jackson 辅助 json 序列化
libraryDependencies ++= Seq(
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.15.2",
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.15.2"
)

// 设置资源目录
Compile / unmanagedResourceDirectories += baseDirectory.value / "src" / "main" / "resources"

// 添加启动类
Compile / mainClass := Some("com.galphos.systemconfig.SystemConfigServiceMain")

// 启用测试
Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oD")
