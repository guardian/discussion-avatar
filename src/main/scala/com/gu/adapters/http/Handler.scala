package com.gu.adapters.http

import com.gu.adapters.store.Store
import com.gu.core.Errors._
import com.gu.core._
import org.json4s.jackson.JsonMethods.parse
import org.scalatra._
import org.scalatra.servlet.FileItem

import scalaz.{-\/, NonEmptyList, \/, \/-}

object Handler {

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

  def healthcheck(): ActionResult = Ok(Message("OK"))

  def gtg(): ActionResult = NotImplemented(Message("Endpoint needs to be specified"))

  def dependencies(): ActionResult = NotImplemented(Message("Endpoint needs to be specified"))

  def getAvatars(store: Store, params: Params): ActionResult = {
    withErrorHandling {
      val filters = Filters.fromParams(params)
      filters flatMap store.get
    }
  }

  def getAvatar(store: Store, avatarId: String): ActionResult = {
    withErrorHandling {
      store.get(avatarId)
    }
  }

  def getAvatarsForUser(store: Store, userId: String): ActionResult = {
    withErrorHandling {
      val user = User(userId.toInt) // TODO handle errors here gracefully
      store.get(user)
    }
  }

  def getActiveAvatarForUser(store: Store, user: User): ActionResult = {
    withErrorHandling {
      store.getActive(user)
    }
  }

  def postAvatar(
    store: Store,
    contentType: Option[String],
    body: String,
    image: FileItem): ActionResult = {

    withErrorHandling {
      //    val cd = new IdentityCookieDecoder(new ProductionKeys)

      //    for {
      //      cookie <- request.cookies.get("GU_U")
      //      user <- cd.getUserDataForGuU(cookie).map(_.user)
      //      username <- user.publicFields.displayName
      //    }

      val user = User("123456".toInt)

      contentType match {
        case Some("application/json") | Some("text/json") => store.fetchImage(user, (parse(body) \ "url").values.toString)
        case Some(s) if s startsWith "multipart/form-data" => store.userUpload(user, image)
        case Some(invalid) => -\/(invalidContentType(NonEmptyList(s"'$invalid' is not a valid content type. Must be 'multipart/form-data' or 'application/json'.")))
      }
    }
  }

  def putAvatarStatus(store: Store, body: String, avatarId: String): ActionResult = {
    withErrorHandling {
      val status = Status((parse(body) \ "status").values.toString) // TODO handle errors gracefully here
      store.updateStatus(avatarId, status)
    }
  }
}
