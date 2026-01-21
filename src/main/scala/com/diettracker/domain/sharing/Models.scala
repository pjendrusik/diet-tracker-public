package com.diettracker.domain.sharing

import com.diettracker.domain.UserId
import enumeratum.EnumEntry.UpperSnakecase
import enumeratum.{Enum, EnumEntry}
import sttp.tapir.{Schema, Validator}
import zio.json._
import zio.json.ast.Json

import java.time.OffsetDateTime
import java.util.UUID

final case class DietShareId(value: UUID) extends AnyVal
object DietShareId {
  implicit val codec: JsonCodec[DietShareId] =
    JsonCodec.uuid.transform(DietShareId(_), _.value)
  implicit val schema: Schema[DietShareId]   =
    Schema.schemaForUUID
      .map[DietShareId](uuid => Some(DietShareId(uuid)))(_.value)
}

final case class DietCopyId(value: UUID) extends AnyVal
object DietCopyId {
  implicit val codec: JsonCodec[DietCopyId] =
    JsonCodec.uuid.transform(DietCopyId(_), _.value)
  implicit val schema: Schema[DietCopyId]   =
    Schema.schemaForUUID
      .map[DietCopyId](uuid => Some(DietCopyId(uuid)))(_.value)
}

final case class DietId(value: UUID) extends AnyVal
object DietId {
  implicit val codec: JsonCodec[DietId] =
    JsonCodec.uuid.transform(DietId(_), _.value)
  implicit val schema: Schema[DietId]   =
    Schema.schemaForUUID
      .map[DietId](uuid => Some(DietId(uuid)))(_.value)
}

final case class DietTitle(value: String) extends AnyVal
object DietTitle {
  implicit val codec: JsonCodec[DietTitle] =
    JsonCodec.string.transform(DietTitle(_), _.value)
  implicit val schema: Schema[DietTitle]   =
    Schema.schemaForString
      .map[DietTitle](value => Some(DietTitle(value)))(_.value)
}

final case class DietOwnerName(value: String) extends AnyVal
object DietOwnerName {
  implicit val codec: JsonCodec[DietOwnerName] =
    JsonCodec.string.transform(DietOwnerName(_), _.value)
  implicit val schema: Schema[DietOwnerName]   =
    Schema.schemaForString
      .map[DietOwnerName](value => Some(DietOwnerName(value)))(_.value)
}

final case class ReadOnlyFlag(value: Boolean) extends AnyVal
object ReadOnlyFlag {
  implicit val codec: JsonCodec[ReadOnlyFlag] =
    JsonCodec.boolean.transform(ReadOnlyFlag(_), _.value)
  implicit val schema: Schema[ReadOnlyFlag]   =
    Schema.schemaForBoolean
      .map[ReadOnlyFlag](value => Some(ReadOnlyFlag(value)))(_.value)
}

final case class DietDocument(value: Json) extends AnyVal
object DietDocument {
  implicit val codec: JsonCodec[DietDocument] =
    JsonCodec(
      implicitly[JsonEncoder[Json]].contramap[DietDocument](_.value),
      implicitly[JsonDecoder[Json]].map(DietDocument(_))
    )
  implicit val schema: Schema[DietDocument]   =
    Schema.schemaForString
      .description("Serialized diet payload encoded as JSON")
      .map[DietDocument](value => value.fromJson[Json].toOption.map(DietDocument(_)))(doc => doc.value.toJson)
}

sealed trait DietStatus extends EnumEntry with UpperSnakecase
object DietStatus       extends Enum[DietStatus] {
  case object Active   extends DietStatus
  case object Archived extends DietStatus

  val values: IndexedSeq[DietStatus] = findValues

  implicit val codec: JsonCodec[DietStatus] =
    JsonCodec.string.transformOrFail(
      value => withNameInsensitiveOption(value).toRight(s"Unknown diet status: $value"),
      status => status.entryName
    )

  implicit val schema: Schema[DietStatus] =
    Schema.string
      .validate(Validator.enumeration(values.toList, v => Some(v.entryName)))
}

sealed trait DietShareStatus extends EnumEntry with UpperSnakecase
object DietShareStatus       extends Enum[DietShareStatus] {
  case object Pending extends DietShareStatus
  case object Active  extends DietShareStatus
  case object Revoked extends DietShareStatus

  val values: IndexedSeq[DietShareStatus] = findValues

  implicit val codec: JsonCodec[DietShareStatus] =
    JsonCodec.string.transformOrFail(
      value => withNameInsensitiveOption(value).toRight(s"Unknown diet share status: $value"),
      status => status.entryName
    )

  implicit val schema: Schema[DietShareStatus] =
    Schema.string
      .validate(Validator.enumeration(values.toList, v => Some(v.entryName)))
}

final case class DietShare(
    id: DietShareId,
    dietId: DietId,
    ownerUserId: UserId,
    recipientUserId: UserId,
    status: DietShareStatus,
    createdAt: OffsetDateTime,
    activatedAt: Option[OffsetDateTime],
    revokedAt: Option[OffsetDateTime],
    revokedBy: Option[UserId],
    lastNotifiedAt: Option[OffsetDateTime]
)
object DietShare {
  implicit val codec: JsonCodec[DietShare] = DeriveJsonCodec.gen
}

final case class DietCopy(
    id: DietCopyId,
    sourceDietId: DietId,
    sourceShareId: Option[DietShareId],
    newDietId: DietId,
    recipientUserId: UserId,
    copiedAt: OffsetDateTime
)
object DietCopy {
  implicit val codec: JsonCodec[DietCopy] = DeriveJsonCodec.gen
}

final case class SharedDietSummary(
    dietShareId: DietShareId,
    dietId: DietId,
    title: DietTitle,
    ownerName: Option[DietOwnerName],
    status: DietShareStatus,
    updatedAt: OffsetDateTime
)
object SharedDietSummary {
  implicit val codec: JsonCodec[SharedDietSummary] = DeriveJsonCodec.gen
}

final case class SharedDietView(
    dietShareId: DietShareId,
    dietId: DietId,
    ownerUserId: UserId,
    ownerName: Option[DietOwnerName],
    diet: DietDocument,
    readOnly: ReadOnlyFlag = ReadOnlyFlag(true)
)
object SharedDietView {
  implicit val codec: JsonCodec[SharedDietView] = DeriveJsonCodec.gen
}
