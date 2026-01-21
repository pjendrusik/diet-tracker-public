package com.diettracker.http

import EndpointSupport._
import com.diettracker.config.Observability
import com.diettracker.domain.{LogNotes, ServingUnit}
import com.diettracker.domain.{IntakeLogId, UserId}
import com.diettracker.services.LogsService
import com.diettracker.services.LogsService.{CreateLogRequest, UpdateLogRequest}
import sttp.tapir._
import sttp.tapir.json.zio._
import sttp.tapir.server.ServerEndpoint
import zio._

import java.time.{LocalDate, ZoneId}

object LogsEndpoints {
  private val logsTag                             = "Logs"
  private val dateQuery: EndpointInput[LocalDate] =
    query[String]("date").mapDecode { raw =>
      try DecodeResult.Value(LocalDate.parse(raw))
      catch {
        case _: Throwable => DecodeResult.Error(raw, new RuntimeException("Invalid date format"))
      }
    }(_.toString)

  private val timezoneQuery: EndpointInput[ZoneId] =
    query[String]("timezone").mapDecode { raw =>
      try DecodeResult.Value(ZoneId.of(raw))
      catch {
        case _: Throwable => DecodeResult.Error(raw, new RuntimeException("Invalid timezone"))
      }
    }(_.getId)

  val createLogEndpoint: PublicEndpoint[(UserId, LogCreateRequest), ApiFailure, LogEntryResponse, Any] =
    endpoint.post
      .in("logs")
      .in(userIdHeader)
      .in(jsonBody[LogCreateRequest])
      .tag(logsTag)
      .name("Create Log Entry")
      .summary("Log consumption of a food item")
      .description("Creates a new intake log entry with automatic nutrition snapshot.")
      .out(jsonBody[LogEntryResponse])
      .errorOut(errorOutput)

  val listLogsEndpoint: PublicEndpoint[(UserId, LocalDate, ZoneId), ApiFailure, DailyLogsResponse, Any] =
    endpoint.get
      .in("logs")
      .in(userIdHeader)
      .in(dateQuery)
      .in(timezoneQuery)
      .tag(logsTag)
      .name("List Logs")
      .summary("List daily logs for a user and timezone")
      .description("Returns all intake log entries for the provided date, adjusted to the supplied timezone.")
      .out(jsonBody[DailyLogsResponse])
      .errorOut(errorOutput)

  val updateLogEndpoint
      : PublicEndpoint[(IntakeLogId, UserId, LogEditRequest), ApiFailure, LogEntryResponse, Any] =
    endpoint.patch
      .in("logs" / path[IntakeLogId]("id"))
      .in(userIdHeader)
      .in(jsonBody[LogEditRequest])
      .tag(logsTag)
      .name("Update Log Entry")
      .summary("Update an existing intake log")
      .description("Partially updates a log entry and recalculates its nutrition snapshot.")
      .out(jsonBody[LogEntryResponse])
      .errorOut(errorOutput)

  val deleteLogEndpoint: PublicEndpoint[(IntakeLogId, UserId), ApiFailure, Unit, Any] =
    endpoint.delete
      .in("logs" / path[IntakeLogId]("id"))
      .in(userIdHeader)
      .tag(logsTag)
      .name("Delete Log Entry")
      .summary("Delete an intake log")
      .description("Removes the specified log entry for the current user.")
      .out(emptyOutput)
      .errorOut(errorOutput)

  def routes(logsService: LogsService): List[ServerEndpoint[Any, Task]] =
    List(
      createRoute(logsService),
      listRoute(logsService),
      updateRoute(logsService),
      deleteRoute(logsService)
    )

  private def createRoute(logsService: LogsService): ServerEndpoint[Any, Task] =
    createLogEndpoint.serverLogic { case (userId, payload) =>
      Observability
        .track("http.logs.post") {
          logsService
            .createLog(userId, toCreateRequest(payload))
            .map(LogEntryResponse.fromDomain)
        }
        .map(Right(_))
        .catchAll(error => ZIO.succeed(Left(toFailure(error))))
    }

  private def listRoute(logsService: LogsService): ServerEndpoint[Any, Task] =
    listLogsEndpoint.serverLogic { case (userId, date, zoneId) =>
      Observability
        .track("http.logs.get") {
          logsService
            .listLogs(userId, date, zoneId)
            .map(entries => DailyLogsResponse(date, entries.map(LogEntryResponse.fromDomain).toList))
        }
        .map(Right(_))
        .catchAll(error => ZIO.succeed(Left(toFailure(error))))
    }

  private def updateRoute(logsService: LogsService): ServerEndpoint[Any, Task] =
    updateLogEndpoint.serverLogic { case (logId, userId, payload) =>
      Observability
        .track("http.logs.patch") {
          logsService
            .updateLog(logId, userId, toUpdateRequest(payload))
            .map(LogEntryResponse.fromDomain)
        }
        .map(Right(_))
        .catchAll(error => ZIO.succeed(Left(toFailure(error))))
    }

  private def deleteRoute(logsService: LogsService): ServerEndpoint[Any, Task] =
    deleteLogEndpoint.serverLogic { case (logId, userId) =>
      Observability
        .track("http.logs.delete") {
          logsService
            .deleteLog(logId, userId)
        }
        .as(Right(()))
        .catchAll(error => ZIO.succeed(Left(toFailure(error))))
    }

  private def toCreateRequest(payload: LogCreateRequest): CreateLogRequest =
    CreateLogRequest(
      foodId = payload.foodId,
      loggedAt = payload.loggedAt,
      quantity = payload.quantity,
      unit = payload.unit.flatMap(normalizeUnit),
      notes = payload.notes.flatMap(normalizeNotes)
    )

  private def toUpdateRequest(payload: LogEditRequest): UpdateLogRequest =
    UpdateLogRequest(
      loggedAt = payload.loggedAt,
      quantity = payload.quantity,
      unit = payload.unit.flatMap(normalizeUnit),
      notes = payload.notes.flatMap(normalizeNotes)
    )

  private def normalizeUnit(unit: ServingUnit): Option[ServingUnit] = {
    val trimmed = unit.value.trim
    if (trimmed.nonEmpty) Some(ServingUnit(trimmed)) else None
  }

  private def normalizeNotes(notes: LogNotes): Option[LogNotes] = {
    val trimmed = notes.value.trim
    if (trimmed.nonEmpty) Some(LogNotes(trimmed)) else None
  }
}
