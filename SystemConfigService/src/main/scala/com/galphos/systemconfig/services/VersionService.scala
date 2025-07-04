package com.galphos.systemconfig.services

import cats.effect.{IO, Resource}
import com.galphos.systemconfig.models._
import com.galphos.systemconfig.db.DoobieMeta._
import com.galphos.systemconfig.db.DatabaseSupport._
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import java.time.ZonedDateTime
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger

class VersionService(xa: Resource[IO, Transactor[IO]]) {
  private val logger = Slf4jLogger.getLogger[IO]
  
  // 获取当前系统版本
  def getCurrentVersion: IO[Option[SystemVersion]] = {
    val query = sql"""
      SELECT id, version, build_number, release_date, release_notes, is_current
      FROM system_version
      WHERE is_current = true
    """.query[(Long, String, String, ZonedDateTime, Option[String], Boolean)]
      .map { case (id, version, buildNumber, releaseDate, releaseNotes, isCurrent) =>
        SystemVersion(Some(id), version, buildNumber, releaseDate, releaseNotes, isCurrent)
      }.option
      
    xa.use(query.transact(_)).handleErrorWith { error =>
      logger.error(error)("获取当前版本信息失败") *> IO.pure(None)
    }
  }
  
  // 获取所有版本历史
  def getVersionHistory: IO[List[SystemVersion]] = {
    val query = sql"""
      SELECT id, version, build_number, release_date, release_notes, is_current
      FROM system_version
      ORDER BY release_date DESC
    """.query[(Long, String, String, ZonedDateTime, Option[String], Boolean)]
      .map { case (id, version, buildNumber, releaseDate, releaseNotes, isCurrent) =>
        SystemVersion(Some(id), version, buildNumber, releaseDate, releaseNotes, isCurrent)
      }.to[List]
      
    xa.use(query.transact(_)).handleErrorWith { error =>
      logger.error(error)("获取版本历史失败") *> IO.pure(List.empty)
    }
  }
}
