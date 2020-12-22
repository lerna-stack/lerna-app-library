package lerna.testkit

import _root_.akka.http.scaladsl.testkit.ScalatestRouteTest
import lerna.testkit.akka.{ AkkaPatienceConfigurationSupport, AkkaSpanScaleFactorSupport }
import org.scalatest.concurrent.{ Eventually, ScalaFutures }

/** A test base class which improve consistency and reduce boilerplate in lerna testkit tests.
  *
  * '''Use `class` instead of `trait` for speedier compiles.'''
  * @see [[https://www.scalatest.org/user_guide/defining_base_classes Defining base classes for your project]]
  */
abstract class LernaRouteTestBaseSpec
    extends LernaTestKitBaseSpec
    with ScalatestRouteTest
    with ScalaFutures
    with Eventually
    with AkkaSpanScaleFactorSupport
    with AkkaPatienceConfigurationSupport
