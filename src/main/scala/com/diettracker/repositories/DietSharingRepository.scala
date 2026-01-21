package com.diettracker.repositories

import com.diettracker.domain.UserId
import com.diettracker.domain.sharing._
import doobie._
import doobie.hikari.HikariTransactor
import doobie.implicits._
import doobie.postgres.implicits._
import zio.{Chunk, Task, ZLayer}
import zio.json._
import zio.interop.catz._

import java.sql.SQLException
import java.time.OffsetDateTime
import java.util.UUID
import scala.annotation.nowarn

trait DietSharingRepository {
  def createShare(input: DietSharingRepository.CreateShare): Task[DietShare]
  def listSharesForOwner(dietId: DietId, ownerUserId: UserId): Task[Chunk[DietShare]]
  def findShareById(shareId: DietShareId): Task[Option[DietShare]]
  def revokeShare(shareId: DietShareId, revokedBy: UserId, revokedAt: OffsetDateTime): Task[Boolean]
  def listSharedDietsForRecipient(recipientUserId: UserId): Task[Chunk[SharedDietSummary]]
  def fetchSharedDietView(shareId: DietShareId, recipientUserId: UserId): Task[Option[SharedDietView]]
}

object DietSharingRepository {
  final case class CreateShare(
      dietId: DietId,
      ownerUserId: UserId,
      recipientUserId: UserId
  )

  val live: ZLayer[HikariTransactor[Task], Nothing, DietSharingRepository] =
    ZLayer.fromFunction(new DietSharingRepositoryLive(_))
}

private final class DietSharingRepositoryLive(xa: HikariTransactor[Task]) extends DietSharingRepository {
  import DietSharingRepository._
  import DietSharingRepositoryLive._

  override def createShare(input: CreateShare): Task[DietShare] = insertShare(input).transact(xa)

  override def listSharesForOwner(dietId: DietId, ownerUserId: UserId): Task[Chunk[DietShare]] =
    selectSharesForOwner(dietId, ownerUserId)
      .transact(xa)
      .map(rows => Chunk.fromIterable(rows.map(_.toDomain)))

  override def findShareById(shareId: DietShareId): Task[Option[DietShare]] =
    selectShareById(shareId).transact(xa).map(_.map(_.toDomain))

  override def revokeShare(
      shareId: DietShareId,
      revokedBy: UserId,
      revokedAt: OffsetDateTime
  ): Task[Boolean] = markShareRevoked(shareId, revokedBy, revokedAt).transact(xa).map(_ > 0)

  override def listSharedDietsForRecipient(recipientUserId: UserId): Task[Chunk[SharedDietSummary]] =
    selectSharedSummaries(recipientUserId)
      .transact(xa)
      .map(rows => Chunk.fromIterable(rows.map(_.toDomain)))

  override def fetchSharedDietView(
      shareId: DietShareId,
      recipientUserId: UserId
  ): Task[Option[SharedDietView]] =
    selectSharedDietView(shareId, recipientUserId)
      .transact(xa)
      .map(_.map(_.toDomain))
}

private object DietSharingRepositoryLive {
  private implicit val dietShareIdMeta: Meta[DietShareId]   =
    Meta[UUID].imap(DietShareId(_))(_.value)
  private implicit val dietIdMeta: Meta[DietId]             =
    Meta[UUID].imap(DietId(_))(_.value)
  private implicit val userIdMeta: Meta[UserId]             =
    Meta[UUID].imap(UserId(_))(_.value)
  @nowarn("msg=never used")
  private implicit val statusMeta: Meta[DietShareStatus]    =
    Meta[String].imap(value =>
      DietShareStatus.withNameInsensitiveOption(value).getOrElse(DietShareStatus.Active)
    )(_.entryName)
  @nowarn("msg=never used")
  private implicit val dietTitleMeta: Meta[DietTitle]       =
    Meta[String].imap(DietTitle(_))(_.value)
  @nowarn("msg=never used")
  private implicit val ownerNameMeta: Meta[DietOwnerName]   =
    Meta[String].imap(DietOwnerName(_))(_.value)
  @nowarn("msg=never used")
  private implicit val dietDocumentMeta: Meta[DietDocument] =
    Meta[String].imap(str =>
      DietDocument(str.fromJson[zio.json.ast.Json].getOrElse(zio.json.ast.Json.Obj()))
    )(_.value.toJson)

  private final case class DietShareRow(
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
  ) {
    def toDomain: DietShare =
      DietShare(
        id = id,
        dietId = dietId,
        ownerUserId = ownerUserId,
        recipientUserId = recipientUserId,
        status = status,
        createdAt = createdAt,
        activatedAt = activatedAt,
        revokedAt = revokedAt,
        revokedBy = revokedBy,
        lastNotifiedAt = lastNotifiedAt
      )
  }

  private final case class SharedDietSummaryRow(
      id: DietShareId,
      dietId: DietId,
      ownerUserId: UserId,
      status: DietShareStatus,
      createdAt: OffsetDateTime,
      activatedAt: Option[OffsetDateTime],
      title: DietTitle,
      ownerName: Option[DietOwnerName],
      updatedAt: OffsetDateTime
  ) {
    def toDomain: SharedDietSummary =
      SharedDietSummary(
        dietShareId = id,
        dietId = dietId,
        title = title,
        ownerName = ownerName,
        status = status,
        updatedAt = activatedAt.orElse(Some(updatedAt)).getOrElse(createdAt)
      )
  }

  private final case class SharedDietViewRow(
      id: DietShareId,
      dietId: DietId,
      ownerUserId: UserId,
      status: DietShareStatus,
      ownerName: Option[DietOwnerName],
      document: DietDocument
  ) {
    def toDomain: SharedDietView =
      SharedDietView(
        dietShareId = id,
        dietId = dietId,
        ownerUserId = ownerUserId,
        ownerName = ownerName,
        diet = document,
        readOnly = ReadOnlyFlag(true)
      )
  }

  private def insertShare(input: DietSharingRepository.CreateShare): ConnectionIO[DietShare] =
    sql"""
          WITH inserted AS (
            INSERT INTO diet_share (
              diet_id,
              owner_user_id,
              recipient_user_id,
              status,
              activated_at
            )
            SELECT d.id,
                   ${input.ownerUserId},
                   ${input.recipientUserId},
                   'ACTIVE',
                   NOW()
              FROM diet d
             WHERE d.id = ${input.dietId}
               AND d.owner_user_id = ${input.ownerUserId}
               AND d.status = 'ACTIVE'
            RETURNING id,
                      diet_id,
                      owner_user_id,
                      recipient_user_id,
                      status,
                      created_at,
                      activated_at,
                      revoked_at,
                      revoked_by,
                      last_notified_at
          )
          SELECT *
            FROM inserted
       """
      .query[DietShareRow]
      .option
      .flatMap {
        case Some(row) => FC.pure(row.toDomain)
        case None      =>
          FC.raiseError(
            new SQLException("DIET_NOT_SHAREABLE", "P0001")
          )
      }

  private def selectSharesForOwner(dietId: DietId, ownerUserId: UserId): ConnectionIO[List[DietShareRow]] =
    sql"""
          SELECT id,
                 diet_id,
                 owner_user_id,
                 recipient_user_id,
                 status,
                 created_at,
                 activated_at,
                 revoked_at,
                 revoked_by,
                 last_notified_at
            FROM diet_share
           WHERE diet_id = $dietId
             AND owner_user_id = $ownerUserId
           ORDER BY created_at DESC
       """
      .query[DietShareRow]
      .to[List]

  private def selectShareById(shareId: DietShareId): ConnectionIO[Option[DietShareRow]] =
    sql"""
          SELECT id,
                 diet_id,
                 owner_user_id,
                 recipient_user_id,
                 status,
                 created_at,
                 activated_at,
                 revoked_at,
                 revoked_by,
                 last_notified_at
            FROM diet_share
           WHERE id = $shareId
       """
      .query[DietShareRow]
      .option

  private def markShareRevoked(
      shareId: DietShareId,
      revokedBy: UserId,
      revokedAt: OffsetDateTime
  ): ConnectionIO[Int] =
    sql"""
          UPDATE diet_share
             SET status = 'REVOKED',
                 revoked_at = $revokedAt,
                 revoked_by = $revokedBy
           WHERE id = $shareId
             AND status <> 'REVOKED'
       """.update.run

  private def selectSharedSummaries(recipientId: UserId): ConnectionIO[List[SharedDietSummaryRow]] =
    sql"""
          SELECT s.id,
                 s.diet_id,
                 s.owner_user_id,
                 s.status,
                 s.created_at,
                 s.activated_at,
                 d.title,
                 d.owner_display_name,
                 d.updated_at
            FROM diet_share s
            JOIN diet d
              ON d.id = s.diet_id
           WHERE s.recipient_user_id = $recipientId
             AND s.status = 'ACTIVE'
             AND d.status = 'ACTIVE'
       """
      .query[SharedDietSummaryRow]
      .to[List]

  private def selectSharedDietView(
      shareId: DietShareId,
      recipientId: UserId
  ): ConnectionIO[Option[SharedDietViewRow]] =
    sql"""
          SELECT s.id,
                 s.diet_id,
                 s.owner_user_id,
                 s.status,
                 d.owner_display_name,
                 d.document::text
            FROM diet_share s
            JOIN diet d
              ON d.id = s.diet_id
           WHERE s.id = $shareId
             AND s.recipient_user_id = $recipientId
             AND s.status = 'ACTIVE'
             AND d.status = 'ACTIVE'
       """
      .query[SharedDietViewRow]
      .option
}
