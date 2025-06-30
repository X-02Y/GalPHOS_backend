ThisBuild / scalaVersion := "3.4.2"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / organization := "com.galphos"
ThisBuild / organizationName := "GalPHOS"

lazy val root = (project in file("."))
  .settings(
    name := "UserManagementService",
    
    libraryDependencies ++= Seq(
      // Web framework
      "org.http4s" %% "http4s-dsl" % "0.23.18",
      "org.http4s" %% "http4s-ember-server" % "0.23.18",
      "org.http4s" %% "http4s-ember-client" % "0.23.18",
      "org.http4s" %% "http4s-circe" % "0.23.18",
      
      // JSON
      "io.circe" %% "circe-core" % "0.14.5",
      "io.circe" %% "circe-generic" % "0.14.5",
      "io.circe" %% "circe-parser" % "0.14.5",
      
      // Database
      "org.postgresql" % "postgresql" % "42.7.1",
      "com.zaxxer" % "HikariCP" % "5.1.0",
      
      // Cats Effect
      "org.typelevel" %% "cats-effect" % "3.5.4",
      "org.typelevel" %% "cats-core" % "2.10.0",
      
      // Logging
      "ch.qos.logback" % "logback-classic" % "1.4.14",
      "org.slf4j" % "slf4j-api" % "2.0.9",
      
      // JWT
      "com.github.jwt-scala" %% "jwt-circe" % "9.4.4",
      
      // Configuration
      "com.typesafe" % "config" % "1.4.3",
      
      // Testing
      "org.scalatest" %% "scalatest" % "3.2.17" % Test,
      "org.scalatestplus" %% "mockito-4-6" % "3.2.15.0" % Test
    ),
    
    // Assembly plugin settings
    assembly / assemblyMergeStrategy := {
      case "META-INF/MANIFEST.MF" => MergeStrategy.discard
      case "META-INF/versions/9/module-info.class" => MergeStrategy.discard
      case "reference.conf" => MergeStrategy.concat
      case x if x.endsWith(".conf") => MergeStrategy.concat
      case x if x.endsWith(".properties") => MergeStrategy.concat
      case _ => MergeStrategy.first
    },
    
    assembly / assemblyJarName := "UserManagementService-assembly-0.1.0-SNAPSHOT.jar",
    
    // Runtime settings
    run / javaOptions ++= Seq(
      "-Dfile.encoding=UTF-8",
      "-Duser.timezone=Asia/Shanghai"
    )
  )
