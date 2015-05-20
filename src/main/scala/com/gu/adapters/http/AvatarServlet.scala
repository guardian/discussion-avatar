package com.gu.adapters.http

import java.io.InputStream

import com.gu.adapters.http.CookieDecoder.userFromCookie
import com.gu.adapters.store.{AvatarStore,QueryResponse}
import com.gu.adapters.http.ImageValidator.validate
import com.gu.adapters.store.AvatarStore
import com.gu.adapters.utils.Attempt.attempt
import com.gu.adapters.utils.InputStreamToByteArray
import com.gu.core.Errors._
import com.gu.core.{Success, _}
import com.gu.identity.cookie.IdentityCookieDecoder
import org.joda.time.DateTime
import org.json4s.JsonAST.JValue
import org.json4s.ext.JodaTimeSerializers
import org.json4s.{DefaultFormats, Formats}
import org.scalatra._
import org.scalatra.json.JacksonJsonSupport
import org.scalatra.servlet._
import org.scalatra.swagger.{Swagger, SwaggerSupport}

import scalaz.{-\/, NonEmptyList, \/, \/-}

class AvatarServlet(store: AvatarStore, decoder: IdentityCookieDecoder)(implicit val swagger: Swagger)
  extends ScalatraServlet
  with JacksonJsonSupport
  with SwaggerSupport
  with SwaggerOps
  with FileUploadSupport
  with CorsSupport {

  val apiUrl = Config.apiUrl
  val pageSize = Config.pageSize

  protected implicit val jsonFormats: Formats =
    DefaultFormats +
      new StatusSerializer ++
      JodaTimeSerializers.all

  configureMultipartHandling(MultipartConfig(maxFileSize = Some(1024*1024)))

  before() {
    contentType = formats("json")
  }

  methodNotAllowed { _ =>
    if (routes.matchingMethodsExcept(Options, requestPath).isEmpty)
      doNotFound() // correct for options("*") CORS behaviour
    else
      MethodNotAllowed(ErrorResponse("Method not supported", Nil))
  }

  error {
    case e: SizeConstraintExceededException =>
      RequestEntityTooLarge(
        ErrorResponse("File exceeds size limit: images must be no more than 1mb in size", Nil))
  }

  notFound {
    NotFound(ErrorResponse("Requested resource not found", Nil))
  }

  options("/*") {
    response.setHeader(
      "Access-Control-Allow-Headers",
      request.getHeader("Access-Control-Request-Headers"))
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
        qr <- store.get(filters)
      } yield FoundAvatars(qr.avatars, qr.hasMore)
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
        qr <- store.get(user)
      } yield FoundAvatars(qr.avatars, qr.hasMore)
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

  post("/migratedAvatar", operation(postMigratedAvatar)) {
    withErrorHandling {
      for {
        mr <- migrateRequestFromBody(parsedBody)
        user <- userFromRequest(mr.userId.toString)
        avatar <- store.fetchMigratedImages(user, mr.image, mr.processedImage, mr.originalFilename, mr.createdAt, mr.isSocial)
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

  def withErrorHandling(response: => \/[Error, Success]): ActionResult = {
    (handleSuccess orElse handleError)(response)
  }

  def links(avatars: List[Avatar], hasMore: Boolean): List[Link] = {

    val cursor = avatars.lift(pageSize-1).map(_.id)
    val first = avatars.headOption.map(_.id)
    val status = params.get("status").map(s => s"status=$s&").getOrElse("")

    val next = for (c <- cursor if hasMore) yield Link("next", s"$apiUrl${request.getPathInfo}?${status}since=$c")
    val prev = for (f <- first if List("since", "until") exists params.contains) yield Link("prev", s"$apiUrl${request.getPathInfo}?${status}until=$f")
    List(prev, next).flatten
  }

  def handleSuccess: PartialFunction[\/[Error, Success], ActionResult] = {
    case \/-(success) => success match {
      case CreatedAvatar(avatar) => Created(avatar)
      case FoundAvatar(avatar) => Ok(AvatarResponse(apiUrl, avatar, Nil))
      case FoundAvatars(avatars, hasMore) => Ok(AvatarsResponse(apiUrl, avatars, links(avatars, hasMore)))
      case okay => Ok(okay.body)
    }
  }

  def handleError[A]: PartialFunction[\/[Error, A], ActionResult] = {
    case -\/(error) => error match {
      case InvalidContentType(msg, errors) => UnsupportedMediaType(ErrorResponse(msg, errors.list))
      case InvalidFilters(msg, errors) => BadRequest(ErrorResponse(msg, errors.list))
      case AvatarNotFound(msg, errors) => NotFound(ErrorResponse(msg, errors.list))
      case DynamoRequestFailed(msg, errors) => ServiceUnavailable(ErrorResponse(msg, errors.list))
      case UnableToReadUserCookie(msg, errors) => BadRequest(ErrorResponse(msg, errors.list))
      case IOFailed(msg, errors) => ServiceUnavailable(ErrorResponse(msg, errors.list))
      case InvalidUserId(msg, errors) => BadRequest(ErrorResponse(msg, errors.list))
      case UnableToReadStatusRequest(msg, errors) => BadRequest(ErrorResponse(msg, errors.list))
      case UnableToReadMigratedAvatarRequest(msg, errors) => BadRequest(ErrorResponse(msg, errors.list))
      case UnableToReadAvatarRequest(msg, errors) => BadRequest(ErrorResponse(msg, errors.list))
      case AvatarAlreadyExists(msg, errors) => Conflict(ErrorResponse(msg, errors.list))
      case InvalidMimeType(msg, errors) => BadRequest(ErrorResponse(msg, errors.list))
    }
  }

  def uploadAvatar(request: RichRequest, user: User, fileParams: Map[String, FileItem]): Error \/ Avatar = {
    request.contentType match {
      case Some("application/json") | Some("text/json") =>
        for {
          req <- avatarRequestFromBody(request.body)
          file <- fileFromUrl(user, req.url)
          image <- validate(file)
          upload <- store.userUpload(user, InputStreamToByteArray(image), req.url, true)
        } yield upload
      case Some(s) if s startsWith "multipart/form-data" =>
        for {
          fr <- fileFromBody(fileParams)
          image <- validate(fr._2)
          upload <- store.userUpload(user, InputStreamToByteArray(image), fr._1, true)
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

  def fileFromUrl(user: User, url: String): Error \/ InputStream = {
    attempt(new java.net.URL(url).openStream())
      .leftMap(_ => ioFailed(NonEmptyList("Unable to load image from url: " + url)))
  }

  def fileFromBody(fileParams: Map[String, FileItem]): Error \/ (String, InputStream) = {
    for {
      file <- attempt(fileParams("image"))
        .leftMap(_ => unableToReadAvatarRequest(NonEmptyList("Could not parse request body")))
    } yield (file.getName, file.getInputStream)
  }

  def statusRequestFromBody(parsedBody: JValue): Error \/ StatusRequest = {
    attempt(parsedBody.extract[StatusRequest])
      .leftMap(_ => unableToReadStatusRequest(NonEmptyList("Could not parse request body")))
  }

  def migrateRequestFromBody(parsedBody: JValue): Error \/ MigratedAvatarRequest = {
    attempt(parsedBody.extract[MigratedAvatarRequest])
      .leftMap(_ => unableToReadMigratedAvatarRequest(NonEmptyList("Could not parse request body")))
  }

  def userFromRequest(userId: String): Error \/ User = {
    attempt(User(userId.toInt))
      .leftMap(_ => invalidUserId(NonEmptyList("Expected integer, found: " + userId)))
  }
}
