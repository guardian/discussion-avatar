package com.gu

import org.json4s.{DefaultFormats, Formats}
import org.scalatra.json._
import org.scalatra.{NotImplemented, Ok, ScalatraServlet}

class AvatarServlet extends ScalatraServlet with JacksonJsonSupport {

  protected implicit val jsonFormats: Formats = DefaultFormats

  before() {
    contentType = formats("json")
  }

  get("/management/healthcheck") {
    Ok(ServiceOk("OK"))
  }

  get("/management/gtg") {
    NotImplemented(ServiceNotOk("Endpoint needs to be specified"))
  }

  get("/management/dependencies") {
    NotImplemented(ServiceNotOk("Endpoint needs to be specified"))
  }
}

sealed trait ServiceResponse
case class ServiceOk(message: String) extends ServiceResponse
case class ServiceNotOk(message: String) extends ServiceResponse