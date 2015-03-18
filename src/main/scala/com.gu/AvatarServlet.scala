package com.gu

import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json._
import org.scalatra.swagger.{SwaggerSupport, Swagger}
import org.scalatra.{NotImplemented, Ok, Params, ScalatraServlet}

import scalaz.Scalaz._
import scalaz.{NonEmptyList, ValidationNel, \/}

class AvatarServlet(implicit val swagger: Swagger)
  extends ScalatraServlet
  with JacksonJsonSupport
  with SwaggerSupport {

  implicit def nelToList[A](nel: NonEmptyList[A]): List[A] = nel.list

  protected implicit val jsonFormats: Formats = DefaultFormats

  protected val applicationDescription = "The Avatar API. Exposes operations for viewing, adding, and moderating Avatars"

  before() {
    contentType = formats("json")
  }

  // Management endpoints

  get("/management/healthcheck") {
    Ok(Message("OK"))
  }

  get("/management/gtg") {
    NotImplemented(Message("Endpoint needs to be specified"))
  }

  get("/management/dependencies") {
    NotImplemented(Message("Endpoint needs to be specified"))
  }

  // Avatar endpoints

  val getAvatars =
    (apiOperation[List[Avatar]]("getAvatars")
      summary "List all avatars"
      parameter (queryParam[Option[String]]("status")
        .description("The request includes a status to filter by")))

  get("/avatars", operation(getAvatars)) {
    val filters = Filters.fromParams(params).disjunction
    val avatars = filters flatMap AvatarRepository.get
    getOrError(avatars)
  }

  val getAvatar =
    (apiOperation[Avatar]("getAvatar")
      summary "Retrieve avatar by ID")

  get("/avatars/:id", operation(getAvatar)) {
    val avatar = AvatarRepository.get(params("id"))
    getOrError(avatar)
  }

  val getActiveAvatarForUser =
    (apiOperation[Avatar]("getActiveAvatarForUser")
      summary "Get active avatar for user")

  get("/avatars/user/:userId/active", operation(getActiveAvatarForUser)) {
    val user = User(params("userId"))
    val avatar = AvatarRepository.get(user)
    getOrError(avatar)
  }

  // TODO, add a browser-usable endpoint (i.e. multipart/form-data)
  val postAvatar =
    (apiOperation[Avatar]("postAvatar")
      summary "Add a new avatar"
      parameter (bodyParam[AvatarRequest]("")
        .description("The request includes the new Avatar's details")))

  post("/avatars", operation(postAvatar)) {
    val avatar = AvatarRepository.get("123") // hack for now
    getOrError(avatar)
  }

  val putAvatarStatus =
    (apiOperation[Avatar]("putAvatarStatus")
      summary "Update avatar status"
      parameters (
        pathParam[String]("id")
          .description("The request includes the Avatar ID"),
        bodyParam[StatusRequest]("")
          .description("The request includes the Avatar's new status")))

  put("/avatars/:id/status", operation(putAvatarStatus)) {
    val avatar = AvatarRepository.get("123") // hack for now
    getOrError(avatar)
  }

  def getOrError[A](r: \/[NonEmptyList[String], A]): Any =
    r valueOr (errors => ErrorResponse(errors))
}

sealed trait RequestParams
case class AvatarRequest(
  userId: Int,
  originalFilename: String,
  status: Status,
  image: String // Note, base64 encoded
)
case class StatusRequest(status: Status)

sealed trait ApiResponse
case class AvatarList(avatars: List[Avatar]) extends ApiResponse
case class Message(message: String) extends ApiResponse
case class ErrorResponse(errors: List[String]) extends ApiResponse

sealed trait Status
case object All extends Status
case object Approved extends Status
case object Rejected extends Status

// no wrappers
// error responses in body
// pagination in headers
// today - basic model with error handling
// tomorrow database / s3
// after, pagination
//

case class Error(
  message: String,
  errors: NonEmptyList[Error]
)

case class User(id: String)

case class Avatar(
  id: String,
  avatarUrl: String,
  userId: Int,
  originalFilename: String,
  status: Status
)

case class Filters(status: Status)

object Filters {
  def fromParams(params: Params): ValidationNel[String, Filters] = {
    val status: ValidationNel[String, Status] = params.get("status") match {
      case Some("approved") => Approved.success
      case Some("rejected") => Rejected.success
      case Some("all") => All.success
      case Some(invalid) => s"'$invalid' is not a valid status type. Must be 'pending', 'approved', or 'all'.".failureNel
      case None => All.success
    }

    status map Filters.apply
  }
}

// note, need to pass in connection to prevent singleton trap
sealed trait Repository {
  def get(filters: Filters): \/[NonEmptyList[String], List[Avatar]]
  def get(id: String): \/[NonEmptyList[String], Avatar]
  def get(user: User): \/[NonEmptyList[String], Avatar]
//  def get(userId: String, filters: Filters): List[Avatar]
//  def getActive(userId: String): Option[Avatar]
}

object AvatarRepository extends Repository {
  val avatars = List(
    Avatar("123-id", "http://avatar-url-1", 123, "foo.gif", Approved),
    Avatar("abc-id", "http://avatar-url-2", 234, "bar.gif", Approved)
  )

  def get(filters: Filters): \/[NonEmptyList[String], List[Avatar]] = avatars.right
  def get(id: String): \/[NonEmptyList[String], Avatar] = avatars.head.right
  def get(user: User): \/[NonEmptyList[String], Avatar] = avatars.head.right
}
