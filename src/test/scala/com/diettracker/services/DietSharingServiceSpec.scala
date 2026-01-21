package com.diettracker.services

import com.diettracker.config.{AppConfig, DatabaseConfig, DatabaseLayer, HttpConfig, SharingConfig}
import com.diettracker.domain.UserId
import com.diettracker.domain.sharing._
import com.diettracker.repositories.{DietCopyRepository, DietRepository, DietSharingRepository}
import com.diettracker.services.DietSharingService.ShareDietCommand
import com.diettracker.services.NotificationPublisher.{ShareCreated, ShareRevoked}
import com.diettracker.services.metrics.SharingMetrics
import com.diettracker.services.validators.DietStatusValidator
import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import zio._
import zio.test._
import zio.json.ast.Json

import java.util.UUID

object DietSharingServiceSpec extends ZIOSpecDefault {
  final case class NotificationProbe(events: Ref[Chunk[String]])
  final case class AuditProbe(events: Ref[Chunk[String]])

  private val notificationProbeLayer: ZLayer[Any, Nothing, NotificationProbe] =
    ZLayer.fromZIO(Ref.make(Chunk.empty[String]).map(NotificationProbe.apply))

  private val auditProbeLayer: ZLayer[Any, Nothing, AuditProbe] =
    ZLayer.fromZIO(Ref.make(Chunk.empty[String]).map(AuditProbe.apply))

  private val notificationPublisherLayer: ZLayer[NotificationProbe, Nothing, NotificationPublisher] =
    ZLayer.fromFunction { probe: NotificationProbe =>
      new NotificationPublisher {
        override def notifyShareCreated(event: ShareCreated): Task[Unit] =
          probe.events.update(_ :+ s"created:${event.shareId.value}")

        override def notifyShareRevoked(event: ShareRevoked): Task[Unit] =
          probe.events.update(_ :+ s"revoked:${event.shareId.value}")
      }
    }

  private val auditTrailLayer: ZLayer[AuditProbe, Nothing, AuditTrailService] =
    ZLayer.fromFunction { probe: AuditProbe =>
      new AuditTrailService {
        override def recordShareCreated(event: AuditTrailService.ShareCreated): Task[Unit] =
          probe.events.update(_ :+ s"audit-created:${event.shareId.value}")

        override def recordShareRevoked(event: AuditTrailService.ShareRevoked): Task[Unit] =
          probe.events.update(_ :+ s"audit-revoked:${event.shareId.value}")

        override def recordDietCopied(event: AuditTrailService.DietCopied): Task[Unit] = ZIO.unit
      }
    }

  private val serviceLayer: ZLayer[
    Scope,
    Throwable,
    DietSharingService with DietRepository with NotificationProbe with AuditProbe
  ] =
    ZLayer.makeSome[Scope, DietSharingService with DietRepository with NotificationProbe with AuditProbe](
      notificationProbeLayer,
      auditProbeLayer,
      postgresLayer,
      DatabaseLayer.live,
      DietRepository.live,
      DietSharingRepository.live,
      DietCopyRepository.live,
      notificationPublisherLayer,
      auditTrailLayer,
      DietStatusValidator.live,
      SharingMetrics.noop,
      DietSharingService.live
    )

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("DietSharingService")(
      test("sharing emits notifications and prevents duplicates") {
        for {
          service   <- ZIO.service[DietSharingService]
          probe     <- ZIO.service[NotificationProbe]
          audit     <- ZIO.service[AuditProbe]
          repo      <- ZIO.service[DietRepository]
          owner      = UserId(UUID.randomUUID())
          recipient  = UserId(UUID.randomUUID())
          diet       = DietId(UUID.randomUUID())
          _         <- repo.createDiet(
                         DietRepository.CreateDiet(
                           id = diet,
                           ownerUserId = owner,
                           ownerName = Some(DietOwnerName("Owner")),
                           title = DietTitle("Owner Diet"),
                           document = DietDocument(Json.Obj("meals" -> Json.Arr())),
                           status = DietStatus.Active
                         )
                       )
          created   <- service.shareDiet(ShareDietCommand(diet, owner, recipient))
          duplicate <- service.shareDiet(ShareDietCommand(diet, owner, recipient)).either
          notifLog  <- probe.events.get
          auditLog  <- audit.events.get
        } yield assertTrue(
          duplicate.isLeft,
          notifLog.contains(s"created:${created.id.value}"),
          auditLog.contains(s"audit-created:${created.id.value}")
        )
      }.provideSomeLayerShared[Scope](serviceLayer),
      test("revoking share logs events and notifications") {
        for {
          service  <- ZIO.service[DietSharingService]
          probe    <- ZIO.service[NotificationProbe]
          audit    <- ZIO.service[AuditProbe]
          repo     <- ZIO.service[DietRepository]
          owner     = UserId(UUID.randomUUID())
          recipient = UserId(UUID.randomUUID())
          diet      = DietId(UUID.randomUUID())
          _        <- repo.createDiet(
                        DietRepository.CreateDiet(
                          id = diet,
                          ownerUserId = owner,
                          ownerName = Some(DietOwnerName("Owner")),
                          title = DietTitle("Owner Diet"),
                          document = DietDocument(Json.Obj("meals" -> Json.Arr())),
                          status = DietStatus.Active
                        )
                      )
          created  <- service.shareDiet(ShareDietCommand(diet, owner, recipient))
          _        <- service.revokeShare(
                        DietSharingService.RevokeShareCommand(
                          shareId = created.id,
                          dietId = diet,
                          ownerUserId = owner
                        )
                      )
          notifLog <- probe.events.get
          auditLog <- audit.events.get
        } yield assertTrue(
          notifLog.contains(s"created:${created.id.value}"),
          notifLog.contains(s"revoked:${created.id.value}"),
          auditLog.contains(s"audit-created:${created.id.value}"),
          auditLog.contains(s"audit-revoked:${created.id.value}")
        )
      }.provideSomeLayerShared[Scope](serviceLayer),
      test("rejects sharing diets with yourself") {
        for {
          service <- ZIO.service[DietSharingService]
          repo    <- ZIO.service[DietRepository]
          owner    = UserId(UUID.randomUUID())
          diet     = DietId(UUID.randomUUID())
          _       <- repo.createDiet(
                       DietRepository.CreateDiet(
                         id = diet,
                         ownerUserId = owner,
                         ownerName = Some(DietOwnerName("Owner")),
                         title = DietTitle("Diet"),
                         document = DietDocument(Json.Obj()),
                         status = DietStatus.Active
                       )
                     )
          result  <- service.shareDiet(ShareDietCommand(diet, owner, owner)).exit
        } yield assertTrue(result.isFailure)
      }.provideSomeLayerShared[Scope](serviceLayer),
      test("rejects sharing archived diets") {
        for {
          service <- ZIO.service[DietSharingService]
          repo    <- ZIO.service[DietRepository]
          owner    = UserId(UUID.randomUUID())
          diet     = DietId(UUID.randomUUID())
          _       <- repo.createDiet(
                       DietRepository.CreateDiet(
                         id = diet,
                         ownerUserId = owner,
                         ownerName = Some(DietOwnerName("Owner")),
                         title = DietTitle("Diet"),
                         document = DietDocument(Json.Obj()),
                         status = DietStatus.Archived
                       )
                     )
          result  <- service.shareDiet(ShareDietCommand(diet, owner, UserId(UUID.randomUUID()))).exit
        } yield assertTrue(result.isFailure)
      }.provideSomeLayerShared[Scope](serviceLayer)
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
