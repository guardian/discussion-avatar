package com.gu.core

import scalaz.NonEmptyList

sealed trait Error {
  val message: String
  val errors: NonEmptyList[String]
}
case class SNSRequestFailed(message: String, errors: NonEmptyList[String]) extends Error
case class InvalidContentType(message: String, errors: NonEmptyList[String]) extends Error
case class InvalidFilters(message: String, errors: NonEmptyList[String]) extends Error
case class AvatarNotFound(message: String, errors: NonEmptyList[String]) extends Error
case class DynamoRequestFailed(message: String, errors: NonEmptyList[String]) extends Error
case class TokenAuthorizationFailed(message: String, errors: NonEmptyList[String]) extends Error
case class UserAuthorizationFailed(message: String, errors: NonEmptyList[String]) extends Error
case class IOFailed(message: String, errors: NonEmptyList[String]) extends Error
case class UnableToReadStatusRequest(message: String, errors: NonEmptyList[String]) extends Error
case class UnableToReadMigratedAvatarRequest(message: String, errors: NonEmptyList[String]) extends Error
case class InvalidUserId(message: String, errors: NonEmptyList[String]) extends Error
case class UnableToReadAvatarRequest(message: String, errors: NonEmptyList[String]) extends Error
case class InvalidMimeType(message: String, errors: NonEmptyList[String]) extends Error
case class InvalidIsSocialFlag(message: String, errors: NonEmptyList[String]) extends Error
case class AvatarAlreadyExists(message: String, errors: NonEmptyList[String]) extends Error

object Errors {

  def avatarAlreadyExists(errors: NonEmptyList[String]): AvatarAlreadyExists =
    AvatarAlreadyExists("Avatar already exists for this user ID", errors)

  def invalidContentType(errors: NonEmptyList[String]): InvalidContentType =
    InvalidContentType(
      "Invalid content type. Support types are: 'multipart/form-data' or 'application/json'.",
      errors
    )

  def invalidFilters(errors: NonEmptyList[String]): InvalidFilters =
    InvalidFilters("Invalid filter parameters", errors)

  def avatarNotFound(errors: NonEmptyList[String]): AvatarNotFound =
    AvatarNotFound("Avatar not found", errors)

  def ioFailed(errors: NonEmptyList[String]): IOFailed =
    IOFailed("IO operation failed", errors)

  def dynamoRequestFailed(errors: NonEmptyList[String]): DynamoRequestFailed =
    DynamoRequestFailed("DynamoDB request failed", errors)

  def tokenAuthorizationFailed(errors: NonEmptyList[String]): TokenAuthorizationFailed =
    TokenAuthorizationFailed("Unable to get API access token. Must be provided in Authorization header as: 'Bearer token=[key]'", errors)

  def userAuthorizationFailed(errors: NonEmptyList[String]): UserAuthorizationFailed =
    UserAuthorizationFailed("Unable to read user cookie. Must be provided in Authorization header as: 'Bearer cookie=[cookie]'", errors)

  def unableToReadStatusRequest(errors: NonEmptyList[String]): UnableToReadStatusRequest = {
    UnableToReadStatusRequest("Unable to read status request", errors)
  }

  def unableToReadAvatarRequest(errors: NonEmptyList[String]): UnableToReadStatusRequest = {
    UnableToReadStatusRequest("Unable to read avatar request", errors)
  }

  def unableToReadMigratedAvatarRequest(errors: NonEmptyList[String]): UnableToReadMigratedAvatarRequest = {
    UnableToReadMigratedAvatarRequest("Unable to read avatar request", errors)
  }

  def invalidUserId(errors: NonEmptyList[String]): InvalidUserId = {
    InvalidUserId("Invalid user ID", errors)
  }

  def invalidIsSocialFlag(errors: NonEmptyList[String]): InvalidIsSocialFlag = {
    InvalidIsSocialFlag("Invalid isSocial boolean flag", errors)
  }

  def invalidMimeType(errors: NonEmptyList[String]): InvalidMimeType = {
    InvalidMimeType("Invalid mime type", errors)
  }
}