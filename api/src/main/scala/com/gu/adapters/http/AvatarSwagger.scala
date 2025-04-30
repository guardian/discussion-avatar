package com.gu.adapters.http

import org.scalatra.ScalatraServlet
import org.scalatra.swagger.{ApiInfo, NativeSwaggerBase, Swagger}
import org.scalatra.swagger.LicenseInfo
import org.scalatra.swagger.ContactInfo

class ResourcesApp(implicit val swagger: Swagger) extends ScalatraServlet with NativeSwaggerBase

object AvatarApiInfo extends ApiInfo(
  title = "Guardian Avatar API",
  description = "Docs for the Avatar API",
  termsOfServiceUrl = "https//github.com/guardian/avatar",
  contact = ContactInfo(
    name = "Discussion Dev",
    url = "https://github.com/orgs/guardian/teams/discussion",
    email = "discussiondev@theguardian.com"
  ),
  license = LicenseInfo(
    name = "To be determined",
    url = "To be added"
  ),
)

class AvatarSwagger extends Swagger(Swagger.SpecVersion, "1.0.0", AvatarApiInfo)