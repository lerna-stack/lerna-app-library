package lerna.management

import akka.actor.ActorSystem
import lerna.testkit.akka.ScalaTestWithClassicActorTestKit
import lerna.tests.LernaBaseSpec
import org.scalatest.Inside

/** A test base class which improve consistency and reduce boilerplate in lerna management tests.
  * '''Use `class` instead of `trait` for speedier compiles.'''
  *
  * @see [[https://www.scalatest.org/user_guide/defining_base_classes Defining base classes for your project]]
  */
abstract class LernaManagementActorBaseSpec(system: ActorSystem)
    extends ScalaTestWithClassicActorTestKit(system)
    with LernaBaseSpec
    with Inside
