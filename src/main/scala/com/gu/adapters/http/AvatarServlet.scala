package com.gu.adapters.http

import com.gu.adapters.http.CookieDecoder.userFromCookie
import com.gu.adapters.store.AvatarStore
import com.gu.core.Errors._
import com.gu.core.Success
import com.gu.core._
import com.gu.identity.cookie.IdentityCookieDecoder
import org.json4s.JsonAST.JValue
import org.json4s.ext.JodaTimeSerializers
import org.json4s.{DefaultFormats, Formats}
import org.scalatra._
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.servlet._
import org.scalatra.swagger.{Swagger, SwaggerSupport}
import scalaz.Scalaz._

import scala.util.{Failure, Try}
import scalaz.{-\/, NonEmptyList, \/, \/-}

class AvatarServlet(store: AvatarStore, decoder: IdentityCookieDecoder)(implicit val swagger: Swagger)
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
    case e: SizeConstraintExceededException =>
      RequestEntityTooLarge(
        ErrorResponse("File exceeds size limit: images must be no more than 1mb in size", Nil))
  }

  notFound {
    NotFound(ErrorResponse("Requested resource not found", Nil))
  }

  get("/service/healthcheck") {
    Message("OK")
  }

  get("/service/gtg") {
    NotImplemented(Message("Endpoint needs to be specified"))
  }
  
  get("/service/dependencies") {
    NotImplemented(Message("Endpoint needs to be specified"))
  }

  get("/avatars", operation(getAvatars)) {
    withErrorHandling {
      for {
        filters <- Filters.fromParams(params)
        avatars <- store.get(filters)
      } yield FoundAvatars(avatars)
    }
  }

  get("/avatars/:id", operation(getAvatar)) {
    withErrorHandling {
      for {
        avatar <- store.get(params("id"))
      } yield FoundAvatar(avatar)
    }
  }

  get("/avatars/user/:userId", operation(getAvatarsForUser)) {
    withErrorHandling {
      for {
        user <- userFromRequest(params("userId"))
        avatars <- store.get(user)
      } yield FoundAvatars(avatars)
    }
  }

  get("/avatars/user/:userId/active", operation(getActiveAvatarForUser)) {
    withErrorHandling {
      for {
        user <- userFromRequest(params("userId"))
        active <- store.getActive(user)
      } yield FoundAvatar(active)
    }
  }

  get("/avatars/user/me/active", operation(getPersonalAvatarForUser)) {
    withErrorHandling {
      for {
        user <- userFromCookie(decoder, request.cookies.get("GU_U"))
        avatar <- store.getPersonal(user)
      } yield FoundAvatar(avatar)
    }
  }

  post("/avatars", operation(postAvatar)) {
    withErrorHandling {
      for {
        user <- userFromCookie(decoder, request.cookies.get("GU_U"))
        avatar <- uploadAvatar(request, user, fileParams)
      } yield CreatedAvatar(avatar)
    }
  }

  put("/avatars/:id/status", operation(putAvatarStatus)) {
    withErrorHandling {
      for {
        sr <- statusRequestFromBody(parsedBody)
        update <- store.updateStatus(params("id"), sr.status)
      } yield UpdatedAvatar(update)
    }
  }

  // for cdn endpoint (avatars.theguardian.com)
  //   /user/:id -> retrieve active avatar for a user
  //   /user/me  -> retrieve active avatar for me (via included cookie)

  def withErrorHandling(response: => \/[Error, Success]): ActionResult = {
    (handleSuccess orElse handleError)(response)
  }

  def handleSuccess: PartialFunction[\/[Error, Success], ActionResult] = {
    case \/-(success) => success match {
      case CreatedAvatar(avatar) => Created(avatar)
      case okay => Ok(okay.body)
    }
  }

  def handleError[A]: PartialFunction[\/[Error, A], ActionResult] = {
    case -\/(error) => error match {
      case InvalidContentType(msg, errors) => UnsupportedMediaType(ErrorResponse(msg, errors.list))
      case InvalidFilters(msg, errors) => BadRequest(ErrorResponse(msg, errors.list))
      case AvatarNotFound(msg, errors) => NotFound(ErrorResponse(msg, errors.list))
      case AvatarRetrievalFailed(msg, errors) => ServiceUnavailable(ErrorResponse(msg, errors.list))
      case DynamoRequestFailed(msg, errors) => ServiceUnavailable(ErrorResponse(msg, errors.list))
      case UnableToReadUserCookie(msg, errors) => BadRequest(ErrorResponse(msg, errors.list))
      case IOFailed(msg, errors) => ServiceUnavailable(ErrorResponse(msg, errors.list))
      case InvalidUserId(msg, errors) => BadRequest(ErrorResponse(msg, errors.list))
      case UnableToReadStatusRequest(msg, errors) => BadRequest(ErrorResponse(msg, errors.list))
    }
  }

  def uploadAvatar(request: RichRequest, user: User, fileParams: Map[String, FileItem]): Error \/ Avatar = {
    request.contentType match {
      case Some("application/json") | Some("text/json") =>
        val url = (parse(request.body) \ "url").values.toString // HANDLE ERRORS HERE
        store.fetchImage(user, url)
      case Some(s) if s startsWith "multipart/form-data" =>
        val image = fileParams("image")
        store.userUpload(user, image.getInputStream, image.getName)
      case Some(invalid) =>
        -\/(invalidContentType(NonEmptyList(s"'$invalid' is not a valid content type.")))
      case None =>
        -\/(invalidContentType(NonEmptyList("No content type specified.")))
    }
  }

  def statusRequestFromBody(parsedBody: JValue): Error \/ StatusRequest = {
    Try(parsedBody.extract[StatusRequest]).toOption
      .toRightDisjunction(unableToReadStatusRequest(NonEmptyList("Could not parse request body")))
  }

  def userFromRequest(userId: String): Error \/ User = {
    Try(User(userId.toInt)).toOption
      .toRightDisjunction(invalidUserId(NonEmptyList(s"Expected integer, found: $userId")))
  }
}
