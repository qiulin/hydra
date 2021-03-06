package hydra.core.akka

import akka.actor.{Actor, ActorRef, ReceiveTimeout, Stash}
import akka.pattern.pipe
import hydra.common.config.ActorConfigSupport
import hydra.common.logging.LoggingAdapter
import hydra.core.HydraException
import hydra.core.akka.InitializingActor.{InitializationError, Initialized}
import hydra.core.protocol.HydraMessage

import scala.concurrent.Future
import scala.concurrent.duration._

trait InitializingActor extends Actor with ActorConfigSupport with Stash with LoggingAdapter {

  /**
    * Since we use this to call context.setReceiveTimeout,
    * DO NOT override this as a `val`. If you do, this will be set to null since the
    * val won't be fully defined until after the constructor of the class exists.
    *
    */
  def initTimeout: FiniteDuration

  context.setReceiveTimeout(Option(initTimeout).getOrElse(1.seconds))

  override def receive: Receive = waitingForInitialization

  var composedReceive: Receive = Actor.emptyBehavior

  def baseReceive: Receive

  def compose(next: Actor.Receive) = {
    composedReceive = next orElse baseReceive
  }

  /**
    * This method is called upon initialization.  It should return one of the ingestor initialization messages:
    * `Initialized` or `InitializationError`
    *
    * The default implementation returns Initialized.
    */
  def init: Future[HydraMessage] = Future.successful(Initialized)

  override def preStart(): Unit = {
    pipe(init)(context.dispatcher) to self
  }

  def waitingForInitialization: Receive = {
    case Initialized =>
      cancelReceiveTimeout
      context.become(composedReceive)
      log.info("{}[{}] initialized", Seq(thisActorName, self.path): _*)
      unstashAll()

    case err@InitializationError(ex) =>
      log.error(ex.getMessage)
      initError(err)

    case ReceiveTimeout =>
      log.error(s"Ingestor $thisActorName[${self.path}] did not initialize in $initTimeout")
      val err = ActorInitializationException(self, s"Ingestor did not initialize in $initTimeout.")
      initError(InitializationError(err))

    case msg =>
      log.debug(s"$thisActorName received message $msg while not initialized; stashing.")
      stash()
  }

  private def initError(err: InitializationError) = {
    cancelReceiveTimeout()
    context.system.eventStream.publish(err)
    context.become(initializationError(err.cause))
  }

  private def cancelReceiveTimeout(): Unit = {
    context.setReceiveTimeout(Duration.Undefined)
    unstashAll()
  }

  /**
    * The Actor status if initialization is not successful.
    *
    * In other words, context.become(initializationError()) will be called if initialization fails.
    *
    * @param ex
    * @return
    */
  def initializationError(ex: Throwable): Receive
}

object InitializingActor {

  case object Initialized extends HydraMessage

  case class InitializationError(cause: Throwable) extends HydraMessage

}

@SerialVersionUID(1L)
class ActorInitializationException(ingestor: ActorRef, message: String, cause: Throwable)
  extends HydraException(ActorInitializationException.enrichedMessage(ingestor, message), cause) {
  def getActor: ActorRef = ingestor
}

object ActorInitializationException {
  private def enrichedMessage(actor: ActorRef, message: String) =
    Option(actor).map(a => s"${a.path}: $message").getOrElse(message)

  private[hydra] def apply(actor: ActorRef, message: String, cause: Throwable = null) =
    new ActorInitializationException(actor, message, cause)

  def unapply(ex: ActorInitializationException): Option[(ActorRef, String, Throwable)] =
    Some((ex.getActor, ex.getMessage, ex.getCause))
}