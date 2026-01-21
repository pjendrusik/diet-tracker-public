package com.diettracker.services.validators

import com.diettracker.domain.UserId
import com.diettracker.domain.sharing.{DietId, DietStatus}
import com.diettracker.repositories.DietRepository
import com.diettracker.services.ServiceError
import com.diettracker.services.ServiceError.{NotFound, ValidationError}
import zio._

trait DietStatusValidator {
  def ensureShareable(dietId: DietId, ownerId: UserId): IO[ServiceError, Unit]
  def ensureCopyable(dietId: DietId): IO[ServiceError, Unit]
}

object DietStatusValidator {
  val live: ZLayer[DietRepository, Nothing, DietStatusValidator] =
    ZLayer.fromFunction(new DietStatusValidatorLive(_))
}

final class DietStatusValidatorLive(dietRepository: DietRepository) extends DietStatusValidator {

  override def ensureShareable(dietId: DietId, ownerId: UserId): IO[ServiceError, Unit] =
    validate(dietId) {
      case Some(record) if record.status != DietStatus.Active =>
        ZIO.fail(ValidationError(s"Diet ${dietId.value} is not active and cannot be shared"))
      case Some(record) if record.ownerUserId != ownerId      =>
        ZIO.fail(ValidationError("You do not own this diet"))
      case Some(_)                                            =>
        ZIO.unit
      case None                                               =>
        ZIO.fail(NotFound(s"Diet ${dietId.value} not found"))
    }

  override def ensureCopyable(dietId: DietId): IO[ServiceError, Unit] =
    validate(dietId) {
      case Some(record) if record.status != DietStatus.Active =>
        ZIO.fail(ValidationError(s"Diet ${dietId.value} is not active and cannot be copied"))
      case Some(_)                                            =>
        ZIO.unit
      case None                                               =>
        ZIO.fail(NotFound(s"Diet ${dietId.value} not found"))
    }

  private def validate(
      dietId: DietId
  )(f: Option[DietRepository.DietRecord] => IO[ServiceError, Unit]): IO[ServiceError, Unit] =
    dietRepository
      .findById(dietId)
      .mapError(error => ValidationError(error.getMessage))
      .flatMap(f)
}
