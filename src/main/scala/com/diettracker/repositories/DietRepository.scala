package com.diettracker.repositories

import com.diettracker.domain.UserId
import com.diettracker.domain.sharing._
import doobie._
import doobie.hikari.HikariTransactor
import doobie.implicits._
import doobie.postgres.implicits._
import zio.{Task, ZLayer}
import zio.interop.catz._
import zio.json._
import zio.json.ast.Json

import java.time.OffsetDateTime
import java.util.UUID

trait DietRepository {
  def findById(dietId: DietId): Task[Option[DietRepository.DietRecord]]
  def createDiet(input: DietRepository.CreateDiet): Task[DietRepository.DietRecord]
}

object DietRepository {
  final case class DietRecord(
      id: DietId,
      ownerUserId: UserId,
      ownerName: Option[DietOwnerName],
      title: DietTitle,
      document: DietDocument,
      status: DietStatus,
      updatedAt: OffsetDateTime
  )

  final case class CreateDiet(
      id: DietId = DietId(UUID.randomUUID()),
      ownerUserId: UserId,
      ownerName: Option[DietOwnerName],
      title: DietTitle,
      document: DietDocument,
      status: DietStatus = DietStatus.Active
  )

  val live: ZLayer[HikariTransactor[Task], Nothing, DietRepository] =
    ZLayer.fromFunction(new DietRepositoryLive(_))
}

private final class DietRepositoryLive(xa: HikariTransactor[Task]) extends DietRepository {
  import DietRepository._

  private implicit val dietIdMeta: Meta[DietId]               = Meta[UUID].imap(DietId(_))(_.value)
  private implicit val userIdMeta: Meta[UserId]               = Meta[UUID].imap(UserId(_))(_.value)
  private implicit val dietStatusMeta: Meta[DietStatus]       =
    Meta[String]
      .imap(value => DietStatus.withNameInsensitiveOption(value).getOrElse(DietStatus.Active))(_.entryName)
  private implicit val dietTitleMeta: Meta[DietTitle]         = Meta[String].imap(DietTitle(_))(_.value)
  private implicit val dietOwnerNameMeta: Meta[DietOwnerName] =
    Meta[String].imap(DietOwnerName(_))(_.value)
  private implicit val dietDocumentMeta: Meta[DietDocument]   =
    Meta[String].imap(str => DietDocument(str.fromJson[Json].getOrElse(Json.Obj())))(doc => doc.value.toJson)

  override def findById(dietId: DietId): Task[Option[DietRecord]] =
    sql"""
          SELECT id,
                 owner_user_id,
                 owner_display_name,
                 title,
                 document::text,
                 status,
                 updated_at
            FROM diet
           WHERE id = $dietId
       """
      .query[DietRecord]
      .option
      .transact(xa)

  override def createDiet(input: CreateDiet): Task[DietRecord] =
    sql"""
          INSERT INTO diet (
            id,
            owner_user_id,
            owner_display_name,
            title,
            document,
            status
          ) VALUES (
            ${input.id},
            ${input.ownerUserId},
            ${input.ownerName},
            ${input.title},
            ${input.document}::jsonb,
            ${input.status}
          )
          RETURNING id,
                    owner_user_id,
                    owner_display_name,
                    title,
                    document::text,
                    status,
                    updated_at
       """
      .query[DietRecord]
      .unique
      .transact(xa)
}
