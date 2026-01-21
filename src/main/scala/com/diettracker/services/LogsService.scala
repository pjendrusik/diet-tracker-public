package com.diettracker.services

import com.diettracker.domain._
import com.diettracker.repositories.IntakeLogRepository
import zio._

import java.time.{LocalDate, OffsetDateTime, ZoneId}

trait LogsService {
  def createLog(userId: UserId, payload: LogsService.CreateLogRequest): IO[ServiceError, IntakeLogEntry]
  def listLogs(userId: UserId, date: LocalDate, zoneId: ZoneId): IO[ServiceError, Chunk[IntakeLogEntry]]
  def updateLog(
      logId: IntakeLogId,
      userId: UserId,
      payload: LogsService.UpdateLogRequest
  ): IO[ServiceError, IntakeLogEntry]
  def deleteLog(logId: IntakeLogId, userId: UserId): IO[ServiceError, Unit]
}

object LogsService {
  final case class CreateLogRequest(
      foodId: FoodId,
      loggedAt: OffsetDateTime,
      quantity: ServingQuantity,
      unit: Option[ServingUnit],
      notes: Option[LogNotes]
  )

  final case class UpdateLogRequest(
      loggedAt: Option[OffsetDateTime],
      quantity: Option[ServingQuantity],
      unit: Option[ServingUnit],
      notes: Option[LogNotes]
  )

  val live: ZLayer[FoodService with NutritionSnapshotService with IntakeLogRepository, Nothing, LogsService] =
    ZLayer.fromFunction(new LogsServiceLive(_, _, _))
}

final class LogsServiceLive(
    foodService: FoodService,
    snapshotService: NutritionSnapshotService,
    logRepository: IntakeLogRepository
) extends LogsService {
  import LogsService._
  import ServiceError._

  private val allowedUnits = Set("g", "ml", "oz", "piece")

  override def createLog(userId: UserId, payload: CreateLogRequest): IO[ServiceError, IntakeLogEntry] =
    for {
      _        <- validateQuantity(payload.quantity)
      _        <- validateUnit(payload.unit.map(_.value))
      food     <- foodService.getFood(payload.foodId, userId)
      snapshot <- snapshotService.buildSnapshot(
                    food,
                    payload.quantity,
                    payload.unit.orElse(Some(food.defaultServingUnit))
                  )
      created  <- logRepository
                    .create(
                      IntakeLogRepository.CreateLog(
                        userId = userId,
                        foodId = payload.foodId,
                        loggedAt = payload.loggedAt,
                        quantity = payload.quantity,
                        unit = payload.unit.getOrElse(food.defaultServingUnit),
                        notes = payload.notes,
                        snapshot = snapshot
                      )
                    )
                    .mapError(e => ValidationError(e.getMessage))
    } yield created

  override def listLogs(
      userId: UserId,
      date: LocalDate,
      zoneId: ZoneId
  ): IO[ServiceError, Chunk[IntakeLogEntry]] = {
    val start = date.atStartOfDay(zoneId).toOffsetDateTime
    val end   = start.plusDays(1)
    logRepository
      .listForDay(userId, start, end)
      .mapError(e => ValidationError(e.getMessage))
  }

  override def updateLog(
      logId: IntakeLogId,
      userId: UserId,
      payload: UpdateLogRequest
  ): IO[ServiceError, IntakeLogEntry] =
    for {
      existing   <- logRepository
                      .findById(logId, userId)
                      .mapError(e => ValidationError(e.getMessage))
                      .someOrFail(NotFound(s"Log $logId not found"))
      quantity    = payload.quantity.getOrElse(existing.quantity)
      _          <- validateQuantity(quantity)
      newUnit     = payload.unit.orElse(Some(existing.unit))
      _          <- validateUnit(newUnit.map(_.value))
      newLoggedAt = payload.loggedAt.getOrElse(existing.loggedAt)
      food       <- foodService.getFood(existing.foodId, userId)
      snapshot   <- snapshotService.buildSnapshot(food, quantity, newUnit.map(identity))
      updated    <- logRepository
                      .update(
                        IntakeLogRepository.UpdateLog(
                          id = existing.id,
                          userId = userId,
                          loggedAt = newLoggedAt,
                          quantity = quantity,
                          unit = newUnit.getOrElse(existing.unit),
                          notes = payload.notes.orElse(existing.notes),
                          snapshot = snapshot
                        )
                      )
                      .mapError(e => ValidationError(e.getMessage))
                      .someOrFail(NotFound(s"Log $logId not found"))
    } yield updated

  override def deleteLog(logId: IntakeLogId, userId: UserId): IO[ServiceError, Unit] =
    logRepository
      .delete(logId, userId)
      .mapError(e => ValidationError(e.getMessage))
      .flatMap { deleted =>
        if (deleted) ZIO.unit else ZIO.fail(NotFound(s"Log $logId not found"))
      }

  private def validateQuantity(quantity: ServingQuantity): IO[ServiceError, Unit] =
    if (quantity.value > 0) ZIO.unit else ZIO.fail(ValidationError("Quantity must be greater than zero"))

  private def validateUnit(unit: Option[String]): IO[ServiceError, Unit] =
    unit match {
      case None                                        => ZIO.unit
      case Some(value) if allowedUnits.contains(value) => ZIO.unit
      case Some(_)                                     => ZIO.fail(ValidationError("Unit must be one of: g, ml, oz, piece"))
    }
}
