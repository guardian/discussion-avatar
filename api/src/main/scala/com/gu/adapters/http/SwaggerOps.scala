package com.gu.adapters.http

import com.gu.core.models.{Avatar, User}
import org.scalatra.swagger._

trait SwaggerOps {
  this: SwaggerSupport =>

  def applicationDescription =
    "The Avatar API. Exposes operations for viewing, adding, and moderating Avatars"

  def getAvatars =
    apiOperation[List[Avatar]]("getAvatars")
      .summary("List all avatars")
      .parameter(queryParam[Option[String]]("status")
        .description("The request includes a status to filter by"))

  def getAvatar =
    apiOperation[Avatar]("getAvatar")
      .summary("Retrieve avatar by ID")

  def getAvatarsForUser =
    apiOperation[List[Avatar]]("getAvatarsForUser")
      .summary("Get avatars for user")
      .parameter(pathParam[Int]("userId")
        .description("The request includes the userId"))

  def getActiveAvatarForUser =
    apiOperation[List[Avatar]]("getActiveAvatarForUser")
      .summary("Get active avatar for user")
      .parameter(pathParam[Int]("userId"))

  def getPersonalAvatarForUser =
    apiOperation[List[Avatar]]("getPersonalAvatarForUser")
      .summary("Get personal avatar for user. This is a non-public endpoint, user cookie must be provided.")

  def postAvatar =
    apiOperation[Avatar]("postAvatar")
      .summary("Add a new avatar")
      .consumes("multipart/form-data")
      .parameters(
        bodyParam[AvatarRequest]("")
          .description("The request includes ")
      )

  def putAvatarStatus =
    apiOperation[Avatar]("putAvatarStatus")
      .summary("Update avatar status")
      .parameters(
        pathParam[String]("id")
          .description("The request includes the Avatar ID"),
        bodyParam[StatusRequest]("")
          .description("The request includes the Avatar's new status")
      )

  def deleteUserPermanently =
    apiOperation[User]("deleteUserPermanently")
      .summary("Delete all of a user's avatar data. Cannot be undone!")
      .parameter(pathParam[Int]("userId"))
      .parameter(queryParam[Boolean]("dryRun"))

  def cleanupUser =
    apiOperation[User]("cleanupUser")
      .summary("Delete all data for non-active avatars iff the user has an active avatar")
      .parameter(pathParam[Int]("userId"))
      .parameter(queryParam[Boolean]("dryRun"))
}