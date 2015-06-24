package com.gu.adapters.http

import org.scalatra.ScalatraServlet
import org.scalatra.swagger.{ ApiInfo, NativeSwaggerBase, Swagger }

class ResourcesApp(implicit val swagger: Swagger) extends ScalatraServlet with NativeSwaggerBase

object AvatarApiInfo extends ApiInfo(
  title = "Guardian Avatar API",
  description = "Docs for the Avatar API",
  termsOfServiceUrl = "https//github.com/guardian/avatar",
  contact = "discussiondev@theguardian.com",
  license = "To be determined",
  licenseUrl = "To be added"
)

class AvatarSwagger extends Swagger(Swagger.SpecVersion, "1.0.0", AvatarApiInfo)