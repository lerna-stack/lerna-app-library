package lerna.http.json

import spray.json.{ DefaultJsonProtocol, JsonFormat }

/** An object that provides factory methods of [[AnyValJsonFormat]]
  */
object AnyValJsonFormat {

  /** Create a JSON format of `T`
    *
    * @param apply The function that converts from the type `V` instance to the type `T` instance
    * @param unapply The function that converts from the type `T` instance to an optional value containing the type `V` instance
    * @param format The serialization format for type `V`
    * @tparam T The type for newly defined serialization
    * @tparam V The type used for underlying serialization
    * @return The Json format of `T`
    */
  def apply[T, V](apply: V => T, unapply: T => Option[V])(implicit format: JsonFormat[V]): JsonFormat[T] =
    new AnyValJsonFormat[T, V](apply, unapply)
}

/** A class that provides a JSON serialization/deserialization of any type
  *
  * The instance with the type `T` is serialized and deserialized using [[spray.json.JsonFormat]] for type `V`.
  *
  * @param apply The function that converts from the type `V` instance to the type `T` instance
  * @param unapply The function that converts from the type `T` instance to an optional value containing the type `V` instance
  * @param format The serialization format for type `V`
  * @tparam T The type for newly defined serialization
  * @tparam V The type used for underlying serialization
  */
class AnyValJsonFormat[T, V] private (apply: V => T, unapply: T => Option[V])(implicit format: JsonFormat[V])
    extends JsonFormat[T]
    with DefaultJsonProtocol {
  import spray.json._

  override def write(t: T): JsValue    = unapply(t).toJson
  override def read(value: JsValue): T = apply(value.convertTo[V])
}
