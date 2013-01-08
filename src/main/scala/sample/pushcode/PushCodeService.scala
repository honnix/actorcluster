package sample.pushcode

import akka.actor.{ActorSystem, Props, Actor}
import io.{Codec, Source}
import akka.routing.{Broadcast, FromConfig}

case class Message(byteCode: Array[Byte])

class ActorClassLoader extends ClassLoader with Actor {
  def receive = {
    case Message(byteCode) =>
      val clazz = defineClass(null, byteCode, 0, byteCode.length)
      clazz.newInstance()
  }
}

case class ClassFilePath(path: String)

class PushCodeService extends Actor {
  private val router = context.actorOf(Props[ActorClassLoader].withRouter(FromConfig), "pushCodeRouter")

  def receive = {
    case ClassFilePath(path) =>
      val bytes = Source.fromFile(path)(Codec.ISO8859).map(_.toByte).toArray
      router ! Broadcast(Message(bytes))
  }
}

object Main extends App {
  System.setProperty("akka.remote.netty.port", args(0))
  val system = ActorSystem("ClusterSystem")
  while (true) {
    val path = readLine()
    system.actorOf(Props[PushCodeService], "pushCodeService") ! ClassFilePath(path)
  }
}

object Client extends App {
  System.setProperty("akka.remote.netty.port", args(0))
  ActorSystem("ClusterSystem")
}
