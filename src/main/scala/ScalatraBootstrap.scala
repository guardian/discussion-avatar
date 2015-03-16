import javax.servlet.ServletContext

import com.gu.AvatarServlet
import org.scalatra._

class ScalatraBootstrap extends LifeCycle {
  override def init(context: ServletContext) {
    context.mount(new AvatarServlet, "/*")
  }
}
