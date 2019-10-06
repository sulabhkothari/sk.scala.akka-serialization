package serialization

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory

case class Book(title: String, year: Int)

object KryoSerialization_Local extends App {
  val config = ConfigFactory.parseString(
    s"""
       |akka.remote.artery.canonical.port = 2551
   """.stripMargin)
    .withFallback(ConfigFactory.load("kryoSerialization.conf"))

  val system = ActorSystem("LocalSystem", config)
  val actorSelection = system.actorSelection("akka://RemoteSystem@localhost:2552/user/remoteActor")
  actorSelection ! Book("Lord of the Rings", 1950)
}

object KryoSerialization_Remote extends App {
  val config = ConfigFactory.parseString(
    s"""
       |akka.remote.artery.canonical.port = 2552
   """.stripMargin)
    .withFallback(ConfigFactory.load("kryoSerialization.conf"))

  val system = ActorSystem("RemoteSystem", config)
  val simpleActor = system.actorOf(Props[SimpleActor], "remoteActor")
}

object KryoSerialization_Persistence extends App {
  val config = ConfigFactory.load("persistentStores.conf").getConfig("postgresStore")
    .withFallback(ConfigFactory.load("kryoSerialization.conf"))

  val system = ActorSystem("PersistentSystem", config)
  val simplePersistentActor = system.actorOf(SimplePersistentActor.props("kryo-actor", true),
    "kryoBookActor")
  //simplePersistentActor ! Book("Lord of the Rings", 1950)
}