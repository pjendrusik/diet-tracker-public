package com.diettracker.services

sealed trait ServiceError extends Throwable { self: Throwable =>
  def message: String = getMessage
}

object ServiceError {
  final case class ValidationError(override val message: String) extends Exception(message) with ServiceError
  final case class NotFound(override val message: String)        extends Exception(message) with ServiceError
  final case class FeatureDisabled(override val message: String) extends Exception(message) with ServiceError
}
