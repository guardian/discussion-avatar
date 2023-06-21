package com.gu.adapters.config

import com.typesafe.config.{Config => TypesafeConfig}

case class IdentityConfig(
  apiUrl: String,
  accessToken: String,
  oktaIssuer: String,
  oktaAudience: String
)

object IdentityConfig {

  def fromTypesafeConfig(config: TypesafeConfig): IdentityConfig =
    IdentityConfig(
      apiUrl = config.getString("identity.apiUrl"),
      accessToken = config.getString("identity.accessToken"),
      oktaIssuer = config.getString("identity.oktaIssuer"),
      oktaAudience = config.getString("identity.oktaAudience")
    )
}
