package com.gu.core

import scalaz.NonEmptyList

sealed trait Error {
  val message: String
  val errors: NonEmptyList[String]
}
sealed case class InvalidContentType(message: String, errors: NonEmptyList[String]) extends Error
sealed case class InvalidFilters(message: String, errors: NonEmptyList[String]) extends Error
sealed case class AvatarNotFound(message: String, errors: NonEmptyList[String]) extends Error
sealed case class AvatarRetrievalFailed(message: String, errors: NonEmptyList[String]) extends Error
sealed case class DynamoRequestFailed(message: String, errors: NonEmptyList[String]) extends Error

object Errors {
  def invalidContentType(errors: NonEmptyList[String]): InvalidContentType =
    InvalidContentType("Invalid content type", errors)

  def invalidFilters(errors: NonEmptyList[String]): InvalidFilters =
    InvalidFilters("Invalid filter parameters", errors)

  def avatarNotFound(errors: NonEmptyList[String]): AvatarNotFound =
    AvatarNotFound("Avatar not found", errors)

  def avatarRetrievalFailed(errors: NonEmptyList[String]): AvatarRetrievalFailed =
    AvatarRetrievalFailed("Avatar retrieval failed", errors)

  def dynamoRequestFailed(errors: NonEmptyList[String]): DynamoRequestFailed =
    DynamoRequestFailed("DynamoDB request failed", errors)
}