package sample

import akka.actor.{Props, Actor, ActorSystem}
import akka.routing.{ConsistentHashingRouter, ScatterGatherFirstCompletedRouter, BroadcastRouter, FromConfig}
import annotation.tailrec
import akka.util.Timeout
import concurrent.Await
import concurrent.duration._
import akka.pattern._
import akka.routing.ConsistentHashingRouter.{ConsistentHashableEnvelope, ConsistentHashable, ConsistentHashMapping}

class PrintActor extends Actor {
  def receive = {
    case x => println(x + " " + self.toString() + " " + sender.toString())
  }
}

case class FibonacciNumber(nbr: Int)

class FibonacciActor extends Actor {
  def receive = {
    case FibonacciNumber(nbr) ⇒ sender ! fibonacci(nbr)
  }

  private def fibonacci(n: Int): Int = {
    @tailrec
    def fib(n: Int, b: Int, a: Int): Int = n match {
      case 0 ⇒ a
      case _ ⇒ fib(n - 1, a + b, b)
    }

    fib(n, 1, 0)
  }
}

class Cache extends Actor {
  var cache = Map.empty[String, String]

  def receive = {
    case Entry(key, value) ⇒ println(self.toString()); cache += (key -> value)
    case Get(key) ⇒ println(self.toString()); sender ! cache.get(key)
    case Evict(key) ⇒ println(self.toString()); cache -= key
  }
}

case class Evict(key: String)

case class Get(key: String) extends ConsistentHashable {
  override def consistentHashKey: Any = key
}

case class Entry(key: String, value: String)

object Router extends App {
  val system = ActorSystem("RouterDemo")

  val router = system.actorOf(Props[PrintActor].withRouter(FromConfig), "myrouter1")
  0 to 10 foreach (router !)

  val broadcastRouter =
    system.actorOf(Props[PrintActor].withRouter(BroadcastRouter(5)), "router")
  broadcastRouter ! "hello"

  val scatterGatherFirstCompletedRouter = system.actorOf(
    Props[FibonacciActor].withRouter(ScatterGatherFirstCompletedRouter(
      nrOfInstances = 5, within = 2 seconds)), "router1")
  implicit val timeout = Timeout(5 seconds)
  val futureResult = scatterGatherFirstCompletedRouter ? FibonacciNumber(10)
  val result = Await.result(futureResult, timeout.duration)
  println(result)

  def hashMapping: ConsistentHashMapping = {
    case Evict(key) ⇒ key
  }

  val cache = system.actorOf(Props[Cache].withRouter(ConsistentHashingRouter(10,
    hashMapping = hashMapping)), name = "cache")

  cache ! ConsistentHashableEnvelope(
    message = Entry("hello", "HELLO"), hashKey = "hello")
  cache ! ConsistentHashableEnvelope(
    message = Entry("hi", "HI"), hashKey = "hi")

  cache ! Get("hello")

  cache ! Get("hi")

  cache ! Evict("hi")
  cache ! Get("hi")
}
