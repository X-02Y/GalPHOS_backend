package Services

import cats.effect.IO
import Models.*
import Database.RegionDAO
import java.util.UUID

class RegionService(dao: RegionDAO) {
  
  // Province operations
  def getAllProvinces(): IO[ProvinceResponse] = {
    dao.getAllProvinces().map { provinces =>
      ProvinceResponse(provinces, provinces.length)
    }
  }
  
  def getProvinceById(id: UUID): IO[Option[Province]] = {
    dao.getProvinceById(id)
  }
  
  def createProvince(name: String): IO[Either[String, Province]] = {
    dao.createProvince(name).map {
      case Some(province) => Right(province)
      case None => Left("Failed to create province")
    }
  }
  
  def deleteProvince(id: UUID): IO[Either[String, String]] = {
    dao.deleteProvince(id).map { rows =>
      if (rows > 0) Right("Province deleted successfully")
      else Left("Province not found")
    }
  }
  
  // School operations
  def getSchoolsByProvince(provinceId: UUID): IO[SchoolResponse] = {
    dao.getSchoolsByProvince(provinceId).map { schools =>
      SchoolResponse(schools, schools.length)
    }
  }
  
  def getAllSchools(): IO[List[School]] = {
    dao.getAllSchools()
  }
  
  def getSchoolById(id: UUID): IO[Option[School]] = {
    dao.getSchoolById(id)
  }
  
  def createSchool(name: String, provinceId: UUID): IO[Either[String, School]] = {
    // First check if province exists
    dao.getProvinceById(provinceId).flatMap {
      case Some(_) =>
        dao.createSchool(name, provinceId).map {
          case Some(school) => Right(school)
          case None => Left("Failed to create school")
        }
      case None =>
        IO.pure(Left("Province not found"))
    }
  }
  
  def updateSchool(id: UUID, name: String): IO[Either[String, School]] = {
    dao.updateSchool(id, name).flatMap { rows =>
      if (rows > 0) {
        dao.getSchoolById(id).map {
          case Some(school) => Right(school)
          case None => Left("School not found after update")
        }
      } else {
        IO.pure(Left("School not found"))
      }
    }
  }
  
  def deleteSchool(id: UUID): IO[Either[String, String]] = {
    dao.deleteSchool(id).map { rows =>
      if (rows > 0) Right("School deleted successfully")
      else Left("School not found")
    }
  }
  
  // Combined data for registration - Frontend compatible format
  def getProvincesAndSchools(): IO[ProvincesAndSchoolsResponse] = {
    for {
      provinces <- dao.getAllProvinces()
      schools <- dao.getAllSchools()
    } yield {
      // Group schools by province and create nested structure
      val schoolsByProvince = schools.groupBy(_.provinceId)
      
      val provincesWithSchools = provinces.map { province =>
        val provinceSchools = schoolsByProvince.getOrElse(province.id, List.empty)
          .map(school => SchoolForFrontend(school.id.toString, school.name, school.createdAt, school.updatedAt))
        
        ProvinceWithSchools(
          id = province.id.toString,
          name = province.name,
          schools = provinceSchools,
          createdAt = province.createdAt,
          updatedAt = province.updatedAt
        )
      }
      
      ProvincesAndSchoolsResponse(data = provincesWithSchools)
    }
  }
  
  // Admin regions data - matches documentation format: { regions, provinces, schools }
  def getAdminRegions(): IO[AdminRegionsResponse] = {
    for {
      provinces <- dao.getAllProvinces()
      schools <- dao.getAllSchools()
    } yield {
      // Group schools by province and create nested structure for "regions"
      val schoolsByProvince = schools.groupBy(_.provinceId)
      
      val regions = provinces.map { province =>
        val provinceSchools = schoolsByProvince.getOrElse(province.id, List.empty)
          .map(school => SchoolForFrontend(school.id.toString, school.name, school.createdAt, school.updatedAt))
        
        ProvinceWithSchools(
          id = province.id.toString,
          name = province.name,
          schools = provinceSchools,
          createdAt = province.createdAt,
          updatedAt = province.updatedAt
        )
      }
      
      AdminRegionsResponse(
        regions = regions,
        provinces = provinces,
        schools = schools
      )
    }
  }
  
  // Region change request operations
  def createRegionChangeRequest(
    userId: UUID,
    userType: String,
    currentProvinceId: Option[UUID],
    currentSchoolId: Option[UUID],
    requestedProvinceId: UUID,
    requestedSchoolId: UUID,
    reason: String
  ): IO[Either[String, RegionChangeRequest]] = {
    // Validate that requested province and school exist
    for {
      province <- dao.getProvinceById(requestedProvinceId)
      school <- dao.getSchoolById(requestedSchoolId)
      result <- (province, school) match {
        case (Some(_), Some(s)) if s.provinceId == requestedProvinceId =>
          dao.createRegionChangeRequest(
            userId, userType, currentProvinceId, currentSchoolId,
            requestedProvinceId, requestedSchoolId, reason
          ).map {
            case Some(request) => Right(request)
            case None => Left("Failed to create region change request")
          }
        case (None, _) =>
          IO.pure(Left("Requested province not found"))
        case (_, None) =>
          IO.pure(Left("Requested school not found"))
        case (_, Some(_)) =>
          IO.pure(Left("School does not belong to the specified province"))
      }
    } yield result
  }
  
  def getRegionChangeRequestsByUser(userId: UUID): IO[RegionChangeRequestResponse] = {
    dao.getRegionChangeRequestsByUser(userId).map { requests =>
      RegionChangeRequestResponse(requests, requests.length)
    }
  }
  
  def getRegionChangeRequests(
    status: Option[String],
    page: Option[Int],
    limit: Option[Int]
  ): IO[RegionChangeRequestResponse] = {
    val actualPage = page.getOrElse(1)
    val actualLimit = limit.getOrElse(10)
    
    for {
      requests <- dao.getRegionChangeRequests(status, actualPage, actualLimit)
      total <- dao.countRegionChangeRequests(status)
    } yield {
      val pagination = PaginationInfo(
        page = actualPage,
        limit = actualLimit,
        total = total,
        totalPages = (total + actualLimit - 1) / actualLimit
      )
      RegionChangeRequestResponse(requests, total, Some(pagination))
    }
  }
  
  def handleRegionChangeRequest(
    requestId: UUID,
    action: String,
    reviewedBy: UUID,
    reviewNote: Option[String]
  ): IO[Either[String, RegionChangeRequest]] = {
    val status = action.toLowerCase match {
      case "approve" => "approved"
      case "reject" => "rejected"
      case _ => return IO.pure(Left("Invalid action. Use 'approve' or 'reject'"))
    }
    
    dao.updateRegionChangeRequestStatus(requestId, status, reviewedBy, reviewNote).flatMap { rows =>
      if (rows > 0) {
        dao.getRegionChangeRequestById(requestId).map {
          case Some(request) => Right(request)
          case None => Left("Request not found after update")
        }
      } else {
        IO.pure(Left("Region change request not found"))
      }
    }
  }
  
  def getRegionChangeRequestById(id: UUID): IO[Option[RegionChangeRequest]] = {
    dao.getRegionChangeRequestById(id)
  }
  
  // Internal API for getting province and school names by IDs
  def getProvinceAndSchoolByIds(provinceId: String, schoolId: String): IO[Either[String, InternalRegionResponse]] = {
    // Convert string IDs to UUIDs
    try {
      val provinceUuid = UUID.fromString(provinceId)
      val schoolUuid = UUID.fromString(schoolId)
      
      for {
        provinceOpt <- dao.getProvinceById(provinceUuid)
        schoolOpt <- dao.getSchoolById(schoolUuid)
      } yield {
        (provinceOpt, schoolOpt) match {
          case (Some(province), Some(school)) =>
            // Verify that the school belongs to the specified province
            if (school.provinceId == provinceUuid) {
              Right(InternalRegionResponse(
                provinceName = province.name,
                schoolName = school.name
              ))
            } else {
              Left(s"School with ID $schoolId does not belong to province with ID $provinceId")
            }
          case (None, _) =>
            Left(s"Province not found with ID: $provinceId")
          case (_, None) =>
            Left(s"School not found with ID: $schoolId")
        }
      }
    } catch {
      case _: IllegalArgumentException =>
        IO.pure(Left("Invalid UUID format for provinceId or schoolId"))
    }
  }
}
