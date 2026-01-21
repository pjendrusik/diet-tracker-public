package com.diettracker.contract

import com.diettracker.http.DietSharingRoutes
import sttp.model.Method
import zio._
import zio.test._

object DietSharingContractSpec extends ZIOSpecDefault {
  private val ownerTag     = "Diet Sharing"
  private val recipientTag = "Shared Diets"

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("DietSharing endpoints contract")(
      test("Share diet endpoint metadata") {
        val endpoint = DietSharingRoutes.createShareEndpoint
        val info     = endpoint.info
        assertTrue(
          info.name.contains("Share Diet"),
          info.tags.contains(ownerTag),
          endpoint.method.contains(Method.POST)
        )
      },
      test("List diet shares endpoint metadata") {
        val endpoint = DietSharingRoutes.listSharesEndpoint
        val info     = endpoint.info
        assertTrue(
          info.name.contains("List Diet Shares"),
          info.tags.contains(ownerTag),
          endpoint.method.contains(Method.GET)
        )
      },
      test("Revoke diet share endpoint metadata") {
        val endpoint = DietSharingRoutes.revokeShareEndpoint
        val info     = endpoint.info
        assertTrue(
          info.name.contains("Revoke Diet Share"),
          info.tags.contains(ownerTag),
          endpoint.method.contains(Method.DELETE)
        )
      },
      test("List shared diets endpoint metadata") {
        val endpoint = DietSharingRoutes.listRecipientEndpoint
        val info     = endpoint.info
        assertTrue(
          info.name.contains("List Shared Diets"),
          info.tags.contains(recipientTag),
          endpoint.method.contains(Method.GET)
        )
      },
      test("View shared diet endpoint metadata") {
        val endpoint = DietSharingRoutes.viewRecipientEndpoint
        val info     = endpoint.info
        assertTrue(
          info.name.contains("View Shared Diet"),
          info.tags.contains(recipientTag),
          endpoint.method.contains(Method.GET),
          info.summary.contains("Open a shared diet in read-only mode")
        )
      },
      test("Copy shared diet endpoint metadata") {
        val info = DietSharingRoutes.copySharedDietEndpoint.info
        assertTrue(
          info.name.contains("Copy Shared Diet"),
          info.tags.contains(recipientTag)
        )
      }
    )
}
