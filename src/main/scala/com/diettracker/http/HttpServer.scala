package com.diettracker.http

import com.diettracker.config.AppConfig
import com.diettracker.services.{DailySummaryService, DietSharingService, FoodService, LogsService}
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.zio._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import zio._
import zio.http.{Middleware, Server}
import zio.json._

final case class HealthResponse(status: String)
object HealthResponse {
  implicit val codec: JsonCodec[HealthResponse] = DeriveJsonCodec.gen
}

object HttpServer {
  private val healthEndpoint: PublicEndpoint[Unit, Unit, HealthResponse, Any] =
    endpoint.get.in("health").out(jsonBody[HealthResponse]).description("Health check")

  private val healthRoute: ServerEndpoint[Any, Task] =
    healthEndpoint.serverLogicSuccess(_ => ZIO.succeed(HealthResponse("ok")))

  private val featureFlag = "feature.customFoodLogs"

  def serve: ZIO[
    Scope with AppConfig with FoodService with LogsService with DailySummaryService with DietSharingService,
    Throwable,
    Nothing
  ] =
    (for {
      cfg             <- ZIO.service[AppConfig]
      foodService     <- ZIO.service[FoodService]
      logsService     <- ZIO.service[LogsService]
      dailySummarySvc <- ZIO.service[DailySummaryService]
      dietSharingSvc  <- ZIO.service[DietSharingService]
      routes           = buildRoutes(cfg, foodService, logsService, dailySummarySvc, dietSharingSvc)
      httpApp          = ZioHttpInterpreter().toHttp(routes) @@ Middleware.debug
      configLayer      = ZLayer.succeed(Server.Config.default.port(cfg.http.port))
      _               <- ZIO.logInfo(s"Starting server on ${cfg.http.host}:${cfg.http.port}")
    } yield (httpApp, configLayer)).flatMap { case (app, layer) =>
      Server
        .serve(app)
        .provideSomeLayer[Scope](layer >>> Server.live)
    }

  private def buildRoutes(
      cfg: AppConfig,
      foodService: FoodService,
      logsService: LogsService,
      dailySummaryService: DailySummaryService,
      dietSharingService: DietSharingService
  ): List[ServerEndpoint[Any, Task]] = {
    val baseRoutes = List(healthRoute)
    if (cfg.isFeatureEnabled(featureFlag)) {
      val featureRoutes =
        FoodsEndpoints.routes(foodService) ++
          LogsEndpoints.routes(logsService) ++
          DailySummaryEndpoints.routes(dailySummaryService) ++
          DietSharingRoutes.routes(dietSharingService)
      val swaggerRoutes = SwaggerInterpreter()
        .fromServerEndpoints[Task](featureRoutes, "Diet Tracker API", "1.0.0")
      baseRoutes ++ featureRoutes ++ swaggerRoutes
    } else baseRoutes
  }
}
