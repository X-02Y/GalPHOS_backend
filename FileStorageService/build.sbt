ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.4.2"

lazy val root = (project in file("."))
  .settings(
    name := "FileStorageService",
    mainClass := Some("Process.FileStorageApp"),
    libraryDependencies ++= Seq(
      // Akka HTTP
      "com.typesafe.akka" %% "akka-http" % "10.5.0",
      "com.typesafe.akka" %% "akka-http-spray-json" % "10.5.0",
      "com.typesafe.akka" %% "akka-actor-typed" % "2.8.0",
      "com.typesafe.akka" %% "akka-stream" % "2.8.0",
      
      // JSON support
      "com.typesafe.akka" %% "akka-http-spray-json" % "10.5.0",
      "io.spray" %% "spray-json" % "1.3.6",
      
      // PostgreSQL
      "org.postgresql" % "postgresql" % "42.6.0",
      "com.zaxxer" % "HikariCP" % "5.0.1",
      
      // File handling
      "commons-io" % "commons-io" % "2.11.0",
      "commons-codec" % "commons-codec" % "1.16.0",
      
      // Apache POI for Excel
      "org.apache.poi" % "poi" % "5.2.4",
      "org.apache.poi" % "poi-ooxml" % "5.2.4",
      
      // Configuration
      "com.typesafe" % "config" % "1.4.2",
      
      // Logging
      "ch.qos.logback" % "logback-classic" % "1.4.11",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
      
      // Testing
      "org.scalatest" %% "scalatest" % "3.2.17" % Test,
      "com.typesafe.akka" %% "akka-http-testkit" % "10.5.0" % Test,
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % "2.8.0" % Test
    ),
    
    // Compiler options
    scalacOptions ++= Seq(
      "-deprecation",
      "-encoding", "UTF-8",
      "-feature",
      "-unchecked"
    ),
    
    // Assembly settings for fat JAR
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case PathList("reference.conf") => MergeStrategy.concat
      case _ => MergeStrategy.first
    }
  )
