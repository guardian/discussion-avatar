package com.gu.adapters.http

import org.scalatra._

import scalaz.\/

trait ServletWithErrorHandling[Error, Success] { this: ScalatraServlet =>

  def getWithErrors(transformers: RouteTransformer*)(action: => \/[Error, (Success, Req)]) = addRoute(Get, transformers, withErrorHandling(action))
  def putWithErrors(transformers: RouteTransformer*)(action: => \/[Error, (Success, Req)]) = addRoute(Put, transformers, withErrorHandling(action))
  def postWithErrors(transformers: RouteTransformer*)(action: => \/[Error, (Success, Req)]) = addRoute(Post, transformers, withErrorHandling(action))
  def deleteWithErrors(transformers: RouteTransformer*)(action: => \/[Error, (Success, Req)]) = addRoute(Delete, transformers, withErrorHandling(action))

  def withErrorHandling(response: => \/[Error, (Success, Req)]): ActionResult = {
    (handleSuccess orElse handleError)(response)
  }

  protected def handleSuccess: PartialFunction[\/[Error, (Success, Req)], ActionResult]

  protected def handleError[A]: PartialFunction[\/[Error, A], ActionResult]

}
