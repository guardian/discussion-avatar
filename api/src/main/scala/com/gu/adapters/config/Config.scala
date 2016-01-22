package com.gu.adapters.config

import com.amazonaws.regions.{ Region, Regions }
import com.gu.adapters.http.AvatarServletProperties
import com.gu.adapters.notifications.SnsProperties
import com.gu.core.store.StoreProperties
import com.gu.identity.cookie.{ GuUDecoder, IdentityCookieDecoder, PreProductionKeys, ProductionKeys }
import com.typesafe.config.{ Config => TypesafeConfig, ConfigFactory }

case class Config(
    avatarServletProperties: AvatarServletProperties,
    storeProperties: StoreProperties
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
      storeProperties(conf)
    )

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
      cookieDecoder = cookieDecoder(conf),
      pageSize = pageSize,
      snsTopicArn = conf.getString("aws.sns.topic.arn")
    )

  private def cookieDecoder(conf: TypesafeConfig): GuUDecoder = {
    val keys = conf.getString("stage") match {
      case "PROD" => new ProductionKeys
      case _ => new PreProductionKeys
    }
    new IdentityCookieDecoder(keys)
  }
}
