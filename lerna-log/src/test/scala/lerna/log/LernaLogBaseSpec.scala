package lerna.log

import lerna.tests.LernaBaseSpec

/** A test base class which improve consistency and reduce boilerplate in lerna log tests.
  * '''Use `class` instead of `trait` for speedier compiles.'''
  *
  * @see [[https://www.scalatest.org/user_guide/defining_base_classes Defining base classes for your project]]
  */
abstract class LernaLogBaseSpec extends LernaBaseSpec
