package lerna.validation

import com.wix.accord._
import com.wix.accord.ViolationBuilder._

/** An object that provides custom combinators of [[com.wix.accord]]
  */
object CustomCombinators {

  /** A validator that validates whether all characters of the given string is ''半角英数字記号''.
    *
    * @return The validator
    */
  lazy val `半角英数字記号`: Validator[String] =
    new NullSafeValidator[String](
      test = { value =>
        value.toCharArray.forall { c =>
          ValidatorUtil.isHankakuEiSujiKigo(c)
        }
      },
      failure = _ -> s"contains a character other than Alphabets, Numbers, or Symbols.",
    )

  /** A validator that validates whether all characters of the given string is ''半角英数字''
    *
    * @return The Validator
    */
  lazy val `半角英数字`: Validator[String] = {

    val r = """^[a-zA-Z0-9]+""".r

    new NullSafeValidator[String](
      test = {
        case r(_*) => true
        case _     => false
      },
      failure = _ -> s"contains a character other than Alphabets or Numbers.",
    )
  }

  /** A validator that validates whether all characters of the given string is ''半角''
    *
    * ''半角'' is either ''半角英数字記号'' or ''半角カナ''.
    *
    * @return The validator
    */
  lazy val `半角`: Validator[String] =
    new NullSafeValidator[String](
      test = { value =>
        value.toCharArray.forall { c =>
          ValidatorUtil.isHankakuEiSujiKigo(c) || ValidatorUtil.isHankakuKana(c)
        }
      },
      failure = _ -> s"contains a character other than Alphabets, Numbers, Symbols, or Halfwidth Katakana.",
    )

  /** A validator that validates whether all characters of the given string is ''全角''
    *
    * ''全角'' is the one that is not ''半角''.
    *
    * @return The validator
    */
  lazy val `全角`: Validator[String] =
    new NullSafeValidator[String](
      test = { value =>
        value.toCharArray.forall { c =>
          !(ValidatorUtil.isHankakuEiSujiKigo(c) || ValidatorUtil.isHankakuKana(c))
        }
      },
      failure = _ -> s"contains Alphabets, Numbers, Symbols, or Halfwidth Katakana.",
    )

  /** A validator that validates whether all characters of the given string is ''半角数字''
    *
    * @return The validator
    */
  lazy val `半角数字`: Validator[String] =
    new NullSafeValidator[String](
      test = { value =>
        value.toCharArray.forall { c =>
          c >= '\u0030' && c <= '\u0039'
        }
      },
      failure = _ -> s"contains a character other than Numbers.",
    )

  /** A validator that validates whether the length of the given string is in range.
    *
    * @param min The minimum of the range (inclusive)
    * @param max The maximum of the range (inclusive)
    * @return The validator
    */
  def lengthRange(min: Int = 0, max: Int = Integer.MAX_VALUE): Validator[String] =
    new NullSafeValidator[String](
      test = { value =>
        ValidatorUtil.isLengthRange(value, min, max)
      },
      failure = _ -> s"is invalid length.",
    )

  /** A validator that validates whether the given string forms the decimal format.
    * The decimal format is `12345.678` like.
    *
    * @example
    * {{{
    * scala> import com.wix.accord._
    * scala> import lerna.validation.CustomCombinators._
    *
    * scala> validate("123.45")(decimal(min=0,max=4,scale=2))
    * res0: Result = Success
    * scala> validate("123.")(decimal(min=3,max=5,scale=0))
    * res1: Result = Success
    *
    * scala> validate("123.45")(decimal(min=4,max=6,scale=2)).isSuccess
    * res3: Boolean = false
    * scala> validate("123.45")(decimal(min=1,max=2,scale=2)).isSuccess
    * res3: Boolean = false
    * scala> validate("123.45")(decimal(min=0,max=10,scale=3)).isSuccess
    * res3: Boolean = false
    *
    * }}}
    *
    * @param min The minimum length of the decimal part
    * @param max The maximum length of the integer part
    * @param scale The length of the fractional part
    * @return The validator
    *
    * @note All of `min`, `max` and `scale` are '''not''' well checked.
    *       This validator behaves weirdly with some parameter combinations.
    *       The behavior will be changed.
    */
  def decimal(min: Int = 0, max: Int = Integer.MAX_VALUE, scale: Int = 0): Validator[String] = {

    val r = s"[0-9]{${min.toString},${max.toString}}\\.[0-9]{${scale.toString}}".r

    new NullSafeValidator[String](
      test = {
        case r(_*) => true
        case _     => false
      },
      failure =
        _ -> s"""is not a decimal format. It should be match the regex "${r.toString}". min = ${min.toString}, max = ${max.toString}, scale = ${scale.toString}""",
    )
  }

}
