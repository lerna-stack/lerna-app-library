package lerna.util.sequence

import akka.actor.ActorSystem
import lerna.testkit.akka.ScalaTestWithClassicActorTestKit
import lerna.tests.LernaBaseSpec

import scala.annotation.nowarn

/** The test base class which improve consistency and reduce boilerplate in lerna sequence actor tests.
  * '''Use `class` instead of `trait` for speedier compiles.'''
  *
  * @see [[https://www.scalatest.org/user_guide/defining_base_classes Defining base classes for your project]]
  */
@nowarn("msg=Use ScalaTestWithTypedActorTestKit")
abstract class LernaSequenceActorBaseSpec(system: ActorSystem)
    extends ScalaTestWithClassicActorTestKit(system)
    with LernaBaseSpec
