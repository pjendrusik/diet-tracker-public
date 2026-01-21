package com.diettracker.services

import com.diettracker.domain._
import com.diettracker.repositories.IntakeLogRepository
import zio._
import scala.math.BigDecimal.RoundingMode

trait NutritionSnapshotService {
  def buildSnapshot(
      food: FoodItem,
      quantity: ServingQuantity,
      unit: Option[ServingUnit]
  ): UIO[IntakeLogRepository.SnapshotDraft]
}

object NutritionSnapshotService {
  val live: ULayer[NutritionSnapshotService] = ZLayer.succeed(new NutritionSnapshotServiceLive)
}

final class NutritionSnapshotServiceLive extends NutritionSnapshotService {
  private val scale = 2

  override def buildSnapshot(
      food: FoodItem,
      quantity: ServingQuantity,
      unit: Option[ServingUnit]
  ): UIO[IntakeLogRepository.SnapshotDraft] =
    ZIO.succeed {
      val servings      = quantity.value
      val calories      = CalorieCount(round(food.caloriesPerServing.value * servings))
      val proteinAmount =
        food.macrosPerServing.map(_.protein.value).getOrElse(BigDecimal(0))
      val carbsAmount   =
        food.macrosPerServing.map(_.carbs.value).getOrElse(BigDecimal(0))
      val fatAmount     =
        food.macrosPerServing.map(_.fat.value).getOrElse(BigDecimal(0))
      val protein       = ProteinGrams(round(proteinAmount * servings))
      val carbs         = CarbGrams(round(carbsAmount * servings))
      val fat           = FatGrams(round(fatAmount * servings))

      IntakeLogRepository.SnapshotDraft(
        foodId = food.id,
        foodName = food.name,
        foodBrand = food.brand,
        servingValue = food.defaultServingValue,
        servingUnit = unit.getOrElse(food.defaultServingUnit),
        quantityLogged = ServingQuantity(servings),
        calories = calories,
        protein = protein,
        carbs = carbs,
        fat = fat,
        foodVersion = food.version
      )
    }

  private def round(value: BigDecimal): BigDecimal = value.setScale(scale, RoundingMode.HALF_UP)
}
