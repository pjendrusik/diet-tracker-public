package com.diettracker.integration

import com.diettracker.config.{AppConfig, DatabaseConfig, HttpConfig, SharingConfig}
import com.diettracker.config.DatabaseLayer
import com.diettracker.domain._
import com.diettracker.repositories.{FoodRepository, IntakeLogRepository}
import com.diettracker.services.FoodService
import com.diettracker.services.FoodService.CreateFoodRequest
import com.diettracker.services.LogsService
import com.diettracker.services.LogsService.{CreateLogRequest, UpdateLogRequest}
import com.diettracker.services.NutritionSnapshotService
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import zio._
import zio.test._

import java.time.{OffsetDateTime, ZoneId}
import java.util.UUID
import java.sql.DriverManager

object LogsIntegrationSpec extends ZIOSpecDefault {
  private lazy val integrationLayer: ZLayer[Scope, Throwable, FoodService with LogsService with AppConfig] =
    ZLayer.makeSome[Scope, FoodService with LogsService with AppConfig](
      postgresConfigLayer,
      DatabaseLayer.live,
      FoodRepository.live,
      IntakeLogRepository.live,
      NutritionSnapshotService.live,
      FoodService.live,
      LogsService.live
    )

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("Logs integration")(
      test("persists log flows and keeps historical snapshots immutable") {
        for {
          foodService <- ZIO.service[FoodService]
          logsService <- ZIO.service[LogsService]
          config      <- ZIO.service[AppConfig]
          userId       = UserId(UUID.randomUUID())
          food        <- createTestFood(foodService, userId)
          now          = OffsetDateTime.now(ZoneId.of("UTC"))
          createdLog  <- logsService.createLog(
                           userId,
                           CreateLogRequest(
                             foodId = food.id,
                             loggedAt = now,
                             quantity = ServingQuantity(BigDecimal(1)),
                             unit = None,
                             notes = Some(LogNotes("breakfast"))
                           )
                         )
          _           <- ZIO.succeed(assertTrue(createdLog.snapshot.calories.value == BigDecimal(150)))
          _           <- updateFoodCalories(config, food.id.value, BigDecimal(220))
          secondLog   <- logsService.createLog(
                           userId,
                           CreateLogRequest(
                             foodId = food.id,
                             loggedAt = now.plusHours(1),
                             quantity = ServingQuantity(BigDecimal(1)),
                             unit = None,
                             notes = Some(LogNotes("snack"))
                           )
                         )
          _           <- ZIO.succeed(assertTrue(secondLog.snapshot.calories.value == BigDecimal(220)))
          dayLogs     <- logsService.listLogs(userId, now.toLocalDate, ZoneId.of("UTC"))
          maybeFirst   = dayLogs.find(_.id == createdLog.id)
          maybeSecond  = dayLogs.find(_.id == secondLog.id)
          _           <- ZIO.succeed(
                           assertTrue(
                             maybeFirst.exists(_.snapshot.calories.value == BigDecimal(150)),
                             maybeSecond.exists(_.snapshot.calories.value == BigDecimal(220))
                           )
                         )
          updated     <- logsService.updateLog(
                           secondLog.id,
                           userId,
                           UpdateLogRequest(
                             loggedAt = None,
                             quantity = Some(ServingQuantity(BigDecimal(2))),
                             unit = None,
                             notes = Some(LogNotes("post workout"))
                           )
                         )
          _           <- ZIO.succeed(assertTrue(updated.snapshot.calories.value == BigDecimal(440)))
          _           <- logsService.deleteLog(createdLog.id, userId)
          remaining   <- logsService.listLogs(userId, now.toLocalDate, ZoneId.of("UTC"))
        } yield assertTrue(
          remaining.length == 1,
          remaining.head.id == secondLog.id,
          remaining.head.snapshot.calories.value == BigDecimal(440)
        )
      }.provideSomeLayerShared[Scope](integrationLayer)
    )

  private def createTestFood(foodService: FoodService, userId: UserId): IO[Throwable, FoodItem] =
    foodService.createFood(
      userId,
      CreateFoodRequest(
        name = FoodName("Integration Oats"),
        brand = Some(FoodBrand("DietTracker")),
        defaultServingValue = ServingQuantity(BigDecimal(100)),
        defaultServingUnit = ServingUnit("g"),
        caloriesPerServing = CalorieCount(BigDecimal(150)),
        macrosPerServing = Some(MacroBreakdown(ProteinGrams(5), CarbGrams(20), FatGrams(6))),
        macrosPer100g = None,
        notes = None
      )
    )

  private def updateFoodCalories(appConfig: AppConfig, foodId: UUID, calories: BigDecimal): Task[Unit] =
    ZIO.attemptBlocking {
      val connection = DriverManager.getConnection(
        appConfig.database.url,
        appConfig.database.user,
        appConfig.database.password
      )
      try {
        val statement = connection.prepareStatement(
          "UPDATE food_item SET calories_per_serving = ? WHERE id = ?"
        )
        try {
          statement.setBigDecimal(1, calories.bigDecimal)
          statement.setObject(2, foodId)
          statement.executeUpdate()
          ()
        } finally statement.close()
      } finally connection.close()
    }

  private val postgresConfigLayer: ZLayer[Scope, Throwable, AppConfig] =
    ZLayer.scoped[Scope] {
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
