import sbt._

object Dependencies {
  val scala = "2.13.13"

  private val zioVersion          = "2.0.21"
  private val tapirVersion        = "1.9.10"
  private val zioHttpVersion      = "3.0.0-RC4"
  private val doobieVersion       = "1.0.0-RC4"
  private val flywayVersion       = "10.9.1"
  private val zioJsonVersion      = "0.6.2"
  private val testcontainersVer   = "1.19.3"
  private val typesafeConfigVer   = "1.4.3"
  private val postgresDriverVer   = "42.7.1"
  private val enumeratumVersion   = "1.7.5"

  val coreDependencies: Seq[ModuleID] = Seq(
    "dev.zio" %% "zio" % zioVersion,
    "dev.zio" %% "zio-streams" % zioVersion,
    "dev.zio" %% "zio-interop-cats" % "23.0.0.4",
    "dev.zio" %% "zio-json" % zioJsonVersion,
    "org.tpolecat" %% "doobie-core" % doobieVersion,
    "org.tpolecat" %% "doobie-hikari" % doobieVersion,
    "org.tpolecat" %% "doobie-postgres" % doobieVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-core" % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-json-zio" % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs" % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-zio-http-server" % tapirVersion,
    "dev.zio" %% "zio-http" % zioHttpVersion,
    "com.beachape" %% "enumeratum" % enumeratumVersion,
    "org.flywaydb" % "flyway-core" % flywayVersion,
    "org.flywaydb" % "flyway-database-postgresql" % flywayVersion,
    "org.postgresql" % "postgresql" % postgresDriverVer,
    "com.typesafe" % "config" % typesafeConfigVer,
    "dev.zio" %% "zio-test" % zioVersion % Test,
    "dev.zio" %% "zio-test-sbt" % zioVersion % Test,
    "org.testcontainers" % "postgresql" % testcontainersVer % Test
  )
}
