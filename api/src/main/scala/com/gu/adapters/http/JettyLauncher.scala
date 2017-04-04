package com.gu.adapters.http

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.webapp.WebAppContext
import org.scalatra.servlet.ScalatraListener
import ch.qos.logback.access.jetty.RequestLogImpl
import org.eclipse.jetty.server.handler.{ HandlerCollection, RequestLogHandler }

object JettyLauncher extends App {

  val port = if (System.getenv("PORT") != null) System.getenv("PORT").toInt else 8080

  val server = new Server(port)
  val context = new WebAppContext()
  context setContextPath "/"
  context.setResourceBase("src/main/webapp")
  context.addEventListener(new ScalatraListener)
  context.addServlet(classOf[DefaultServlet], "/")

  val requestLog = new RequestLogImpl()
  requestLog.setResource("/logback-access.xml")
  val requestLogHandler = new RequestLogHandler()
  requestLogHandler.setRequestLog(requestLog)

  val handlers = new HandlerCollection()
  handlers.setHandlers(Array(context, requestLogHandler))

  server.setHandler(handlers)

  server.start
  server.join
}
