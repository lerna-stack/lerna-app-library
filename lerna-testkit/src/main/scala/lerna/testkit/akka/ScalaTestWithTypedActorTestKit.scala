package lerna.testkit.akka

import akka.actor.testkit.typed.TestKitSettings
import akka.actor.testkit.typed.scaladsl.{ ActorTestKit, ActorTestKitBase, ScalaTestWithActorTestKit }
import akka.actor.typed.ActorSystem
import com.typesafe.config.{ Config, ConfigFactory }

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

  def this() =
    this(ActorTestKit())

  def this(system: ActorSystem[_]) =
    this(ActorTestKit(system))

  def this(config: String) =
    this(ActorTestKit(ActorTestKitBase.testNameFromCallStack(), ConfigFactory.parseString(config)))

  def this(config: Config) =
    this(ActorTestKit(ActorTestKitBase.testNameFromCallStack(), config))

  def this(config: Config, settings: TestKitSettings) =
    this(ActorTestKit(ActorTestKitBase.testNameFromCallStack(), config, settings))

}
