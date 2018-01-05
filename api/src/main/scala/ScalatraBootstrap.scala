import java.util.TimeZone
import javax.servlet.ServletContext

import com.gu.adapters.config.Config
import com.gu.adapters.http.{AvatarServlet, AvatarSwagger, ResourcesApp}
import com.gu.adapters.notifications.SNS
import com.gu.adapters.queue.DeletionEventHandler
import com.gu.adapters.store.{Dynamo, DynamoProperties, S3}
import com.gu.core.store.AvatarStore
import org.scalatra._

class ScalatraBootstrap extends LifeCycle {

  val config = Config()

  implicit val swagger = new AvatarSwagger

  override def init(context: ServletContext) {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    val storeProps = config.storeProperties
    val avatarStore = AvatarStore(S3(storeProps.awsRegion), Dynamo(storeProps.awsRegion, DynamoProperties(storeProps)), storeProps)
    val avatarServlet = new AvatarServlet(
      avatarStore,
      new SNS(config.snsProperties),
      config.avatarServletProperties
    )
    context.mount(avatarServlet, "/v1", "v1")
    context.mount(new ResourcesApp, "/api-docs")
    new DeletionEventHandler(config.deletionEventsProps, avatarStore).sqsQueue()
  }
}
