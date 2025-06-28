package Process

import cats.effect.IO
import Database.{DatabaseConnection, RegionDAO}
import Services.{RegionService, AuthService}
import Controllers.RegionController
import Config.ServerConfig

object Init {
  
  def init(config: ServerConfig): IO[RegionController] = {
    for {
      // Initialize database connection
      dbConnection <- IO.pure(new DatabaseConnection(config))
      
      // Initialize DAO
      regionDAO <- IO.pure(new RegionDAO(dbConnection))
      
      // Initialize services
      authService <- IO.pure(new AuthService(config))
      regionService <- IO.pure(new RegionService(regionDAO))
      
      // Initialize controller
      regionController <- IO.pure(new RegionController(regionService, authService))
      
    } yield regionController
  }
}
