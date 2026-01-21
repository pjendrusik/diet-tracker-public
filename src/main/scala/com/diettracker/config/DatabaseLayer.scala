package com.diettracker.config

import doobie.hikari.HikariTransactor
import zio._
import zio.interop.catz._
import zio.interop.catz.implicits._

import scala.concurrent.ExecutionContext

object DatabaseLayer {
  val live: ZLayer[Scope with AppConfig, Throwable, HikariTransactor[Task]] = ZLayer.scoped {
    for {
      cfg        <- ZIO.service[AppConfig]
      transactor <- HikariTransactor
                      .newHikariTransactor[Task](
                        "org.postgresql.Driver",
                        cfg.database.url,
                        cfg.database.user,
                        cfg.database.password,
                        ExecutionContext.global
                      )
                      .toScopedZIO
    } yield transactor
  }
}
