package com.diettracker.config

import com.typesafe.config.ConfigFactory
import zio._
import scala.jdk.CollectionConverters._

final case class HttpConfig(host: String, port: Int)
final case class DatabaseConfig(url: String, user: String, password: String, schema: String)
final case class SharingMetricsConfig(
    shareSuccessCounter: String,
    shareLatencyTimer: String,
    revokeLatencyTimer: String,
    copySuccessCounter: String,
    copyLatencyTimer: String,
    notificationDeliveryTimer: String,
    recipientListLatencyTimer: String,
    recipientViewLatencyTimer: String,
    revocationClosureCounter: String
)
object SharingMetricsConfig {
  val default: SharingMetricsConfig =
    SharingMetricsConfig(
      shareSuccessCounter = "diet_sharing_share_success_total",
      shareLatencyTimer = "diet_sharing_share_latency_ms",
      revokeLatencyTimer = "diet_sharing_revoke_latency_ms",
      copySuccessCounter = "diet_sharing_copy_success_total",
      copyLatencyTimer = "diet_sharing_copy_latency_ms",
      notificationDeliveryTimer = "diet_sharing_notification_latency_ms",
      recipientListLatencyTimer = "diet_sharing_recipient_list_latency_ms",
      recipientViewLatencyTimer = "diet_sharing_recipient_view_latency_ms",
      revocationClosureCounter = "diet_sharing_revocation_closure_total"
    )
}

final case class SharingConfig(
    notificationTopic: String,
    auditRetentionDays: Int,
    maxRecipientsPerDiet: Int,
    copyLockTimeoutSeconds: Int,
    metrics: SharingMetricsConfig
)
object SharingConfig        {
  val default: SharingConfig =
    SharingConfig(
      notificationTopic = "diet-sharing-events",
      auditRetentionDays = 365,
      maxRecipientsPerDiet = 5,
      copyLockTimeoutSeconds = 10,
      metrics = SharingMetricsConfig.default
    )
}

final case class AppConfig(
    featureFlags: Chunk[String],
    http: HttpConfig,
    database: DatabaseConfig,
    sharing: SharingConfig
) {
  def isFeatureEnabled(flag: String): Boolean = featureFlags.contains(flag)
}

object AppConfig {
  val layer: ZLayer[Any, Throwable, AppConfig] = ZLayer {
    ZIO.attempt {
      val conf        = ConfigFactory.load().getConfig("app")
      val flags       = conf.getStringList("featureFlags")
      val httpConfig  = conf.getConfig("http")
      val dbConfig    = conf.getConfig("database")
      val sharingConf = conf.getConfig("sharing")
      val metricsConf = sharingConf.getConfig("metrics")
      val sharing     = SharingConfig(
        notificationTopic = sharingConf.getString("notificationTopic"),
        auditRetentionDays = sharingConf.getInt("auditRetentionDays"),
        maxRecipientsPerDiet = sharingConf.getInt("maxRecipientsPerDiet"),
        copyLockTimeoutSeconds = sharingConf.getInt("copyLockTimeoutSeconds"),
        metrics = SharingMetricsConfig(
          shareSuccessCounter = metricsConf.getString("shareSuccessCounter"),
          shareLatencyTimer = metricsConf.getString("shareLatencyTimer"),
          revokeLatencyTimer = metricsConf.getString("revokeLatencyTimer"),
          copySuccessCounter = metricsConf.getString("copySuccessCounter"),
          copyLatencyTimer = metricsConf.getString("copyLatencyTimer"),
          notificationDeliveryTimer = metricsConf.getString("notificationDeliveryTimer"),
          recipientListLatencyTimer = metricsConf.getString("recipientListLatencyTimer"),
          recipientViewLatencyTimer = metricsConf.getString("recipientViewLatencyTimer"),
          revocationClosureCounter = metricsConf.getString("revocationClosureCounter")
        )
      )

      AppConfig(
        featureFlags = Chunk.fromIterable(flags.asScala),
        http = HttpConfig(
          host = httpConfig.getString("host"),
          port = httpConfig.getInt("port")
        ),
        database = DatabaseConfig(
          url = dbConfig.getString("url"),
          user = dbConfig.getString("user"),
          password = dbConfig.getString("password"),
          schema = dbConfig.getString("schema")
        ),
        sharing = sharing
      )
    }
  }
}
