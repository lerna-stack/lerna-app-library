package lerna.testkit.akka

import akka.actor.testkit.typed.scaladsl.{ ActorTestKit, ScalaTestWithActorTestKit }
import akka.actor.typed.ActorSystem

/** A test class which improve consistency and reduce boilerplate.
  *
  * ==Overview==
  *
  * This class inherits from
  * [[https://doc.akka.io/api/akka/2.6/akka/actor/testkit/typed/scaladsl/ScalaTestWithActorTestKit.html ScalaTestWithActorTestKit]]
  */
abstract class ScalaTestWithTypedActorTestKit(testKit: ActorTestKit)
    extends ScalaTestWithActorTestKit(testKit)
    with AkkaTypedSpanScaleFactorSupport {

  def this() = this(ActorTestKit())
  def this(system: ActorSystem[_]) = this(ActorTestKit(system))

}
