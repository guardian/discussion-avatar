package com.gu.core

import com.gu.identity.cookie.{ PreProductionKeys, ProductionKeys, IdentityCookieDecoder }
import com.typesafe.config.ConfigFactory

object Config {

  private[this] val conf = ConfigFactory.load()

  val apiUrl = conf.getString("api.baseUrl")
  val pageSize = 10
  val stage = conf.getString("stage")

  val preProdCookie = 21801602 -> "WyIyMTgwMTYwMiIsIiIsIm5pY2xvbmciLCIyIiwxNDM5MjI0NTk4MDcyLDEsMTQwNzUxMzI0NzAwMCx0cnVlXQ.MCwCFCVF9u4tC6_dQJ6AFJArmBsfLp43AhR3YmlIfrlc9ZppczxpHDOVybEJbQ"
  val cookieDecoder = stage match {
    case "PROD" => new IdentityCookieDecoder(new ProductionKeys)
    case _ => new IdentityCookieDecoder(new PreProductionKeys)
  }

  val dynamoTable = conf.getString("aws.dynamodb.table")
  val statusIndex = "Status-index"
  val userIndex = "UserId-index"

  val s3PrivateBucket = conf.getString("aws.s3.private")
  val s3PublicBucket = conf.getString("aws.s3.public")

  val avatarBaseUrl = conf.getString("avatar.baseUrl")
}
