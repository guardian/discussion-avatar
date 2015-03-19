package com.gu.entities

case class Avatar(
  id: String,
  avatarUrl: String,
  userId: Int,
  originalFilename: String,
  status: Status
)
