package com.diettracker

import zio._
import zio.test._

object SmokeSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("SmokeSpec")(
      test("project boots") {
        assertTrue(1 + 1 == 2)
      }
    )
}
