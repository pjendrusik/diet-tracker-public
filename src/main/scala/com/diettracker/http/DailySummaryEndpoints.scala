package com.diettracker.http

import EndpointSupport._
import com.diettracker.config.Observability
import com.diettracker.domain.UserId
import com.diettracker.services.DailySummaryService
import sttp.tapir._
import sttp.tapir.json.zio._
import sttp.tapir.server.ServerEndpoint
import zio._

import java.time.{LocalDate, ZoneId}

object DailySummaryEndpoints {
  private val summaryTag                          = "Daily Summary"
  private val dateQuery: EndpointInput[LocalDate] =
    query[String]("date").map(str => LocalDate.parse(str))(_.toString)

  private val timezoneQuery: EndpointInput[ZoneId] =
    query[Option[String]]("timezone")
      .map(_.map(ZoneId.of).getOrElse(ZoneId.of("UTC")))(zone => Some(zone.getId))

  val dailySummaryEndpoint
      : PublicEndpoint[(UserId, LocalDate, ZoneId), ApiFailure, DailySummaryResponse, Any] =
    endpoint.get
      .in("daily-summary")
      .in(userIdHeader)
      .in(dateQuery)
      .in(timezoneQuery)
      .tag(summaryTag)
      .name("Daily Summary")
      .summary("View daily nutrition summary")
      .description(
        "Aggregates all log entries for the given date and timezone, returning totals and macro coverage."
      )
      .out(jsonBody[DailySummaryResponse])
      .errorOut(errorOutput)

  def routes(service: DailySummaryService): List[ServerEndpoint[Any, Task]] = List(summaryRoute(service))

  private def summaryRoute(service: DailySummaryService): ServerEndpoint[Any, Task] =
    dailySummaryEndpoint.serverLogic { case (userId, date, zone) =>
      Observability
        .track("http.daily-summary.get") {
          service
            .getDailySummary(userId, date, zone)
            .map(toResponse)
        }
        .map(Right(_))
        .catchAll(error => ZIO.succeed(Left(toFailure(error))))
    }

  private def toResponse(summary: com.diettracker.domain.DailyIntakeSummary): DailySummaryResponse =
    DailySummaryResponse(
      date = summary.date,
      entries = summary.entries.map(LogEntryResponse.fromDomain).toList,
      totals = DailySummaryTotals(
        calories = summary.totalCalories,
        protein = summary.totalProtein,
        carbs = summary.totalCarbs,
        fat = summary.totalFat,
        macroCompleteness = MacroCompletenessPayload(
          protein = summary.macroCompleteness.protein,
          carbs = summary.macroCompleteness.carbs,
          fat = summary.macroCompleteness.fat
        )
      )
    )
}
