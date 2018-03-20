package com.gu.adapters.http

import com.gu.adapters.config.Config
import com.gu.adapters.http.CookieDecoder.userFromCookie
import com.gu.adapters.http.Image._
import com.gu.adapters.notifications.{Notifications, Publisher}
import com.gu.core.models.Errors._
import com.gu.core.models._
import com.gu.core.store.AvatarStore
import com.gu.core.utils.ErrorHandling.{attempt, logError}
import com.gu.identity.cookie.GuUDecoder
import org.json4s.JsonAST.JValue
import org.json4s.jackson.Serialization.write
import org.scalatra._
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.servlet._
import org.scalatra.swagger.{Swagger, SwaggerSupport}

import scalaz.{Success => _, _}

class AvatarServlet(
  store: AvatarStore,
  publisher: Publisher,
  props: AvatarServletProperties
)(implicit val swagger: Swagger)
  extends ScalatraServlet
    with ServletWithErrorHandling[Error, Success]
    with AuthorizedApiServlet[Success]
    with JacksonJsonSupport
    with SwaggerSupport
    with SwaggerOps
    with FileUploadSupport
    with CorsSupport {

  val apiUrl = props.apiUrl
  val pageSize = props.pageSize
  val apiKeys = props.apiKeys
  val snsTopicArn = props.snsTopicArn
  val decoder = props.cookieDecoder

  protected implicit val jsonFormats = JsonFormats.all

  configureMultipartHandling(MultipartConfig(maxFileSize = Some(1024 * 1024)))

  before() {
    contentType = formats("json")
  }

  methodNotAllowed { _ =>
    if (routes.matchingMethodsExcept(Options, requestPath).isEmpty)
      doNotFound() // correct for options("*") CORS behaviour
    else
      MethodNotAllowed(ErrorResponse("Method not supported"))
  }

  error {
    case e: SizeConstraintExceededException =>
      RequestEntityTooLarge(
        body = write(ErrorResponse("File exceeds size limit: images must be no more than 1mb in size")),
        headers = Map("Content-Type" -> "application/json; charset=UTF-8")
      )
  }

  notFound {
    NotFound(ErrorResponse("Requested resource not found"))
  }

  options("/*") {
    response.setHeader(
      "Access-Control-Allow-Headers",
      request.getHeader("Access-Control-Request-Headers")
    )
  }

  get("/service/healthcheck") {
    Message(Map("status" -> "OK"))
  }

  get("/service/gtg") {
    NotImplemented(ErrorResponse("Endpoint needs to be specified"))
  }

  get("/service/dependencies") {
    NotImplemented(ErrorResponse("Endpoint needs to be specified"))
  }

  get("/service/data/:userId") {
    NotImplemented(ErrorResponse("Endpoint needs to be specified"))
  }

  apiDelete("/service/data/:userId", operation(deleteUserPermanently)) { auth =>
    for {
      user <- User.userFromId(params("userId"))
      deleted <- store.deleteAll(user)
      req = Req(apiUrl, request.getPathInfo)
    } yield (deleted, req)
  }

  apiPut("/avatars/user/:userId/cleanup", operation(cleanupUser)) { auth: String =>
    for {
      user <- User.userFromId(params("userId"))
      deleted <- store.cleanupInactive(user)
      req = Req(apiUrl, request.getPathInfo)
    } yield (deleted, req)
  }

  get("/") {
    Message(
      uri = Some(apiUrl),
      data = Map("description" -> "The Guardian's Avatar API"),
      links = List(
        Link("avatars", apiUrl + "/avatars"),
        Link("avatar", apiUrl + "/avatars/{id}"),
        Link("user-avatars", apiUrl + "/avatars/user/{userId}"),
        Link("user-active", apiUrl + "/avatars/user/{userId}/active"),
        Link("me-active", apiUrl + "/avatars/user/me/active")
      )
    )
  }

  apiGet("/avatars", operation(getAvatars)) { auth: String =>
    for {
      filters <- Filter.fromParams(params)
      avatar <- store.get(filters)
      url = Req(apiUrl, request.getPathInfo, filters)
    } yield (avatar, url)
  }

  apiGet("/avatars/:id", operation(getAvatar)) { auth =>
    for {
      avatar <- store.get(params("id"))
      req = Req(apiUrl, request.getPathInfo)
    } yield (avatar, req)
  }

  apiGet("/avatars/user/:userId", operation(getAvatarsForUser)) { auth =>
    for {
      user <- User.userFromId(params("userId"))
      avatar <- store.get(user)
      req = Req(apiUrl, request.getPathInfo)
    } yield (avatar, req)
  }

  apiGet("/avatars/user/:userId/active", operation(getActiveAvatarForUser)) { auth =>
    for {
      user <- User.userFromId(params("userId"))
      active <- store.getActive(user)
      req = Req(apiUrl, request.getPathInfo)
    } yield (active, req)
  }

  getWithErrors("/avatars/user/me/active", operation(getPersonalAvatarForUser)) {
    for {
      user <- userFromCookie(decoder, request.cookies.get(Config.secureCookie))
      avatar <- store.getPersonal(user)
      req = Req(apiUrl, request.getPathInfo)
    } yield (avatar, req)
  }

  postWithErrors("/avatars", operation(postAvatar)) {
    for {
      user <- userFromCookie(decoder, request.cookies.get(Config.secureCookie))
      created <- uploadAvatar(request, user, fileParams)
      req = Req(apiUrl, request.getPathInfo)
    } yield {
      Notifications.publishAvatar(publisher, snsTopicArn, "Avatar Upload", created)
      (created, req)
    }
  }

  apiPut("/avatars/:id/status", operation(putAvatarStatus)) { auth =>
    for {
      sr <- statusRequestFromBody(parsedBody)
      updated <- store.updateStatus(params("id"), sr.status)
      req = Req(apiUrl, request.getPathInfo)
    } yield (updated, req)
  }

  def handleSuccess: PartialFunction[Error \/ (Success, Req), ActionResult] = {
    case \/-((success, url)) => success match {
      case CreatedAvatar(avatar) => Created(AvatarResponse(avatar, url))
      case FoundAvatar(avatar) => Ok(AvatarResponse(avatar, url))
      case FoundAvatars(avatars, hasMore) => Ok(AvatarsResponse(avatars, url, hasMore, pageSize))
      case UpdatedAvatar(avatar) => Ok(AvatarResponse(avatar, url))
      case ud: UserDeleted => Ok(DeletedUserResponse(None, ud, Nil))
      case uc: UserCleaned => Ok(CleanedUserResponse(None, uc, Nil))
    }
  }

  def handleError[A]: PartialFunction[\/[Error, A], ActionResult] = {
    case -\/(error) =>
      val response: ActionResult =
        error match {
          case InvalidContentType(msg, errors) => UnsupportedMediaType(ErrorResponse(msg, errors))
          case InvalidFilters(msg, errors) => BadRequest(ErrorResponse(msg, errors))
          case AvatarNotFound(msg, errors) => NotFound(ErrorResponse(msg, errors))
          case DynamoRequestFailed(msg, errors) => ServiceUnavailable(ErrorResponse(msg, errors))
          case TokenAuthorizationFailed(msg, errors) => Unauthorized(ErrorResponse(msg, errors))
          case UserAuthorizationFailed(msg, errors) => Unauthorized(ErrorResponse(msg, errors))
          case IOFailed(msg, errors) => ServiceUnavailable(ErrorResponse(msg, errors))
          case InvalidUserId(msg, errors) => BadRequest(ErrorResponse(msg, errors))
          case UnableToReadStatusRequest(msg, errors) => BadRequest(ErrorResponse(msg, errors))
          case UnableToReadAvatarRequest(msg, errors) => BadRequest(ErrorResponse(msg, errors))
          case InvalidIsSocialFlag(msg, errors) => BadRequest(ErrorResponse(msg, errors))
          case InvalidMimeType(msg, errors) => BadRequest(ErrorResponse(msg, errors))
          case UserDeletionFailed(msg, errors) => InternalServerError(ErrorResponse(msg, errors))
        }
      logError(msg = "Returning HTTP error", e = error, statusCode = Some(response.status.code))

      response
  }

  def getIsSocial(param: Option[String]): Error \/ Boolean = {
    attempt(param.exists(_.toBoolean)).leftMap(_ => invalidIsSocialFlag(NonEmptyList(s"'${param.get}' is not a valid isSocial flag")))
  }

  def uploadAvatar(request: RichRequest, user: User, fileParams: Map[String, FileItem]): Error \/ CreatedAvatar = {
    request.contentType match {
      case Some("application/json") | Some("text/json") =>
        for {
          req <- avatarRequestFromBody(request.body)
          bytesAndMimeType <- getImageFromUrl(req.url)
          (bytes, mimeType) = bytesAndMimeType
          upload <- store.userUpload(user, bytes, mimeType, req.url, req.isSocial)
        } yield upload
      case Some(s) if s startsWith "multipart/form-data" =>
        for {
          bytesAndMimeTypeAndFname <- getImageFromFile(fileParams)
          (bytes, mimeType, fname) = bytesAndMimeTypeAndFname
          isSocial <- getIsSocial(request.parameters.get("isSocial"))
          upload <- store.userUpload(user, bytes, mimeType, fname, isSocial)
        } yield upload
      case Some(invalid) =>
        -\/(invalidContentType(NonEmptyList(s"'$invalid' is not a valid content type.")))
      case None =>
        -\/(invalidContentType(NonEmptyList("No content type specified.")))
    }
  }

  def avatarRequestFromBody(body: String): Error \/ AvatarRequest = {
    attempt(parse(body).extract[AvatarRequest])
      .leftMap(_ => unableToReadAvatarRequest(NonEmptyList("Could not parse request body")))
  }

  def statusRequestFromBody(parsedBody: JValue): Error \/ StatusRequest = {
    attempt(parsedBody.extract[StatusRequest])
      .leftMap(_ => unableToReadStatusRequest(NonEmptyList("Could not parse request body")))
  }

}

case class AvatarServletProperties(
  apiKeys: List[String],
  apiUrl: String,
  cookieDecoder: GuUDecoder,
  pageSize: Int,
  snsTopicArn: String
)
