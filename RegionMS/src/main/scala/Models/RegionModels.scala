package Models

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

// Custom encoders/decoders for OffsetDateTime to ensure proper JSON formatting
given Encoder[OffsetDateTime] = Encoder.encodeString.contramap[OffsetDateTime](_.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
given Decoder[OffsetDateTime] = Decoder.decodeString.emap { str =>
  try {
    Right(OffsetDateTime.parse(str, DateTimeFormatter.ISO_OFFSET_DATE_TIME))
  } catch {
    case _: Exception => Left(s"Invalid date format: $str")
  }
}

case class Province(
  id: UUID,
  name: String,
  createdAt: OffsetDateTime,
  updatedAt: OffsetDateTime
)

object Province {
  given Decoder[Province] = deriveDecoder[Province]
  given Encoder[Province] = deriveEncoder[Province]
}

case class School(
  id: UUID,
  name: String,
  provinceId: UUID,
  province: Option[Province] = None,
  createdAt: OffsetDateTime,
  updatedAt: OffsetDateTime
)

object School {
  given Decoder[School] = deriveDecoder[School]
  given Encoder[School] = deriveEncoder[School]
}

case class RegionChangeRequest(
  id: UUID,
  userId: UUID,
  userType: String,
  currentProvinceId: Option[UUID],
  currentSchoolId: Option[UUID],
  requestedProvinceId: UUID,
  requestedSchoolId: UUID,
  reason: String,
  status: String,
  reviewedBy: Option[UUID],
  reviewNote: Option[String],
  createdAt: OffsetDateTime,
  updatedAt: OffsetDateTime,
  // Populated fields
  currentProvince: Option[Province] = None,
  currentSchool: Option[School] = None,
  requestedProvince: Option[Province] = None,
  requestedSchool: Option[School] = None
)

object RegionChangeRequest {
  given Decoder[RegionChangeRequest] = deriveDecoder[RegionChangeRequest]
  given Encoder[RegionChangeRequest] = deriveEncoder[RegionChangeRequest]
}

// Request DTOs
case class CreateProvinceRequest(name: String)  // Changed from provinceName to name
case class CreateSchoolRequest(provinceId: UUID, name: String)  // Changed from schoolName to name
case class UpdateSchoolRequest(name: String)  // Changed from schoolName to name
case class RegionChangeSubmissionRequest(
  provinceId: UUID,
  schoolId: UUID,
  reason: String
)
case class HandleRegionChangeRequest(
  action: String,
  reason: Option[String]
)

// Response DTOs
case class ProvinceResponse(provinces: List[Province], total: Int)
case class SchoolResponse(schools: List[School], total: Int)
case class RegionChangeRequestResponse(
  requests: List[RegionChangeRequest], 
  total: Int,
  pagination: Option[PaginationInfo] = None
)
// For frontend compatibility - provinces with nested schools
case class ProvinceWithSchools(
  id: String,  // Frontend expects string IDs
  name: String,
  schools: List[SchoolForFrontend],
  createdAt: OffsetDateTime,
  updatedAt: OffsetDateTime
)

case class SchoolForFrontend(
  id: String,  // Frontend expects string IDs
  name: String,
  createdAt: OffsetDateTime,
  updatedAt: OffsetDateTime
)

// Response for provinces-schools endpoint (frontend compatible)
case class ProvincesAndSchoolsResponse(data: List[ProvinceWithSchools])

// Legacy response for separate provinces and schools
case class SeparateProvincesAndSchoolsResponse(provinces: List[Province], schools: List[School])

// Response for admin regions endpoint - matches documentation format
case class AdminRegionsResponse(
  regions: List[ProvinceWithSchools], // Using ProvinceWithSchools as "Region" with nested schools
  provinces: List[Province],
  schools: List[School]
)

case class PaginationInfo(
  page: Int,
  limit: Int,
  total: Int,
  totalPages: Int
)

case class StandardResponse[T](
  success: Boolean,
  data: Option[T] = None,
  message: Option[String] = None
)

object StandardResponse {
  def success[T](data: T): StandardResponse[T] = StandardResponse[T](success = true, data = Some(data))
  def success[T](data: T, message: String): StandardResponse[T] = StandardResponse[T](success = true, data = Some(data), message = Some(message))
  def successMessage(message: String): StandardResponse[String] = StandardResponse[String](success = true, message = Some(message))
  def error(message: String): StandardResponse[String] = StandardResponse[String](success = false, message = Some(message))
}

object Models {
  given Decoder[CreateProvinceRequest] = deriveDecoder[CreateProvinceRequest]
  given Encoder[CreateProvinceRequest] = deriveEncoder[CreateProvinceRequest]
  
  given Decoder[CreateSchoolRequest] = deriveDecoder[CreateSchoolRequest]
  given Encoder[CreateSchoolRequest] = deriveEncoder[CreateSchoolRequest]
  
  given Decoder[UpdateSchoolRequest] = deriveDecoder[UpdateSchoolRequest]
  given Encoder[UpdateSchoolRequest] = deriveEncoder[UpdateSchoolRequest]
  
  given Decoder[RegionChangeSubmissionRequest] = deriveDecoder[RegionChangeSubmissionRequest]
  given Encoder[RegionChangeSubmissionRequest] = deriveEncoder[RegionChangeSubmissionRequest]
  
  given Decoder[HandleRegionChangeRequest] = deriveDecoder[HandleRegionChangeRequest]
  given Encoder[HandleRegionChangeRequest] = deriveEncoder[HandleRegionChangeRequest]
  
  given Decoder[ProvinceResponse] = deriveDecoder[ProvinceResponse]
  given Encoder[ProvinceResponse] = deriveEncoder[ProvinceResponse]
  
  given Decoder[SchoolResponse] = deriveDecoder[SchoolResponse]
  given Encoder[SchoolResponse] = deriveEncoder[SchoolResponse]
  
  given Decoder[RegionChangeRequestResponse] = deriveDecoder[RegionChangeRequestResponse]
  given Encoder[RegionChangeRequestResponse] = deriveEncoder[RegionChangeRequestResponse]
  
  given Decoder[ProvincesAndSchoolsResponse] = deriveDecoder[ProvincesAndSchoolsResponse]
  given Encoder[ProvincesAndSchoolsResponse] = deriveEncoder[ProvincesAndSchoolsResponse]
  
  given Decoder[PaginationInfo] = deriveDecoder[PaginationInfo]
  given Encoder[PaginationInfo] = deriveEncoder[PaginationInfo]
  
  given [T: Decoder]: Decoder[StandardResponse[T]] = deriveDecoder[StandardResponse[T]]
  given [T: Encoder]: Encoder[StandardResponse[T]] = deriveEncoder[StandardResponse[T]]
}

object ProvinceWithSchools {
  given Decoder[ProvinceWithSchools] = deriveDecoder[ProvinceWithSchools]
  given Encoder[ProvinceWithSchools] = deriveEncoder[ProvinceWithSchools]
}

object SchoolForFrontend {
  given Decoder[SchoolForFrontend] = deriveDecoder[SchoolForFrontend]
  given Encoder[SchoolForFrontend] = deriveEncoder[SchoolForFrontend]
}

object ProvincesAndSchoolsResponse {
  given Decoder[ProvincesAndSchoolsResponse] = deriveDecoder[ProvincesAndSchoolsResponse]
  given Encoder[ProvincesAndSchoolsResponse] = deriveEncoder[ProvincesAndSchoolsResponse]
}

object SeparateProvincesAndSchoolsResponse {
  given Decoder[SeparateProvincesAndSchoolsResponse] = deriveDecoder[SeparateProvincesAndSchoolsResponse]
  given Encoder[SeparateProvincesAndSchoolsResponse] = deriveEncoder[SeparateProvincesAndSchoolsResponse]
}

object AdminRegionsResponse {
  given Decoder[AdminRegionsResponse] = deriveDecoder[AdminRegionsResponse]
  given Encoder[AdminRegionsResponse] = deriveEncoder[AdminRegionsResponse]
}
