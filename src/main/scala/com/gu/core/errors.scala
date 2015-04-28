package com.gu.core

import scalaz.NonEmptyList

sealed trait Error {
  val message: String
  val errors: NonEmptyList[String]
}
case class InvalidContentType(message: String, errors: NonEmptyList[String]) extends Error
case class InvalidFilters(message: String, errors: NonEmptyList[String]) extends Error
case class AvatarNotFound(message: String, errors: NonEmptyList[String]) extends Error
case class AvatarRetrievalFailed(message: String, errors: NonEmptyList[String]) extends Error
case class DynamoRequestFailed(message: String, errors: NonEmptyList[String]) extends Error
case class UnableToReadUserCookie(message: String, errors: NonEmptyList[String]) extends Error
case class IOFailed(message: String, errors: NonEmptyList[String]) extends Error

object Errors {
  def invalidContentType(errors: NonEmptyList[String]): InvalidContentType =
    InvalidContentType(
      "Invalid content type. Support types are: 'multipart/form-data' or 'application/json'.",
      errors)

  def invalidFilters(errors: NonEmptyList[String]): InvalidFilters =
    InvalidFilters("Invalid filter parameters", errors)

  def avatarNotFound(errors: NonEmptyList[String]): AvatarNotFound =
    AvatarNotFound("Avatar not found", errors)

  def avatarRetrievalFailed(errors: NonEmptyList[String]): AvatarRetrievalFailed =
    AvatarRetrievalFailed("Avatar retrieval failed", errors)

  def ioFailed(errors: NonEmptyList[String]): IOFailed =
    IOFailed("IO operation failed", errors)

  def dynamoRequestFailed(errors: NonEmptyList[String]): DynamoRequestFailed =
    DynamoRequestFailed("DynamoDB request failed", errors)
  
  def unableToReadUserCookie(errors: NonEmptyList[String]): UnableToReadUserCookie =
    UnableToReadUserCookie("Unable to read user cookie", errors)
}