package com.diettracker.domain

import sttp.tapir.Schema
import zio.Chunk
import zio.json.{DeriveJsonCodec, JsonCodec}
import java.time.OffsetDateTime
import java.util.UUID

final case class UserId(value: UUID) extends AnyVal
object UserId {
  implicit val codec: JsonCodec[UserId] =
    JsonCodec.uuid.transform(UserId(_), _.value)
  implicit val schema: Schema[UserId]   =
    Schema.schemaForUUID
      .map[UserId](uuid => Some(UserId(uuid)))(_.value)
}

final case class FoodId(value: UUID) extends AnyVal
object FoodId {
  implicit val codec: JsonCodec[FoodId] =
    JsonCodec.uuid.transform(FoodId(_), _.value)
  implicit val schema: Schema[FoodId]   =
    Schema.schemaForUUID
      .map[FoodId](uuid => Some(FoodId(uuid)))(_.value)
}

final case class IntakeLogId(value: UUID) extends AnyVal
object IntakeLogId {
  implicit val codec: JsonCodec[IntakeLogId] =
    JsonCodec.uuid.transform(IntakeLogId(_), _.value)
  implicit val schema: Schema[IntakeLogId]   =
    Schema.schemaForUUID
      .map[IntakeLogId](uuid => Some(IntakeLogId(uuid)))(_.value)
}

final case class NutritionSnapshotId(value: UUID) extends AnyVal
object NutritionSnapshotId {
  implicit val codec: JsonCodec[NutritionSnapshotId] =
    JsonCodec.uuid.transform(NutritionSnapshotId(_), _.value)
  implicit val schema: Schema[NutritionSnapshotId]   =
    Schema.schemaForUUID
      .map[NutritionSnapshotId](uuid => Some(NutritionSnapshotId(uuid)))(_.value)
}

final case class FoodName(value: String) extends AnyVal
object FoodName {
  implicit val codec: JsonCodec[FoodName] =
    JsonCodec.string.transform(FoodName(_), _.value)
  implicit val schema: Schema[FoodName]   =
    Schema.schemaForString
      .map[FoodName](value => Some(FoodName(value)))(_.value)
}

final case class FoodBrand(value: String) extends AnyVal
object FoodBrand {
  implicit val codec: JsonCodec[FoodBrand] =
    JsonCodec.string.transform(FoodBrand(_), _.value)
  implicit val schema: Schema[FoodBrand]   =
    Schema.schemaForString
      .map[FoodBrand](value => Some(FoodBrand(value)))(_.value)
}

final case class ServingUnit(value: String) extends AnyVal
object ServingUnit {
  implicit val codec: JsonCodec[ServingUnit] =
    JsonCodec.string.transform(ServingUnit(_), _.value)
  implicit val schema: Schema[ServingUnit]   =
    Schema.schemaForString
      .map[ServingUnit](value => Some(ServingUnit(value)))(_.value)
}

final case class ServingQuantity(value: BigDecimal) extends AnyVal
object ServingQuantity {
  implicit val codec: JsonCodec[ServingQuantity] =
    JsonCodec.bigDecimal.transform(
      value => ServingQuantity(scala.math.BigDecimal(value)),
      quantity => quantity.value.bigDecimal
    )
  implicit val schema: Schema[ServingQuantity]   =
    Schema.schemaForBigDecimal
      .map[ServingQuantity](value => Some(ServingQuantity(value)))(_.value)
}

final case class CalorieCount(value: BigDecimal) extends AnyVal
object CalorieCount {
  implicit val codec: JsonCodec[CalorieCount] =
    JsonCodec.bigDecimal.transform(
      value => CalorieCount(scala.math.BigDecimal(value)),
      calories => calories.value.bigDecimal
    )
  implicit val schema: Schema[CalorieCount]   =
    Schema.schemaForBigDecimal
      .map[CalorieCount](value => Some(CalorieCount(value)))(_.value)
}

final case class ProteinGrams(value: BigDecimal) extends AnyVal
object ProteinGrams {
  implicit val codec: JsonCodec[ProteinGrams] =
    JsonCodec.bigDecimal.transform(
      value => ProteinGrams(scala.math.BigDecimal(value)),
      protein => protein.value.bigDecimal
    )
  implicit val schema: Schema[ProteinGrams]   =
    Schema.schemaForBigDecimal
      .map[ProteinGrams](value => Some(ProteinGrams(value)))(_.value)
}

final case class CarbGrams(value: BigDecimal) extends AnyVal
object CarbGrams {
  implicit val codec: JsonCodec[CarbGrams] =
    JsonCodec.bigDecimal.transform(
      value => CarbGrams(scala.math.BigDecimal(value)),
      carbs => carbs.value.bigDecimal
    )
  implicit val schema: Schema[CarbGrams]   =
    Schema.schemaForBigDecimal
      .map[CarbGrams](value => Some(CarbGrams(value)))(_.value)
}

final case class FatGrams(value: BigDecimal) extends AnyVal
object FatGrams {
  implicit val codec: JsonCodec[FatGrams] =
    JsonCodec.bigDecimal.transform(
      value => FatGrams(scala.math.BigDecimal(value)),
      fat => fat.value.bigDecimal
    )
  implicit val schema: Schema[FatGrams]   =
    Schema.schemaForBigDecimal
      .map[FatGrams](value => Some(FatGrams(value)))(_.value)
}

final case class FoodNotes(value: String) extends AnyVal
object FoodNotes {
  implicit val codec: JsonCodec[FoodNotes] =
    JsonCodec.string.transform(FoodNotes(_), _.value)
  implicit val schema: Schema[FoodNotes]   =
    Schema.schemaForString
      .map[FoodNotes](value => Some(FoodNotes(value)))(_.value)
}

final case class LogNotes(value: String) extends AnyVal
object LogNotes {
  implicit val codec: JsonCodec[LogNotes] =
    JsonCodec.string.transform(LogNotes(_), _.value)
  implicit val schema: Schema[LogNotes]   =
    Schema.schemaForString
      .map[LogNotes](value => Some(LogNotes(value)))(_.value)
}

final case class FoodVersion(value: Int) extends AnyVal
object FoodVersion {
  implicit val codec: JsonCodec[FoodVersion] =
    JsonCodec.int.transform(FoodVersion(_), _.value)
  implicit val schema: Schema[FoodVersion]   =
    Schema.schemaForInt
      .map[FoodVersion](value => Some(FoodVersion(value)))(_.value)
}

final case class SummaryDate(value: String) extends AnyVal
object SummaryDate {
  implicit val codec: JsonCodec[SummaryDate] =
    JsonCodec.string.transform(SummaryDate(_), _.value)
  implicit val schema: Schema[SummaryDate]   =
    Schema.schemaForString
      .map[SummaryDate](value => Some(SummaryDate(value)))(_.value)
}

final case class MacroGoalStatus(value: Boolean) extends AnyVal
object MacroGoalStatus {
  implicit val codec: JsonCodec[MacroGoalStatus] =
    JsonCodec.boolean.transform(MacroGoalStatus(_), _.value)
  implicit val schema: Schema[MacroGoalStatus]   =
    Schema.schemaForBoolean
      .map[MacroGoalStatus](value => Some(MacroGoalStatus(value)))(_.value)
}

final case class FoodItem(
    id: FoodId,
    userId: UserId,
    name: FoodName,
    brand: Option[FoodBrand],
    defaultServingValue: ServingQuantity,
    defaultServingUnit: ServingUnit,
    caloriesPerServing: CalorieCount,
    macrosPerServing: Option[MacroBreakdown],
    macrosPer100g: Option[MacroBreakdown],
    notes: Option[FoodNotes],
    version: FoodVersion,
    createdAt: OffsetDateTime,
    updatedAt: OffsetDateTime
)

final case class MacroBreakdown(protein: ProteinGrams, carbs: CarbGrams, fat: FatGrams)
object MacroBreakdown {
  private final case class MacroBreakdownPayload(protein: BigDecimal, carbs: BigDecimal, fat: BigDecimal)

  implicit val codec: JsonCodec[MacroBreakdown] =
    DeriveJsonCodec
      .gen[MacroBreakdownPayload]
      .transform(
        payload =>
          MacroBreakdown(ProteinGrams(payload.protein), CarbGrams(payload.carbs), FatGrams(payload.fat)),
        breakdown =>
          MacroBreakdownPayload(breakdown.protein.value, breakdown.carbs.value, breakdown.fat.value)
      )

  implicit val schema: Schema[MacroBreakdown] =
    Schema
      .derived[MacroBreakdownPayload]
      .map[MacroBreakdown](payload =>
        Some(MacroBreakdown(ProteinGrams(payload.protein), CarbGrams(payload.carbs), FatGrams(payload.fat)))
      )(breakdown =>
        MacroBreakdownPayload(breakdown.protein.value, breakdown.carbs.value, breakdown.fat.value)
      )
}

final case class NutritionSnapshot(
    id: NutritionSnapshotId,
    intakeLogId: IntakeLogId,
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
    foodVersion: FoodVersion,
    createdAt: OffsetDateTime
)

final case class IntakeLogEntry(
    id: IntakeLogId,
    userId: UserId,
    foodId: FoodId,
    loggedAt: OffsetDateTime,
    quantity: ServingQuantity,
    unit: ServingUnit,
    notes: Option[LogNotes],
    deletedAt: Option[OffsetDateTime],
    createdAt: OffsetDateTime,
    updatedAt: OffsetDateTime,
    snapshot: NutritionSnapshot
)

final case class DailyIntakeSummary(
    userId: UserId,
    date: SummaryDate,
    entries: Chunk[IntakeLogEntry],
    totalCalories: CalorieCount,
    totalProtein: ProteinGrams,
    totalCarbs: CarbGrams,
    totalFat: FatGrams,
    macroCompleteness: MacroCompleteness
)

final case class MacroCompleteness(protein: MacroGoalStatus, carbs: MacroGoalStatus, fat: MacroGoalStatus)
