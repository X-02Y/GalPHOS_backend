package Controllers

import cats.effect.IO
import cats.syntax.all.*
import cats.data.Validated
import cats.data.Validated.{Valid, Invalid}
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.circe.CirceEntityCodec.*
import org.typelevel.ci.CIStringSyntax
import io.circe.generic.auto.*
import io.circe.syntax.*
import Services.{RegionService, AuthService}
import Models.*
import Models.{given}
import java.util.UUID

class RegionController(regionService: RegionService, authService: AuthService) {
  
  // Helper method to extract auth token from request
  private def extractAuthToken(request: Request[IO]): Option[String] = {
    authService.extractTokenFromHeader(request.headers.get(ci"Authorization").map(_.head.value))
  }
  
  // Helper method for authentication
  private def withAuth[T](request: Request[IO], requiredRole: String)(
    action: (UUID, String) => IO[Response[IO]]
  ): IO[Response[IO]] = {
    extractAuthToken(request) match {
      case Some(token) =>
        authService.requireRole(token, requiredRole).flatMap {
          case Right((userId, role)) => action(userId, role)
          case Left(error) => Forbidden(StandardResponse.error(error).asJson)
        }
      case None =>
        Forbidden(StandardResponse.error("Authorization header missing").asJson)
    }
  }
  
  // Helper method for multiple role authentication
  private def withAnyAuth[T](request: Request[IO], allowedRoles: List[String])(
    action: (UUID, String) => IO[Response[IO]]
  ): IO[Response[IO]] = {
    extractAuthToken(request) match {
      case Some(token) =>
        authService.requireAnyRole(token, allowedRoles).flatMap {
          case Right((userId, role)) => action(userId, role)
          case Left(error) => Forbidden(StandardResponse.error(error).asJson)
        }
      case None =>
        Forbidden(StandardResponse.error("Authorization header missing").asJson)
    }
  }
  
  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {
    
    // Health check endpoint
    case GET -> Root / "health" =>
      Ok("OK")
    
    // Public endpoint for getting provinces and schools (used in registration)
    case GET -> Root / "api" / "regions" / "provinces-schools" =>
      regionService.getProvincesAndSchools().flatMap { response =>
        // Wrap the data in StandardResponse format expected by frontend
        Ok(StandardResponse.success(response.data).asJson)
      }.handleErrorWith { error =>
        InternalServerError(StandardResponse.error(s"Failed to fetch data: ${error.getMessage}").asJson)
      }
    
    // Admin endpoints for province management
    case request @ GET -> Root / "api" / "admin" / "regions" / "provinces" =>
      withAuth(request, "admin") { (_, _) =>
        regionService.getAllProvinces().flatMap { response =>
          Ok(response.asJson)
        }.handleErrorWith { error =>
          InternalServerError(StandardResponse.error(s"Failed to fetch provinces: ${error.getMessage}").asJson)
        }
      }
    
    case request @ POST -> Root / "api" / "admin" / "regions" / "provinces" =>
      withAuth(request, "admin") { (_, _) =>
        request.as[CreateProvinceRequest].flatMap { req =>
          regionService.createProvince(req.provinceName).flatMap {
            case Right(province) =>
              Created(StandardResponse.success(province).asJson)
            case Left(error) =>
              BadRequest(StandardResponse.error(error).asJson)
          }
        }.handleErrorWith { error =>
          BadRequest(StandardResponse.error(s"Invalid request: ${error.getMessage}").asJson)
        }
      }
    
    case request @ DELETE -> Root / "api" / "admin" / "regions" / "provinces" / UUIDVar(provinceId) =>
      withAuth(request, "admin") { (_, _) =>
        regionService.deleteProvince(provinceId).flatMap {
          case Right(message) =>
            Ok(StandardResponse.successMessage(message).asJson)
          case Left(error) =>
            NotFound(StandardResponse.error(error).asJson)
        }.handleErrorWith { error =>
          InternalServerError(StandardResponse.error(s"Failed to delete province: ${error.getMessage}").asJson)
        }
      }
    
    // Admin endpoints for school management
    case request @ GET -> Root / "api" / "admin" / "regions" / "schools" :? ProvinceIdQueryParamMatcher(provinceIdOpt) =>
      withAuth(request, "admin") { (_, _) =>
        provinceIdOpt match {
          case Some(Valid(provinceId)) =>
            regionService.getSchoolsByProvince(provinceId).flatMap { response =>
              Ok(response.asJson)
            }.handleErrorWith { error =>
              InternalServerError(StandardResponse.error(s"Failed to fetch schools: ${error.getMessage}").asJson)
            }
          case Some(Invalid(_)) =>
            BadRequest(StandardResponse.error("Invalid province ID format").asJson)
          case None =>
            BadRequest(StandardResponse.error("Province ID is required").asJson)
        }
      }
    
    case request @ POST -> Root / "api" / "admin" / "regions" / "schools" =>
      withAuth(request, "admin") { (_, _) =>
        request.as[CreateSchoolRequest].flatMap { req =>
          regionService.createSchool(req.schoolName, req.provinceId).flatMap {
            case Right(school) =>
              Created(StandardResponse.success(school).asJson)
            case Left(error) =>
              BadRequest(StandardResponse.error(error).asJson)
          }
        }.handleErrorWith { error =>
          BadRequest(StandardResponse.error(s"Invalid request: ${error.getMessage}").asJson)
        }
      }
    
    case request @ PUT -> Root / "api" / "admin" / "regions" / "schools" / UUIDVar(schoolId) =>
      withAuth(request, "admin") { (_, _) =>
        request.as[UpdateSchoolRequest].flatMap { req =>
          regionService.updateSchool(schoolId, req.schoolName).flatMap {
            case Right(school) =>
              Ok(StandardResponse.success(school).asJson)
            case Left(error) =>
              NotFound(StandardResponse.error(error).asJson)
          }
        }.handleErrorWith { error =>
          BadRequest(StandardResponse.error(s"Invalid request: ${error.getMessage}").asJson)
        }
      }
    
    case request @ DELETE -> Root / "api" / "admin" / "regions" / "schools" / UUIDVar(schoolId) =>
      withAuth(request, "admin") { (_, _) =>
        regionService.deleteSchool(schoolId).flatMap {
          case Right(message) =>
            Ok(StandardResponse.successMessage(message).asJson)
          case Left(error) =>
            NotFound(StandardResponse.error(error).asJson)
        }.handleErrorWith { error =>
          InternalServerError(StandardResponse.error(s"Failed to delete school: ${error.getMessage}").asJson)
        }
      }
    
    // Student endpoints for region change requests
    case request @ POST -> Root / "api" / "student" / "region-change" =>
      withAuth(request, "student") { (userId, _) =>
        request.as[RegionChangeSubmissionRequest].flatMap { req =>
          regionService.createRegionChangeRequest(
            userId = userId,
            userType = "student",
            currentProvinceId = None, // We could fetch current info from user service
            currentSchoolId = None,
            requestedProvinceId = req.provinceId,
            requestedSchoolId = req.schoolId,
            reason = req.reason
          ).flatMap {
            case Right(request) =>
              Created(StandardResponse.success(Map("requestId" -> request.id)).asJson)
            case Left(error) =>
              BadRequest(StandardResponse.error(error).asJson)
          }
        }.handleErrorWith { error =>
          BadRequest(StandardResponse.error(s"Invalid request: ${error.getMessage}").asJson)
        }
      }
    
    case request @ GET -> Root / "api" / "student" / "region-change-status" =>
      withAuth(request, "student") { (userId, _) =>
        regionService.getRegionChangeRequestsByUser(userId).flatMap { response =>
          // Return the most recent request
          response.requests.headOption match {
            case Some(request) =>
              Ok(StandardResponse.success(Map(
                "status" -> request.status,
                "requestId" -> request.id.toString,
                "reason" -> request.reason,
                "createdAt" -> request.createdAt.toString
              )).asJson)
            case None =>
              Ok(StandardResponse.success(Map(
                "status" -> "none"
              )).asJson)
          }
        }.handleErrorWith { error =>
          InternalServerError(StandardResponse.error(s"Failed to fetch status: ${error.getMessage}").asJson)
        }
      }
    
    // Coach endpoints for region change requests
    case request @ POST -> Root / "api" / "coach" / "region-change-request" =>
      withAuth(request, "coach") { (userId, _) =>
        request.as[RegionChangeSubmissionRequest].flatMap { req =>
          regionService.createRegionChangeRequest(
            userId = userId,
            userType = "coach",
            currentProvinceId = None,
            currentSchoolId = None,
            requestedProvinceId = req.provinceId,
            requestedSchoolId = req.schoolId,
            reason = req.reason
          ).flatMap {
            case Right(request) =>
              Created(StandardResponse.success(Map("requestId" -> request.id)).asJson)
            case Left(error) =>
              BadRequest(StandardResponse.error(error).asJson)
          }
        }.handleErrorWith { error =>
          BadRequest(StandardResponse.error(s"Invalid request: ${error.getMessage}").asJson)
        }
      }
    
    case request @ GET -> Root / "api" / "coach" / "profile" / "change-region-requests" =>
      withAuth(request, "coach") { (userId, _) =>
        regionService.getRegionChangeRequestsByUser(userId).flatMap { response =>
          Ok(response.asJson)
        }.handleErrorWith { error =>
          InternalServerError(StandardResponse.error(s"Failed to fetch requests: ${error.getMessage}").asJson)
        }
      }
    
    // Admin endpoints for region change request management
    case request @ GET -> Root / "api" / "admin" / "regions" / "change-requests" :? 
      StatusQueryParamMatcher(status) +& PageQueryParamMatcher(pageOpt) +& LimitQueryParamMatcher(limitOpt) =>
      withAuth(request, "admin") { (_, _) =>
        val page = pageOpt.fold(Some(1))(_.fold(_ => None, Some(_)))
        val limit = limitOpt.fold(Some(10))(_.fold(_ => None, Some(_)))
        
        (page, limit) match {
          case (Some(p), Some(l)) =>
            regionService.getRegionChangeRequests(status, Some(p), Some(l)).flatMap { response =>
              Ok(response.asJson)
            }.handleErrorWith { error =>
              InternalServerError(StandardResponse.error(s"Failed to fetch requests: ${error.getMessage}").asJson)
            }
          case _ =>
            BadRequest(StandardResponse.error("Invalid page or limit parameter").asJson)
        }
      }
    
    case request @ POST -> Root / "api" / "admin" / "regions" / "change-requests" / UUIDVar(requestId) =>
      withAuth(request, "admin") { (userId, _) =>
        request.as[HandleRegionChangeRequest].flatMap { req =>
          regionService.handleRegionChangeRequest(requestId, req.action, userId, req.reason).flatMap {
            case Right(request) =>
              Ok(StandardResponse.success(request).asJson)
            case Left(error) =>
              BadRequest(StandardResponse.error(error).asJson)
          }
        }.handleErrorWith { error =>
          BadRequest(StandardResponse.error(s"Invalid request: ${error.getMessage}").asJson)
        }
      }
  }
}

// Query parameter matchers
import org.http4s.{QueryParamDecoder, ParseFailure}
import scala.util.Try

implicit val uuidQueryParamDecoder: QueryParamDecoder[UUID] = 
  QueryParamDecoder[String].emap(str => 
    Try(UUID.fromString(str)).toEither.left.map(ex => ParseFailure("Invalid UUID", ex.getMessage))
  )

object ProvinceIdQueryParamMatcher extends OptionalValidatingQueryParamDecoderMatcher[UUID]("provinceId")
object StatusQueryParamMatcher extends OptionalQueryParamDecoderMatcher[String]("status")
object PageQueryParamMatcher extends OptionalValidatingQueryParamDecoderMatcher[Int]("page")
object LimitQueryParamMatcher extends OptionalValidatingQueryParamDecoderMatcher[Int]("limit")
