package com.diettracker.services

import com.diettracker.domain._
import com.diettracker.repositories.FoodRepository
import zio._

trait FoodService {
  def createFood(userId: UserId, payload: FoodService.CreateFoodRequest): IO[ServiceError, FoodItem]
  def listFoods(userId: UserId): IO[ServiceError, Chunk[FoodItem]]
  def searchFoods(userId: UserId, query: Option[String]): IO[ServiceError, Chunk[FoodItem]]
  def getFood(foodId: FoodId, userId: UserId): IO[ServiceError, FoodItem]
  def updateFood(
      foodId: FoodId,
      userId: UserId,
      payload: FoodService.UpdateFoodRequest
  ): IO[ServiceError, FoodItem]
  def deleteFood(foodId: FoodId, userId: UserId, force: Boolean): IO[ServiceError, Unit]
}

object FoodService {
  final case class CreateFoodRequest(
      name: FoodName,
      brand: Option[FoodBrand],
      defaultServingValue: ServingQuantity,
      defaultServingUnit: ServingUnit,
      caloriesPerServing: CalorieCount,
      macrosPerServing: Option[MacroBreakdown],
      macrosPer100g: Option[MacroBreakdown],
      notes: Option[FoodNotes]
  )

  type UpdateFoodRequest = CreateFoodRequest

  val live: ZLayer[FoodRepository, Nothing, FoodService] =
    ZLayer.fromFunction(new FoodServiceLive(_))
}

final class FoodServiceLive(foodRepository: FoodRepository) extends FoodService {
  import FoodService._
  import ServiceError._

  private val allowedUnits = Set("g", "ml", "oz", "piece")

  override def createFood(userId: UserId, payload: CreateFoodRequest): IO[ServiceError, FoodItem] =
    for {
      _       <- validate(payload)
      created <- foodRepository
                   .create(
                     FoodRepository.CreateFood(
                       userId = userId,
                       name = payload.name,
                       brand = payload.brand,
                       defaultServingValue = payload.defaultServingValue,
                       defaultServingUnit = payload.defaultServingUnit,
                       caloriesPerServing = payload.caloriesPerServing,
                       macrosPerServing = payload.macrosPerServing,
                       macrosPer100g = payload.macrosPer100g,
                       notes = payload.notes
                     )
                   )
                   .mapError(e => ValidationError(e.getMessage))
    } yield created

  override def listFoods(userId: UserId): IO[ServiceError, Chunk[FoodItem]] =
    foodRepository.list(userId).mapError(e => ValidationError(e.getMessage))

  override def searchFoods(userId: UserId, query: Option[String]): IO[ServiceError, Chunk[FoodItem]] =
    foodRepository.search(userId, query).mapError(e => ValidationError(e.getMessage))

  override def getFood(foodId: FoodId, userId: UserId): IO[ServiceError, FoodItem] =
    foodRepository
      .findById(foodId, userId)
      .mapError(e => ValidationError(e.getMessage))
      .someOrFail(NotFound(s"Food $foodId not found"))

  override def updateFood(
      foodId: FoodId,
      userId: UserId,
      payload: UpdateFoodRequest
  ): IO[ServiceError, FoodItem] =
    for {
      existing <- getFood(foodId, userId)
      _        <- validate(payload)
      updated  <- foodRepository
                    .update(
                      FoodRepository.UpdateFood(
                        id = existing.id,
                        userId = userId,
                        name = payload.name,
                        brand = payload.brand,
                        defaultServingValue = payload.defaultServingValue,
                        defaultServingUnit = payload.defaultServingUnit,
                        caloriesPerServing = payload.caloriesPerServing,
                        macrosPerServing = payload.macrosPerServing,
                        macrosPer100g = payload.macrosPer100g,
                        notes = payload.notes
                      )
                    )
                    .mapError(e => ValidationError(e.getMessage))
                    .someOrFail(NotFound(s"Food $foodId not found"))
    } yield updated

  override def deleteFood(foodId: FoodId, userId: UserId, force: Boolean): IO[ServiceError, Unit] =
    for {
      _       <- getFood(foodId, userId)
      hasLogs <-
        foodRepository.hasLogs(foodId, userId).mapError(e => ValidationError(e.getMessage))
      _       <-
        if (hasLogs)
          if (force)
            ZIO.fail(ValidationError("Foods referenced by historical logs cannot be deleted"))
          else
            ZIO.fail(
              ValidationError("Food has historical logs; deletion blocked (use force=true to acknowledge)")
            )
        else ZIO.unit
      deleted <-
        foodRepository.delete(foodId, userId).mapError(e => ValidationError(e.getMessage))
      _       <- if (deleted) ZIO.unit else ZIO.fail(NotFound(s"Food $foodId not found"))
    } yield ()

  private def validate(payload: CreateFoodRequest): IO[ServiceError, Unit] = {
    def ensure(cond: Boolean, message: String): IO[ServiceError, Unit] =
      ZIO.fail(ValidationError(message)).unlessZIO(ZIO.succeed(cond)).unit

    for {
      _ <- ensure(payload.name.value.trim.length >= 2, "Food name must be at least 2 characters")
      _ <- ensure(payload.defaultServingValue.value > 0, "Default serving value must be positive")
      _ <- ensure(payload.caloriesPerServing.value >= 0, "Calories per serving must be zero or positive")
      _ <- ensure(
             allowedUnits.contains(payload.defaultServingUnit.value),
             s"Serving unit must be one of ${allowedUnits.mkString(",")}"
           )
      _ <- validateMacros(payload.macrosPerServing)
      _ <- validateMacros(payload.macrosPer100g)
    } yield ()
  }

  private def validateMacros(macros: Option[MacroBreakdown]): IO[ServiceError, Unit] =
    macros match {
      case None        => ZIO.unit
      case Some(value) =>
        if (value.protein.value >= 0 && value.carbs.value >= 0 && value.fat.value >= 0) ZIO.unit
        else ZIO.fail(ValidationError("Macro values must be zero or positive"))
    }
}
