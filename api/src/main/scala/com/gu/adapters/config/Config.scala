package com.gu.adapters.config

import com.amazonaws.regions.{Region, Regions}
import com.gu.adapters.http.AvatarServletProperties
import com.gu.adapters.notifications.SnsProperties
import com.gu.adapters.queue.SqsDeletionConsumerProps
import com.gu.core.store.StoreProperties
import com.typesafe.config.{ConfigFactory, Config => TypesafeConfig}

case class ElkConfig(enabled: Boolean, streamName: String, region: String, stage: String)

case class Config(
    avatarServletProperties: AvatarServletProperties,
    storeProperties: StoreProperties,
    deletionEventsProps: SqsDeletionConsumerProps,
    elkConfig: ElkConfig,
    identityConfig: IdentityConfig
) {
  val snsProperties = SnsProperties(storeProperties.awsRegion, avatarServletProperties.snsTopicArn)
}

object Config {

  private val pageSize = 10

  val secureCookie = "SC_GU_U"

  def apply(): Config = {
    apply(ConfigFactory.load())
  }

  def apply(conf: TypesafeConfig): Config =
    Config(
      avatarServletProperties(conf),
      storeProperties(conf),
      deletionEventsProps(conf),
      elkConfig(conf),
      IdentityConfig.fromTypesafeConfig(conf)
    )

  private def deletionEventsProps(conf: TypesafeConfig): SqsDeletionConsumerProps = {
    SqsDeletionConsumerProps(conf.getString("aws.sqs.deleted.url"), conf.getString("aws.region") )
  }

  protected def storeProperties(conf: TypesafeConfig): StoreProperties =
    StoreProperties(
      awsRegion = Region.getRegion(Regions.fromName(conf.getString("aws.region"))),
      kvTable = conf.getString("aws.dynamodb.table"),
      fsIncomingBucket = conf.getString("aws.s3.incoming"),
      pageSize = pageSize,
      fsProcessedBucket = conf.getString("aws.s3.processed"),
      fsPublicBucket = conf.getString("aws.s3.public"),
      fsRawBucket = conf.getString("aws.s3.raw"),
      kvStatusIndex = "status-index",
      kvUserIndex = "user-id-index"
    )

  private def avatarServletProperties(conf: TypesafeConfig): AvatarServletProperties =
    AvatarServletProperties(
      apiKeys = conf.getString("api.keys").split(',').toList,
      apiUrl = conf.getString("api.baseUrl") + "/v1",
      pageSize = pageSize,
      snsTopicArn = conf.getString("aws.sns.topic.arn")
    )

  private def elkConfig(conf: TypesafeConfig): ElkConfig = {
    ElkConfig(
      conf.getString("elk.logging.enabled").toBoolean,
      conf.getString("elk.logging.stream"),
      conf.getString("aws.region"),
      conf.getString("stage")
    )
  }
}
