package com.gu.adapters.http

import com.gu.adapters.config.Config
import com.gu.adapters.http.Image._
import com.gu.adapters.notifications.{Notifications, Publisher}
import com.gu.core.models.Errors._
import com.gu.core.models._
import com.gu.core.store.AvatarStore
import com.gu.core.utils.ErrorHandling.{attempt, logError}
import com.typesafe.scalalogging.LazyLogging
import org.json4s.JsonAST.JValue
import org.json4s.jackson.Serialization.write
import org.scalatra._
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.servlet._
import org.scalatra.swagger.{Swagger, SwaggerSupport}

class AvatarServlet(
  store: AvatarStore,
  publisher: Publisher,
  props: AvatarServletProperties,
  authenticationService: AuthenticationService
)(implicit val swagger: Swagger)
  extends ScalatraServlet
  with ServletWithErrorHandling[Error, Success]
  with AuthorizedApiServlet[Success]
  with JacksonJsonSupport
  with SwaggerSupport
  with SwaggerOps
  with FileUploadSupport
  with CorsSupport
  with LazyLogging {

  import CorsSupport._

  // When upgrading the API to Scala 2.12, we had to upgrade Scalatra from 2.3 to 2.6,
  // since 2.3 wasn't available for Scala 2.12.
  // The CorsSupport functionality differs in 2.6.
  // Of relevance, the Access-Control-Allow-Origin head is only set
  // if the Origin header is present AND it is in the configured set of allowed origins,
  // in contrast to 2.3 where it is set if the Origin header is present.
  // Ultimately we want to establish a set of allowed domains (work has been done towards this goal)
  // but as a stop gap, simulate the behaviour of 2.3.
  override protected def augmentSimpleRequest(): Unit = {
    super.augmentSimpleRequest()
    if (response.getHeader(AccessControlAllowOriginHeader).isEmpty) {
      response.setHeader(AccessControlAllowOriginHeader, request.headers.getOrElse(OriginHeader, ""))
    }
  }

  val apiUrl = props.apiUrl
  val pageSize = props.pageSize
  val apiKeys = props.apiKeys
  val snsTopicArn = props.snsTopicArn

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
    case _: SizeConstraintExceededException =>
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

  apiDelete("/service/data/:userId", operation(deleteUserPermanently)) { _ =>
    for {
      user <- User.userFromId(params("userId"))
      deleted <- store.deleteAll(user)
      req = Req(apiUrl, request.getPathInfo)
    } yield (deleted, req)
  }

  apiPut("/avatars/user/:userId/cleanup", operation(cleanupUser)) { _ =>
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

  apiGet("/avatars", operation(getAvatars)) { _ =>
    for {
      filters <- Filter.fromParams(params)
      avatar <- store.get(filters)
      url = Req(apiUrl, request.getPathInfo, filters)
    } yield (avatar, url)
  }

  apiGet("/avatars/:id", operation(getAvatar)) { _ =>
    for {
      avatar <- store.get(params("id"))
      req = Req(apiUrl, request.getPathInfo)
    } yield (avatar, req)
  }

  apiGet("/avatars/user/:userId", operation(getAvatarsForUser)) { _ =>
    for {
      user <- User.userFromId(params("userId"))
      avatar <- store.get(user)
      req = Req(apiUrl, request.getPathInfo)
    } yield (avatar, req)
  }

  apiGet("/avatars/user/:userId/active", operation(getActiveAvatarForUser)) { _ =>
    for {
      user <- User.userFromId(params("userId"))
      active <- store.getActive(user)
      req = Req(apiUrl, request.getPathInfo)
    } yield (active, req)
  }

  getWithErrors("/avatars/user/me/active", operation(getPersonalAvatarForUser)) {
    for {
      user <- authenticationService.authenticateUser(request.cookies.get(Config.secureCookie), request.headers.get("Authorization"), AccessScope.readSelf)
      avatar <- store.getPersonal(user)
      req = Req(apiUrl, request.getPathInfo)
    } yield (avatar, req)
  }

  postWithErrors("/avatars", operation(postAvatar)) {
    for {
      user <- authenticationService.authenticateUser(request.cookies.get(Config.secureCookie), request.headers.get("Authorization"), AccessScope.updateSelf)
      created <- uploadAvatar(request, user, fileParams)
      req = Req(apiUrl, request.getPathInfo)
    } yield {
      Notifications.publishAvatar(publisher, snsTopicArn, "Avatar Upload", created)
      (created, req)
    }
  }

  apiPut("/avatars/:id/status", operation(putAvatarStatus)) { _ =>
    for {
      sr <- statusRequestFromBody(parsedBody)
      updated <- store.updateStatus(params("id"), sr.status)
      req = Req(apiUrl, request.getPathInfo)
    } yield (updated, req)
  }

  def handleSuccess: PartialFunction[Either[Error, (Success, Req)], ActionResult] = {
    case Right((success, url)) => success match {
      case CreatedAvatar(avatar) => Created(AvatarResponse(avatar, url))
      case FoundAvatar(avatar) => Ok(AvatarResponse(avatar, url))
      case FoundAvatars(avatars, hasMore) => Ok(AvatarsResponse(avatars, url, hasMore, pageSize))
      case UpdatedAvatar(avatar) => Ok(AvatarResponse(avatar, url))
      case ud: UserDeleted => Ok(DeletedUserResponse(None, ud, Nil))
      case uc: UserCleaned => Ok(CleanedUserResponse(None, uc, Nil))
    }
  }

  def handleError[A]: PartialFunction[Either[Error, A], ActionResult] = {
    case Left(error) =>
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
          case OAuthTokenAuthorizationFailed(msg, errors, statusCode) =>
            statusCode match {
              case 400 => BadRequest(ErrorResponse(msg, errors))
              case 401 => Unauthorized(ErrorResponse(msg, errors))
              case 403 => Forbidden(ErrorResponse(msg, errors))
              case _ => InternalServerError(ErrorResponse(msg, errors))
            }
        }
      logError(msg = "Returning HTTP error", e = error, statusCode = Some(response.status))

      response
  }

  def getIsSocial(param: Option[String]): Either[Error, Boolean] = {
    attempt(param.exists(_.toBoolean)).toEither
      .left.map(_ => invalidIsSocialFlag(List(s"'${param.get}' is not a valid isSocial flag")))
  }

  def uploadAvatar(request: RichRequest, user: User, fileParams: FileSingleParams): Either[Error, CreatedAvatar] = {
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
        Left(invalidContentType(List(s"'$invalid' is not a valid content type.")))
      case None =>
        Left(invalidContentType(List("No content type specified.")))
    }
  }

  def avatarRequestFromBody(body: String): Either[Error, AvatarRequest] = {
    attempt(parse(body).extract[AvatarRequest])
      .toEither
      .left.map(_ => unableToReadAvatarRequest(List("Could not parse request body")))
  }

  def statusRequestFromBody(parsedBody: JValue): Either[Error, StatusRequest] = {
    attempt(parsedBody.extract[StatusRequest])
      .toEither
      .left.map(_ => unableToReadStatusRequest(List("Could not parse request body")))
  }

}

case class AvatarServletProperties(
  apiKeys: List[String],
  apiUrl: String,
  pageSize: Int,
  snsTopicArn: String
)
