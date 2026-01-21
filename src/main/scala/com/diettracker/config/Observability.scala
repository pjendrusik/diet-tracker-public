package com.diettracker.config

import zio._

object Observability {
  def track[R, E, A](operation: String)(effect: ZIO[R, E, A]): ZIO[R, E, A] =
    for {
      start  <- ZIO.succeed(java.lang.System.currentTimeMillis())
      result <- effect
      end    <- ZIO.succeed(java.lang.System.currentTimeMillis())
      _      <- ZIO.logDebug(s"[$operation] took ${end - start}ms")
    } yield result
}
