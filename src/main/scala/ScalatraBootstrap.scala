import java.util.TimeZone
import javax.servlet.ServletContext

import com.gu.adapters.http.{AvatarServlet, AvatarSwagger, ResourcesApp}
import com.gu.adapters.store.AvatarAwsStore
import org.scalatra._

class ScalatraBootstrap extends LifeCycle {

  implicit val swagger = new AvatarSwagger

  override def init(context: ServletContext) {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    context.mount(new AvatarServlet(AvatarAwsStore()), "/v1", "v1")
    context.mount(new ResourcesApp, "/api-docs")
  }
}
