package com.diettracker.repositories

import com.diettracker.config.{AppConfig, DatabaseConfig, DatabaseLayer, HttpConfig, SharingConfig}
import com.diettracker.domain.UserId
import com.diettracker.domain.sharing._
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import zio._
import zio.test._
import zio.json.ast.Json

import java.time.OffsetDateTime
import java.util.UUID

object DietCopyRepositorySpec extends ZIOSpecDefault {
  private val repoLayer: ZLayer[Scope, Throwable, DietCopyRepository with DietRepository] =
    ZLayer.makeSome[Scope, DietCopyRepository with DietRepository](
      postgresLayer,
      DatabaseLayer.live,
      DietRepository.live,
      DietCopyRepository.live
    )

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("DietCopyRepository")(
      test("create copy records new rows per recipient") {
        for {
          repo    <- ZIO.service[DietCopyRepository]
          diets   <- ZIO.service[DietRepository]
          source   = DietId(UUID.randomUUID())
          newDiet  = DietId(UUID.randomUUID())
          user     = UserId(UUID.randomUUID())
          now      = OffsetDateTime.now()
          _       <- diets.createDiet(
                       DietRepository.CreateDiet(
                         id = source,
                         ownerUserId = user,
                         ownerName = Some(DietOwnerName("Owner")),
                         title = DietTitle("Original"),
                         document = DietDocument(Json.Obj("meals" -> Json.Arr())),
                         status = DietStatus.Active
                       )
                     )
          _       <- diets.createDiet(
                       DietRepository.CreateDiet(
                         id = newDiet,
                         ownerUserId = user,
                         ownerName = Some(DietOwnerName("Owner")),
                         title = DietTitle("Copy"),
                         document = DietDocument(Json.Obj("meals" -> Json.Arr())),
                         status = DietStatus.Active
                       )
                     )
          created <- repo.createCopy(
                       DietCopyRepository.CreateCopy(
                         sourceDietId = source,
                         sourceShareId = None,
                         newDietId = newDiet,
                         recipientUserId = user,
                         copiedAt = now
                       )
                     )
          copies  <- repo.listCopiesForRecipient(user)
        } yield assertTrue(
          created.newDietId == newDiet,
          copies.exists(_.id == created.id)
        )
      }.provideSomeLayerShared[Scope](repoLayer),
      test("cloneDietFromShare copies latest active diet and rejects archived ones") {
        for {
          repo       <- ZIO.service[DietCopyRepository]
          diets      <- ZIO.service[DietRepository]
          activeId    = DietId(UUID.randomUUID())
          archivedId  = DietId(UUID.randomUUID())
          newDietId   = DietId(UUID.randomUUID())
          recipient   = UserId(UUID.randomUUID())
          now         = OffsetDateTime.now()
          _          <- diets.createDiet(
                          DietRepository.CreateDiet(
                            id = activeId,
                            ownerUserId = recipient,
                            ownerName = Some(DietOwnerName("Owner")),
                            title = DietTitle("Baseline"),
                            document = DietDocument(Json.Obj("meals" -> Json.Arr())),
                            status = DietStatus.Active
                          )
                        )
          _          <- diets.createDiet(
                          DietRepository.CreateDiet(
                            id = archivedId,
                            ownerUserId = recipient,
                            ownerName = Some(DietOwnerName("Owner")),
                            title = DietTitle("Archived"),
                            document = DietDocument(Json.Obj("meals" -> Json.Arr())),
                            status = DietStatus.Archived
                          )
                        )
          copy       <- repo.cloneDietFromShare(
                          DietCopyRepository.CloneDietFromShare(
                            sourceDietId = activeId,
                            sourceShareId = None,
                            newDietId = newDietId,
                            recipientUserId = recipient,
                            copiedAt = now
                          )
                        )
          archivedEx <- repo
                          .cloneDietFromShare(
                            DietCopyRepository.CloneDietFromShare(
                              sourceDietId = archivedId,
                              sourceShareId = None,
                              newDietId = DietId(UUID.randomUUID()),
                              recipientUserId = recipient,
                              copiedAt = now
                            )
                          )
                          .exit
        } yield assertTrue(
          copy.sourceDietId == activeId,
          copy.newDietId == newDietId,
          archivedEx.isFailure
        )
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
