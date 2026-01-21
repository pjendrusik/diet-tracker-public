package com.diettracker.services

import com.diettracker.domain.UserId
import com.diettracker.domain.sharing.{DietId, DietShareId}
import doobie.hikari.HikariTransactor
import doobie.implicits._
import doobie.postgres.implicits._
import zio._
import zio.interop.catz._

import java.time.OffsetDateTime

trait AuditTrailService {
  def recordShareCreated(event: AuditTrailService.ShareCreated): Task[Unit]
  def recordShareRevoked(event: AuditTrailService.ShareRevoked): Task[Unit]
  def recordDietCopied(event: AuditTrailService.DietCopied): Task[Unit]
}

object AuditTrailService {
  final case class ShareCreated(
      dietId: DietId,
      ownerUserId: UserId,
      recipientUserId: UserId,
      shareId: DietShareId,
      occurredAt: OffsetDateTime
  )
  final case class ShareRevoked(
      dietId: DietId,
      ownerUserId: UserId,
      recipientUserId: UserId,
      shareId: DietShareId,
      occurredAt: OffsetDateTime
  )
  final case class DietCopied(
      sourceDietId: DietId,
      newDietId: DietId,
      recipientUserId: UserId,
      sourceShareId: Option[DietShareId],
      occurredAt: OffsetDateTime
  )

  val live: ZLayer[HikariTransactor[Task], Nothing, AuditTrailService] =
    ZLayer.fromFunction(new AuditTrailServiceLive(_))
}

private final class AuditTrailServiceLive(xa: HikariTransactor[Task]) extends AuditTrailService {
  import AuditTrailService._

  override def recordShareCreated(event: ShareCreated): Task[Unit] =
    insert(
      eventType = "SHARE_CREATED",
      actorUserId = event.ownerUserId,
      targetDietId = event.dietId,
      targetRecipientId = Some(event.recipientUserId),
      metadata = Some(
        s"""{"shareId":"${event.shareId.value}","status":"ACTIVE"}"""
      ),
      occurredAt = event.occurredAt
    )

  override def recordShareRevoked(event: ShareRevoked): Task[Unit] =
    insert(
      eventType = "SHARE_REVOKED",
      actorUserId = event.ownerUserId,
      targetDietId = event.dietId,
      targetRecipientId = Some(event.recipientUserId),
      metadata = Some(
        s"""{"shareId":"${event.shareId.value}","status":"REVOKED"}"""
      ),
      occurredAt = event.occurredAt
    )

  override def recordDietCopied(event: DietCopied): Task[Unit] =
    insert(
      eventType = "DIET_COPIED",
      actorUserId = event.recipientUserId,
      targetDietId = event.sourceDietId,
      targetRecipientId = Some(event.recipientUserId),
      metadata = Some(
        s"""{"newDietId":"${event.newDietId.value}","sourceShareId":${event.sourceShareId
            .map(id => s""""${id.value}"""")
            .getOrElse("null")}}"""
      ),
      occurredAt = event.occurredAt
    )

  private def insert(
      eventType: String,
      actorUserId: UserId,
      targetDietId: DietId,
      targetRecipientId: Option[UserId],
      metadata: Option[String],
      occurredAt: OffsetDateTime
  ): Task[Unit] =
    sql"""
          INSERT INTO audit_log (
            event_type,
            actor_user_id,
            target_diet_id,
            target_recipient_id,
            metadata,
            occurred_at
          ) VALUES (
            $eventType,
            ${actorUserId.value},
            ${targetDietId.value},
            ${targetRecipientId.map(_.value)},
            CAST($metadata AS JSONB),
            $occurredAt
          )
       """.update.run.transact(xa).unit
}
