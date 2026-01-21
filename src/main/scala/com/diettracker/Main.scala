package com.diettracker

import com.diettracker.config.{AppConfig, DatabaseLayer}
import com.diettracker.http.HttpServer
import com.diettracker.repositories._
import com.diettracker.services._
import com.diettracker.services.metrics.SharingMetrics
import com.diettracker.services.validators.DietStatusValidator
import zio._

object Main extends ZIOAppDefault {
  private val appLayer: ZLayer[
    Scope,
    Throwable,
    AppConfig with FoodService with LogsService with DailySummaryService with DietSharingService
  ] =
    ZLayer.makeSome[
      Scope,
      AppConfig with FoodService with LogsService with DailySummaryService with DietSharingService
    ](
      AppConfig.layer,
      DatabaseLayer.live,
      FoodRepository.live,
      IntakeLogRepository.live,
      DailySummaryRepository.live,
      DietRepository.live,
      DietSharingRepository.live,
      DietCopyRepository.live,
      NutritionSnapshotService.live,
      FoodService.live,
      LogsService.live,
      DailySummaryService.live,
      AuditTrailService.live,
      NotificationPublisher.logging,
      SharingMetrics.live,
      DietStatusValidator.live,
      DietSharingService.live
    )

  override def run: URIO[ZIOAppArgs with Scope, Any] =
    HttpServer.serve.provideSomeLayer[ZIOAppArgs with Scope](appLayer).orDie
}
