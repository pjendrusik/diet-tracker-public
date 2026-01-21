package com.diettracker.repositories

import com.diettracker.domain.UserId
import com.diettracker.repositories.IntakeLogRepository.LogRow
import com.diettracker.repositories.IntakeLogRepository._
import doobie._
import doobie.hikari.HikariTransactor
import doobie.implicits._
import doobie.postgres.implicits._
import zio._
import zio.interop.catz._

import java.time.OffsetDateTime

trait DailySummaryRepository {
  def fetch(
      userId: UserId,
      from: OffsetDateTime,
      to: OffsetDateTime
  ): Task[DailySummaryRepository.SummaryData]
}

object DailySummaryRepository {
  final case class TotalsRow(
      totalCalories: BigDecimal,
      totalProtein: BigDecimal,
      totalCarbs: BigDecimal,
      totalFat: BigDecimal,
      proteinComplete: Boolean,
      carbsComplete: Boolean,
      fatComplete: Boolean
  )

  final case class SummaryData(entries: List[LogRow], totals: TotalsRow)

  val live: ZLayer[HikariTransactor[Task], Nothing, DailySummaryRepository] =
    ZLayer.fromFunction(new DailySummaryRepositoryLive(_))
}

final class DailySummaryRepositoryLive(xa: HikariTransactor[Task]) extends DailySummaryRepository {
  import DailySummaryRepository._

  override def fetch(userId: UserId, from: OffsetDateTime, to: OffsetDateTime): Task[SummaryData] =
    (for {
      entries <- selectEntries(userId, from, to)
      totals  <- selectTotals(userId, from, to)
    } yield SummaryData(entries, totals)).transact(xa)

  private def selectEntries(
      userId: UserId,
      from: OffsetDateTime,
      to: OffsetDateTime
  ): ConnectionIO[List[LogRow]] =
    sql"""
          SELECT l.id,
                 l.user_id,
                 l.food_id,
                 l.logged_at,
                 l.quantity,
                 l.unit,
                 l.notes,
                 l.deleted_at,
                 l.created_at,
                 l.updated_at,
                 s.id,
                 s.food_name,
                 s.food_brand,
                 s.serving_value,
                 s.serving_unit,
                 s.quantity_logged,
                 s.calories,
                 s.protein_g,
                 s.carbs_g,
                 s.fat_g,
                 s.food_version,
                 s.created_at
            FROM intake_log l
            JOIN nutrition_snapshot s ON s.intake_log_id = l.id
           WHERE l.user_id = $userId
             AND l.logged_at >= $from
             AND l.logged_at < $to
             AND l.deleted_at IS NULL
        ORDER BY l.logged_at ASC, l.created_at ASC
       """.query[LogRow].to[List]

  private def selectTotals(
      userId: UserId,
      from: OffsetDateTime,
      to: OffsetDateTime
  ): ConnectionIO[TotalsRow] =
    sql"""
          SELECT COALESCE(SUM(s.calories), 0)    AS total_calories,
                 COALESCE(SUM(s.protein_g), 0)   AS total_protein,
                 COALESCE(SUM(s.carbs_g), 0)     AS total_carbs,
                 COALESCE(SUM(s.fat_g), 0)       AS total_fat,
                 COALESCE(BOOL_AND(s.protein_g > 0), FALSE) AS protein_complete,
                 COALESCE(BOOL_AND(s.carbs_g > 0), FALSE)   AS carbs_complete,
                 COALESCE(BOOL_AND(s.fat_g > 0), FALSE)     AS fat_complete
            FROM intake_log l
            JOIN nutrition_snapshot s ON s.intake_log_id = l.id
           WHERE l.user_id = $userId
             AND l.logged_at >= $from
             AND l.logged_at < $to
             AND l.deleted_at IS NULL
       """.query[TotalsRow].unique
}
