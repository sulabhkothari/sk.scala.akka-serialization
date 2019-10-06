package serialization

import akka.actor.{ActorSystem, Props}
import akka.serialization.Serializer
import com.typesafe.config.ConfigFactory
import spray.json._

case class Person(name: String, age: Int)

class PersonSerializer extends Serializer {
  val SEPARATOR = "//"

  override def identifier: Int = 74238

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case person@Person(name, age) =>
      println(s"Serializing $person")
      s"[$name$SEPARATOR$age]".getBytes
    case _ => throw new IllegalArgumentException("only Persons are supported for this serializer")
  }

  // False means no class hint because this will only be used for Person class
  override def includeManifest: Boolean = false

  /*
    Manifest is a hint to say which class to instantiate from the array of bytes
   */
  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    val string = new String(bytes)
    val values = string.substring(1, string.length - 1).split(SEPARATOR)
    val person = Person(values(0), values(1).toInt)
    println(s"Desrialized $person")
    person
  }
}

class PersonJsonSerializer extends Serializer with DefaultJsonProtocol {
  implicit val format = jsonFormat2(Person)

  override def identifier: Int = 74239

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case person: Person =>
      val json = person.toJson.prettyPrint
      println(s"Converting $person to $json")
      json.getBytes
    case _ => throw new IllegalArgumentException("only Persons are supported for this serializer")
  }

  override def includeManifest: Boolean = false

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    val string = new String(bytes)
    val person = string.parseJson.convertTo[Person]
    println(s"Deserialized $string to $person")
    person
  }
}

object CustomSerialization_Local extends App {
  val config = ConfigFactory.parseString(
    s"""
       |akka.remote.artery.canonical.port = 2551
   """.stripMargin)
    .withFallback(ConfigFactory.load("customSerialization.conf"))

  val system = ActorSystem("LocalSystem", config)
  val actorSelection = system.actorSelection("akka://RemoteSystem@localhost:2552/user/remoteActor")
  actorSelection ! Person("Alice", 23)
}

object CustomSerialization_Remote extends App {
  val config = ConfigFactory.parseString(
    s"""
       |akka.remote.artery.canonical.port = 2552
   """.stripMargin)
    .withFallback(ConfigFactory.load("customSerialization.conf"))

  val system = ActorSystem("RemoteSystem", config)
  val simpleActor = system.actorOf(Props[SimpleActor], "remoteActor")
}

object CustomSerialization_Persistence extends App {
  val config = ConfigFactory.load("persistentStores.conf").getConfig("postgresStore")
    .withFallback(ConfigFactory.load("customSerialization.conf"))

  val system = ActorSystem("PersistentSystem", config)
  val simplePersistentActor = system.actorOf(SimplePersistentActor.props("person-json", true),
    "personJsonActor")
  //simplePersistentActor ! Person("Alice", 23)
}