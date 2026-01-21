package com.diettracker.http

import EndpointSupport._
import com.diettracker.domain.UserId
import com.diettracker.domain.sharing.{DietId, DietShareId}
import com.diettracker.http.{
  DietCopyResponse,
  DietShareListResponse,
  DietShareResponse,
  ShareDietPayload,
  SharedDietListResponse,
  SharedDietSummaryPayload,
  SharedDietViewResponse
}
import com.diettracker.services.DietSharingService
import com.diettracker.services.DietSharingService._
import sttp.tapir._
import sttp.tapir.json.zio._
import sttp.tapir.server.ServerEndpoint
import zio._

object DietSharingRoutes {
  private val ownerTag     = "Diet Sharing"
  private val recipientTag = "Shared Diets"
  val createShareEndpoint
      : PublicEndpoint[(DietId, UserId, ShareDietPayload), ApiFailure, DietShareResponse, Any] =
    endpoint.post
      .in("diets" / path[DietId]("dietId") / "shares")
      .in(userIdHeader)
      .in(jsonBody[ShareDietPayload])
      .tag(ownerTag)
      .name("Share Diet")
      .summary("Share a diet with another user")
      .description("Allows a diet owner to grant read-only access to another user.")
      .out(jsonBody[DietShareResponse])
      .errorOut(errorOutput)

  val listSharesEndpoint: PublicEndpoint[(DietId, UserId), ApiFailure, DietShareListResponse, Any] =
    endpoint.get
      .in("diets" / path[DietId]("dietId") / "shares")
      .in(userIdHeader)
      .tag(ownerTag)
      .name("List Diet Shares")
      .summary("List all users a diet is shared with")
      .description(
        "Returns the recipients that have access to the specified diet, including their current share status."
      )
      .out(jsonBody[DietShareListResponse])
      .errorOut(errorOutput)

  val revokeShareEndpoint: PublicEndpoint[(DietId, DietShareId, UserId), ApiFailure, Unit, Any] =
    endpoint.delete
      .in("diets" / path[DietId]("dietId") / "shares" / path[DietShareId]("shareId"))
      .in(userIdHeader)
      .tag(ownerTag)
      .name("Revoke Diet Share")
      .summary("Revoke access for a shared diet")
      .description("Revokes a recipient's read-only access and invalidates their session immediately.")
      .out(emptyOutput)
      .errorOut(errorOutput)

  val listRecipientEndpoint: PublicEndpoint[UserId, ApiFailure, SharedDietListResponse, Any] =
    endpoint.get
      .in("me" / "shared-diets")
      .in(userIdHeader)
      .tag(recipientTag)
      .name("List Shared Diets")
      .summary("List diets shared with the current user")
      .description("Shows diets shared with the authenticated user, including owner metadata and status.")
      .out(jsonBody[SharedDietListResponse])
      .errorOut(errorOutput)

  val viewRecipientEndpoint: PublicEndpoint[(DietShareId, UserId), ApiFailure, SharedDietViewResponse, Any] =
    endpoint.get
      .in("me" / "shared-diets" / path[DietShareId]("shareId"))
      .in(userIdHeader)
      .tag(recipientTag)
      .name("View Shared Diet")
      .summary("Open a shared diet in read-only mode")
      .description("Returns diet content and indicates it cannot be edited by the recipient.")
      .out(jsonBody[SharedDietViewResponse])
      .errorOut(errorOutput)

  val copySharedDietEndpoint: PublicEndpoint[(DietShareId, UserId), ApiFailure, DietCopyResponse, Any] =
    endpoint.post
      .in("me" / "shared-diets" / path[DietShareId]("shareId") / "copy")
      .in(userIdHeader)
      .tag(recipientTag)
      .name("Copy Shared Diet")
      .summary("Copy a shared diet into the recipient's workspace")
      .description(
        "Creates an independent copy of the shared diet for the recipient to edit without affecting the original."
      )
      .out(jsonBody[DietCopyResponse])
      .errorOut(errorOutput)

  def routes(service: DietSharingService): List[ServerEndpoint[Any, Task]] =
    List(
      createRoute(service),
      listRoute(service),
      revokeRoute(service),
      listRecipientRoute(service),
      viewRecipientRoute(service),
      copyRoute(service)
    )

  private def createRoute(service: DietSharingService): ServerEndpoint[Any, Task] =
    createShareEndpoint.serverLogic { case (dietId, ownerId, payload) =>
      service
        .shareDiet(
          ShareDietCommand(
            dietId = dietId,
            ownerUserId = ownerId,
            recipientUserId = payload.recipientUserId
          )
        )
        .map(share => Right(DietShareResponse.fromDomain(share)))
        .catchAll(error => ZIO.succeed(Left(toFailure(error))))
    }

  private def listRoute(service: DietSharingService): ServerEndpoint[Any, Task] =
    listSharesEndpoint.serverLogic { case (dietId, ownerId) =>
      service
        .listShares(ListSharesQuery(dietId, ownerId))
        .map(shares => Right(DietShareListResponse(shares.map(DietShareResponse.fromDomain).toList)))
        .catchAll(error => ZIO.succeed(Left(toFailure(error))))
    }

  private def revokeRoute(service: DietSharingService): ServerEndpoint[Any, Task] =
    revokeShareEndpoint.serverLogic { case (dietId, shareId, ownerId) =>
      service
        .revokeShare(
          RevokeShareCommand(
            shareId = shareId,
            dietId = dietId,
            ownerUserId = ownerId
          )
        )
        .as(Right(()))
        .catchAll(error => ZIO.succeed(Left(toFailure(error))))
    }

  private def listRecipientRoute(service: DietSharingService): ServerEndpoint[Any, Task] =
    listRecipientEndpoint.serverLogic { recipientId =>
      service
        .listSharedDiets(recipientId)
        .map(shares => Right(SharedDietListResponse(shares.map(SharedDietSummaryPayload.fromDomain).toList)))
        .catchAll(error => ZIO.succeed(Left(toFailure(error))))
    }

  private def viewRecipientRoute(service: DietSharingService): ServerEndpoint[Any, Task] =
    viewRecipientEndpoint.serverLogic { case (shareId, recipientId) =>
      service
        .viewSharedDiet(
          ViewShareQuery(
            shareId = shareId,
            recipientUserId = recipientId
          )
        )
        .map(view => Right(SharedDietViewResponse.fromDomain(view)))
        .catchAll(error => ZIO.succeed(Left(toFailure(error))))
    }

  private def copyRoute(service: DietSharingService): ServerEndpoint[Any, Task] =
    copySharedDietEndpoint.serverLogic { case (shareId, recipientId) =>
      service
        .copySharedDiet(
          CopySharedDietCommand(
            shareId = shareId,
            recipientUserId = recipientId
          )
        )
        .map(copy => Right(DietCopyResponse.fromDomain(copy)))
        .catchAll(error => ZIO.succeed(Left(toFailure(error))))
    }
}
