package com.gu.adapters.http

import com.gu.adapters.store.Store
import com.gu.core.Errors._
import com.gu.core._
import org.json4s.ext.JodaTimeSerializers
import org.json4s.{DefaultFormats, Formats}
import org.scalatra._
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.servlet.{FileUploadSupport, MultipartConfig, SizeConstraintExceededException}
import org.scalatra.swagger.{Swagger, SwaggerSupport}

import scalaz.{-\/, NonEmptyList, \/, \/-}

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

  get("/service/healthcheck") {
    Ok(Message("OK"))
  }
  get("/service/gtg") {
    NotImplemented(Message("Endpoint needs to be specified"))
  }
  get("/service/dependencies") {
    NotImplemented(Message("Endpoint needs to be specified"))
  }

  get("/avatars", operation(getAvatars)) {
    withErrorHandling {
      Filters.fromParams(params) flatMap store.get
    }
  }

  get("/avatars/:id", operation(getAvatar)) {
    withErrorHandling {
      store.get(params("id"))
    }
  }

  get("/avatars/user/:userId", operation(getAvatarsForUser)) {
    withErrorHandling {
      val user = User(params("userId").toInt)
      store.get(user)
    }
  }

  get("/avatars/user/:userId/active", operation(getActiveAvatarForUser)) {
    withErrorHandling {
      val user = User(params("userId").toInt)
      store.getActive(user)
    }
  }

  post("/avatars", operation(postAvatar)) {
    withErrorHandling {
      //    val cd = new IdentityCookieDecoder(new ProductionKeys)

      //    for {
      //      cookie <- request.cookies.get("GU_U")
      //      user <- cd.getUserDataForGuU(cookie).map(_.user)
      //      username <- user.publicFields.displayName
      //    }

      val user = User("123456".toInt)
      val url = (parse(request.body) \ "url").values.toString
      val image = fileParams("image")

      request.contentType match {
        case Some("application/json") | Some("text/json") => store.fetchImage(user, url)
        case Some(s) if s startsWith "multipart/form-data" => store.userUpload(user, image)
        case Some(invalid) => -\/(invalidContentType(NonEmptyList(s"'$invalid' is not a valid content type. Must be 'multipart/form-data' or 'application/json'.")))
      }
    }
  }

  put("/avatars/:id/status", operation(putAvatarStatus)) {
    withErrorHandling {
      val status = Status((parse(request.body) \ "status").values.toString) // TODO handle errors gracefully here
      store.updateStatus(params("id"), status)
    }
  }

  //get("/avatars/user/me/active", operation(getActiveAvatarForUserInfo))(getActiveAvatarForUser())
  //get("/avatars/stats", operation(getAvatarStatsInfo))(getAvatarStats)

  // for cdn endpoint (avatars.theguardian.com)
  //   /user/:id -> retrieve active avatar for a user
  //   /user/me  -> retrieve active avatar for me (via included cookie)

  def withErrorHandling(response: => \/[Error, Any]): ActionResult = {
    (handleSuccess orElse handleError)(response)
  }

  def redirectOrError(response: \/[Error, Any]): ActionResult = {
    (handleRedirect orElse handleError)(response)
  }

  def handleSuccess: PartialFunction[\/[Error, Any], ActionResult] = {
    case \/-(success) => Ok(success)
  }

  def handleRedirect: PartialFunction[\/[Error, Any], ActionResult] = {
    case \/-(success) => TemporaryRedirect(success.toString)
  }

  def handleError[A]: PartialFunction[\/[Error, A], ActionResult] = {
    case -\/(error) => error match {
      case InvalidContentType(msg, errors) => UnsupportedMediaType(ErrorResponse(msg, errors.list))
      case InvalidFilters(msg, errors) => BadRequest(ErrorResponse(msg, errors.list))
      case AvatarNotFound(msg, errors) => NotFound(ErrorResponse(msg, errors.list))
      case AvatarRetrievalFailed(msg, errors) => ServiceUnavailable(ErrorResponse(msg, errors.list))
      case DynamoRequestFailed(msg, errors) => ServiceUnavailable(ErrorResponse(msg, errors.list))
    }
  }
}
