package lerna.util.time

import java.time.{ Clock, LocalDateTime }

/** A trait that provides factory methods of [[java.time.LocalDateTime]]
  */
trait LocalDateTimeFactory {

  /** The clock that is used by creating a time instance
    */
  protected val clock: Clock

  /** Get the current time based on the `clock`
    *
    * @return The current time
    */
  def now(): LocalDateTime = {
    LocalDateTime.now(clock)
  }
}

/** An object that provides factory features of [[LocalDateTimeFactory]]
  */
object LocalDateTimeFactory {

  /** Create a default [[LocalDateTimeFactory]] instance
    *
    * @return The factory
    */
  def apply(): LocalDateTimeFactory = {
    new LocalDateTimeFactoryImpl()
  }
}
