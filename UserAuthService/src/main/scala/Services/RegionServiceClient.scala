package Services

import cats.effect.IO
import io.circe.*
import io.circe.parser.*
import io.circe.generic.auto.*
import org.slf4j.LoggerFactory
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.net.URI
import java.time.Duration

// RegionMS 响应模型
case class Province(
  id: String,
  name: String,
  schools: List[School]
)

case class School(
  id: String,
  name: String
)

case class RegionResponse(
  success: Boolean,
  data: Option[List[Province]] = None,
  message: Option[String] = None
)

// 内部API响应模型
case class RegionNamesResponse(
  provinceName: String,
  schoolName: String
)

case class RegionErrorResponse(
  error: String
)

trait RegionServiceClient {
  def validateProvinceAndSchool(provinceId: String, schoolId: String): IO[Either[String, Boolean]]
  def getProvincesAndSchools(): IO[Either[String, List[Province]]]
  // 新增：内部通信API - 根据ID获取省份和学校名称
  def getProvinceAndSchoolNamesByIds(provinceId: String, schoolId: String): IO[Either[String, RegionNamesResponse]]
}

class RegionServiceClientImpl(regionServiceUrl: String) extends RegionServiceClient {
  private val logger = LoggerFactory.getLogger("RegionServiceClient")
  private val httpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .build()

  override def validateProvinceAndSchool(provinceId: String, schoolId: String): IO[Either[String, Boolean]] = {
    for {
      provincesResult <- getProvincesAndSchools()
      result <- provincesResult match {
        case Right(provinces) =>
          // 查找省份是否存在
          provinces.find(_.id == provinceId) match {
            case Some(province) =>
              // 查找学校是否属于该省份（学校在该省份的 schools 列表中）
              province.schools.find(_.id == schoolId) match {
                case Some(_) =>
                  logger.info(s"省份和学校验证成功: provinceId=$provinceId, schoolId=$schoolId")
                  IO.pure(Right(true))
                case None =>
                  logger.warn(s"学校不存在或不属于指定省份: provinceId=$provinceId, schoolId=$schoolId")
                  IO.pure(Left(s"学校ID $schoolId 不存在或不属于省份 $provinceId"))
              }
            case None =>
              logger.warn(s"省份不存在: provinceId=$provinceId")
              IO.pure(Left(s"省份ID $provinceId 不存在"))
          }
        case Left(error) =>
          IO.pure(Left(s"获取省份学校信息失败: $error"))
      }
    } yield result
  }

  override def getProvincesAndSchools(): IO[Either[String, List[Province]]] = {
    IO.blocking {
      try {
        val request = HttpRequest.newBuilder()
          .uri(URI.create(s"$regionServiceUrl/api/regions/provinces-schools"))
          .header("Content-Type", "application/json")
          .GET()
          .build()

        logger.debug(s"调用 RegionMS API: $regionServiceUrl/api/regions/provinces-schools")
        
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        
        if (response.statusCode() == 200) {
          parse(response.body()) match {
            case Right(json) =>
              // 尝试解析为 RegionResponse 格式
              json.as[RegionResponse] match {
                case Right(regionResponse) if regionResponse.success =>
                  regionResponse.data match {
                    case Some(provinces) =>
                      logger.info(s"成功获取省份学校信息，省份数量: ${provinces.length}")
                      Right(provinces)
                    case None =>
                      logger.warn("RegionMS 返回空数据")
                      Left("RegionMS 返回空数据")
                  }
                case Right(regionResponse) =>
                  val errorMsg = regionResponse.message.getOrElse("RegionMS 返回失败状态")
                  logger.error(s"RegionMS 返回错误: $errorMsg")
                  Left(errorMsg)
                case Left(decodeError) =>
                  logger.error(s"解析 RegionMS 响应失败: ${decodeError.getMessage}")
                  Left(s"解析响应失败: ${decodeError.getMessage}")
              }
            case Left(parseError) =>
              logger.error(s"JSON 解析失败: ${parseError.getMessage}")
              Left(s"JSON 解析失败: ${parseError.getMessage}")
          }
        } else {
          logger.error(s"RegionMS API 调用失败，状态码: ${response.statusCode()}")
          Left(s"RegionMS API 调用失败，状态码: ${response.statusCode()}")
        }
      } catch {
        case ex: Exception =>
          logger.error(s"调用 RegionMS 时发生异常: ${ex.getMessage}", ex)
          Left(s"调用 RegionMS 时发生异常: ${ex.getMessage}")
      }
    }
  }

  override def getProvinceAndSchoolNamesByIds(provinceId: String, schoolId: String): IO[Either[String, RegionNamesResponse]] = {
    IO.blocking {
      try {
        val request = HttpRequest.newBuilder()
          .uri(URI.create(s"$regionServiceUrl/internal/regions?provinceId=$provinceId&schoolId=$schoolId"))
          .header("Content-Type", "application/json")
          .GET()
          .build()

        logger.debug(s"调用 RegionMS 内部API: $regionServiceUrl/internal/regions?provinceId=$provinceId&schoolId=$schoolId")
        
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        
        if (response.statusCode() == 200) {
          parse(response.body()) match {
            case Right(json) =>
              json.as[RegionNamesResponse] match {
                case Right(regionNames) =>
                  logger.info(s"成功获取地区名称: 省份=${regionNames.provinceName}, 学校=${regionNames.schoolName}")
                  Right(regionNames)
                case Left(decodeError) =>
                  logger.error(s"解析地区名称响应失败: ${decodeError.getMessage}")
                  Left(s"解析响应失败: ${decodeError.getMessage}")
              }
            case Left(parseError) =>
              logger.error(s"JSON 解析失败: ${parseError.getMessage}")
              Left(s"JSON 解析失败: ${parseError.getMessage}")
          }
        } else if (response.statusCode() == 400) {
          // 处理地区不存在或参数错误的情况
          parse(response.body()) match {
            case Right(json) =>
              json.as[RegionErrorResponse] match {
                case Right(errorResponse) =>
                  logger.warn(s"地区API调用错误: ${errorResponse.error}")
                  Left(errorResponse.error)
                case Left(_) =>
                  Left("地区信息获取失败")
              }
            case Left(_) =>
              Left("地区信息获取失败")
          }
        } else {
          logger.error(s"RegionMS 内部API 调用失败，状态码: ${response.statusCode()}")
          Left(s"RegionMS 内部API 调用失败，状态码: ${response.statusCode()}")
        }
      } catch {
        case ex: Exception =>
          logger.error(s"调用 RegionMS 内部API 时发生异常: ${ex.getMessage}", ex)
          Left(s"调用 RegionMS 内部API 时发生异常: ${ex.getMessage}")
      }
    }
  }
}
