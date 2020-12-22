package lerna.http.json

import lerna.util.lang.Equals._
import spray.json.{ DeserializationException, JsString, JsValue, RootJsonFormat }

/** An object that provides factory methods of [[EnumJsonFormat]]
  */
object EnumJsonFormat {

  /** Create a JSON format of [[scala.Enumeration]]
    *
    * @param enu The instance of Enumeration `T`
    * @tparam T The type of the given Enumeration
    * @return The JSON format of Enumeration `T`
    */
  def apply[T <: scala.Enumeration](enu: T): RootJsonFormat[T#Value] = new EnumJsonFormat[T](enu)

}

/** A class that provides a JSON serialization/deserialization of [[scala.Enumeration]]
  *
  * @param enu The instance of enumeration with type `T`
  * @tparam T The type of the given enumeration
  * @note Based on the code found: [[https://github.com/spray/spray-json/issues/200]]
  */
class EnumJsonFormat[T <: scala.Enumeration](enu: T) extends RootJsonFormat[T#Value] {
  override def write(obj: T#Value): JsValue = JsString(obj.toString)

  override def read(json: JsValue): T#Value = {
    json match {
      case JsString(txt) if enu.values.exists(_.toString === txt) => enu.withName(txt)
      case somethingElse =>
        throw DeserializationException(s"Expected one of (${enu.values.mkString(",")}) instead of $somethingElse")
    }
  }
}
