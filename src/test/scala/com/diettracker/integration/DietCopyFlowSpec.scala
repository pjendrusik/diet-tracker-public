package com.diettracker.integration

import com.diettracker.config.{AppConfig, DatabaseConfig, DatabaseLayer, HttpConfig, SharingConfig}
import com.diettracker.domain.UserId
import com.diettracker.domain.sharing._
import com.diettracker.repositories.{DietCopyRepository, DietRepository, DietSharingRepository}
import com.diettracker.services.DietSharingService._
import com.diettracker.services._
import com.diettracker.services.metrics.SharingMetrics
import com.diettracker.services.validators.DietStatusValidator
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import zio._
import zio.test._
import zio.json.ast.Json

import java.util.UUID

object DietCopyFlowSpec extends ZIOSpecDefault {
  private val sharingLayer
      : ZLayer[Scope, Throwable, DietSharingService with DietCopyRepository with DietRepository] =
    ZLayer.makeSome[Scope, DietSharingService with DietCopyRepository with DietRepository](
      postgresLayer,
      DatabaseLayer.live,
      DietRepository.live,
      DietSharingRepository.live,
      DietCopyRepository.live,
      AuditTrailService.live,
      NotificationPublisher.logging,
      DietStatusValidator.live,
      SharingMetrics.noop,
      DietSharingService.live
    )

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("Diet copy flow")(
      test("recipient copies shared diet and receives unique id") {
        for {
          service  <- ZIO.service[DietSharingService]
          diets    <- ZIO.service[DietRepository]
          copyRepo <- ZIO.service[DietCopyRepository]
          owner     = UserId(UUID.randomUUID())
          recipient = UserId(UUID.randomUUID())
          diet      = DietId(UUID.randomUUID())
          _        <- diets.createDiet(
                        DietRepository.CreateDiet(
                          id = diet,
                          ownerUserId = owner,
                          ownerName = Some(DietOwnerName("Owner")),
                          title = DietTitle("Plan"),
                          document = DietDocument(Json.Obj("meals" -> Json.Arr())),
                          status = DietStatus.Active
                        )
                      )
          share    <- service.shareDiet(ShareDietCommand(diet, owner, recipient))
          copy     <- service.copySharedDiet(CopySharedDietCommand(share.id, recipient))
          _        <- service.revokeShare(RevokeShareCommand(share.id, diet, owner))
          second   <- service.copySharedDiet(CopySharedDietCommand(share.id, recipient)).either
          copies   <- copyRepo.listCopiesForRecipient(recipient)
        } yield assertTrue(
          copy.sourceDietId == diet,
          copy.sourceShareId.contains(share.id),
          copy.recipientUserId == recipient,
          copies.exists(_.newDietId == copy.newDietId),
          second.isLeft
        )
      }.provideSomeLayerShared[Scope](sharingLayer)
    )

  private lazy val postgresLayer: ZLayer[Scope, Throwable, AppConfig] = ZLayer.scoped[Scope] {
    for {
      container <- ZIO.acquireRelease(
                     ZIO.attempt {
                       val c = new PostgreSQLContainer(DockerImageName.parse("postgres:15-alpine"))
                       c.start()
                       c
                     }
                   )(c => ZIO.attempt(c.stop()).ignore)
      jdbcUrl    = container.getJdbcUrl.replace("?loggerLevel=OFF", "")
      _         <- ZIO.attempt(Class.forName("org.postgresql.Driver"))
      _         <- ZIO.attempt {
                     Flyway
                       .configure()
                       .dataSource(jdbcUrl, container.getUsername, container.getPassword)
                       .driver("org.postgresql.Driver")
                       .load()
                       .migrate()
                   }
      config     = AppConfig(
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
