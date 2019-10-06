package serialization

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.serialization.Serializer
import com.sksamuel.avro4s.{AvroInputStream, AvroOutputStream, AvroSchema}
import com.typesafe.config.ConfigFactory
import serialization.DataModel.ProtobufVote

import scala.util.Random

case class Vote(ssn: String, candidate: String)

case object VoteEnd

object VoteGenerator {
  val random = new Random()
  val candidates = List("Alice", "Bob", "Charlie", "Trump", "Obama")

  def getRandomCandidate = candidates(random.nextInt(candidates.length))

  def generateVotes(count: Int) = (1 to count).map(_ => Vote(UUID.randomUUID().toString, getRandomCandidate))

  def generateProtobufVotes(count: Int) = (1 to count).map { _ =>
    ProtobufVote.newBuilder()
      .setSsn(UUID.randomUUID().toString)
      .setCandidate(getRandomCandidate)
      .build()
  }
}

class VoteAvroSerializer extends Serializer {
  val voteSchema = AvroSchema[Vote]

  override def identifier: Int = 75432

  override def toBinary(o: AnyRef): Array[Byte] = o match {
    case vote: Vote =>
      val baos = new ByteArrayOutputStream()
      val avroOutputStream = AvroOutputStream.binary[Vote].to(baos).build(voteSchema)
      avroOutputStream.write(vote)
      avroOutputStream.flush()
      avroOutputStream.close()
      baos.toByteArray
    case _ => throw new IllegalArgumentException("We only support Votes in this benchmark serializer")
  }

  override def includeManifest: Boolean = true

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    val inputStream = AvroInputStream.binary[Vote].from(new ByteArrayInputStream(bytes)).build(voteSchema)
    val voteIterator: Iterator[Vote] = inputStream.iterator
    val vote = voteIterator.next()
    inputStream.close()
    vote
  }
}

class VoteAggregator extends Actor with ActorLogging {
  override def receive: Receive = ready

  def ready: Receive = {
    case _: Vote | _: ProtobufVote => context.become(online(1, System.currentTimeMillis()))
  }

  def online(voteCount: Int, originalTime: Long): Receive = {
    case _: Vote | _: ProtobufVote =>
      context.become(online(voteCount + 1, originalTime))
    case VoteEnd =>
      val duration = (System.currentTimeMillis() - originalTime) * 1.0 / 1000
      log.info(f"Received $voteCount votes in $duration%5.3f seconds")
      context.become(ready)
  }
}

object VotingStation extends App {
  val config = ConfigFactory.parseString(
    s"""
       |akka.remote.artery.canonical.port = 2551
   """.stripMargin)
    .withFallback(ConfigFactory.load("serializersBenchmark"))

  val system = ActorSystem("VotingStation", config)
  val actorSelection = system.actorSelection("akka://VotingCentralizer@localhost:2552/user/voteAggregator")
  val votes = VoteGenerator.generateVotes(1000000).foreach(actorSelection ! _)
  actorSelection ! VoteEnd
}

object VotingCentralizer extends App {
  val config = ConfigFactory.parseString(
    s"""
       |akka.remote.artery.canonical.port = 2552
   """.stripMargin)
    .withFallback(ConfigFactory.load("serializersBenchmark"))

  val system = ActorSystem("VotingCentralizer", config)
  val simpleActor = system.actorOf(Props[VoteAggregator], "voteAggregator")
}

/**
  * TIME RESULTS:
  *
  * 100k
  *   - java: 2.310s
  *   - avro: 2.066s
  *   - kryo: 1.351s
  *   - protobuf: 1.255s
  * 1M
  *   - java: 20.079s
  *   - avro: 13.999s
  *   - kryo: 8.948s
  *   - protobuf: 11.341s
  *
  * MEMORY TESTS:
  * select avg(length(message)), persistence_id from public.journal group by persistence_id;
  *
  *          avg          |   persistence_id
  * ----------------------+--------------------
  *  128.0001000000000000 | benchmark-avro
  *  194.9853000000000000 | benchmark-java
  *  149.9613000000000000 | benchmark-protobuf
  *  107.9881000000000000 | benchmark-kryo
  */

object SerializationBenchmark_Persistence extends App {
  val config = ConfigFactory.load("persistentStores").getConfig("postgresStore")
    .withFallback(ConfigFactory.load("serializersBenchmark"))

  val system = ActorSystem("PersistentSystem", config)
  val simplePersistentActor = system.actorOf(SimplePersistentActor.props("benchmark-protobuf", false),
    "benchmark")
  val votes = VoteGenerator.generateVotes(10000)
  votes.foreach{ vote =>
    simplePersistentActor ! vote
  }
}
