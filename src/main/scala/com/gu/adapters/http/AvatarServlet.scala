package com.gu.adapters.http

import java.io.FileInputStream

import com.gu.adapters.store.Store
import com.gu.entities._
import org.json4s.{DefaultFormats, Formats}
import org.scalatra._
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.servlet.{MultipartConfig, FileUploadSupport}
import org.scalatra.swagger.{Swagger, SwaggerSupport}
import scalaz.{-\/, \/, \/-}
import org.scalatra.servlet.SizeConstraintExceededException
import com.gu.identity.cookie.{ProductionKeys, IdentityCookieDecoder}
import java.util.UUID
import org.joda.time.DateTime
import com.gu.entities.{Pending}

class AvatarServlet(store: Store)(implicit val swagger: Swagger)
  extends ScalatraServlet
  with JacksonJsonSupport
  with SwaggerSupport
  with FileUploadSupport {

  protected implicit val jsonFormats: Formats = DefaultFormats + new StatusSerializer

  protected val applicationDescription = "The Avatar API. Exposes operations for viewing, adding, and moderating Avatars"

  configureMultipartHandling(MultipartConfig(maxFileSize = Some(1024*1024)))

  before() {
    contentType = formats("json")
  }

  error {
    case e: SizeConstraintExceededException => RequestEntityTooLarge(ErrorResponse("File exceeds size limit: images must be no more than 1mb in size", Nil))
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

  def getAvatar(id: String): ActionResult = {
    val avatar = store.get(id)
    getOrError(avatar)
  }

  val getActiveAvatarForUserInfo =
    (apiOperation[List[Avatar]]("getActiveAvatarForUser")
      summary "Get active avatar for user")

  def getActiveAvatarsForUser(userId: String): ActionResult = {
    val user = User(params("userId"))
    val avatar = store.get(user)
    getOrError(avatar)
  }

  val postAvatarInfo =
    (apiOperation[Avatar]("postAvatar")
      summary "Add a new avatar"
      consumes "multipart/form-data")

  def postAvatar(): ActionResult = {
    val file = fileParams("image")

//    val cd = new IdentityCookieDecoder(new ProductionKeys)

//    for {
//      cookie <- request.cookies.get("GU_U")
//      user <- cd.getUserDataForGuU(cookie).map(_.user)
//      username <- user.publicFields.displayName
//    }

    // store.add() -> save to s3 and update dynamoDB

    val avatar = store.save(123, file)
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
  get("/avatars/:id", operation(getAvatarInfo))(getAvatar(params("id")))
  get("/avatars/user/:userId",
    operation(getActiveAvatarForUserInfo))(getActiveAvatarsForUser(params("userId")))
  get("avatars/user/me/active", operation(getAvatarInfo))(getAvatar(params("id"))) // hack for now

  post("/avatars", operation(postAvatarInfo))(postAvatar())
  put("/avatars/:id/status", operation(putAvatarStatusInfo))(putAvatarStatus())

  // for cdn endpoint (avatars.theguardian.com)
  //   /user/:id -> retrieve active avatar for a user
  //   /user/me  -> retrieve active avatar for me (via included cookie)
}
