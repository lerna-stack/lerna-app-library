package lerna.tests

import akka.actor.ActorSystem
import lerna.testkit.akka.ScalaTestWithClassicActorTestKit

/** The test base class which improve consistency and reduce boilerplate in lerna tests.
  * '''Use `class` instead of `trait` for speedier compiles.'''
  *
  * @see [[https://www.scalatest.org/user_guide/defining_base_classes Defining base classes for your project]]
  */
abstract class LernaWithClassicActorTestKitBaseSpec(system: ActorSystem)
    extends ScalaTestWithClassicActorTestKit(system)
    with LernaBaseSpec
