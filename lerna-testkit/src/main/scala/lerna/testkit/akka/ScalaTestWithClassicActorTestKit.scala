package lerna.testkit.akka

import akka.actor.ActorSystem
import akka.testkit.{ ImplicitSender, TestKit }
import org.scalatest.{ BeforeAndAfterAll, TestSuite }
import org.scalatest.concurrent.{ Eventually, ScalaFutures }

/** A class that provides integration of ScalaTest and classic Akka TestKit
  *
  * ==Overview==
  *
  * This class is inspired by
  * [[https://doc.akka.io/api/akka/2.6/akka/actor/testkit/typed/scaladsl/ScalaTestWithActorTestKit.html ScalaTestWithActorTestKit]]
  */
abstract class ScalaTestWithClassicActorTestKit(system: ActorSystem)
    extends TestKit(system)
    with TestSuite
    with BeforeAndAfterAll
    with ScalaFutures
    with Eventually
    with ImplicitSender
    with AkkaPatienceConfigurationSupport
    with AkkaSpanScaleFactorSupport {

  override def afterAll(): Unit = {
    try shutdown(system)
    finally super.afterAll()
  }
}
