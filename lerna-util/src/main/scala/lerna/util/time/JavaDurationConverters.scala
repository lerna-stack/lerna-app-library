/** Copyright (C) 2009-2018 Lightbend Inc. <https://www.lightbend.com>
  * Copied from akka.util.JavaDurationConverters.
  * We seem to use official converters form 2.13.0 version.
  */
package lerna.util.time

import java.time.{ Duration => JDuration }

import scala.concurrent.duration.{ Duration, FiniteDuration }

/** An object that provides extension methods related to [[java.time.Duration]] and [[scala.concurrent.duration.Duration]]
  */
object JavaDurationConverters extends JavaDurationConverters

trait JavaDurationConverters {

  /** The implicit conversion from [[java.time.Duration]] to [[scala.concurrent.duration.FiniteDuration]]
    * @param self The Java Duration
    */
  final implicit class JavaDurationOps(val self: JDuration) {
    def asScala: FiniteDuration = Duration.fromNanos(self.toNanos)
  }

  /** The implicit conversion from [[scala.concurrent.duration.Duration]] to [[java.time.Duration]]
    * @param self The Scala Duration
    */
  final implicit class ScalaDurationOps(val self: Duration) {
    def asJava: JDuration = JDuration.ofNanos(self.toNanos)
  }
}
