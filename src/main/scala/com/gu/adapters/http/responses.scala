package com.gu.adapters.http

import com.gu.core.Avatar

case class Link(rel: String, href: String)

sealed trait ApiResponse
case class Message(message: String) extends ApiResponse
case class ErrorResponse(message: String, errors: List[String]) extends ApiResponse
case class AvatarResponse(uri: String, data: Avatar, links: List[Link]) extends ApiResponse
case class AvatarsResponse(uri: String, data: List[Avatar], links: List[Link]) extends ApiResponse
