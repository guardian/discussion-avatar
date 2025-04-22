package com.gu.adapters.http

import com.gu.adapters.http.TokenAuth._
import org.scalatra._
import com.gu.core.models.Error
trait AuthorizedApiServlet[Success] {
  this: ScalatraServlet with ServletWithErrorHandling[Error, Success] =>

  def apiKeys: List[String]

  def apiGet(transformers: RouteTransformer*)(action: String => Either[Error, (Success, Req)]) = addRoute(Get, transformers, withAuth(action))
  def apiPut(transformers: RouteTransformer*)(action: String => Either[Error, (Success, Req)]) = addRoute(Put, transformers, withAuth(action))
  def apiPost(transformers: RouteTransformer*)(action: String => Either[Error, (Success, Req)]) = addRoute(Post, transformers, withAuth(action))
  def apiDelete(transformers: RouteTransformer*)(action: String => Either[Error, (Success, Req)]) = addRoute(Delete, transformers, withAuth(action))

  protected def withAuth(response: String => Either[Error, (Success, Req)]): ActionResult = withErrorHandling {
    isValidKey(request.header("Authorization"), apiKeys) flatMap response
  }

}
