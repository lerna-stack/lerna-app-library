package lerna.tests

import com.eed3si9n.expecty.Expecty

/** A trait that provides Power Assertions support
  *
  * @see [[https://github.com/pniederw/expecty Expecty]]
  */
trait PowerAssertionsSupport {
  val expect: Expecty = new Expecty()
}
