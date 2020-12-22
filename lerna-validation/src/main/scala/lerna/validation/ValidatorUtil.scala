package lerna.validation

import java.text.BreakIterator

import lerna.util.lang.Equals._

/** An object that provides utilities for validation/validators.
  */
object ValidatorUtil {

  /** Return `true` if the length of the given string `value` is in the range [`min`, `max`] (both inclusive), `false` otherwise.
    *
    * @param value the string in which length is used
    * @param min the min of the range
    * @param max the max of the range
    * @return `true` if the `value` satisfies the condition, `false` otherwise.
    */
  def isLengthRange(value: String, min: Int = 0, max: Int = Integer.MAX_VALUE): Boolean = {
    val bi = BreakIterator.getCharacterInstance()
    bi.setText(value)

    val count =
      Iterator
        .continually(bi.next())
        .takeWhile(_ !== BreakIterator.DONE)
        .length

    count >= min && count <= max
  }

  /** Return `true` if the given character `c` is a single-byte character, `false` otherwise.
    *
    * ==Details==
    * This method treats the specific Unicode Block Range as a single-byte character.
    * The range is ''Basic Latin'' (0000..007f) and ''Latin-1 Supplement'' (0080..00FF), which is compatible with ISO 8859-1.
    * You can see the Unicode block range in [[https://www.unicode.org/charts/PDF/U0000.pdf Official Unicode Consortium code chart]]
    *
    * @param c the character that is checked
    * @return `true` if the character satisfies the condition, `false` otherwise.
    */
  def isHankakuEiSujiKigo(c: Char): Boolean = {
    c <= '\u00ff'
  }

  /** Return `true` if the given character `c` is a Halfwidth Katakana, `false` otherwise.
    *
    * ==Details==
    * This method treats the specific Unicode Block Range (FF61..FF9F) as a Halfwidth Katakana.
    * You can see the Unicode block range in [[https://www.unicode.org/charts/PDF/UFF00.pdf Official Unicode Consortium code chart]]
    *
    * @param c the character that is checked
    * @return `true` if the character satisfies the condition, `false` otherwise.
    */
  def isHankakuKana(c: Char): Boolean = {
    c >= '\uff61' && c <= '\uff9f'
  }
}
