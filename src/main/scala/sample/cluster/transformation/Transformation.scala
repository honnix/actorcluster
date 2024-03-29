package sample.cluster.transformation

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.RootActorPath
import akka.actor.Terminated
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.CurrentClusterState
import akka.cluster.ClusterEvent.MemberEvent
import akka.cluster.ClusterEvent.MemberUp
import akka.cluster.Member
import akka.cluster.MemberStatus
import akka.pattern.ask
import akka.util.Timeout

case class TransformationJob(text: String)

case class TransformationResult(text: String)

case class JobFailed(reason: String, job: TransformationJob)

case object BackendRegistration

class TransformationBackend extends Actor {
  val cluster = Cluster(context.system)

  // subscribe to cluster changes, MemberEvent
  // re-subscribe when restart
  override def preStart() {
    cluster.subscribe(self, classOf[MemberEvent])
  }

  override def postStop() {
    cluster.unsubscribe(self)
  }

  def receive = {
    case TransformationJob(text) ⇒ sender ! TransformationResult(text.toUpperCase)
    case state: CurrentClusterState ⇒
      state.members.filter(_.status == MemberStatus.Up) foreach register
    case MemberUp(m) ⇒ register(m)
  }

  // try to register to all nodes, even though there
  // might not be any frontend on all nodes
  def register(member: Member) {
    context.actorFor(RootActorPath(member.address) / "user" / "frontend") !
      BackendRegistration
  }
}

object TransformationBackend extends App {
  // Override the configuration of the port
  // when specified as program argument
  if (args.nonEmpty) System.setProperty("akka.remote.netty.port", args(0))

  val system = ActorSystem("ClusterBackendSystem")
  system.actorOf(Props[TransformationBackend], "backend")
}

class TransformationFrontend extends Actor {
  var backends = IndexedSeq.empty[ActorRef]
  var jobCounter = 0

  def receive = {
    case job: TransformationJob if backends.isEmpty ⇒
      sender ! JobFailed("Service unavailable, try again later", job)

    case job: TransformationJob ⇒
      jobCounter += 1
      backends(jobCounter % backends.size) forward job

    case BackendRegistration if !backends.contains(sender) ⇒
      context watch sender
      backends = backends :+ sender

    case Terminated(a) ⇒
      backends = backends.filterNot(_ == a)
  }
}

object TransformationFrontend extends App {
  // Override the configuration of the port
  // when specified as program argument
  if (args.nonEmpty) System.setProperty("akka.remote.netty.port", args(0))

  val system = ActorSystem("ClusterFrontendSystem")
  val frontend = system.actorOf(Props[TransformationFrontend], "frontend")

  import system.dispatcher

  implicit val timeout = Timeout(5000)
  for (n ← 1 to 120) {
    (frontend ? TransformationJob("hello-" + n)) onSuccess {
      case result ⇒ println(result)
    }
    // wait a while until next request,
    // to avoid flooding the console with output
    Thread.sleep(2000)
  }
  system.shutdown()
}
