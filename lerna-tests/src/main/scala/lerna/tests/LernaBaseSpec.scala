package lerna.tests

import lerna.testkit.EqualsSupport
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

/** A test trait which improve consistency and reduce boilerplate in lerna tests.
  *
  * @see [[https://www.scalatest.org/user_guide/defining_base_classes Defining base classes for your project]]
  */
trait LernaBaseSpec extends AnyWordSpecLike with Matchers with EqualsSupport with PowerAssertionsSupport
