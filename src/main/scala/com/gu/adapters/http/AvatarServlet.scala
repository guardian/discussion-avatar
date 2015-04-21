package com.gu.adapters.http

import com.gu.adapters.store.Store
import com.gu.entities.Errors.invalidContentType
import com.gu.entities._
import org.json4s.ext.JodaTimeSerializers
import org.json4s.{DefaultFormats, Formats}
import org.scalatra._
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.servlet.{FileUploadSupport, MultipartConfig, SizeConstraintExceededException}
import org.scalatra.swagger.{Swagger, SwaggerSupport}

import scalaz.{NonEmptyList, -\/, \/, \/-}

class AvatarServlet(store: Store)(implicit val swagger: Swagger)
  extends ScalatraServlet
  with JacksonJsonSupport
  with SwaggerSupport
  with FileUploadSupport {

  protected implicit val jsonFormats: Formats = DefaultFormats + new StatusSerializer ++ JodaTimeSerializers.all

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

  val getAvatarsForUserInfo =
    (apiOperation[List[Avatar]]("getAvatarsForUser")
      summary "Get avatars for user")

  def getAvatarsForUser(userId: String): ActionResult = {
    val user = User(params("userId").toInt)
    val avatar = store.get(user)
    getOrError(avatar)
  }

  val getActiveAvatarForUserInfo =
    (apiOperation[List[Avatar]]("getActiveAvatarForUser")
      summary "Get active avatar for user")

  def getActiveAvatarForUser(): ActionResult = {
    val user = User("123456".toInt) // FIXME -- get user id from cookie
    val avatarUrl = store.getActive(user)
    redirectOrError(avatarUrl)
  }

  val postAvatarInfo =
    (apiOperation[Avatar]("postAvatar")
      summary "Add a new avatar"
      consumes "multipart/form-data")

  def postAvatar(): ActionResult = {

    //    val cd = new IdentityCookieDecoder(new ProductionKeys)

    //    for {
    //      cookie <- request.cookies.get("GU_U")
    //      user <- cd.getUserDataForGuU(cookie).map(_.user)
    //      username <- user.publicFields.displayName
    //    }

    val user = User("123456".toInt)

    val avatar = request.contentType match {
          case Some("application/json") | Some("text/json") => store.fetchImage(user, (parse(request.body) \ "url").values.toString)
          case Some(s) if s startsWith "multipart/form-data" => store.userUpload(user, fileParams("image"))
          case Some(invalid) => -\/(invalidContentType(NonEmptyList(s"'$invalid' is not a valid content type. Must be 'multipart/form-data' or 'application/json'.")))
    }
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

  def putAvatarStatus(id: String): ActionResult = {
    val status = Status((parse(request.body) \ "status").values.toString)
    val avatar = store.updateStatus(id, status)
    getOrError(avatar)
  }

  val getAvatarStatsInfo =
    (apiOperation[List[Avatar]]("getAvatarStats")
      summary "Get avatars statistics")

  def getAvatarStats: ActionResult = {
    val stats = store.getStats
    getOrError(stats)
  }

  def getOrError[A](r: \/[Error, A]): ActionResult = r match {
    case \/-(success) => Ok(success)
    case -\/(error) => error match {
      case InvalidContentType(msg, errors) => UnsupportedMediaType(ErrorResponse(msg, errors.list))
      case InvalidFilters(msg, errors) => BadRequest(ErrorResponse(msg, errors.list))
      case AvatarNotFound(msg, errors) => NotFound(ErrorResponse(msg, errors.list))
      case AvatarRetrievalFailed(msg, errors) => ServiceUnavailable(ErrorResponse(msg, errors.list))
      case DynamoRequestFailed(msg, errors) => ServiceUnavailable(ErrorResponse(msg, errors.list))
    }
  }

  def redirectOrError[A](r: \/[Error, A]): ActionResult = r match {
    case \/-(success) => TemporaryRedirect(success.toString)
    case -\/(error) => error match {
      case InvalidContentType(msg, errors) => UnsupportedMediaType(ErrorResponse(msg, errors.list))
      case InvalidFilters(msg, errors) => BadRequest(ErrorResponse(msg, errors.list))
      case AvatarNotFound(msg, errors) => NotFound(ErrorResponse(msg, errors.list))
      case AvatarRetrievalFailed(msg, errors) => ServiceUnavailable(ErrorResponse(msg, errors.list))
      case DynamoRequestFailed(msg, errors) => ServiceUnavailable(ErrorResponse(msg, errors.list))
    }
  }

  get("/management/healthcheck")(healthcheck())
  get("/management/gtg")(gtg())
  get("/management/dependencies")(dependencies())

  get("/avatars", operation(getAvatarsInfo))(getAvatars(params))
  get("/avatars/:id", operation(getAvatarInfo))(getAvatar(params("id")))
  get("/avatars/user/:userId",
    operation(getAvatarsForUserInfo))(getAvatarsForUser(params("userId")))
  get("/avatars/user/me/active", operation(getActiveAvatarForUserInfo))(getActiveAvatarForUser())

  post("/avatars", operation(postAvatarInfo))(postAvatar())
  put("/avatars/:id/status", operation(putAvatarStatusInfo))(putAvatarStatus(params("id")))
  get("/avatars/stats", operation(getAvatarStatsInfo))(getAvatarStats)

  // for cdn endpoint (avatars.theguardian.com)
  //   /user/:id -> retrieve active avatar for a user
  //   /user/me  -> retrieve active avatar for me (via included cookie)
}
