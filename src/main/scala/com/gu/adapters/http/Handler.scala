package com.gu.adapters.http

import com.gu.adapters.store.Store
import com.gu.entities.Errors._
import com.gu.entities._
import org.json4s.jackson.JsonMethods.parse
import org.scalatra._
import org.scalatra.servlet.FileItem

import scalaz.{-\/, NonEmptyList, \/, \/-}

object Handler {

  def getOrError(response: \/[Error, Any]): ActionResult = {
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

  def healthcheck(): ActionResult = Ok(Message("OK"))

  def gtg(): ActionResult = NotImplemented(Message("Endpoint needs to be specified"))

  def dependencies(): ActionResult = NotImplemented(Message("Endpoint needs to be specified"))

  def getAvatars(store: Store, params: Params): ActionResult = {
    val filters = Filters.fromParams(params)
    val avatars = filters flatMap store.get
    getOrError(avatars)
  }

  def getAvatar(store: Store, avatarId: String): ActionResult = {
    val avatar = store.get(avatarId)
    getOrError(avatar)
  }

  def getAvatarsForUser(store: Store, userId: String): ActionResult = {
    val user = User(userId.toInt) // TODO handle errors here gracefully
    val avatar = store.get(user)
    getOrError(avatar)
  }

  def getActiveAvatarForUser(store: Store, user: User): ActionResult = {
    val avatar = store.getActive(user)
    getOrError(avatar)
  }

  def postAvatar(
    store: Store,
    contentType: Option[String],
    body: String,
    image: FileItem): ActionResult = {

    //    val cd = new IdentityCookieDecoder(new ProductionKeys)

    //    for {
    //      cookie <- request.cookies.get("GU_U")
    //      user <- cd.getUserDataForGuU(cookie).map(_.user)
    //      username <- user.publicFields.displayName
    //    }

    val user = User("123456".toInt)

    val avatar = contentType match {
      case Some("application/json") | Some("text/json") => store.fetchImage(user, (parse(body) \ "url").values.toString)
      case Some(s) if s startsWith "multipart/form-data" => store.userUpload(user, image)
      case Some(invalid) => -\/(invalidContentType(NonEmptyList(s"'$invalid' is not a valid content type. Must be 'multipart/form-data' or 'application/json'.")))
    }

    getOrError(avatar)
  }

  def putAvatarStatus(store: Store, body: String, avatarId: String): ActionResult = {
    val status = Status((parse(body) \ "status").values.toString) // TODO handle errors gracefully here
    val avatar = store.updateStatus(avatarId, status)
    getOrError(avatar)
  }
}
