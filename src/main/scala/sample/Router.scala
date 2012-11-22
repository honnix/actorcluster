package sample

import akka.actor.{Props, Actor, ActorSystem}
import akka.routing.FromConfig

class Router {

}

object Router extends App {
  val system = ActorSystem("RouterDemo")
  val router = system.actorOf(Props(new Actor {
    def receive = {
      case x => println(x + " " + self.toString())
    }
  }).withRouter(FromConfig), "myrouter1")

  for (i <- (0 to 10))
    router ! "hello"
}
