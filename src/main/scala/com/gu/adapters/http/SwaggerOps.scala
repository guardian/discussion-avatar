package com.gu.adapters.http

import java.util.Date

import com.gu.core.Avatar
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
          .description("The request includes "))

  def putAvatarStatus =
    apiOperation[Avatar]("putAvatarStatus")
      .summary("Update avatar status")
      .parameters(
        pathParam[String]("id")
          .description("The request includes the Avatar ID"),
        bodyParam[StatusRequest]("")
          .description("The request includes the Avatar's new status"))

  def postMigratedAvatar =
    apiOperation[Avatar]("postMigratedAvatar")
      .summary("Post an existing avatar (to perform a migration)")
      .parameters(
        bodyParam[MigratedAvatarRequest]("")
          .description("The request includes all the details required to migrate an existing avatar")
      )}
