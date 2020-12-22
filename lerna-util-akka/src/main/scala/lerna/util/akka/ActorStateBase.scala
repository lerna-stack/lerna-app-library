package lerna.util.akka

import akka.actor.Actor.Receive

/** A trait that represents a base actor state for event sourcing
  *
  * The trait is for a classic actor (not typed).
  * You don't need any more if you use a typed actor.
  *
  * @tparam Event The type of Event that is handled by the actor
  * @tparam State The type of State of the actor
  */
trait ActorStateBase[Event, State <: ActorStateBase[Event, State]] {

  /** The type of event handler of the actor
    * The [[EventHandler]] should handle an event and then return a new state
    */
  type EventHandler = PartialFunction[Event, State]

  /** The event handler of this state
    * @return The event handler
    */
  def updated: EventHandler

  /** The command handler of this state
    * @return The command handler
    */
  def receiveCommand: Receive

}
