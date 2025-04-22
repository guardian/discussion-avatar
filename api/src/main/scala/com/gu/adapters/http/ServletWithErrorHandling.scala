package com.gu.adapters.http

import org.scalatra._

trait ServletWithErrorHandling[Error, Success] { this: ScalatraServlet =>

  def getWithErrors(transformers: RouteTransformer*)(action: => Either[Error, (Success, Req)]) = addRoute(Get, transformers, withErrorHandling(action))
  def putWithErrors(transformers: RouteTransformer*)(action: => Either[Error, (Success, Req)]) = addRoute(Put, transformers, withErrorHandling(action))
  def postWithErrors(transformers: RouteTransformer*)(action: => Either[Error, (Success, Req)]) = addRoute(Post, transformers, withErrorHandling(action))
  def deleteWithErrors(transformers: RouteTransformer*)(action: => Either[Error, (Success, Req)]) = addRoute(Delete, transformers, withErrorHandling(action))

  def withErrorHandling(response: => Either[Error, (Success, Req)]): ActionResult = {
    (handleSuccess orElse handleError)(response)
  }

  protected def handleSuccess: PartialFunction[Either[Error, (Success, Req)], ActionResult]

  protected def handleError[A]: PartialFunction[Either[Error, A], ActionResult]

}
