package lerna.util.akka

import akka.actor.ActorRef

/** A class that represents a reply destination [[akka.actor.ActorRef]]
  *
  * @param actorRef The reply destination actor
  */
@deprecated(message = "Use typed Actor", since = "2.0.0")
final case class ReplyTo(actorRef: ActorRef) extends AnyVal
