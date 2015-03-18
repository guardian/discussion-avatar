import javax.servlet.ServletContext

import com.gu.{AvatarSwagger, ResourcesApp, AvatarServlet}
import org.scalatra._

class ScalatraBootstrap extends LifeCycle {

  implicit val swagger = new AvatarSwagger

  override def init(context: ServletContext) {
    context.mount(new AvatarServlet, "/v1", "v1")
    context.mount(new ResourcesApp, "/api-docs")
  }
}
