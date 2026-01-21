package com.diettracker.repositories

import com.diettracker.domain._
import cats.implicits._
import doobie._
import doobie.hikari.HikariTransactor
import doobie.implicits._
import doobie.postgres.implicits._
import zio._
import zio.interop.catz._

import java.time.OffsetDateTime
import java.util.UUID

trait IntakeLogRepository {
  def create(input: IntakeLogRepository.CreateLog): Task[IntakeLogEntry]
  def update(input: IntakeLogRepository.UpdateLog): Task[Option[IntakeLogEntry]]
  def delete(id: IntakeLogId, userId: UserId): Task[Boolean]
  def findById(id: IntakeLogId, userId: UserId): Task[Option[IntakeLogEntry]]
  def listForDay(userId: UserId, from: OffsetDateTime, to: OffsetDateTime): Task[Chunk[IntakeLogEntry]]
}

object IntakeLogRepository {
  private[repositories] implicit val intakeLogIdMeta: Meta[IntakeLogId]        =
    Meta[UUID].imap(IntakeLogId(_))(_.value)
  private[repositories] implicit val userIdMeta: Meta[UserId]                  =
    Meta[UUID].imap(UserId(_))(_.value)
  private[repositories] implicit val foodIdMeta: Meta[FoodId]                  =
    Meta[UUID].imap(FoodId(_))(_.value)
  private[repositories] implicit val snapshotIdMeta: Meta[NutritionSnapshotId] =
    Meta[UUID].imap(NutritionSnapshotId(_))(_.value)
  final case class SnapshotDraft(
      foodId: FoodId,
      foodName: FoodName,
      foodBrand: Option[FoodBrand],
      servingValue: ServingQuantity,
      servingUnit: ServingUnit,
      quantityLogged: ServingQuantity,
      calories: CalorieCount,
      protein: ProteinGrams,
      carbs: CarbGrams,
      fat: FatGrams,
      foodVersion: FoodVersion
  )

  final case class CreateLog(
      userId: UserId,
      foodId: FoodId,
      loggedAt: OffsetDateTime,
      quantity: ServingQuantity,
      unit: ServingUnit,
      notes: Option[LogNotes],
      snapshot: SnapshotDraft
  )

  final case class UpdateLog(
      id: IntakeLogId,
      userId: UserId,
      loggedAt: OffsetDateTime,
      quantity: ServingQuantity,
      unit: ServingUnit,
      notes: Option[LogNotes],
      snapshot: SnapshotDraft
  )

  final case class LogRow(
      id: UUID,
      userId: UUID,
      foodId: UUID,
      loggedAt: OffsetDateTime,
      quantity: BigDecimal,
      unit: String,
      notes: Option[String],
      deletedAt: Option[OffsetDateTime],
      createdAt: OffsetDateTime,
      updatedAt: OffsetDateTime,
      snapshotId: UUID,
      snapshotFoodName: String,
      snapshotFoodBrand: Option[String],
      snapshotServingValue: BigDecimal,
      snapshotServingUnit: String,
      snapshotQuantity: BigDecimal,
      snapshotCalories: BigDecimal,
      snapshotProtein: BigDecimal,
      snapshotCarbs: BigDecimal,
      snapshotFat: BigDecimal,
      snapshotVersion: Int,
      snapshotCreatedAt: OffsetDateTime
  ) {
    def toDomain: IntakeLogEntry = {
      val snapshot = NutritionSnapshot(
        id = NutritionSnapshotId(snapshotId),
        intakeLogId = IntakeLogId(id),
        foodId = FoodId(foodId),
        foodName = FoodName(snapshotFoodName),
        foodBrand = snapshotFoodBrand.map(FoodBrand(_)),
        servingValue = ServingQuantity(snapshotServingValue),
        servingUnit = ServingUnit(snapshotServingUnit),
        quantityLogged = ServingQuantity(snapshotQuantity),
        calories = CalorieCount(snapshotCalories),
        protein = ProteinGrams(snapshotProtein),
        carbs = CarbGrams(snapshotCarbs),
        fat = FatGrams(snapshotFat),
        foodVersion = FoodVersion(snapshotVersion),
        createdAt = snapshotCreatedAt
      )

      IntakeLogEntry(
        id = IntakeLogId(id),
        userId = UserId(userId),
        foodId = FoodId(foodId),
        loggedAt = loggedAt,
        quantity = ServingQuantity(quantity),
        unit = ServingUnit(unit),
        notes = notes.map(LogNotes(_)),
        deletedAt = deletedAt,
        createdAt = createdAt,
        updatedAt = updatedAt,
        snapshot = snapshot
      )
    }
  }

  val live: ZLayer[HikariTransactor[Task], Nothing, IntakeLogRepository] =
    ZLayer.fromFunction(new IntakeLogRepositoryLive(_))
}

final class IntakeLogRepositoryLive(xa: HikariTransactor[Task]) extends IntakeLogRepository {
  import IntakeLogRepository._

  override def create(input: CreateLog): Task[IntakeLogEntry] = insertLog(input).transact(xa).map(_.toDomain)

  override def update(input: UpdateLog): Task[Option[IntakeLogEntry]] =
    updateLog(input).transact(xa).map(_.map(_.toDomain))

  override def delete(id: IntakeLogId, userId: UserId): Task[Boolean] =
    markDeleted(id, userId).transact(xa).map(_ > 0)

  override def findById(id: IntakeLogId, userId: UserId): Task[Option[IntakeLogEntry]] =
    selectById(id, userId).transact(xa).map(_.map(_.toDomain))

  override def listForDay(
      userId: UserId,
      from: OffsetDateTime,
      to: OffsetDateTime
  ): Task[Chunk[IntakeLogEntry]] =
    selectForDay(userId, from, to).transact(xa).map(rows => Chunk.fromIterable(rows.map(_.toDomain)))

  private def insertLog(input: CreateLog): ConnectionIO[LogRow] =
    for {
      logId <-
        sql"""
              INSERT INTO intake_log (
                user_id, food_id, logged_at, quantity, unit, notes
              ) VALUES (
                ${input.userId},
                ${input.foodId},
                ${input.loggedAt},
                ${input.quantity.value},
                ${input.unit.value},
                ${input.notes.map(_.value)}
              ) RETURNING id
            """.query[UUID].unique
      _     <-
        sql"""
              INSERT INTO nutrition_snapshot (
                intake_log_id,
                food_id,
                food_name,
                food_brand,
                serving_value,
                serving_unit,
                quantity_logged,
                calories,
                protein_g,
                carbs_g,
                fat_g,
                food_version
              ) VALUES (
                $logId,
                ${input.foodId},
                ${input.snapshot.foodName.value},
                ${input.snapshot.foodBrand.map(_.value)},
                ${input.snapshot.servingValue.value},
                ${input.snapshot.servingUnit.value},
                ${input.snapshot.quantityLogged.value},
                ${input.snapshot.calories.value},
                ${input.snapshot.protein.value},
                ${input.snapshot.carbs.value},
                ${input.snapshot.fat.value},
                ${input.snapshot.foodVersion.value}
              )
            """.update.run
      row   <- selectByIdInternal(IntakeLogId(logId))
    } yield row

  private def updateLog(input: UpdateLog): ConnectionIO[Option[LogRow]] =
    for {
      updatedRows <-
        sql"""
              UPDATE intake_log
                 SET logged_at = ${input.loggedAt},
                     quantity = ${input.quantity.value},
                     unit = ${input.unit.value},
                     notes = ${input.notes.map(_.value)},
                     updated_at = NOW()
               WHERE id = ${input.id.value} AND user_id = ${input.userId.value} AND deleted_at IS NULL
            """.update.run
      _           <-
        if (updatedRows == 0) 0.pure[ConnectionIO]
        else
          sql"""
                UPDATE nutrition_snapshot
                   SET food_name = ${input.snapshot.foodName.value},
                       food_brand = ${input.snapshot.foodBrand.map(_.value)},
                       serving_value = ${input.snapshot.servingValue.value},
                       serving_unit = ${input.snapshot.servingUnit.value},
                       quantity_logged = ${input.snapshot.quantityLogged.value},
                       calories = ${input.snapshot.calories.value},
                       protein_g = ${input.snapshot.protein.value},
                       carbs_g = ${input.snapshot.carbs.value},
                       fat_g = ${input.snapshot.fat.value},
                       food_version = ${input.snapshot.foodVersion.value}
                 WHERE intake_log_id = ${input.id.value}
              """.update.run
      result      <-
        if (updatedRows == 0) none[LogRow].pure[ConnectionIO] else selectByIdInternal(input.id).map(Some(_))
    } yield result

  private def markDeleted(id: IntakeLogId, userId: UserId): ConnectionIO[Int] =
    sql"""
          UPDATE intake_log
             SET deleted_at = NOW()
           WHERE id = ${id.value} AND user_id = ${userId.value} AND deleted_at IS NULL
        """.update.run

  private def selectById(id: IntakeLogId, userId: UserId): ConnectionIO[Option[LogRow]] =
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
           WHERE l.id = ${id.value} AND l.user_id = ${userId.value}
        """.query[LogRow].option

  private def selectByIdInternal(id: IntakeLogId): ConnectionIO[LogRow] =
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
           WHERE l.id = $id
        """.query[LogRow].unique

  private def selectForDay(
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
           WHERE l.user_id = ${userId.value}
             AND l.logged_at >= $from
             AND l.logged_at < $to
             AND l.deleted_at IS NULL
        ORDER BY l.logged_at ASC
        """.query[LogRow].to[List]
}
