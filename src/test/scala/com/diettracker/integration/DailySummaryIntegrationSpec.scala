package com.diettracker.integration

import com.diettracker.config.{AppConfig, DatabaseConfig, HttpConfig, SharingConfig}
import com.diettracker.config.DatabaseLayer
import com.diettracker.domain._
import com.diettracker.repositories.{DailySummaryRepository, FoodRepository, IntakeLogRepository}
import com.diettracker.services.{DailySummaryService, FoodService, LogsService, ServiceError}
import com.diettracker.services.FoodService.CreateFoodRequest
import com.diettracker.services.LogsService.CreateLogRequest
import com.diettracker.services.NutritionSnapshotService
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import zio._
import zio.test._

import java.time.{LocalDate, OffsetDateTime, ZoneId}
import java.util.UUID

object DailySummaryIntegrationSpec extends ZIOSpecDefault {
  private lazy val integrationLayer
      : ZLayer[Scope, Throwable, FoodService with LogsService with DailySummaryService with AppConfig] =
    ZLayer.makeSome[Scope, FoodService with LogsService with DailySummaryService with AppConfig](
      postgresLayer,
      DatabaseLayer.live,
      FoodRepository.live,
      IntakeLogRepository.live,
      DailySummaryRepository.live,
      NutritionSnapshotService.live,
      FoodService.live,
      LogsService.live,
      DailySummaryService.live
    )

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("Daily summary integration")(
      test("aggregates totals and reports macro completeness percent") {
        val zone = ZoneId.of("UTC")
        for {
          foodService    <- ZIO.service[FoodService]
          logsService    <- ZIO.service[LogsService]
          summaryService <- ZIO.service[DailySummaryService]
          userId          = UserId(UUID.randomUUID())
          withMacros     <- createFood(foodService, userId, "Protein Bowl", macrosDefined = true)
          withoutMacros  <- createFood(foodService, userId, "Plain Rice", macrosDefined = false)
          today           = LocalDate.now(zone)
          now             = OffsetDateTime.now(zone)
          _              <- logsService.createLog(
                              userId,
                              CreateLogRequest(
                                foodId = withMacros.id,
                                loggedAt = now,
                                quantity = ServingQuantity(BigDecimal(1)),
                                unit = None,
                                notes = Some(LogNotes("lunch"))
                              )
                            )
          _              <- logsService.createLog(
                              userId,
                              CreateLogRequest(
                                foodId = withoutMacros.id,
                                loggedAt = now.plusHours(1),
                                quantity = ServingQuantity(BigDecimal(1)),
                                unit = None,
                                notes = Some(LogNotes("snack"))
                              )
                            )
          summary        <- summaryService.getDailySummary(userId, today, zone)
          totalCalories   = summary.totalCalories.value
          totalProtein    = summary.totalProtein.value
          completeness    = summary.macroCompleteness
        } yield assertTrue(
          totalCalories == BigDecimal(300),
          totalProtein == BigDecimal(25),
          !completeness.protein.value,
          !completeness.carbs.value,
          !completeness.fat.value,
          summary.entries.length == 2
        )
      }.provideSomeLayerShared[Scope](integrationLayer)
    )

  private def createFood(
      foodService: FoodService,
      userId: UserId,
      name: String,
      macrosDefined: Boolean
  ): IO[ServiceError, com.diettracker.domain.FoodItem] =
    foodService.createFood(
      userId,
      CreateFoodRequest(
        name = FoodName(name),
        brand = Some(FoodBrand("Integration")),
        defaultServingValue = ServingQuantity(BigDecimal(100)),
        defaultServingUnit = ServingUnit("g"),
        caloriesPerServing = CalorieCount(if (macrosDefined) BigDecimal(200) else BigDecimal(100)),
        macrosPerServing =
          if (macrosDefined)
            Some(MacroBreakdown(ProteinGrams(25), CarbGrams(30), FatGrams(10)))
          else None,
        macrosPer100g = None,
        notes = None
      )
    )

  private val postgresLayer: ZLayer[Scope, Throwable, AppConfig] = ZLayer.scoped[Scope] {
    for {
      container        <- ZIO.acquireRelease(
                            ZIO.attempt {
                              val c = new PostgreSQLContainer(DockerImageName.parse("postgres:15-alpine"))
                              c.start()
                              c
                            }
                          )(c => ZIO.attempt(c.stop()).ignore)
      jdbcUrl           = container.getJdbcUrl.replace("?loggerLevel=OFF", "")
      _                <- ZIO.attempt(Class.forName("org.postgresql.Driver"))
      _                <- ZIO.attempt {
                            Flyway
                              .configure()
                              .dataSource(jdbcUrl, container.getUsername, container.getPassword)
                              .driver("org.postgresql.Driver")
                              .load()
                              .migrate()
                          }
      config: AppConfig = AppConfig(
                            featureFlags = Chunk("feature.customFoodLogs"),
                            http = HttpConfig(host = "localhost", port = 0),
                            database = DatabaseConfig(
                              url = jdbcUrl,
                              user = container.getUsername,
                              password = container.getPassword,
                              schema = "public"
                            ),
                            sharing = SharingConfig.default
                          )
    } yield config
  }
}
