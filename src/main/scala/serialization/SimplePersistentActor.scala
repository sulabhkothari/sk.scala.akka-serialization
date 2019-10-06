package serialization

import akka.actor.{ActorLogging, Props}
import akka.persistence.PersistentActor

object SimplePersistentActor {
  def props(persistenceId: String, shouldLog: Boolean) = Props(new SimplePersistentActor(persistenceId, shouldLog))
}
class SimplePersistentActor(override val persistenceId: String, shouldLog: Boolean = true)
  extends PersistentActor with ActorLogging{
  override def receiveRecover: Receive = {
    case event =>
      if(shouldLog) log.info(s"Recovered $event")
  }

  override def receiveCommand: Receive = {
    case message => persist(message) { _ =>
      if(shouldLog) log.info(s"Persisted $message")
    }
  }
}
