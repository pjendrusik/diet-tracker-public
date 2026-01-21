package com.diettracker.services

import com.diettracker.domain.UserId
import com.diettracker.domain.sharing.{DietId, DietShareId}
import zio.{Task, ULayer, ZIO, ZLayer}

trait NotificationPublisher {
  def notifyShareCreated(event: NotificationPublisher.ShareCreated): Task[Unit]
  def notifyShareRevoked(event: NotificationPublisher.ShareRevoked): Task[Unit]
}

object NotificationPublisher {
  final case class ShareCreated(
      dietId: DietId,
      ownerUserId: UserId,
      recipientUserId: UserId,
      shareId: DietShareId
  )
  final case class ShareRevoked(
      dietId: DietId,
      ownerUserId: UserId,
      recipientUserId: UserId,
      shareId: DietShareId
  )

  val logging: ULayer[NotificationPublisher] = ZLayer.succeed(new LoggingNotificationPublisher)
}

private final class LoggingNotificationPublisher extends NotificationPublisher {
  import NotificationPublisher._

  override def notifyShareCreated(event: ShareCreated): Task[Unit] =
    ZIO.logInfo(
      s"[notifications] share created for diet ${event.dietId.value} -> recipient ${event.recipientUserId.value}"
    )

  override def notifyShareRevoked(event: ShareRevoked): Task[Unit] =
    ZIO.logInfo(
      s"[notifications] share revoked for diet ${event.dietId.value} -> recipient ${event.recipientUserId.value}"
    )
}
