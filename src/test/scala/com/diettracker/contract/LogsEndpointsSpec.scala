package com.diettracker.contract

import com.diettracker.http.LogsEndpoints
import zio._
import zio.test._

object LogsEndpointsSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("Logs endpoints contract")(
      test("Create log endpoint metadata") {
        val info = LogsEndpoints.createLogEndpoint.info
        assertTrue(
          info.name.contains("Create Log Entry"),
          info.tags.contains("Logs")
        )
      },
      test("List logs endpoint metadata") {
        val info = LogsEndpoints.listLogsEndpoint.info
        assertTrue(
          info.name.contains("List Logs"),
          info.tags.contains("Logs")
        )
      },
      test("Update log endpoint metadata") {
        val info = LogsEndpoints.updateLogEndpoint.info
        assertTrue(
          info.name.contains("Update Log Entry"),
          info.tags.contains("Logs")
        )
      },
      test("Delete log endpoint metadata") {
        val info = LogsEndpoints.deleteLogEndpoint.info
        assertTrue(
          info.name.contains("Delete Log Entry"),
          info.tags.contains("Logs")
        )
      }
    )
}
