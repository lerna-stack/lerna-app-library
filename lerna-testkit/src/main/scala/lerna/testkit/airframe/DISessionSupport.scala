package lerna.testkit.airframe

import org.scalatest.{ BeforeAndAfterAll, Suite }
import wvlet.airframe.{ Design, Session }

/** A trait that provides an Airframe DI session in a test suit
  *
  * ==Overview==
  * The trait provides an Airframe DI session in a test suit.
  * The session is initialized if you use it.
  */
trait DISessionSupport extends BeforeAndAfterAll { this: Suite =>

  /** The Airframe DI Design that you use in the test
    */
  protected val diDesign: Design

  /** The Airframe DI Session that you use in the test.
    * You can override if you need it.
    */
  protected lazy val diSession: Session = diDesign.newSession

  override def afterAll(): Unit = {
    try super.afterAll()
    finally diSession.shutdown
  }
}
