package lerna.log.logback.converter

/** An object that provides utilities for log converters
  */
object ConverterUtil {

  /** Concert the given string to one-line string
    *
    * @param src The string to be converted
    * @return The one-line string
    */
  def toOneline(src: String): String = {

    src.flatMap {
      case '\\'  => "\\\\"
      case '\r'  => ""
      case '\n'  => "\\n"
      case '\t'  => "    "
      case other => Character.toString(other)
    }
  }

}
