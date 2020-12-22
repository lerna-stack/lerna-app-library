package lerna.testkit

import org.scalatest.{ Matchers, WordSpecLike }

/** A test trait which improve consistency and reduce boilerplate in lerna testkit.
  *
  * @see [[https://www.scalatest.org/user_guide/defining_base_classes Defining base classes for your project]]
  */
trait LernaTestKitBaseSpec extends WordSpecLike with Matchers with EqualsSupport
