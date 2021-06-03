package lerna.testkit.akka

import akka.actor.testkit.typed.scaladsl.{ ActorTestKit, ActorTestKitBase }
import akka.actor.typed.ActorSystem
import org.scalatest.concurrent.{ Eventually, ScalaFutures }
import org.scalatest.time.Span
import org.scalatest.{ BeforeAndAfterAll, TestSuite }

/** A test class which improve consistency and reduce boilerplate.
  *
  * ==Overview==
  *
  * This class is inspired by
  * [[https://doc.akka.io/api/akka/2.6/akka/actor/testkit/typed/scaladsl/ScalaTestWithActorTestKit.html ScalaTestWithActorTestKit]]
  *
  * ScalaTest 3.0.x に依存しているため [[akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit ScalaTestWithActorTestKit]] をそのまま使えなかった
  * TODO: ScalaTest version up して [[akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit ScalaTestWithActorTestKit]] 使用に切替
  */
abstract class ScalaTestWithTypedActorTestKit(testKit: ActorTestKit)
    extends ActorTestKitBase(testKit)
    with TestSuite
    with BeforeAndAfterAll
    with ScalaFutures
    with Eventually
    with AkkaTypedSpanScaleFactorSupport {

  def this() = this(ActorTestKit())
  def this(system: ActorSystem[_]) = this(ActorTestKit(system))

  /** `PatienceConfig` from [[akka.actor.testkit.typed.TestKitSettings#DefaultTimeout]]
    */
  implicit val patience: PatienceConfig =
    PatienceConfig(testKit.testKitSettings.DefaultTimeout.duration, Span(100, org.scalatest.time.Millis))

  /** Shuts down the ActorTestKit. If override be sure to call super.afterAll
    * or shut down the testkit explicitly with `testKit.shutdownTestKit()`.
    */
  override protected def afterAll(): Unit = {
    super.afterAll()
    testKit.shutdownTestKit()
  }
}
