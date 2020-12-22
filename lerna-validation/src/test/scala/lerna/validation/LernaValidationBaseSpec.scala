package lerna.validation

import lerna.tests.LernaBaseSpec

/** A test base class which improve consistency and reduce boilerplate in lerna validation tests.
  * '''Use `class` instead of `trait` for speedier compiles.'''
  *
  * @see [[https://www.scalatest.org/user_guide/defining_base_classes Defining base classes for your project]]
  */
abstract class LernaValidationBaseSpec extends LernaBaseSpec
