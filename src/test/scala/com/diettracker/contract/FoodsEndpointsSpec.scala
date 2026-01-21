package com.diettracker.contract

import com.diettracker.http.FoodsEndpoints
import zio._
import zio.test._

object FoodsEndpointsSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("Foods endpoints contract")(
      test("Create food endpoint named") {
        assertTrue(FoodsEndpoints.createFoodEndpoint.info.name.contains("Create Food"))
      },
      test("Search foods endpoint named") {
        assertTrue(FoodsEndpoints.searchFoodsEndpoint.info.name.contains("List/Search Foods"))
      },
      test("Update food endpoint named") {
        assertTrue(FoodsEndpoints.updateFoodEndpoint.info.name.contains("Update Food"))
      },
      test("Delete food endpoint named") {
        assertTrue(FoodsEndpoints.deleteFoodEndpoint.info.name.contains("Delete Food"))
      }
    )
}
