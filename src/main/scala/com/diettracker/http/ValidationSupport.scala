package com.diettracker.http

import sttp.tapir.{ValidationResult, Validator}
import zio._
import zio.json._

final case class FieldError(field: String, message: String)
object FieldError {
  implicit val codec: JsonCodec[FieldError] = DeriveJsonCodec.gen
}

final case class ValidationErrorResponse(errors: Chunk[FieldError])
object ValidationErrorResponse {
  implicit val codec: JsonCodec[ValidationErrorResponse] = DeriveJsonCodec.gen
}

object ValidationSupport {
  private def customValidator[T](message: String)(predicate: T => Boolean): Validator[T] =
    Validator.Custom[T]({ value =>
      if (predicate(value)) ValidationResult.Valid else ValidationResult.Invalid(message)
    })

  def nonBlank(field: String): Validator[String] =
    customValidator[String](s"$field must not be blank")(_.nonEmpty)

  def positiveDecimal(field: String): Validator[BigDecimal] =
    customValidator[BigDecimal](s"$field must be zero or positive")(_ >= BigDecimal(0))

  def errorResponse(errors: Chunk[FieldError]): ValidationErrorResponse = ValidationErrorResponse(errors)
}
