package com.diettracker.contract

import com.diettracker.http.DailySummaryEndpoints
import zio._
import zio.test._

object DailySummaryEndpointsSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("Daily summary endpoints contract")(
      test("Daily summary endpoint metadata") {
        val info = DailySummaryEndpoints.dailySummaryEndpoint.info
        assertTrue(
          info.name.contains("Daily Summary"),
          info.tags.contains("Daily Summary")
        )
      }
    )
}
