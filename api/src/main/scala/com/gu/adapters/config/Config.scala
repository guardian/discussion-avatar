package com.gu.adapters.config

import com.amazonaws.regions.{ Regions, Region }
import com.gu.identity.cookie.{ IdentityCookieDecoder, PreProductionKeys, ProductionKeys }
import com.typesafe.config.{ ConfigFactory, Config => TypesafeConfig }

trait Config {

  protected def conf: TypesafeConfig

  lazy val apiUrl = conf.getString("api.baseUrl") + "/v1"
  lazy val apiKeys = conf.getString("api.keys").split(',').toList
  lazy val pageSize = 10
  lazy val stage = conf.getString("stage")

  lazy val cookieDecoder = stage match {
    case "PROD" => new IdentityCookieDecoder(new ProductionKeys)
    case _ => new IdentityCookieDecoder(new PreProductionKeys)
  }

  lazy val dynamoTable = conf.getString("aws.dynamodb.table")
  lazy val statusIndex = "status-index"
  lazy val userIndex = "user-id-index"

  lazy val s3IncomingBucket = conf.getString("aws.s3.incoming")
  lazy val s3RawBucket = conf.getString("aws.s3.raw")
  lazy val s3ProcessedBucket = conf.getString("aws.s3.processed")
  lazy val s3PublicBucket = conf.getString("aws.s3.public")
  lazy val snsTopicArn = conf.getString("aws.sns.topic.arn")
  lazy val awsRegion = Region.getRegion(Regions.fromName(conf.getString("aws.region")))
}

object AvatarApiConfig extends Config {
  protected val conf = ConfigFactory.load()
}