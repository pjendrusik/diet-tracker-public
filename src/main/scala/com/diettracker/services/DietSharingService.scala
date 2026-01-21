package com.diettracker.services

import com.diettracker.domain.UserId
import com.diettracker.domain.sharing._
import com.diettracker.repositories.{DietCopyRepository, DietSharingRepository}
import com.diettracker.services.metrics.SharingMetrics
import com.diettracker.services.validators.DietStatusValidator
import zio._

import java.time.{OffsetDateTime, ZoneOffset}
import java.util.UUID

trait DietSharingService {
  def shareDiet(command: DietSharingService.ShareDietCommand): IO[ServiceError, DietShare]
  def listShares(query: DietSharingService.ListSharesQuery): IO[ServiceError, Chunk[DietShare]]
  def revokeShare(command: DietSharingService.RevokeShareCommand): IO[ServiceError, Unit]
  def listSharedDiets(recipientId: UserId): IO[ServiceError, Chunk[SharedDietSummary]]
  def viewSharedDiet(query: DietSharingService.ViewShareQuery): IO[ServiceError, SharedDietView]
  def copySharedDiet(command: DietSharingService.CopySharedDietCommand): IO[ServiceError, DietCopy]
}

object DietSharingService {
  final case class ShareDietCommand(
      dietId: DietId,
      ownerUserId: UserId,
      recipientUserId: UserId
  )

  final case class ListSharesQuery(
      dietId: DietId,
      ownerUserId: UserId
  )

  final case class RevokeShareCommand(
      shareId: DietShareId,
      dietId: DietId,
      ownerUserId: UserId
  )

  final case class ViewShareQuery(
      shareId: DietShareId,
      recipientUserId: UserId
  )

  final case class CopySharedDietCommand(
      shareId: DietShareId,
      recipientUserId: UserId
  )

  val live: ZLayer[
    DietSharingRepository
      with DietCopyRepository
      with NotificationPublisher
      with AuditTrailService
      with DietStatusValidator
      with SharingMetrics,
    Nothing,
    DietSharingService
  ] =
    ZLayer.fromZIO {
      for {
        repo          <- ZIO.service[DietSharingRepository]
        copies        <- ZIO.service[DietCopyRepository]
        notifications <- ZIO.service[NotificationPublisher]
        audit         <- ZIO.service[AuditTrailService]
        clock         <- ZIO.clock
        validator     <- ZIO.service[DietStatusValidator]
        metrics       <- ZIO.service[SharingMetrics]
      } yield new DietSharingServiceLive(repo, copies, notifications, audit, validator, metrics, clock)
    }
}

final class DietSharingServiceLive(
    repository: DietSharingRepository,
    copyRepository: DietCopyRepository,
    notificationPublisher: NotificationPublisher,
    auditTrailService: AuditTrailService,
    dietStatusValidator: DietStatusValidator,
    sharingMetrics: SharingMetrics,
    clock: Clock
) extends DietSharingService {
  import DietSharingService._
  import NotificationPublisher._
  import ServiceError._

  override def shareDiet(command: ShareDietCommand): IO[ServiceError, DietShare] =
    sharingMetrics.trackShare {
      for {
        _     <- ensure(command.ownerUserId != command.recipientUserId, "Cannot share a diet with yourself")
        _     <- dietStatusValidator.ensureShareable(command.dietId, command.ownerUserId)
        share <-
          repository
            .createShare(
              DietSharingRepository.CreateShare(
                dietId = command.dietId,
                ownerUserId = command.ownerUserId,
                recipientUserId = command.recipientUserId
              )
            )
            .mapError(dbError)
        now   <- currentTime
        _     <- sharingMetrics
                   .trackNotification(
                     notificationPublisher
                       .notifyShareCreated(
                         ShareCreated(
                           dietId = command.dietId,
                           ownerUserId = command.ownerUserId,
                           recipientUserId = command.recipientUserId,
                           shareId = share.id
                         )
                       )
                   )
                   .mapError(e => ValidationError(e.getMessage))
        _     <- auditTrailService
                   .recordShareCreated(
                     AuditTrailService.ShareCreated(
                       dietId = share.dietId,
                       ownerUserId = share.ownerUserId,
                       recipientUserId = share.recipientUserId,
                       shareId = share.id,
                       occurredAt = now
                     )
                   )
                   .mapError(e => ValidationError(e.getMessage))
      } yield share
    }

  override def listShares(query: ListSharesQuery): IO[ServiceError, Chunk[DietShare]] =
    repository
      .listSharesForOwner(query.dietId, query.ownerUserId)
      .mapError(dbError)

  override def revokeShare(command: RevokeShareCommand): IO[ServiceError, Unit] =
    sharingMetrics.trackRevoke {
      for {
        shareOpt <- repository.findShareById(command.shareId).mapError(dbError)
        share    <- ZIO
                      .fromOption(shareOpt)
                      .orElseFail(NotFound(s"Share ${command.shareId.value} not found"))
        _        <- ensure(share.ownerUserId == command.ownerUserId, "You do not own this diet share")
        _        <- ensure(share.dietId == command.dietId, "Share does not belong to provided diet")
        now      <- currentTime
        updated  <- repository.revokeShare(command.shareId, command.ownerUserId, now).mapError(dbError)
        _        <- if (updated) ZIO.unit else ZIO.fail(NotFound(s"Share ${command.shareId.value} already revoked"))
        revoked   = share.copy(
                      status = DietShareStatus.Revoked,
                      revokedAt = Some(now),
                      revokedBy = Some(command.ownerUserId)
                    )
        _        <- sharingMetrics
                      .trackNotification(
                        notificationPublisher
                          .notifyShareRevoked(
                            ShareRevoked(
                              dietId = revoked.dietId,
                              ownerUserId = revoked.ownerUserId,
                              recipientUserId = revoked.recipientUserId,
                              shareId = revoked.id
                            )
                          )
                      )
                      .mapError(e => ValidationError(e.getMessage))
        _        <- auditTrailService
                      .recordShareRevoked(
                        AuditTrailService.ShareRevoked(
                          dietId = revoked.dietId,
                          ownerUserId = revoked.ownerUserId,
                          recipientUserId = revoked.recipientUserId,
                          shareId = revoked.id,
                          occurredAt = now
                        )
                      )
                      .mapError(e => ValidationError(e.getMessage))
        _        <- sharingMetrics.trackRevocationClosure(ZIO.unit)
      } yield ()
    }

  override def listSharedDiets(recipientId: UserId): IO[ServiceError, Chunk[SharedDietSummary]] =
    sharingMetrics.trackRecipientList {
      repository.listSharedDietsForRecipient(recipientId).mapError(dbError)
    }

  override def viewSharedDiet(query: ViewShareQuery): IO[ServiceError, SharedDietView] =
    sharingMetrics.trackRecipientView {
      repository
        .fetchSharedDietView(query.shareId, query.recipientUserId)
        .mapError(dbError)
        .someOrFail(NotFound("Shared diet not found or access revoked"))
    }

  override def copySharedDiet(command: CopySharedDietCommand): IO[ServiceError, DietCopy] =
    sharingMetrics.trackCopy {
      for {
        shareOpt <- repository.findShareById(command.shareId).mapError(dbError)
        share    <- ZIO
                      .fromOption(shareOpt)
                      .orElseFail(NotFound(s"Share ${command.shareId.value} not found"))
        _        <- ensure(share.recipientUserId == command.recipientUserId, "You do not have access to this share")
        _        <- ensure(share.status == DietShareStatus.Active, "Share must be active to copy")
        _        <- dietStatusValidator.ensureCopyable(share.dietId)
        now      <- currentTime
        newDietId = DietId(UUID.randomUUID())
        copy     <- copyRepository
                      .cloneDietFromShare(
                        DietCopyRepository.CloneDietFromShare(
                          sourceDietId = share.dietId,
                          sourceShareId = Some(share.id),
                          newDietId = newDietId,
                          recipientUserId = command.recipientUserId,
                          copiedAt = now
                        )
                      )
                      .mapError(dbError)
        _        <- auditTrailService
                      .recordDietCopied(
                        AuditTrailService.DietCopied(
                          sourceDietId = share.dietId,
                          newDietId = copy.newDietId,
                          recipientUserId = copy.recipientUserId,
                          sourceShareId = Some(share.id),
                          occurredAt = now
                        )
                      )
                      .mapError(e => ValidationError(e.getMessage))
      } yield copy
    }

  private def ensure(condition: => Boolean, message: String): IO[ServiceError, Unit] =
    if (condition) ZIO.unit else ZIO.fail(ValidationError(message))

  private def currentTime: UIO[OffsetDateTime] =
    clock.instant.map(instant => OffsetDateTime.ofInstant(instant, ZoneOffset.UTC))

  private def dbError(throwable: Throwable): ServiceError =
    throwable match {
      case sql: java.sql.SQLException if Option(sql.getSQLState).contains("23505") =>
        ValidationError("Recipient already has access to this diet")
      case sql: java.sql.SQLException
          if Option(sql.getSQLState).contains("P0001") &&
            Option(sql.getMessage).exists(_.contains("DIET_NOT_SHAREABLE")) =>
        ValidationError("Diet cannot be shared in its current state")
      case other                                                                   => ValidationError(other.getMessage)
    }
}
