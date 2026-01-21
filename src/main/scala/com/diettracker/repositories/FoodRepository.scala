package com.diettracker.repositories

import com.diettracker.domain._
import doobie._
import doobie.hikari.HikariTransactor
import doobie.implicits._
import doobie.postgres.implicits._
import zio._
import zio.interop.catz._
import zio.json._

import java.time.OffsetDateTime
import java.util.UUID

trait FoodRepository {
  def create(input: FoodRepository.CreateFood): Task[FoodItem]
  def list(userId: UserId): Task[Chunk[FoodItem]]
  def findById(id: FoodId, userId: UserId): Task[Option[FoodItem]]
  def search(userId: UserId, query: Option[String]): Task[Chunk[FoodItem]]
  def update(input: FoodRepository.UpdateFood): Task[Option[FoodItem]]
  def delete(id: FoodId, userId: UserId): Task[Boolean]
  def hasLogs(id: FoodId, userId: UserId): Task[Boolean]
}

object FoodRepository {
  final case class CreateFood(
      userId: UserId,
      name: FoodName,
      brand: Option[FoodBrand],
      defaultServingValue: ServingQuantity,
      defaultServingUnit: ServingUnit,
      caloriesPerServing: CalorieCount,
      macrosPerServing: Option[MacroBreakdown],
      macrosPer100g: Option[MacroBreakdown],
      notes: Option[FoodNotes]
  )

  final case class UpdateFood(
      id: FoodId,
      userId: UserId,
      name: FoodName,
      brand: Option[FoodBrand],
      defaultServingValue: ServingQuantity,
      defaultServingUnit: ServingUnit,
      caloriesPerServing: CalorieCount,
      macrosPerServing: Option[MacroBreakdown],
      macrosPer100g: Option[MacroBreakdown],
      notes: Option[FoodNotes]
  )

  val live: ZLayer[HikariTransactor[Task], Nothing, FoodRepository] =
    ZLayer.fromFunction(new FoodRepositoryLive(_))

  private[repositories] final case class FoodRow(
      id: FoodId,
      userId: UserId,
      name: String,
      brand: Option[String],
      defaultServingValue: BigDecimal,
      defaultServingUnit: String,
      caloriesPerServing: BigDecimal,
      macrosPerServing: Option[String],
      macrosPer100g: Option[String],
      notes: Option[String],
      version: Int,
      createdAt: OffsetDateTime,
      updatedAt: OffsetDateTime
  ) {
    def toDomain: Task[FoodItem] =
      for {
        serving <- decodeMacro(macrosPerServing)
        per100  <- decodeMacro(macrosPer100g)
      } yield FoodItem(
        id = id,
        userId = userId,
        name = FoodName(name),
        brand = brand.map(FoodBrand(_)),
        defaultServingValue = ServingQuantity(defaultServingValue),
        defaultServingUnit = ServingUnit(defaultServingUnit),
        caloriesPerServing = CalorieCount(caloriesPerServing),
        macrosPerServing = serving,
        macrosPer100g = per100,
        notes = notes.map(FoodNotes(_)),
        version = FoodVersion(version),
        createdAt = createdAt,
        updatedAt = updatedAt
      )

    private def decodeMacro(json: Option[String]): Task[Option[MacroBreakdown]] =
      ZIO.foreach(json)(value =>
        ZIO
          .fromEither(value.fromJson[MacroBreakdown])
          .mapError(err => new RuntimeException(s"Failed to decode macro JSON: $err"))
      )
  }
}

final class FoodRepositoryLive(xa: HikariTransactor[Task]) extends FoodRepository {
  import FoodRepository._
  private implicit val foodIdMeta: Meta[FoodId] = Meta[UUID].imap(FoodId(_))(_.value)
  private implicit val userIdMeta: Meta[UserId] = Meta[UUID].imap(UserId(_))(_.value)

  override def create(input: CreateFood): Task[FoodItem] = insertFood(input).transact(xa).flatMap(_.toDomain)

  override def list(userId: UserId): Task[Chunk[FoodItem]] =
    selectByUser(userId)
      .transact(xa)
      .flatMap(rows => ZIO.foreach(rows)(_.toDomain))
      .map(rows => Chunk.fromIterable(rows))

  override def findById(id: FoodId, userId: UserId): Task[Option[FoodItem]] =
    selectById(id, userId)
      .transact(xa)
      .flatMap(row => ZIO.foreach(row)(_.toDomain))

  override def search(userId: UserId, query: Option[String]): Task[Chunk[FoodItem]] = {
    val normalized = query.map(_.trim).filter(_.nonEmpty)
    val action     = normalized match {
      case Some(term) => selectBySearch(userId, term.toLowerCase)
      case None       => selectByUser(userId)
    }
    action
      .transact(xa)
      .flatMap(rows => ZIO.foreach(rows)(_.toDomain))
      .map(list => Chunk.fromIterable(list))
  }

  override def update(input: UpdateFood): Task[Option[FoodItem]] =
    updateFood(input)
      .transact(xa)
      .flatMap(row => ZIO.foreach(row)(_.toDomain))

  override def delete(id: FoodId, userId: UserId): Task[Boolean] =
    sql"""
          DELETE FROM food_item
           WHERE id = $id AND user_id = $userId
       """.update.run.transact(xa).map(_ > 0)

  override def hasLogs(id: FoodId, userId: UserId): Task[Boolean] =
    sql"""
          SELECT EXISTS(
                   SELECT 1 FROM intake_log
                    WHERE food_id = $id AND user_id = $userId
                 )
       """.query[Boolean].unique.transact(xa)

  private def insertFood(input: CreateFood): ConnectionIO[FoodRow] = {
    val macrosPerServingJson = input.macrosPerServing.map(_.toJson)
    val macrosPer100Json     = input.macrosPer100g.map(_.toJson)

    sql"""
          INSERT INTO food_item (
            user_id,
            name,
            brand,
            default_serving_value,
            default_serving_unit,
            calories_per_serving,
            macros_per_serving,
            macros_per_100g,
            notes
          ) VALUES (
            ${input.userId},
            ${input.name.value},
            ${input.brand.map(_.value)},
            ${input.defaultServingValue.value},
            ${input.defaultServingUnit.value},
            ${input.caloriesPerServing.value},
            CAST(${macrosPerServingJson} AS JSONB),
            CAST(${macrosPer100Json} AS JSONB),
            ${input.notes.map(_.value)}
          )
          RETURNING id,
                    user_id,
                    name,
                    brand,
                    default_serving_value,
                    default_serving_unit,
                    calories_per_serving,
                    macros_per_serving::TEXT,
                    macros_per_100g::TEXT,
                    notes,
                    version,
                    created_at,
                    updated_at
       """.query[FoodRow].unique
  }

  private def selectByUser(userId: UserId): ConnectionIO[List[FoodRow]] =
    sql"""
          SELECT id,
                 user_id,
                 name,
                 brand,
                 default_serving_value,
                 default_serving_unit,
                 calories_per_serving,
                 macros_per_serving::TEXT,
                 macros_per_100g::TEXT,
                 notes,
                 version,
                 created_at,
                 updated_at
            FROM food_item
           WHERE user_id = $userId
           ORDER BY created_at DESC
       """.query[FoodRow].to[List]

  private def selectById(id: FoodId, userId: UserId): ConnectionIO[Option[FoodRow]] =
    sql"""
          SELECT id,
                 user_id,
                 name,
                 brand,
                 default_serving_value,
                 default_serving_unit,
                       calories_per_serving,
                       macros_per_serving::TEXT,
                       macros_per_100g::TEXT,
                       notes,
                       version,
                       created_at,
                       updated_at
           FROM food_item
          WHERE id = $id AND user_id = $userId
       """.query[FoodRow].option

  private def selectBySearch(userId: UserId, term: String): ConnectionIO[List[FoodRow]] = {
    val likeTerm = s"%$term%"
    sql"""
          SELECT id,
                 user_id,
                 name,
                 brand,
                 default_serving_value,
                 default_serving_unit,
                 calories_per_serving,
                 macros_per_serving::TEXT,
                 macros_per_100g::TEXT,
                 notes,
                 version,
                 created_at,
                 updated_at
            FROM food_item
           WHERE user_id = $userId
             AND (LOWER(name) LIKE $likeTerm OR LOWER(COALESCE(brand, '')) LIKE $likeTerm)
           ORDER BY name ASC
       """.query[FoodRow].to[List]
  }

  private def updateFood(input: UpdateFood): ConnectionIO[Option[FoodRow]] = {
    val servingJson = input.macrosPerServing.map(_.toJson)
    val per100Json  = input.macrosPer100g.map(_.toJson)

    sql"""
          UPDATE food_item
             SET name = ${input.name.value},
                 brand = ${input.brand.map(_.value)},
                 default_serving_value = ${input.defaultServingValue.value},
                 default_serving_unit = ${input.defaultServingUnit.value},
                 calories_per_serving = ${input.caloriesPerServing.value},
                 macros_per_serving = CAST($servingJson AS JSONB),
                 macros_per_100g = CAST($per100Json AS JSONB),
                 notes = ${input.notes.map(_.value)},
                 version = version + 1,
                 updated_at = NOW()
           WHERE id = ${input.id} AND user_id = ${input.userId}
       RETURNING id,
                 user_id,
                 name,
                 brand,
                 default_serving_value,
                 default_serving_unit,
                 calories_per_serving,
                 macros_per_serving::TEXT,
                 macros_per_100g::TEXT,
                 notes,
                 version,
                 created_at,
                 updated_at
       """.query[FoodRow].option
  }
}
