package com.galphos.systemconfig.db

import doobie._
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.implicits._
import doobie.implicits.javasql._
import doobie.util.meta._
import java.time.ZonedDateTime
import java.time.ZoneId
import java.sql.{Timestamp => SqlTimestamp}

/**
 * 提供数据库相关的支持函数和类型实例
 */
object DatabaseSupport {
  // 使用 DoobieMeta 中定义的 ZonedDateTime 类型实例
  import DoobieMeta._
  
  // 提供通用数据库辅助函数
  def currentTimestamp: Fragment = fr"CURRENT_TIMESTAMP"
}
