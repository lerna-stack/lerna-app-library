package lerna.util.time

import java.sql.Timestamp
import java.time.LocalDateTime

/** An object that provides extension methods related to date-time API
  */
object DateTimeConverters {

  /** The implicit conversions from [[java.time.LocalDateTime]] to [[java.sql.Timestamp]]
    * @param self The [[java.time.LocalDateTime]] instance
    */
  final implicit class DateTimeOps(val self: LocalDateTime) extends AnyVal {
    def asTimestamp: Timestamp = Timestamp.valueOf(self)
  }

}
