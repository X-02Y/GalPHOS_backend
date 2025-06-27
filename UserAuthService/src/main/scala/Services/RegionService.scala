package Services

import Models.*
import cats.effect.IO
import cats.implicits.*
import Database.{DatabaseManager, SqlParameter}
import org.slf4j.{Logger, LoggerFactory}
import io.circe.Json
import java.util.UUID

trait RegionService {
  def getAllProvinces(): IO[List[ProvinceInfo]]
  def getSchoolsByProvince(provinceId: String): IO[List[SchoolInfo]]
}

class RegionServiceImpl extends RegionService {
  private val logger = LoggerFactory.getLogger("RegionService")
  private val schemaName = "authservice"

  override def getAllProvinces(): IO[List[ProvinceInfo]] = {
    val sql = s"""
      SELECT province_id, name
      FROM $schemaName.province_table
      ORDER BY name
    """.stripMargin
    
    for {
      results <- DatabaseManager.executeQuery(sql)
      provinces <- IO.pure(results.map(jsonToProvince))
      provincesWithSchools <- provinces.traverse { province =>
        for {
          schools <- getSchoolsByProvince(province.provinceId)
        } yield ProvinceInfo(
          id = province.provinceId,
          name = province.name,
          schools = schools
        )
      }
    } yield provincesWithSchools
  }

  override def getSchoolsByProvince(provinceId: String): IO[List[SchoolInfo]] = {
    val sql = s"""
      SELECT school_id, name
      FROM $schemaName.school_table
      WHERE province_id = ?
      ORDER BY name
    """.stripMargin
    
    val params = List(SqlParameter("String", provinceId))
    
    for {
      results <- DatabaseManager.executeQuery(sql, params)
      schools <- IO.pure(results.map(jsonToSchool))
    } yield schools
  }

  private def jsonToProvince(json: Json): Province = {
    Province(
      provinceId = DatabaseManager.decodeFieldUnsafe[String](json, "province_id"),
      name = DatabaseManager.decodeFieldUnsafe[String](json, "name")
    )
  }

  private def jsonToSchool(json: Json): SchoolInfo = {
    SchoolInfo(
      id = DatabaseManager.decodeFieldUnsafe[String](json, "school_id"),
      name = DatabaseManager.decodeFieldUnsafe[String](json, "name")
    )
  }
}
