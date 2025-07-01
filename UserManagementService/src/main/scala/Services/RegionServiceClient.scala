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
  name: String
)

case class School(
  id: String,
  name: String,
  provinceId: String
)

case class ProvinceWithSchools(
  id: String,
  name: String,
  schools: List[School]
)

case class RegionApiResponse[T](
  success: Boolean,
  data: Option[T] = None,
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
  def getProvinces(): IO[Either[String, List[Province]]]
  def getSchools(): IO[Either[String, List[School]]]
  def getProvincesWithSchools(): IO[Either[String, List[ProvinceWithSchools]]]
  // 新增：内部通信API - 根据ID获取省份和学校名称
  def getProvinceAndSchoolNamesByIds(provinceId: String, schoolId: String): IO[Either[String, RegionNamesResponse]]
}

class RegionServiceClientImpl(regionServiceUrl: String) extends RegionServiceClient {
  private val logger = LoggerFactory.getLogger("RegionServiceClient")
  private val httpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .build()

  override def getProvinces(): IO[Either[String, List[Province]]] = {
    IO.blocking {
      try {
        val request = HttpRequest.newBuilder()
          .uri(URI.create(s"$regionServiceUrl/api/regions/provinces"))
          .header("Content-Type", "application/json")
          .GET()
          .build()

        logger.debug(s"调用 RegionMS API: $regionServiceUrl/api/regions/provinces")
        
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        
        if (response.statusCode() == 200) {
          parseResponse[List[Province]](response.body()) match {
            case Right(provinces) => Right(provinces)
            case Left(error) => Left(s"解析省份列表失败: $error")
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

  override def getSchools(): IO[Either[String, List[School]]] = {
    IO.blocking {
      try {
        val request = HttpRequest.newBuilder()
          .uri(URI.create(s"$regionServiceUrl/api/regions/schools"))
          .header("Content-Type", "application/json")
          .GET()
          .build()

        logger.debug(s"调用 RegionMS API: $regionServiceUrl/api/regions/schools")
        
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        
        if (response.statusCode() == 200) {
          parseResponse[List[School]](response.body()) match {
            case Right(schools) => Right(schools)
            case Left(error) => Left(s"解析学校列表失败: $error")
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

  override def getProvincesWithSchools(): IO[Either[String, List[ProvinceWithSchools]]] = {
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
          parseResponse[List[ProvinceWithSchools]](response.body()) match {
            case Right(data) => Right(data)
            case Left(error) => Left(s"解析省份学校数据失败: $error")
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

  // 新增：内部通信API实现
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
          parseInternalResponse(response.body()) match {
            case Right(regionNames) =>
              logger.info(s"成功获取地区名称: 省份=${regionNames.provinceName}, 学校=${regionNames.schoolName}")
              Right(regionNames)
            case Left(error) =>
              Left(error)
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

  // 内部API响应解析（直接返回对象，不包装在标准响应中）
  private def parseInternalResponse(jsonStr: String): Either[String, RegionNamesResponse] = {
    parse(jsonStr).left.map(_.getMessage).flatMap { json =>
      // 首先尝试解析成功响应
      json.as[RegionNamesResponse] match {
        case Right(regionNames) => Right(regionNames)
        case Left(_) =>
          // 如果失败，尝试解析错误响应
          json.as[RegionErrorResponse] match {
            case Right(errorResp) => Left(errorResp.error)
            case Left(decodeError) => Left(s"JSON解析失败: ${decodeError.getMessage}")
          }
      }
    }
  }

  // 通用响应解析方法
  private def parseResponse[T](jsonStr: String)(implicit decoder: Decoder[T]): Either[String, T] = {
    parse(jsonStr).left.map(_.getMessage).flatMap { json =>
      // 首先尝试解析为包装的API响应
      json.as[RegionApiResponse[T]] match {
        case Right(apiResponse) if apiResponse.success =>
          apiResponse.data match {
            case Some(data) => Right(data)
            case None => Left("API响应中没有数据")
          }
        case Right(apiResponse) =>
          Left(apiResponse.message.getOrElse("API调用失败"))
        case Left(_) =>
          // 如果包装响应解析失败，尝试直接解析数据
          json.as[T] match {
            case Right(data) => Right(data)
            case Left(decodeError) => Left(s"JSON解析失败: ${decodeError.getMessage}")
          }
      }
    }
  }
}

// 使用伴生对象创建默认实例
object RegionServiceClient {
  def apply(regionServiceUrl: String): RegionServiceClient = new RegionServiceClientImpl(regionServiceUrl)
}
