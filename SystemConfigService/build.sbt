ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.4.2"

scriptClasspath := Seq("*")

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

assembly / mainClass := Some("Process.Server")
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
    Compile / javacOptions ++= Seq("-encoding", "UTF-8"),
    Compile / scalacOptions ++= Seq("-encoding", "UTF-8"),
    
    libraryDependencies ++= Seq(
      // HTTP4S dependencies
      "org.http4s" %% "http4s-ember-server" % "0.23.23",
      "org.http4s" %% "http4s-ember-client" % "0.23.23",
      "org.http4s" %% "http4s-circe" % "0.23.23",
      "org.http4s" %% "http4s-dsl" % "0.23.23",
      
      // Circe for JSON
      "io.circe" %% "circe-core" % "0.14.6",
      "io.circe" %% "circe-generic" % "0.14.6",
      "io.circe" %% "circe-parser" % "0.14.6",
      
      // Cats Effect
      "org.typelevel" %% "cats-effect" % "3.5.2",
      
      // Logging
      "ch.qos.logback" % "logback-classic" % "1.4.11",
      "org.slf4j" % "slf4j-api" % "2.0.9",
      
      // Database
      "org.postgresql" % "postgresql" % "42.6.0",
      "com.zaxxer" % "HikariCP" % "5.0.1",
      
      // JWT
      "com.github.jwt-scala" %% "jwt-circe" % "9.4.4",
      
      // UUID generation
      "com.fasterxml.uuid" % "java-uuid-generator" % "4.2.0",
      
      // Time handling
      "org.typelevel" %% "cats-time" % "0.5.1",
      
      // BCrypt for password hashing
      "at.favre.lib" % "bcrypt" % "0.9.0"
    )
  )
