package lerna.util

import lerna.tests.LernaBaseSpec
import org.scalatest.Inside

/** The test base class which improve consistency and reduce boilerplate in lerna util tests.
  * '''Use `class` instead of `trait` for speedier compiles.'''
  *
  * @see [[https://www.scalatest.org/user_guide/defining_base_classes Defining base classes for your project]]
  */
abstract class LernaUtilBaseSpec extends LernaBaseSpec with Inside {}
