package com.gu.adapters.http

import com.gu.core.Avatar

sealed trait ApiResponse
case class Message(message: String) extends ApiResponse
case class ErrorResponse(message: String, errors: List[String]) extends ApiResponse
case class AvatarList(avatars: List[Avatar]) extends ApiResponse
