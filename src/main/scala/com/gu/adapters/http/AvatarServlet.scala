package com.gu.adapters.http

import com.gu.adapters.store.Store
import com.gu.entities._
import org.json4s.{DefaultFormats, Formats}
import org.scalatra._
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.swagger.{Swagger, SwaggerSupport}
import scalaz.{-\/, \/, \/-}

class AvatarServlet(store: Store)(implicit val swagger: Swagger)
  extends ScalatraServlet
  with JacksonJsonSupport
  with SwaggerSupport {

  protected implicit val jsonFormats: Formats = DefaultFormats + new StatusSerializer

  protected val applicationDescription = "The Avatar API. Exposes operations for viewing, adding, and moderating Avatars"

  before() {
    contentType = formats("json")
  }

  def healthcheck(): ActionResult = Ok(Message("OK"))

  def gtg(): ActionResult = NotImplemented(Message("Endpoint needs to be specified"))

  def dependencies(): ActionResult = NotImplemented(Message("Endpoint needs to be specified"))

  val getAvatarsInfo =
    (apiOperation[List[Avatar]]("getAvatars")
      summary "List all avatars"
      parameter (queryParam[Option[String]]("status")
      .description("The request includes a status to filter by")))

  def getAvatars(params: Params): ActionResult = {
    val filters = Filters.fromParams(params)
    val avatars = filters flatMap store.get
    getOrError(avatars)
  }

  val getAvatarInfo =
    (apiOperation[Avatar]("getAvatar")
      summary "Retrieve avatar by ID")

  def getAvatar(): ActionResult = {
    val avatar = store.get(params("id"))
    getOrError(avatar)
  }

  val getActiveAvatarForUserInfo =
    (apiOperation[Avatar]("getActiveAvatarForUser")
      summary "Get active avatar for user")

  def getActiveAvatarForUser(userId: String): ActionResult = {
    val user = User(params("userId"))
    val avatar = store.get(user)
    getOrError(avatar)
  }

  // TODO, add a browser-usable endpoint (i.e. multipart/form-data)
  val postAvatarInfo =
    (apiOperation[Avatar]("postAvatar")
      summary "Add a new avatar"
      parameter (bodyParam[AvatarRequest]("")
      .description("The request includes the new Avatar's details")))

  def postAvatar(): ActionResult = {
    val avatar = store.get("123") // hack for now
    getOrError(avatar)
  }

  val putAvatarStatusInfo =
    (apiOperation[Avatar]("putAvatarStatus")
      summary "Update avatar status"
      parameters (
      pathParam[String]("id")
        .description("The request includes the Avatar ID"),
      bodyParam[StatusRequest]("")
        .description("The request includes the Avatar's new status")))

  def putAvatarStatus(): ActionResult = {
    val avatar = store.get("123") // hack for now
    getOrError(avatar)
  }

  def getOrError[A](r: \/[Error, A]): ActionResult = r match {
    case \/-(success) => Ok(success)
    case -\/(error) => error match {
      case InvalidFilters(msg, errors) => BadRequest(ErrorResponse(msg, errors.list))
      case AvatarNotFound(msg, errors) => NotFound(ErrorResponse(msg, errors.list))
    }
  }

  get("/management/healthcheck")(healthcheck())
  get("/management/gtg")(gtg())
  get("/management/dependencies")(dependencies())

  get("/avatars", operation(getAvatarsInfo))(getAvatars(params))
  get("/avatars/:id", operation(getAvatarInfo))(getAvatar())
  get("/avatars/user/:userId/active",
    operation(getActiveAvatarForUserInfo))(getActiveAvatarForUser(params("userId")))

  post("/avatars", operation(postAvatarInfo))(postAvatar())
  put("/avatars/:id/status", operation(putAvatarStatusInfo))(putAvatarStatus())
}
