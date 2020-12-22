package lerna.testkit

import org.scalactic.TypeCheckedTripleEquals

import scala.language.implicitConversions

/** A trait that provides a [[org.scalactic.TypeCheckedTripleEquals.CheckingEqualizer]] for any type
  *
  * ==Overview==
  * This trait provides a [[org.scalactic.TypeCheckedTripleEquals.CheckingEqualizer]] for [[org.scalactic.TypeCheckedTripleEquals]].
  * For more details, see [[org.scalactic.TypeCheckedTripleEquals]].
  *
  * @see [[lerna.testkit.EqualsSupport$]]
  */
trait EqualsSupport extends TypeCheckedTripleEquals {
  implicit override def convertToCheckingEqualizer[T](left: T): CheckingEqualizer[T] = new CheckingEqualizer(left) {
    override def toString: String = Option(left).fold("null")(_.toString)
  }
}

/** A object that provides a [[org.scalactic.TypeCheckedTripleEquals.CheckingEqualizer]] for any type
  *
  * ==Overview==
  * This object is useful to use the features provided by [[lerna.testkit.EqualsSupport]] without mix-in the trait.
  *
  * @example
  * {{{
  * scala> import lerna.testkit.EqualsSupport._
  *
  * scala> 1 === 1
  * res0: Boolean = true
  * scala> 1 === 2
  * res1: Boolean = false
  * }}}
  *
  * A compile error occurs if the wrong types are compared.
  * {{{
  * scala> import lerna.testkit.EqualsSupport._
  * scala> import org.scalatest.Assertions._
  *
  * scala> assertDoesNotCompile("1 === 1.0").isSucceeded
  * res0: Boolean = true
  * }}}
  */
object EqualsSupport extends EqualsSupport
