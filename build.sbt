ThisBuild / version := "1.0.0"
ThisBuild / scalaVersion := "3.4.2"
ThisBuild / organization := "com.galphos"

// 全局设置
lazy val commonSettings = Seq(
  javacOptions ++= Seq("-source", "21", "-target", "21"),
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-unchecked",
    "-Wunused:imports",
    "-Wunused:locals",
    "-Wunused:params",
    "-Wunused:privates"
  )
)

// 根项目
lazy val root = (project in file("."))
  .settings(
    name := "GalPHOS-Backend",
    commonSettings
  )
