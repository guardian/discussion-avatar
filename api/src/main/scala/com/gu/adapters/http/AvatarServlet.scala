package com.gu.adapters.http

import com.gu.adapters.http.CookieDecoder.userFromHeader
import com.gu.adapters.http.ImageValidator.validate
import com.gu.adapters.http.TokenAuth.isValidKey
import com.gu.adapters.notifications.{ Notifications, Publisher }
import com.gu.adapters.store.AvatarStore
import com.gu.adapters.utils.ErrorHandling.{ attempt, logError }
import com.gu.adapters.utils.IO.{ readBytesFromFile, readBytesFromUrl }
import com.gu.core.Errors._
import com.gu.core.{ Success, _ }
import com.gu.identity.cookie.IdentityCookieDecoder
import org.json4s.JsonAST.JValue
import org.json4s.jackson.Serialization.write
import org.scalatra._
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.servlet._
import org.scalatra.swagger.{ Swagger, SwaggerSupport }
import com.gu.adapters.utils.ErrorHandling.attempt

import scalaz.{ Success => _, _ }

class AvatarServlet(store: AvatarStore, publisher: Publisher, props: AvatarServletProperties)(implicit val swagger: Swagger)
    extends ScalatraServlet
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

  get("/avatars", operation(getAvatars)) {
    withErrorHandling {
      for {
        auth <- isValidKey(request.header("Authorization"), apiKeys)
        filters <- Filters.fromParams(params)
        avatar <- store.get(filters)
        url = Req(apiUrl, request.getPathInfo, filters)
      } yield (avatar, url)
    }
  }

  get("/avatars/:id", operation(getAvatar)) {
    withErrorHandling {
      for {
        auth <- isValidKey(request.header("Authorization"), apiKeys)
        avatar <- store.get(params("id"))
        req = Req(apiUrl, request.getPathInfo)
      } yield (avatar, req)
    }
  }

  get("/avatars/user/:userId", operation(getAvatarsForUser)) {
    withErrorHandling {
      for {
        auth <- isValidKey(request.header("Authorization"), apiKeys)
        user <- userFromRequest(params("userId"))
        avatar <- store.get(user)
        req = Req(apiUrl, request.getPathInfo)
      } yield (avatar, req)
    }
  }

  get("/avatars/user/:userId/active", operation(getActiveAvatarForUser)) {
    withErrorHandling {
      for {
        auth <- isValidKey(request.header("Authorization"), apiKeys)
        user <- userFromRequest(params("userId"))
        active <- store.getActive(user)
        req = Req(apiUrl, request.getPathInfo)
      } yield (active, req)
    }
  }

  get("/avatars/user/me/active", operation(getPersonalAvatarForUser)) {
    withErrorHandling {
      for {
        user <- userFromHeader(decoder, request.header("Authorization"))
        avatar <- store.getPersonal(user)
        req = Req(apiUrl, request.getPathInfo)
      } yield (avatar, req)
    }
  }

  post("/avatars", operation(postAvatar)) {
    withErrorHandling {
      for {
        user <- userFromHeader(decoder, request.header("Authorization"))
        created <- uploadAvatar(request, user, fileParams)
        req = Req(apiUrl, request.getPathInfo)
      } yield {
        Notifications.publishAvatar(publisher, snsTopicArn, "Avatar Upload", created)
        (created, req)
      }
    }
  }

  post("/migrateAvatar", operation(postMigratedAvatar)) {
    withErrorHandling {
      for {
        auth <- isValidKey(request.header("Authorization"), apiKeys)
        created <- fetchMigratedAvatar(request)
        req = Req(apiUrl, request.getPathInfo)
      } yield (created, req)
    }
  }

  put("/avatars/:id/status", operation(putAvatarStatus)) {
    withErrorHandling {
      for {
        auth <- isValidKey(request.header("Authorization"), apiKeys)
        sr <- statusRequestFromBody(parsedBody)
        updated <- store.updateStatus(params("id"), sr.status)
        req = Req(apiUrl, request.getPathInfo)
      } yield (updated, req)
    }
  }

  def withErrorHandling(response: => \/[Error, (Success, Req)]): ActionResult = {
    (handleSuccess orElse handleError)(response)
  }

  def handleSuccess: PartialFunction[\/[Error, (Success, Req)], ActionResult] = {
    case \/-((success, url)) => success match {
      case CreatedAvatar(avatar) => Created(AvatarResponse(avatar, url))
      case FoundAvatar(avatar) => Ok(AvatarResponse(avatar, url))
      case FoundAvatars(avatars, hasMore) => Ok(AvatarsResponse(avatars, url, hasMore, pageSize))
      case UpdatedAvatar(avatar) => Ok(AvatarResponse(avatar, url))
      case MigratedAvatar(avatar) => Created(AvatarResponse(avatar, url))
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
          case AvatarAlreadyExists(msg, errors) => Conflict(ErrorResponse(msg, errors))
          case UnableToReadMigratedAvatarRequest(msg, errors) => BadRequest(ErrorResponse(msg, errors))
        }
      logError(msg = "Returning HTTP error", e = error, statusCode = Some(response.status.code))

      response
  }

  def getUrl(url: String): Error \/ (Array[Byte], String) = {
    readBytesFromUrl(url) flatMap {
      case bytes => validate(bytes).map(mt => (bytes, mt))
    }
  }

  def getFile(fileParams: Map[String, FileItem]): Error \/ (Array[Byte], String, String) = {
    readBytesFromFile(fileParams) flatMap {
      case (fname, bytes) => validate(bytes).map(mt => (bytes, mt, fname))
    }
  }

  def getIsSocial(param: Option[String]): Error \/ Boolean = {
    attempt(param.exists(_.toBoolean)).leftMap(_ => invalidIsSocialFlag(NonEmptyList(s"'${param.get}' is not a valid isSocial flag")))
  }

  def uploadAvatar(request: RichRequest, user: User, fileParams: Map[String, FileItem]): Error \/ CreatedAvatar = {
    request.contentType match {
      case Some("application/json") | Some("text/json") =>
        for {
          req <- avatarRequestFromBody(request.body)
          bytesAndMimeType <- getUrl(req.url)
          (bytes, mimeType) = bytesAndMimeType
          upload <- store.userUpload(user, bytes, mimeType, req.url, req.isSocial)
        } yield upload
      case Some(s) if s startsWith "multipart/form-data" =>
        for {
          bytesAndMimeTypeAndFname <- getFile(fileParams)
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

  def fetchMigratedAvatar(request: RichRequest): Error \/ CreatedAvatar = {
    for {
      req <- migrateRequestFromBody(request.body)
      user <- userFromRequest(req.userId.toString)
      imageBytesAndMimeType <- getUrl(req.image)
      (imageBytes, imageMimeType) = imageBytesAndMimeType
      processedImageBytesAndMimeType <- getUrl(req.processedImage)
      (processedImageBytes, processedImageMimeType) = processedImageBytesAndMimeType
      upload <- store.migratedUserUpload(user, imageBytes, imageMimeType, processedImageBytes, processedImageMimeType, req.originalFilename, Status(req.status), req.createdAt, req.isSocial)
    } yield upload
  }

  def avatarRequestFromBody(body: String): Error \/ AvatarRequest = {
    attempt(parse(body).extract[AvatarRequest])
      .leftMap(_ => unableToReadAvatarRequest(NonEmptyList("Could not parse request body")))
  }

  def statusRequestFromBody(parsedBody: JValue): Error \/ StatusRequest = {
    attempt(parsedBody.extract[StatusRequest])
      .leftMap(_ => unableToReadStatusRequest(NonEmptyList("Could not parse request body")))
  }

  def migrateRequestFromBody(body: String): Error \/ MigratedAvatarRequest = {
    attempt(parse(body).extract[MigratedAvatarRequest])
      .leftMap(_ => unableToReadMigratedAvatarRequest(NonEmptyList("Could not parse request body")))
  }

  def userFromRequest(userId: String): Error \/ User = {
    attempt(User(userId.toInt))
      .leftMap(_ => invalidUserId(NonEmptyList("Expected integer, found: " + userId)))
  }
}

case class AvatarServletProperties(
  apiKeys: List[String],
  apiUrl: String,
  cookieDecoder: IdentityCookieDecoder,
  pageSize: Int,
  snsTopicArn: String
)
