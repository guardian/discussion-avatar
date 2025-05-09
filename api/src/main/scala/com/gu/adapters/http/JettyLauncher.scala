package com.gu.adapters.http

import org.scalatra.servlet.ScalatraListener
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.ee10.servlet.DefaultServlet
import ch.qos.logback.access.jetty.RequestLogImpl
import org.eclipse.jetty.util.resource.Resource
import org.eclipse.jetty.util.resource.ResourceFactory
import java.net.URL
import org.eclipse.jetty.ee10.servlet.ServletContextHandler

object JettyLauncher extends App {
  val port = if (System.getenv("PORT") != null) System.getenv("PORT").toInt else 8080

  val server = new Server(port)
  val context = new ServletContextHandler()

  context.setContextPath("/")
  context.addEventListener(new ScalatraListener)
  context.addServlet(classOf[DefaultServlet], "/")

  val requestLog = new RequestLogImpl()
  requestLog.setResource("/logback-access.xml")

  server.setRequestLog(requestLog)
  server.setHandler(context)

  server.start
  server.join
}
