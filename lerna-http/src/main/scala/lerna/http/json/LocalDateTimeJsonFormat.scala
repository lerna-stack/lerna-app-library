package lerna.http.json

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import spray.json.RootJsonFormat

/** An object that provides factory methods of [[LocalDateTimeJsonFormat]]
  */
object LocalDateTimeJsonFormat {

  /** Create a JSON format of [[java.time.LocalDateTime]]
    *
    * @param pattern The format pattern that can be handled by [[java.time.format.DateTimeFormatter]]
    * @return The JSON format of [[java.time.LocalDateTime]]
    */
  def apply(pattern: String): RootJsonFormat[LocalDateTime] = {
    apply(DateTimeFormatter.ofPattern(pattern))
  }

  /** Create a JSON formatter of [[java.time.LocalDateTime]]
    *
    * @param formatter The formatter of [[java.time.LocalDateTime]]
    * @return The JSON format of [[java.time.LocalDateTime]]
    */
  def apply(formatter: DateTimeFormatter): RootJsonFormat[LocalDateTime] = {
    new LocalDateTimeJsonFormat(formatter)
  }
}

/** A class that provides a JSON format of [[java.time.LocalDateTime]]
  *
  * @param formatter The formatter of [[java.time.LocalDateTime]]
  */
final class LocalDateTimeJsonFormat private (formatter: DateTimeFormatter) extends RootJsonFormat[LocalDateTime] {
  import spray.json._

  override def write(datetime: LocalDateTime): JsValue = JsString(datetime.format(formatter))
  override def read(json: JsValue): LocalDateTime = json match {
    case JsString(dateStr) => LocalDateTime.parse(dateStr, formatter)
    case x                 => deserializationError(s"Expected DateTime as JsString, but got $x")
  }
}
