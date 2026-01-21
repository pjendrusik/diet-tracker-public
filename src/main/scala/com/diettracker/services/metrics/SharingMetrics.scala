package com.diettracker.services.metrics

import com.diettracker.config.{AppConfig, SharingMetricsConfig}
import zio._

trait SharingMetrics {
  def trackShare[E, A](effect: IO[E, A]): IO[E, A]
  def trackRevoke[E, A](effect: IO[E, A]): IO[E, A]
  def trackNotification[E, A](effect: IO[E, A]): IO[E, A]
  def trackCopy[E, A](effect: IO[E, A]): IO[E, A]
  def trackRecipientList[E, A](effect: IO[E, A]): IO[E, A]
  def trackRecipientView[E, A](effect: IO[E, A]): IO[E, A]
  def trackRevocationClosure[E, A](effect: IO[E, A]): IO[E, A]
}

object SharingMetrics {
  val live: ZLayer[AppConfig, Nothing, SharingMetrics] =
    ZLayer.fromZIO {
      for {
        cfg <- ZIO.service[AppConfig]
      } yield new SharingMetricsLive(cfg.sharing.metrics)
    }

  val noop: ULayer[SharingMetrics] =
    ZLayer.succeed(new SharingMetrics {
      override def trackShare[E, A](effect: IO[E, A]): IO[E, A]             = effect
      override def trackRevoke[E, A](effect: IO[E, A]): IO[E, A]            = effect
      override def trackNotification[E, A](effect: IO[E, A]): IO[E, A]      = effect
      override def trackCopy[E, A](effect: IO[E, A]): IO[E, A]              = effect
      override def trackRecipientList[E, A](effect: IO[E, A]): IO[E, A]     = effect
      override def trackRecipientView[E, A](effect: IO[E, A]): IO[E, A]     = effect
      override def trackRevocationClosure[E, A](effect: IO[E, A]): IO[E, A] = effect
    })
}

final class SharingMetricsLive(names: SharingMetricsConfig) extends SharingMetrics {
  private def track[E, A](metricName: String, successCounter: Option[String] = None)(
      effect: IO[E, A]
  ): IO[E, A] =
    for {
      start     <- ZIO.succeed(java.lang.System.nanoTime())
      exit      <- effect.exit
      end       <- ZIO.succeed(java.lang.System.nanoTime())
      durationMs = (end - start) / 1000000
      result    <- exit match {
                     case Exit.Success(value) =>
                       (ZIO
                         .logInfo(s"[$metricName] success latency_ms=$durationMs") *>
                         ZIO.foreachDiscard(successCounter)(name => ZIO.logInfo(s"[$name] incremented")))
                         .as(value)
                     case Exit.Failure(cause) =>
                       ZIO
                         .logWarning(
                           s"[$metricName] failure latency_ms=$durationMs reason=${cause.prettyPrint}"
                         )
                         .flatMap(_ => ZIO.failCause(cause))
                   }
    } yield result

  override def trackShare[E, A](effect: IO[E, A]): IO[E, A] =
    track(names.shareLatencyTimer, Some(names.shareSuccessCounter))(effect)

  override def trackRevoke[E, A](effect: IO[E, A]): IO[E, A] = track(names.revokeLatencyTimer)(effect)

  override def trackNotification[E, A](effect: IO[E, A]): IO[E, A] =
    track(names.notificationDeliveryTimer)(effect)

  override def trackCopy[E, A](effect: IO[E, A]): IO[E, A] =
    track(names.copyLatencyTimer, Some(names.copySuccessCounter))(effect)

  override def trackRecipientList[E, A](effect: IO[E, A]): IO[E, A] =
    track(names.recipientListLatencyTimer)(effect)

  override def trackRecipientView[E, A](effect: IO[E, A]): IO[E, A] =
    track(names.recipientViewLatencyTimer)(effect)

  override def trackRevocationClosure[E, A](effect: IO[E, A]): IO[E, A] =
    track(names.revocationClosureCounter)(effect)
}
