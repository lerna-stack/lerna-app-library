package lerna.http

import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.headers.CustomHeader

/** A trait that provides a custom HTTP header with a credential value or a secret value
  *
  * The trait overrides `toString` for masking the value of this header.
  */
trait SecretHeader { this: HttpHeader =>
  import lerna.util.security.SecretVal._

  private[this] val outer = this

  private[this] lazy val delegate: CustomHeader = new CustomHeader {
    override val name: String               = outer.name()
    override val value: String              = outer.value().asSecret.toString
    override val renderInRequests: Boolean  = outer.renderInRequests()
    override val renderInResponses: Boolean = outer.renderInResponses()
  }

  override final def toString: String = delegate.toString()
}
