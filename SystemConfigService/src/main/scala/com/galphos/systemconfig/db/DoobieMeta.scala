package com.galphos.systemconfig.db

import cats.Show
import cats.effect.IO
import doobie._
import doobie.implicits._
import doobie.implicits.javasql.TimestampMeta  // Important for SQL timestamp handling
import doobie.postgres.implicits._
import doobie.util.meta.Meta
import io.circe.{Decoder, Encoder, Json}
import java.time.{Instant, ZoneId, ZonedDateTime}
import java.sql.{Timestamp => SqlTimestamp}

/**
 * 自定义的 Doobie Meta 实例，支持特殊类型与数据库类型的转换
 */
object DoobieMeta {
  // ZonedDateTime Meta instance - 使用 timestamptz 转换
  implicit val zonedDateTimeMeta: Meta[ZonedDateTime] = Meta[SqlTimestamp].imap(
    ts => ZonedDateTime.ofInstant(ts.toInstant(), ZoneId.systemDefault())
  )(
    zdt => new SqlTimestamp(zdt.toInstant().toEpochMilli())
  )
  
  // Option[ZonedDateTime] Meta instance
  implicit val optionZonedDateTimeMeta: Meta[Option[ZonedDateTime]] = 
    zonedDateTimeMeta.imap(Option(_))(_.orNull)

  // Read[ZonedDateTime] instance - explicitly define for better type safety
  implicit val zonedDateTimeRead: Read[ZonedDateTime] = Read[SqlTimestamp].map(
    ts => ZonedDateTime.ofInstant(ts.toInstant(), ZoneId.systemDefault())
  )
  
  // Put[ZonedDateTime] instance
  implicit val zonedDateTimePut: Put[ZonedDateTime] = Put[SqlTimestamp].contramap(
    zdt => new SqlTimestamp(zdt.toInstant().toEpochMilli())
  )
  
  // Get[ZonedDateTime] instance
  implicit val zonedDateTimeGet: Get[ZonedDateTime] = Get[SqlTimestamp].map(
    ts => ZonedDateTime.ofInstant(ts.toInstant(), ZoneId.systemDefault())
  )
  
  // Show instance for ZonedDateTime
  implicit val showZonedDateTime: Show[ZonedDateTime] = Show.show(_.toString)
}
