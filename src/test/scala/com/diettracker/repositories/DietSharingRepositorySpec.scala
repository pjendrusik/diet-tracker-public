package com.diettracker.repositories

import com.diettracker.config.{AppConfig, DatabaseConfig, DatabaseLayer, HttpConfig, SharingConfig}
import com.diettracker.domain.UserId
import com.diettracker.domain.sharing._
import com.diettracker.repositories.DietRepository.CreateDiet
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import zio._
import zio.test._

import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID

object DietSharingRepositorySpec extends ZIOSpecDefault {
  private val repoLayer: ZLayer[Scope, Throwable, DietSharingRepository with DietRepository] =
    ZLayer.makeSome[Scope, DietSharingRepository with DietRepository](
      postgresLayer,
      DatabaseLayer.live,
      DietRepository.live,
      DietSharingRepository.live
    )

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("DietSharingRepository")(
      test("create, list, and revoke shares enforcing uniqueness") {
        for {
          repo       <- ZIO.service[DietSharingRepository]
          diets      <- ZIO.service[DietRepository]
          ownerId     = UserId(UUID.randomUUID())
          recipientId = UserId(UUID.randomUUID())
          dietId      = DietId(UUID.randomUUID())
          _          <- diets.createDiet(
                          CreateDiet(
                            id = dietId,
                            ownerUserId = ownerId,
                            ownerName = Some(DietOwnerName("Owner")),
                            title = DietTitle("Diet"),
                            document = DietDocument(zio.json.ast.Json.Obj()),
                            status = DietStatus.Active
                          )
                        )
          share      <- repo.createShare(
                          DietSharingRepository.CreateShare(
                            dietId = dietId,
                            ownerUserId = ownerId,
                            recipientUserId = recipientId
                          )
                        )
          shares     <- repo.listSharesForOwner(dietId, ownerId)
          now         = OffsetDateTime.now(ZoneOffset.UTC)
          revoked    <- repo.revokeShare(share.id, ownerId, now)
          updated    <- repo.findShareById(share.id)
          duplicate  <- repo
                          .createShare(
                            DietSharingRepository.CreateShare(
                              dietId = dietId,
                              ownerUserId = ownerId,
                              recipientUserId = recipientId
                            )
                          )
                          .exit
        } yield assertTrue(
          share.status == DietShareStatus.Active,
          shares.exists(_.id == share.id),
          revoked,
          updated.exists(_.status == DietShareStatus.Revoked),
          duplicate.isFailure
        )
      }.provideSomeLayerShared[Scope](repoLayer),
      test("rejects sharing archived or foreign diets") {
        for {
          repo         <- ZIO.service[DietSharingRepository]
          diets        <- ZIO.service[DietRepository]
          ownerId       = UserId(UUID.randomUUID())
          otherOwner    = UserId(UUID.randomUUID())
          recipientId   = UserId(UUID.randomUUID())
          activeDiet    = DietId(UUID.randomUUID())
          archivedDiet  = DietId(UUID.randomUUID())
          _            <- diets.createDiet(
                            CreateDiet(
                              id = activeDiet,
                              ownerUserId = ownerId,
                              ownerName = Some(DietOwnerName("Owner")),
                              title = DietTitle("Active"),
                              document = DietDocument(zio.json.ast.Json.Obj()),
                              status = DietStatus.Active
                            )
                          )
          _            <- diets.createDiet(
                            CreateDiet(
                              id = archivedDiet,
                              ownerUserId = ownerId,
                              ownerName = Some(DietOwnerName("Owner")),
                              title = DietTitle("Archived"),
                              document = DietDocument(zio.json.ast.Json.Obj()),
                              status = DietStatus.Archived
                            )
                          )
          foreignFail  <- repo
                            .createShare(
                              DietSharingRepository.CreateShare(
                                dietId = activeDiet,
                                ownerUserId = otherOwner,
                                recipientUserId = recipientId
                              )
                            )
                            .exit
          archivedFail <- repo
                            .createShare(
                              DietSharingRepository.CreateShare(
                                dietId = archivedDiet,
                                ownerUserId = ownerId,
                                recipientUserId = recipientId
                              )
                            )
                            .exit
        } yield assertTrue(foreignFail.isFailure, archivedFail.isFailure)
      }.provideSomeLayerShared[Scope](repoLayer)
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
