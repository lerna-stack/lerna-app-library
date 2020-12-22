package lerna.util.sequence

import lerna.util.tenant.Tenant

import scala.concurrent.Future

/** A trait that provides generating ID sequence
  *
  * A concrete implementation for this trait must behave like below.
  *  - Generate unique ID only for the given `subID`
  *    This means that IDs generated with different `subID` can be the same value.
  *    We can think `subID` as a namespace.
  *  - A Generated ID should be in the range [0, `maxSequence`] (both inclusive)
  *
  * Behaviors that are not described above are dependent on a concrete implementation.
  */
trait SequenceFactory {

  /** Generate the next ID for the given ''sub ID''
    *
    * @param subId The sub ID
    * @return A [[scala.concurrent.Future]] containing the generated ID
    */
  final def nextId(subId: String)(implicit tenant: Tenant): Future[BigInt] = nextId(Option(subId))

  /** Generate the next ID for a default ''sub ID''
    *
    * @return The generated ID
    */
  final def nextId()(implicit tenant: Tenant): Future[BigInt] = nextId(None)

  /** Generate the next ID for the given ''sub ID''
    *
    * @param subId The namespace. If it is [[scala.None]], a default ''sub ID'' is used.
    * @return A [[scala.concurrent.Future]] containing the generated ID
    */
  def nextId(subId: Option[String])(implicit tenant: Tenant): Future[BigInt]

  /** The maximum ID that can be generated
    * @return The maximum ID
    */
  def maxSequence: BigInt
}
