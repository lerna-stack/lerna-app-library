package lerna.http

import akka.http.scaladsl.testkit.ScalatestRouteTest

/** A test base class which improve consistency and reduce boilerplate in lerna http route tests.
  * '''Use `class` instead of `trait` for speedier compiles.'''
  *
  * @see [[https://www.scalatest.org/user_guide/defining_base_classes Defining base classes for your project]]
  */
abstract class LernaHttpRouteBaseSpec extends LernaHttpBaseSpec with ScalatestRouteTest
