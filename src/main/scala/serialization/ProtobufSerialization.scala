package serialization

import akka.actor.{ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import serialization.DataModel.OnlineStoreUser

object ProtobufSerialization_Local extends App {
  val config = ConfigFactory.parseString(
    s"""
       |akka.remote.artery.canonical.port = 2551
   """.stripMargin)
    .withFallback(ConfigFactory.load("protobufSerialization.conf"))

  val system = ActorSystem("LocalSystem", config)
  val actorSelection = system.actorSelection("akka://RemoteSystem@localhost:2552/user/remoteActor")

  val onlineStoreUser = OnlineStoreUser.newBuilder()
    .setId(45621)
    .setUserName("Daniel-rtjvm")
    .setUserEmail("daniel@rtjvm.com")
    .build()

  actorSelection ! onlineStoreUser
}

object ProtobufSerialization_Remote extends App {
  val config = ConfigFactory.parseString(
    s"""
       |akka.remote.artery.canonical.port = 2552
   """.stripMargin)
    .withFallback(ConfigFactory.load("protobufSerialization.conf"))

  val system = ActorSystem("RemoteSystem", config)
  val simpleActor = system.actorOf(Props[SimpleActor], "remoteActor")
}

object ProtobufSerialization_Persistence extends App {
  val config = ConfigFactory.load("persistentStores.conf").getConfig("postgresStore")
    .withFallback(ConfigFactory.load("protobufSerialization.conf"))

  val system = ActorSystem("PersistentSystem", config)
  val simplePersistentActor = system.actorOf(SimplePersistentActor.props("protobuf-actor", true),
    "protobufActor")

  val onlineStoreUser = OnlineStoreUser.newBuilder()
    .setId(45622)
    .setUserName("Daniel-rtjvm")
    .setUserEmail("daniel@rtjvm.com")
    .setUserPhone("23874628734")
    .build()

  //simplePersistentActor ! onlineStoreUser
}

/*
  Execute from src folder:
  ./main/exec/protoc --java_out=main/java main/proto/dataModel.proto
 */