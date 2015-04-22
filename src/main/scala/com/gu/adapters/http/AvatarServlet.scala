package com.gu.adapters.http

import com.gu.adapters.store.Store
import com.gu.entities._
import org.json4s.ext.JodaTimeSerializers
import org.json4s.{DefaultFormats, Formats}
import org.scalatra._
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.servlet.{FileUploadSupport, MultipartConfig, SizeConstraintExceededException}
import org.scalatra.swagger.{Swagger, SwaggerSupport}

class AvatarServlet(store: Store)(implicit val swagger: Swagger)
  extends ScalatraServlet
  with JacksonJsonSupport
  with SwaggerSupport
  with SwaggerOps
  with FileUploadSupport {

  protected implicit val jsonFormats: Formats =
    DefaultFormats +
      new StatusSerializer ++
      JodaTimeSerializers.all

  configureMultipartHandling(MultipartConfig(maxFileSize = Some(1024*1024)))

  before() {
    contentType = formats("json")
  }

  error {
    case e: SizeConstraintExceededException => RequestEntityTooLarge(ErrorResponse("File exceeds size limit: images must be no more than 1mb in size", Nil))
  }

  get("/service/healthcheck")(Handler.healthcheck())
  get("/service/gtg")(Handler.gtg())
  get("/service/dependencies")(Handler.dependencies())

  get("/avatars", operation(getAvatars))(Handler.getAvatars(store, params))
  get("/avatars/:id", operation(getAvatar))(Handler.getAvatar(store, params("id")))

  get("/avatars/user/:userId", operation(getAvatarsForUser))(
    Handler.getAvatarsForUser(store, params("userId")))

  get("/avatars/user/:userId/active", operation(getActiveAvatarForUser))(
    Handler.getActiveAvatarForUser(store, User(params("userId").toInt)))

  post("/avatars", operation(postAvatar))(Handler.postAvatar(
    store, request.contentType,
    request.body,
    fileParams("image")))

  put("/avatars/:id/status", operation(putAvatarStatus))(Handler.putAvatarStatus(store, request.body, params("id")))

  //get("/avatars/user/me/active", operation(getActiveAvatarForUserInfo))(getActiveAvatarForUser())
  //get("/avatars/stats", operation(getAvatarStatsInfo))(getAvatarStats)

  // for cdn endpoint (avatars.theguardian.com)
  //   /user/:id -> retrieve active avatar for a user
  //   /user/me  -> retrieve active avatar for me (via included cookie)
}
