package serialization

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import akka.actor.{ActorSystem, Props}
import akka.serialization.Serializer
import com.sksamuel.avro4s.{AvroInputStream, AvroOutputStream, AvroSchema}
import com.typesafe.config.ConfigFactory

case class BankAccount(iban: String, bankCode: String, amount: Double, currency: String)

case class CompanyRegistry(name: String, accounts: Seq[BankAccount], activityCode: String, marketCap: Double)

class RtjvmSerializer extends Serializer {
  val companyRegistrySchema = AvroSchema[CompanyRegistry]

  override def identifier: Int = 75432

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case companyRegistry: CompanyRegistry =>
      val baos = new ByteArrayOutputStream()
      val avroOutputStream = AvroOutputStream.binary[CompanyRegistry].to(baos).build(companyRegistrySchema)
      avroOutputStream.write(companyRegistry)
      avroOutputStream.flush()
      avroOutputStream.close()
      baos.toByteArray
    case _ => throw new IllegalArgumentException("We only support company registry for Avro")
  }

  override def includeManifest: Boolean = true

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    val inputStream = AvroInputStream.binary[CompanyRegistry].from(new ByteArrayInputStream(bytes)).build(companyRegistrySchema)
    val companyRegistryIterator: Iterator[CompanyRegistry] = inputStream.iterator
    val companyRegistry = companyRegistryIterator.next()
    inputStream.close()
    companyRegistry
  }
}

object AvroSerialization_Local extends App {
  val config = ConfigFactory.parseString(
    s"""
       |akka.remote.artery.canonical.port = 2551
   """.stripMargin)
    .withFallback(ConfigFactory.load("AvroSerialization"))

  val system = ActorSystem("LocalSystem", config)
  val actorSelection = system.actorSelection("akka://RemoteSystem@localhost:2552/user/remoteActor")
  actorSelection ! CompanyRegistry(
    "Google",
    Seq(
      BankAccount("US-1234", "google-bank", 4.3, "gazillion dollars"),
      BankAccount("GB-4321", "google-bank", 0.3, "trillion pounds")
    ),
    "ads",
    2376423
  )
}

object AvroSerialization_Remote extends App {
  val config = ConfigFactory.parseString(
    s"""
       |akka.remote.artery.canonical.port = 2552
   """.stripMargin)
    .withFallback(ConfigFactory.load("AvroSerialization"))

  val system = ActorSystem("RemoteSystem", config)
  val simpleActor = system.actorOf(Props[SimpleActor], "remoteActor")
}

object AvroSerialization_Persistence extends App {
  val config = ConfigFactory.load("persistentStores").getConfig("postgresStore")
    .withFallback(ConfigFactory.load("AvroSerialization"))

  val system = ActorSystem("PersistentSystem", config)
  val simplePersistentActor = system.actorOf(SimplePersistentActor.props("avro-actor", true),
    "avroBookActor")
  //  simplePersistentActor ! CompanyRegistry(
  //    "Google",
  //    Seq(
  //      BankAccount("US-1234", "google-bank", 4.3, "gazillion dollars"),
  //      BankAccount("GB-4321", "google-bank", 0.3, "trillion pounds")
  //    ),
  //    "ads",
  //    2376423
  //  )
}

object SimpleAvroApp extends App {
  println(AvroSchema[CompanyRegistry])
}