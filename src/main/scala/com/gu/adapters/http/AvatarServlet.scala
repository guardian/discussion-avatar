package com.gu.adapters.http

import com.gu.adapters.store.AvatarStore
import com.gu.entities._
import org.json4s.{DefaultFormats, Formats}
import org.scalatra._
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.swagger.{Swagger, SwaggerSupport}
import scalaz.{-\/, \/, \/-}

class AvatarServlet(implicit val swagger: Swagger)
  extends ScalatraServlet
  with JacksonJsonSupport
  with SwaggerSupport {

  protected implicit val jsonFormats: Formats = DefaultFormats + new StatusSerializer

  protected val applicationDescription = "The Avatar API. Exposes operations for viewing, adding, and moderating Avatars"

  before() {
    contentType = formats("json")
  }

  // Management endpoints

  get("/management/healthcheck") {
    Ok(Message("OK"))
  }

  get("/management/gtg") {
    NotImplemented(Message("Endpoint needs to be specified"))
  }

  get("/management/dependencies") {
    NotImplemented(Message("Endpoint needs to be specified"))
  }

  // Avatar endpoints

  val getAvatars =
    (apiOperation[List[Avatar]]("getAvatars")
      summary "List all avatars"
      parameter (queryParam[Option[String]]("status")
        .description("The request includes a status to filter by")))

  get("/avatars", operation(getAvatars)) {
    val filters = Filters.fromParams(params)
    val avatars = filters flatMap AvatarStore.get
    getOrError(avatars)
  }

  val getAvatar =
    (apiOperation[Avatar]("getAvatar")
      summary "Retrieve avatar by ID")

  get("/avatars/:id", operation(getAvatar)) {
    val avatar = AvatarStore.get(params("id"))
    getOrError(avatar)
  }

  val getActiveAvatarForUser =
    (apiOperation[Avatar]("getActiveAvatarForUser")
      summary "Get active avatar for user")

  get("/avatars/user/:userId/active", operation(getActiveAvatarForUser)) {
    val user = User(params("userId"))
    val avatar = AvatarStore.get(user)
    getOrError(avatar)
  }

  // TODO, add a browser-usable endpoint (i.e. multipart/form-data)
  val postAvatar =
    (apiOperation[Avatar]("postAvatar")
      summary "Add a new avatar"
      parameter (bodyParam[AvatarRequest]("")
        .description("The request includes the new Avatar's details")))

  post("/avatars", operation(postAvatar)) {
    val avatar = AvatarStore.get("123") // hack for now
    getOrError(avatar)
  }

  val putAvatarStatus =
    (apiOperation[Avatar]("putAvatarStatus")
      summary "Update avatar status"
      parameters (
        pathParam[String]("id")
          .description("The request includes the Avatar ID"),
        bodyParam[StatusRequest]("")
          .description("The request includes the Avatar's new status")))

  put("/avatars/:id/status", operation(putAvatarStatus)) {
    val avatar = AvatarStore.get("123") // hack for now
    getOrError(avatar)
  }

  def getOrError[A](r: \/[Error, A]): Any =
    r match {
      case \/-(success) => Ok(success)
      case -\/(error) => error match {
        case InvalidFilters(msg, errors) => BadRequest(ErrorResponse(msg, errors.list))
        case AvatarNotFound(msg, errors) => NotFound(ErrorResponse(msg, errors.list))
      }
    }
}
