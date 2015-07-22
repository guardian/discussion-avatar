import java.util.TimeZone
import javax.servlet.ServletContext

import com.gu.adapters.config.AvatarApiConfig
import com.gu.adapters.http.{ AvatarServlet, AvatarSwagger, ResourcesApp }
import com.gu.adapters.notifications.SNS
import com.gu.adapters.store.AvatarStore
import org.scalatra._

class ScalatraBootstrap extends LifeCycle {

  val config = AvatarApiConfig

  implicit val swagger = new AvatarSwagger

  override def init(context: ServletContext) {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    context.mount(new AvatarServlet(AvatarStore(config), config.cookieDecoder, new SNS(config.awsRegion, config.snsTopicArn), config), "/v1", "v1")
    context.mount(new ResourcesApp, "/api-docs")
  }
}
