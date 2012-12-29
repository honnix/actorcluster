package sample.nested

import akka.actor.{ActorSystem, Props, Actor}
import akka.routing.FromConfig
import akka.pattern._
import akka.util.Timeout
import concurrent.duration._
import concurrent.ExecutionContext
import ExecutionContext.Implicits.global
import akka.routing.ConsistentHashingRouter.ConsistentHashable

case class Message(id: String) extends ConsistentHashable {
  def consistentHashKey = id
}

class Something

class ClassA extends Actor {
  private val router = context.actorOf(Props[ClassB].withRouter(FromConfig), "routerInsideClassA")

  private implicit val timeout = Timeout(5 seconds)

  def receive = {
    case x =>
      val replyTo = sender
      router ? x pipeTo replyTo
  }
}

class ClassB extends Actor {
  println("===============ClassB " + self.path)

  def receive = {
    case x => context.actorOf(Props(new ClassC), "actorOfClassC") ! 'go
  }
}

class ClassC extends Actor {
  def receive = {
    case _ => context.actorOf(Props(new ClassD(new Something)).withRouter(FromConfig), "routerInsideClassC")
  }
}

class ClassD(something: Something) extends Actor {
  println("===============ClassD " + self.path)

  def receive = {
    case _ => println("")
  }
}

object Main extends App {
  System.setProperty("akka.remote.netty.port", args(0))
  ActorSystem("ClusterSystem").actorOf(Props[ClassA], "actorOfClassA") ! Message("my_id")
}

object Client extends App {
  System.setProperty("akka.remote.netty.port", args(0))
  ActorSystem("ClusterSystem").actorOf(Props(new ClassD(null)), "actorOfClassD")
}
