package lerna.http

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.http.scaladsl.ClientTransport
import akka.http.scaladsl.model.headers
import akka.http.scaladsl.settings.{ ClientConnectionSettings, ConnectionPoolSettings }
import lerna.util.tenant.Tenant

/** A trait that provides generating HTTP proxy settings
  *
  * The proxy settings can be configured by [[com.typesafe.config.Config]].
  * You should configure the settings if you use an HTTP proxy.
  */
trait HttpRequestProxySupport {

  /** The [[akka.actor.ActorSystem]] that is used for extracting settings.
    */
  val system: ActorSystem

  private val baseConnectionPoolSettings = ConnectionPoolSettings(system)

  private val tenantsConfig                        = system.settings.config.getConfig("lerna.http.proxy.tenants")
  private def proxyConfig(implicit tenant: Tenant) = tenantsConfig.getConfig(s"${tenant.id}")

  private def host(implicit tenant: Tenant) = proxyConfig.getString("host")
  private def port(implicit tenant: Tenant) = proxyConfig.getInt("port")

  private def username(implicit tenant: Tenant) = proxyConfig.getString("authentication.username")
  private def password(implicit tenant: Tenant) = proxyConfig.getString("authentication.password")

  private def proxyAddress(implicit tenant: Tenant)    = InetSocketAddress.createUnresolved(host, port)
  private def httpCredentials(implicit tenant: Tenant) = headers.BasicHttpCredentials(username, password)

  private def httpsProxyTransport(implicit tenant: Tenant) = ClientTransport.httpsProxy(proxyAddress, httpCredentials)

  /** Create a [[akka.http.scaladsl.settings.ConnectionPoolSettings]] for the configuration in this trait.
    *
    * @param useProxy Whether use HTTP proxy or not. Use the proxy if `true`
    * @param tenant Tenant used to get proxy settings
    * @return The configuration of connection pool
    */
  protected def generateRequestSetting(useProxy: Boolean)(implicit tenant: Tenant): ConnectionPoolSettings = {
    if (useProxy) {
      val clientConnectionSettings = ClientConnectionSettings(system).withTransport(httpsProxyTransport)
      baseConnectionPoolSettings.withConnectionSettings(clientConnectionSettings)
    } else {
      baseConnectionPoolSettings
    }
  }
}
