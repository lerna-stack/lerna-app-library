package lerna.tests

import lerna.testkit.EqualsSupport
import org.scalatest.{ Matchers, WordSpecLike }

/** A test trait which improve consistency and reduce boilerplate in lerna tests.
  *
  * @see [[https://www.scalatest.org/user_guide/defining_base_classes Defining base classes for your project]]
  */
trait LernaBaseSpec extends WordSpecLike with Matchers with EqualsSupport with PowerAssertionsSupport
