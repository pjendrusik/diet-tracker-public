package com.diettracker.repositories

import com.diettracker.domain.UserId
import com.diettracker.domain.sharing._
import doobie._
import doobie.hikari.HikariTransactor
import doobie.implicits._
import doobie.postgres.implicits._
import zio.{Chunk, Task, ZLayer}
import zio.interop.catz._
import zio.json._

import java.sql.SQLException
import java.time.OffsetDateTime
import java.util.UUID
import scala.annotation.nowarn

trait DietCopyRepository {
  def createCopy(input: DietCopyRepository.CreateCopy): Task[DietCopy]
  def listCopiesForRecipient(recipientUserId: UserId): Task[Chunk[DietCopy]]
  def cloneDietFromShare(input: DietCopyRepository.CloneDietFromShare): Task[DietCopy]
}

object DietCopyRepository {
  final case class CreateCopy(
      sourceDietId: DietId,
      sourceShareId: Option[DietShareId],
      newDietId: DietId,
      recipientUserId: UserId,
      copiedAt: OffsetDateTime
  )

  final case class CloneDietFromShare(
      sourceDietId: DietId,
      sourceShareId: Option[DietShareId],
      newDietId: DietId,
      recipientUserId: UserId,
      copiedAt: OffsetDateTime
  )

  val live: ZLayer[HikariTransactor[Task], Nothing, DietCopyRepository] =
    ZLayer.fromFunction(new DietCopyRepositoryLive(_))
}

private final class DietCopyRepositoryLive(xa: HikariTransactor[Task]) extends DietCopyRepository {
  import DietCopyRepository._
  private implicit val dietIdMeta: Meta[DietId]               = Meta[UUID].imap(DietId(_))(_.value)
  private implicit val dietShareIdMeta: Meta[DietShareId]     = Meta[UUID].imap(DietShareId(_))(_.value)
  private implicit val userIdMeta: Meta[UserId]               = Meta[UUID].imap(UserId(_))(_.value)
  @nowarn("msg=never used")
  private implicit val dietCopyIdMeta: Meta[DietCopyId]       = Meta[UUID].imap(DietCopyId(_))(_.value)
  @nowarn("msg=never used")
  private implicit val dietTitleMeta: Meta[DietTitle]         = Meta[String].imap(DietTitle(_))(_.value)
  @nowarn("msg=never used")
  private implicit val dietOwnerNameMeta: Meta[DietOwnerName] =
    Meta[String].imap(DietOwnerName(_))(_.value)
  @nowarn("msg=never used")
  private implicit val dietDocumentMeta: Meta[DietDocument]   =
    Meta[String].imap(str =>
      DietDocument(str.fromJson[zio.json.ast.Json].getOrElse(zio.json.ast.Json.Obj()))
    )(_.value.toJson)

  override def createCopy(input: CreateCopy): Task[DietCopy] = insertCopy(input).transact(xa)

  override def listCopiesForRecipient(recipientUserId: UserId): Task[Chunk[DietCopy]] =
    selectCopies(recipientUserId)
      .transact(xa)
      .map(rows => Chunk.fromIterable(rows))

  override def cloneDietFromShare(input: CloneDietFromShare): Task[DietCopy] =
    cloneDietTransaction(input).transact(xa)

  private final case class DietSnapshot(
      id: DietId,
      ownerName: Option[DietOwnerName],
      title: DietTitle,
      document: DietDocument
  )

  private def insertCopy(input: CreateCopy): ConnectionIO[DietCopy] =
    sql"""
          INSERT INTO diet_copy (
            source_diet_id,
            source_share_id,
            new_diet_id,
            recipient_user_id,
            copied_at
          ) VALUES (
            ${input.sourceDietId},
            ${input.sourceShareId},
            ${input.newDietId},
            ${input.recipientUserId},
            ${input.copiedAt}
          )
          RETURNING id,
                    source_diet_id,
                    source_share_id,
                    new_diet_id,
                    recipient_user_id,
                    copied_at
       """
      .query[DietCopy]
      .unique

  private def selectCopies(recipientId: UserId): ConnectionIO[List[DietCopy]] =
    sql"""
          SELECT id,
                 source_diet_id,
                 source_share_id,
                 new_diet_id,
                 recipient_user_id,
                 copied_at
            FROM diet_copy
           WHERE recipient_user_id = $recipientId
        """
      .query[DietCopy]
      .to[List]

  private def cloneDietTransaction(input: CloneDietFromShare): ConnectionIO[DietCopy] =
    for {
      snapshot <- lockSourceDiet(input.sourceDietId)
      _        <- sql"""
            INSERT INTO diet (
              id,
              owner_user_id,
              owner_display_name,
              title,
              document,
              status
            )
            VALUES (
              ${input.newDietId},
              ${input.recipientUserId},
              ${snapshot.ownerName},
              ${snapshot.title},
              ${snapshot.document}::jsonb,
              'ACTIVE'
            )
          """.update.run
      copy     <- insertCopy(
                    CreateCopy(
                      sourceDietId = input.sourceDietId,
                      sourceShareId = input.sourceShareId,
                      newDietId = input.newDietId,
                      recipientUserId = input.recipientUserId,
                      copiedAt = input.copiedAt
                    )
                  )
    } yield copy

  private def lockSourceDiet(sourceDietId: DietId): ConnectionIO[DietSnapshot] =
    sql"""
          SELECT id,
                 owner_display_name,
                 title,
                 document::text
            FROM diet
           WHERE id = $sourceDietId
             AND status = 'ACTIVE'
           FOR UPDATE
       """
      .query[DietSnapshot]
      .option
      .flatMap {
        case Some(row) => FC.pure(row)
        case None      =>
          FC.raiseError(
            new SQLException("DIET_NOT_COPYABLE", "P0001")
          )
      }
}
