package lerna.util.time

import java.time.{ Clock, Instant, ZoneId, ZoneOffset }

/** An object that provides factory methods of [[FixedLocalDateTimeFactory]]
  */
object FixedLocalDateTimeFactory {

  /** Create a [[FixedLocalDateTimeFactory]] instance
    *
    * The `dateTime` must represent a valid instant in UTC.
    *   e.g.) 2019-05-01T00:00:00Z
    *
    * @param dateTime The date-time string that is used as a clock
    * @return The factory instance
    */
  def apply(dateTime: String): FixedLocalDateTimeFactory =
    new FixedLocalDateTimeFactory(Instant.parse(dateTime), ZoneOffset.UTC)

  /** Create a [[FixedLocalDateTimeFactory]] instance
    *
    * @param fixedInstant The fixed instantaneous point on the timeline that is used as a clock
    * @param zone The time zone that is used to interprets the instant
    * @return The factory instance
    */
  def apply(fixedInstant: Instant, zone: ZoneId): FixedLocalDateTimeFactory =
    new FixedLocalDateTimeFactory(fixedInstant, zone)
}

/** A [[LocalDateTimeFactory]] implementation that returns fixed time based on the given clock
  *
  * ==Cautions==
  * [[LocalDateTimeFactory]] returns a current time without time-zone.
  * The given `zone` parameter is only used at initialization.
  * If you want to get a date and time with a time zone, consider using [[java.time.ZonedDateTime]] directly.
  *
  * @param fixedInstant The fixed instantaneous point on the timeline
  * @param zone The time zone that is used to interprets the instant
  */
class FixedLocalDateTimeFactory private (fixedInstant: Instant, zone: ZoneId) extends LocalDateTimeFactory {
  override protected val clock: Clock = Clock.fixed(fixedInstant, zone)
}
