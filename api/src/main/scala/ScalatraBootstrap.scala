import java.util.TimeZone

import javax.servlet.ServletContext
import com.gu.adapters.config.Config
import com.gu.adapters.http.{AuthenticationService, AvatarServlet, AvatarSwagger, ResourcesApp}
import com.gu.adapters.notifications.SNS
import com.gu.adapters.queue.SqsDeletionConsumer
import com.gu.adapters.store.{Dynamo, DynamoProperties, S3}
import com.gu.core.akka.Akka
import com.gu.core.store.AvatarStore
import com.gu.logstash.Logstash
import logstash.LogbackOperationsPool
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
      config.avatarServletProperties,
      AuthenticationService.fromIdentityConfig(config.identityConfig)
    )
    context.mount(avatarServlet, "/v1", "v1")
    context.mount(new ResourcesApp, "/api-docs")
    new SqsDeletionConsumer(config.deletionEventsProps, avatarStore).listen()
    new Logstash(new LogbackOperationsPool()).init(config)
  }

  override def destroy(context: ServletContext) {
    Akka.system.terminate()
  }
}
