package lerna.http

import lerna.tests.LernaBaseSpec
import org.scalatest.Inside

/** A test base class which improve consistency and reduce boilerplate in lerna http tests.
  * '''Use `class` instead of `trait` for speedier compiles.'''
  *
  * @see [[https://www.scalatest.org/user_guide/defining_base_classes Defining base classes for your project]]
  */
abstract class LernaHttpBaseSpec extends LernaBaseSpec with Inside
