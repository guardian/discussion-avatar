package com.gu.adapters.store

import com.gu.adapters.http.Filters
import com.gu.entities.{User, Approved, Avatar, Error, Status}

import scalaz.Scalaz._
import scalaz.\/

sealed trait Store {
  def get(filters: Filters): \/[Error, List[Avatar]]
  def get(id: String): \/[Error, Avatar]
  def get(user: User): \/[Error, List[Avatar]]
  def getActive(user: User): \/[Error, Avatar]

  def add(avatar: Avatar): \/[Error, Avatar]
  def updateStatus(id: String, status: Status): \/[Error, Avatar]
}

object AvatarStore extends Store {
  val avatars = List(
    Avatar("123-id", "http://avatar-url-1", 123, "foo.gif", Approved, true),
    Avatar("abc-id", "http://avatar-url-2", 234, "bar.gif", Approved, true)
  )

  def get(filters: Filters): \/[Error, List[Avatar]] = avatars.right
  def get(id: String): \/[Error, Avatar] = avatars.head.right
  def get(user: User): \/[Error, List[Avatar]] = avatars.right
  def getActive(user: User): \/[Error, Avatar] = avatars.head.right

  def add(avatar: Avatar): \/[Error, Avatar] = ???
  def updateStatus(id: String, status: Status): \/[Error, Avatar] = ???
}