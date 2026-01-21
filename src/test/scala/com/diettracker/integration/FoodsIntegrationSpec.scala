package com.diettracker.integration

import com.diettracker.config.{AppConfig, DatabaseConfig, HttpConfig, SharingConfig}
import com.diettracker.config.DatabaseLayer
import com.diettracker.domain._
import com.diettracker.repositories.{FoodRepository, IntakeLogRepository}
import com.diettracker.services.{FoodService, LogsService, ServiceError}
import com.diettracker.services.FoodService.CreateFoodRequest
import com.diettracker.services.LogsService.CreateLogRequest
import com.diettracker.services.NutritionSnapshotService
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import zio._
import zio.test._

import java.time.{OffsetDateTime, ZoneId}
import java.util.UUID

object FoodsIntegrationSpec extends ZIOSpecDefault {
  private lazy val integrationLayer: ZLayer[Scope, Throwable, FoodService with LogsService with AppConfig] =
    ZLayer.makeSome[Scope, FoodService with LogsService with AppConfig](
      postgresLayer,
      DatabaseLayer.live,
      FoodRepository.live,
      IntakeLogRepository.live,
      NutritionSnapshotService.live,
      FoodService.live,
      LogsService.live
    )

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("Foods integration")(
      test("food updates bump versions while preserving existing snapshots and enforcing delete safeguards") {
        for {
          foodService    <- ZIO.service[FoodService]
          logsService    <- ZIO.service[LogsService]
          userId          = UserId(UUID.randomUUID())
          breakfast      <- createFood(foodService, userId, "Breakfast Wrap", 150)
          smoothie       <- createFood(foodService, userId, "Berry Smoothie", 220)
          searchHit      <- foodService.searchFoods(userId, Some("smooth"))
          now             = OffsetDateTime.now(ZoneId.of("UTC"))
          logEntry       <- logsService.createLog(
                              userId,
                              CreateLogRequest(
                                foodId = breakfast.id,
                                loggedAt = now,
                                quantity = ServingQuantity(BigDecimal(1)),
                                unit = None,
                                notes = Some(LogNotes("initial"))
                              )
                            )
          updated        <- foodService.updateFood(
                              breakfast.id,
                              userId,
                              CreateFoodRequest(
                                name = FoodName("Breakfast Wrap"),
                                brand = breakfast.brand,
                                defaultServingValue = breakfast.defaultServingValue,
                                defaultServingUnit = breakfast.defaultServingUnit,
                                caloriesPerServing = CalorieCount(BigDecimal(200)),
                                macrosPerServing = breakfast.macrosPerServing,
                                macrosPer100g = breakfast.macrosPer100g,
                                notes = breakfast.notes
                              )
                            )
          newLog         <- logsService.createLog(
                              userId,
                              CreateLogRequest(
                                foodId = breakfast.id,
                                loggedAt = now.plusHours(1),
                                quantity = ServingQuantity(BigDecimal(1)),
                                unit = None,
                                notes = Some(LogNotes("after update"))
                              )
                            )
          deleteAttempt  <- foodService.deleteFood(breakfast.id, userId, force = false).either
          deleteSmoothie <- foodService.deleteFood(smoothie.id, userId, force = false).either
        } yield assertTrue(
          searchHit.exists(_.id == smoothie.id),
          logEntry.snapshot.calories.value == BigDecimal(150),
          updated.version.value == breakfast.version.value + 1,
          newLog.snapshot.calories.value == BigDecimal(200),
          deleteAttempt.isLeft,
          deleteSmoothie.isRight
        )
      }.provideSomeLayerShared[Scope](integrationLayer)
    )

  private def createFood(
      foodService: FoodService,
      userId: UserId,
      name: String,
      calories: Int
  ): IO[ServiceError, FoodItem] =
    foodService.createFood(
      userId,
      CreateFoodRequest(
        name = FoodName(name),
        brand = Some(FoodBrand("Integration")),
        defaultServingValue = ServingQuantity(BigDecimal(100)),
        defaultServingUnit = ServingUnit("g"),
        caloriesPerServing = CalorieCount(BigDecimal(calories)),
        macrosPerServing = Some(
          MacroBreakdown(ProteinGrams(10), CarbGrams(15), FatGrams(5))
        ),
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
