package lerna.util.tenant

/** A trait that represents a tenant
  */
trait Tenant {

  /** An ID of the tenant
    * @return The ID
    */
  def id: String
}
