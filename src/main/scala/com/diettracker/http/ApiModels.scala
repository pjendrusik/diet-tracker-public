package com.diettracker.http

import com.diettracker.domain._
import com.diettracker.domain.sharing._
import sttp.tapir.Schema
import zio.json._

import java.time.{LocalDate, OffsetDateTime}

final case class ApiError(code: String, message: String)
object ApiError          {
  implicit val codec: JsonCodec[ApiError] = DeriveJsonCodec.gen
  implicit val schema: Schema[ApiError]   = Schema.derived
}

final case class CreateFoodPayload(
    name: FoodName,
    brand: Option[FoodBrand],
    defaultServingValue: ServingQuantity,
    defaultServingUnit: ServingUnit,
    caloriesPerServing: CalorieCount,
    macrosPerServing: Option[MacroBreakdown],
    macrosPer100g: Option[MacroBreakdown],
    notes: Option[FoodNotes]
)
object CreateFoodPayload {
  implicit val codec: JsonCodec[CreateFoodPayload] = DeriveJsonCodec.gen
  implicit val schema: Schema[CreateFoodPayload]   = Schema.derived
}

final case class FoodResponse(
    id: FoodId,
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
object FoodResponse      {
  implicit val codec: JsonCodec[FoodResponse] = DeriveJsonCodec.gen
  implicit val schema: Schema[FoodResponse]   = Schema.derived

  def fromDomain(food: FoodItem): FoodResponse =
    FoodResponse(
      id = food.id,
      name = food.name,
      brand = food.brand,
      defaultServingValue = food.defaultServingValue,
      defaultServingUnit = food.defaultServingUnit,
      caloriesPerServing = food.caloriesPerServing,
      macrosPerServing = food.macrosPerServing,
      macrosPer100g = food.macrosPer100g,
      notes = food.notes,
      version = food.version,
      createdAt = food.createdAt,
      updatedAt = food.updatedAt
    )
}

final case class FoodSearchResponse(items: List[FoodResponse])
object FoodSearchResponse       {
  implicit val codec: JsonCodec[FoodSearchResponse] = DeriveJsonCodec.gen
  implicit val schema: Schema[FoodSearchResponse]   = Schema.derived
}

final case class LogCreateRequest(
    foodId: FoodId,
    loggedAt: OffsetDateTime,
    quantity: ServingQuantity,
    unit: Option[ServingUnit],
    notes: Option[LogNotes]
)
object LogCreateRequest         {
  implicit val codec: JsonCodec[LogCreateRequest] = DeriveJsonCodec.gen
  implicit val schema: Schema[LogCreateRequest]   = Schema.derived
}

final case class LogEditRequest(
    loggedAt: Option[OffsetDateTime],
    quantity: Option[ServingQuantity],
    unit: Option[ServingUnit],
    notes: Option[LogNotes]
)
object LogEditRequest           {
  implicit val codec: JsonCodec[LogEditRequest] = DeriveJsonCodec.gen
  implicit val schema: Schema[LogEditRequest]   = Schema.derived
}

final case class NutritionSnapshotPayload(
    calories: CalorieCount,
    protein: ProteinGrams,
    carbs: CarbGrams,
    fat: FatGrams,
    servingValue: ServingQuantity,
    servingUnit: ServingUnit,
    quantityLogged: ServingQuantity,
    foodVersion: FoodVersion
)
object NutritionSnapshotPayload {
  implicit val codec: JsonCodec[NutritionSnapshotPayload] = DeriveJsonCodec.gen
  implicit val schema: Schema[NutritionSnapshotPayload]   = Schema.derived

  def fromDomain(snapshot: NutritionSnapshot): NutritionSnapshotPayload =
    NutritionSnapshotPayload(
      calories = snapshot.calories,
      protein = snapshot.protein,
      carbs = snapshot.carbs,
      fat = snapshot.fat,
      servingValue = snapshot.servingValue,
      servingUnit = snapshot.servingUnit,
      quantityLogged = snapshot.quantityLogged,
      foodVersion = snapshot.foodVersion
    )
}

final case class LogEntryResponse(
    id: IntakeLogId,
    foodId: FoodId,
    loggedAt: OffsetDateTime,
    quantity: ServingQuantity,
    unit: ServingUnit,
    notes: Option[LogNotes],
    snapshot: NutritionSnapshotPayload
)
object LogEntryResponse         {
  implicit val codec: JsonCodec[LogEntryResponse] = DeriveJsonCodec.gen
  implicit val schema: Schema[LogEntryResponse]   = Schema.derived

  def fromDomain(entry: IntakeLogEntry): LogEntryResponse =
    LogEntryResponse(
      id = entry.id,
      foodId = entry.foodId,
      loggedAt = entry.loggedAt,
      quantity = entry.quantity,
      unit = entry.unit,
      notes = entry.notes,
      snapshot = NutritionSnapshotPayload.fromDomain(entry.snapshot)
    )
}

final case class DailyLogsResponse(date: LocalDate, entries: List[LogEntryResponse])
object DailyLogsResponse        {
  implicit val codec: JsonCodec[DailyLogsResponse] = DeriveJsonCodec.gen
  implicit val schema: Schema[DailyLogsResponse]   = Schema.derived
}

final case class MacroCompletenessPayload(
    protein: MacroGoalStatus,
    carbs: MacroGoalStatus,
    fat: MacroGoalStatus
)
object MacroCompletenessPayload {
  implicit val codec: JsonCodec[MacroCompletenessPayload] = DeriveJsonCodec.gen
  implicit val schema: Schema[MacroCompletenessPayload]   = Schema.derived
}

final case class DailySummaryTotals(
    calories: CalorieCount,
    protein: ProteinGrams,
    carbs: CarbGrams,
    fat: FatGrams,
    macroCompleteness: MacroCompletenessPayload
)
object DailySummaryTotals       {
  implicit val codec: JsonCodec[DailySummaryTotals] = DeriveJsonCodec.gen
  implicit val schema: Schema[DailySummaryTotals]   = Schema.derived
}

final case class DailySummaryResponse(
    date: SummaryDate,
    entries: List[LogEntryResponse],
    totals: DailySummaryTotals
)
object DailySummaryResponse     {
  implicit val codec: JsonCodec[DailySummaryResponse] = DeriveJsonCodec.gen
  implicit val schema: Schema[DailySummaryResponse]   = Schema.derived
}

final case class ShareDietPayload(recipientUserId: UserId)
object ShareDietPayload  {
  implicit val codec: JsonCodec[ShareDietPayload] = DeriveJsonCodec.gen
  implicit val schema: Schema[ShareDietPayload]   = Schema.derived
}

final case class DietShareResponse(
    id: DietShareId,
    dietId: DietId,
    ownerUserId: UserId,
    recipientUserId: UserId,
    status: DietShareStatus,
    createdAt: OffsetDateTime,
    activatedAt: Option[OffsetDateTime],
    revokedAt: Option[OffsetDateTime]
)
object DietShareResponse {
  implicit val codec: JsonCodec[DietShareResponse] = DeriveJsonCodec.gen
  implicit val schema: Schema[DietShareResponse]   = Schema.derived

  def fromDomain(share: DietShare): DietShareResponse =
    DietShareResponse(
      id = share.id,
      dietId = share.dietId,
      ownerUserId = share.ownerUserId,
      recipientUserId = share.recipientUserId,
      status = share.status,
      createdAt = share.createdAt,
      activatedAt = share.activatedAt,
      revokedAt = share.revokedAt
    )
}

final case class DietShareListResponse(shares: List[DietShareResponse])
object DietShareListResponse    {
  implicit val codec: JsonCodec[DietShareListResponse] = DeriveJsonCodec.gen
  implicit val schema: Schema[DietShareListResponse]   = Schema.derived
}

final case class SharedDietSummaryPayload(
    dietShareId: DietShareId,
    dietId: DietId,
    title: DietTitle,
    ownerName: Option[DietOwnerName],
    status: DietShareStatus,
    updatedAt: OffsetDateTime
)
object SharedDietSummaryPayload {
  implicit val codec: JsonCodec[SharedDietSummaryPayload] = DeriveJsonCodec.gen
  implicit val schema: Schema[SharedDietSummaryPayload]   = Schema.derived

  def fromDomain(summary: SharedDietSummary): SharedDietSummaryPayload =
    SharedDietSummaryPayload(
      dietShareId = summary.dietShareId,
      dietId = summary.dietId,
      title = summary.title,
      ownerName = summary.ownerName,
      status = summary.status,
      updatedAt = summary.updatedAt
    )
}

final case class SharedDietListResponse(sharedDiets: List[SharedDietSummaryPayload])
object SharedDietListResponse {
  implicit val codec: JsonCodec[SharedDietListResponse] = DeriveJsonCodec.gen
  implicit val schema: Schema[SharedDietListResponse]   = Schema.derived
}

final case class SharedDietViewResponse(
    dietShareId: DietShareId,
    dietId: DietId,
    ownerUserId: UserId,
    ownerName: Option[DietOwnerName],
    readOnly: ReadOnlyFlag,
    diet: DietDocument
)
object SharedDietViewResponse {
  implicit val codec: JsonCodec[SharedDietViewResponse] = DeriveJsonCodec.gen
  implicit val schema: Schema[SharedDietViewResponse]   = {
    implicit val dietDocumentSchema: Schema[DietDocument] = Schema.string
    val derived                                           = Schema.derived[SharedDietViewResponse]
    val _                                                 = dietDocumentSchema
    derived
  }

  def fromDomain(view: SharedDietView): SharedDietViewResponse =
    SharedDietViewResponse(
      dietShareId = view.dietShareId,
      dietId = view.dietId,
      ownerUserId = view.ownerUserId,
      ownerName = view.ownerName,
      readOnly = view.readOnly,
      diet = view.diet
    )
}

final case class DietCopyResponse(
    dietCopyId: DietCopyId,
    sourceShareId: Option[DietShareId],
    newDietId: DietId,
    sourceDietId: DietId,
    recipientUserId: UserId,
    copiedAt: OffsetDateTime
)
object DietCopyResponse       {
  implicit val codec: JsonCodec[DietCopyResponse] = DeriveJsonCodec.gen
  implicit val schema: Schema[DietCopyResponse]   = Schema.derived

  def fromDomain(copy: DietCopy): DietCopyResponse =
    DietCopyResponse(
      dietCopyId = copy.id,
      sourceShareId = copy.sourceShareId,
      newDietId = copy.newDietId,
      recipientUserId = copy.recipientUserId,
      sourceDietId = copy.sourceDietId,
      copiedAt = copy.copiedAt
    )
}
