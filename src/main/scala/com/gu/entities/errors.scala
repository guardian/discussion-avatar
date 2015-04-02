package com.gu.entities

import scalaz.NonEmptyList

sealed trait Error {
  val message: String
  val errors: NonEmptyList[String]
}
case class InvalidFilters(message: String, errors: NonEmptyList[String]) extends Error
case class AvatarNotFound(message: String, errors: NonEmptyList[String]) extends Error
case class RetrievalError(message: String, errors: NonEmptyList[String]) extends Error

object Errors {
  def invalidFilters(errors: NonEmptyList[String]): InvalidFilters =
    InvalidFilters("Invalid filter parameters", errors)

  def avatarNotFound(errors: NonEmptyList[String]): AvatarNotFound =
    AvatarNotFound("Avatar not found", errors)

  def retrievalError(errors: NonEmptyList[String]): RetrievalError =
    RetrievalError("Unable to retrieve Avatar", errors)
}