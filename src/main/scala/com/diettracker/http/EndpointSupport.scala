package com.diettracker.http

import com.diettracker.domain._
import com.diettracker.domain.sharing.{DietId, DietShareId}
import com.diettracker.services.ServiceError
import sttp.model.StatusCode
import sttp.tapir.Codec.PlainCodec
import sttp.tapir._
import sttp.tapir.json.zio._
import java.util.UUID

object EndpointSupport {
  type ApiFailure = (StatusCode, ApiError)

  val errorOutput: EndpointOutput[ApiFailure] = statusCode.and(jsonBody[ApiError])

  implicit val userIdCodec: PlainCodec[UserId]           = Codec.uuid.map(UserId(_))(_.value)
  implicit val foodIdCodec: PlainCodec[FoodId]           = Codec.uuid.map(FoodId(_))(_.value)
  implicit val intakeLogIdCodec: PlainCodec[IntakeLogId] = Codec.uuid.map(IntakeLogId(_))(_.value)
  implicit val dietIdCodec: PlainCodec[DietId]           = Codec.uuid.map(DietId(_))(_.value)
  implicit val dietShareIdCodec: PlainCodec[DietShareId] = Codec.uuid.map(DietShareId(_))(_.value)

  val userIdHeader: EndpointInput[UserId] =
    header[String]("X-User-Id").mapDecode { raw =>
      try DecodeResult.Value(UserId(UUID.fromString(raw)))
      catch {
        case _: IllegalArgumentException =>
          DecodeResult.Error(raw, new RuntimeException("Invalid X-User-Id header"))
      }
    }(_.value.toString)

  def toFailure(error: ServiceError): ApiFailure =
    error match {
      case e: ServiceError.ValidationError => StatusCode.BadRequest -> ApiError("validation_error", e.message)
      case e: ServiceError.NotFound        => StatusCode.NotFound   -> ApiError("not_found", e.message)
      case e: ServiceError.FeatureDisabled => StatusCode.Forbidden  -> ApiError("feature_disabled", e.message)
    }
}
