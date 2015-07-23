import java.util.TimeZone
import javax.servlet.ServletContext

import com.gu.adapters.config.Config
import com.gu.adapters.http.{ AvatarServlet, AvatarSwagger, ResourcesApp }
import com.gu.adapters.notifications.SNS
import com.gu.adapters.store.AvatarStore
import org.scalatra._

class ScalatraBootstrap extends LifeCycle {

  val config = Config()

  implicit val swagger = new AvatarSwagger

  override def init(context: ServletContext) {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    val avatarServlet = new AvatarServlet(
      AvatarStore(config.storeProperties),
      new SNS(config.snsProperties),
      config.avatarServletProperties
    )
    context.mount(avatarServlet, "/v1", "v1")
    context.mount(new ResourcesApp, "/api-docs")
  }
}
