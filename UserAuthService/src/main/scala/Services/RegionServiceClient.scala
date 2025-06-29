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

trait RegionServiceClient {
  def validateProvinceAndSchool(provinceId: String, schoolId: String): IO[Either[String, Boolean]]
  def getProvincesAndSchools(): IO[Either[String, List[Province]]]
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
}
