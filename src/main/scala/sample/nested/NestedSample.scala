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
    // case x => context.actorOf(Props(new ClassC), "actorOfClassC") ! 'go
    case x => println(s"${self}: ${x}")
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

  val system = ActorSystem("ClusterSystem")

  system.actorOf(Props[ClassB], "workerOfRouterInsideClassA")
  val actorA = system.actorOf(Props[ClassA], "actorOfClassA")

  while (true) {
    actorA ! Message("Main")
    Thread.sleep(1000)
  }
}

object Client extends App {
  System.setProperty("akka.remote.netty.port", args(0))

  val system = ActorSystem("ClusterSystem")

  system.actorOf(Props[ClassB], "workerOfRouterInsideClassA")
  val actorA = system.actorOf(Props[ClassA], "actorOfClassA")

  while (true) {
    actorA ! Message("Client")
    Thread.sleep(1000)
  }
}
