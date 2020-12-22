package lerna.util.akka

import akka.actor.ActorRef

/** A class that represents a reply destination [[akka.actor.ActorRef]]
  *
  * @param actorRef The reply destination actor
  */
final case class ReplyTo(actorRef: ActorRef) extends AnyVal
