package com.gu.core.models
sealed trait Error {
  val message: String
  val errors: List[String]
}
case class InvalidContentType(message: String, errors: List[String]) extends Error
case class InvalidFilters(message: String, errors: List[String]) extends Error
case class AvatarNotFound(message: String, errors: List[String]) extends Error
case class DynamoRequestFailed(message: String, errors: List[String]) extends Error
case class TokenAuthorizationFailed(message: String, errors: List[String]) extends Error
case class UserAuthorizationFailed(message: String, errors: List[String]) extends Error
case class IOFailed(message: String, errors: List[String]) extends Error
case class UnableToReadStatusRequest(message: String, errors: List[String]) extends Error
case class InvalidUserId(message: String, errors: List[String]) extends Error
case class UnableToReadAvatarRequest(message: String, errors: List[String]) extends Error
case class InvalidMimeType(message: String, errors: List[String]) extends Error
case class InvalidIsSocialFlag(message: String, errors: List[String]) extends Error
case class UserDeletionFailed(message: String, errors: List[String]) extends Error
case class OAuthTokenAuthorizationFailed(message: String, errors: List[String], statusCode: Int) extends Error

object Errors {

  def invalidContentType(errors: List[String]): InvalidContentType =
    InvalidContentType(
      "Invalid content type. Support types are: 'multipart/form-data' or 'application/json'.",
      errors
    )

  def invalidFilters(errors: List[String]): InvalidFilters =
    InvalidFilters("Invalid filter parameters", errors)

  def avatarNotFound(errors: List[String]): AvatarNotFound =
    AvatarNotFound("Avatar not found", errors)

  def ioFailed(errors: List[String]): IOFailed =
    IOFailed("IO operation failed", errors)

  def dynamoRequestFailed(errors: List[String]): DynamoRequestFailed =
    DynamoRequestFailed("DynamoDB request failed", errors)

  def tokenAuthorizationFailed(errors: List[String]): TokenAuthorizationFailed =
    TokenAuthorizationFailed("Unable to get API access token. Must be provided in Authorization header as: 'Bearer token=[key]'", errors)

  def userAuthorizationFailed(errors: List[String]): UserAuthorizationFailed =
    UserAuthorizationFailed("Unable to read user cookie. Must be provided in Authorization header as: 'Bearer cookie=[cookie]'", errors)

  def unableToReadStatusRequest(errors: List[String]): UnableToReadStatusRequest = {
    UnableToReadStatusRequest("Unable to read status request", errors)
  }

  def unableToReadAvatarRequest(errors: List[String]): UnableToReadStatusRequest = {
    UnableToReadStatusRequest("Unable to read avatar request", errors)
  }

  def invalidUserId(errors: List[String]): InvalidUserId = {
    InvalidUserId("Invalid user ID", errors)
  }

  def invalidIsSocialFlag(errors: List[String]): InvalidIsSocialFlag = {
    InvalidIsSocialFlag("Invalid isSocial boolean flag", errors)
  }

  def invalidMimeType(errors: List[String]): InvalidMimeType = {
    InvalidMimeType("Invalid mime type", errors)
  }

  def deletionFailed(errors: List[String]): UserDeletionFailed = {
    UserDeletionFailed("Unable to delete one or more records for the user", errors)
  }

  def oauthTokenAuthorizationFailed(errors: List[String], statusCode: Int): OAuthTokenAuthorizationFailed = {
    OAuthTokenAuthorizationFailed("OAuth Token Authorization Failed", errors, statusCode)
  }
}