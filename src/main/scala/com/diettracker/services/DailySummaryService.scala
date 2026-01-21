package com.diettracker.services

import com.diettracker.domain._
import com.diettracker.repositories.DailySummaryRepository
import zio._

import java.time.{LocalDate, ZoneId}

trait DailySummaryService {
  def getDailySummary(userId: UserId, date: LocalDate, zoneId: ZoneId): IO[ServiceError, DailyIntakeSummary]
}

object DailySummaryService {
  val live: ZLayer[DailySummaryRepository, Nothing, DailySummaryService] =
    ZLayer.fromFunction(new DailySummaryServiceLive(_))
}

final class DailySummaryServiceLive(repository: DailySummaryRepository) extends DailySummaryService {
  import ServiceError._

  override def getDailySummary(
      userId: UserId,
      date: LocalDate,
      zoneId: ZoneId
  ): IO[ServiceError, DailyIntakeSummary] = {
    val start = date.atStartOfDay(zoneId).toOffsetDateTime
    val end   = start.plusDays(1)

    repository
      .fetch(userId, start, end)
      .map { result =>
        val entries = Chunk.fromIterable(result.entries.map(_.toDomain))
        DailyIntakeSummary(
          userId = userId,
          date = SummaryDate(date.toString),
          entries = entries,
          totalCalories = CalorieCount(result.totals.totalCalories),
          totalProtein = ProteinGrams(result.totals.totalProtein),
          totalCarbs = CarbGrams(result.totals.totalCarbs),
          totalFat = FatGrams(result.totals.totalFat),
          macroCompleteness = MacroCompleteness(
            protein = MacroGoalStatus(result.totals.proteinComplete),
            carbs = MacroGoalStatus(result.totals.carbsComplete),
            fat = MacroGoalStatus(result.totals.fatComplete)
          )
        )
      }
      .mapError(e => ValidationError(e.getMessage))
  }
}
