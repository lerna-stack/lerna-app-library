package lerna.util.security

import lerna.util.lang.Equals._

/** An object that provides factory methods of [[SecretVal]]
  */
object SecretVal {

  /** A class that provides extension methods related to [[SecretVal]] for any type
    * @param value Any value of the type `T`
    * @tparam T The type of the target instance
    */
  implicit class SecretValExt[T](value: T) {

    /** Create a [[SecretVal]] holding the given `value`
      * @return The [[SecretVal]]
      */
    def asSecret: SecretVal[T] = SecretVal(value)
  }

  private def mask(value: String): String = {
    value.replaceAll(".", "*")
  }
}

/** A class that represents confidential information
  *
  * `toString` of this class returns a masked value.
  * It is useful for avoiding to log the confidential information accidentally.
  *
  * @param underlying The confidential information
  * @tparam T The type of the confidential information
  */
final case class SecretVal[T](underlying: T) {
  import SecretVal._

  override def toString: String = underlying match {
    case p: Product if p.productArity === 0 => p.toString
    case p: Product                         => s"${p.productPrefix}(${p.productIterator.map(v => mask(v.toString)).mkString(", ")})"
    case _                                  => mask(underlying.toString)
  }
}
