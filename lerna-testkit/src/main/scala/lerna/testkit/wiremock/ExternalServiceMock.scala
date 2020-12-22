package lerna.testkit.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.`extension`.responsetemplating.ResponseTemplateTransformer
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.stubbing.StubImport

/** The trait that provides a WireMockServer.
  *
  * The trait supports [[java.lang.AutoCloseable]], so you can use it with a loan-pattern.
  */
trait ExternalServiceMock extends AutoCloseable {
  import WireMockConfiguration._

  /** Use HTTPS or not.
    */
  protected lazy val https: Boolean = false

  /** The hostname of the mock server.
    */
  protected lazy val host: String = "localhost"

  /** The port number of the mock server.
    *
    * A random port is used if you specify [[scala.None]].
    */
  protected lazy val port: Option[Int] = None

  /** The mock server.
    *
    * The server is automatically started when you use it.
    */
  lazy val server: WireMockServer = {
    val baseConfig = wireMockConfig()
      .bindAddress(host)
      .port(0) // workaround: ポート番号を指定しているのにもかかわらずデフォルト（8080）に bind されてしまうのを回避
      .extensions(new ResponseTemplateTransformer(false))
      .maxRequestJournalEntries(1000) // この設定がないと無現にRequestLogを溜め込んでいき、GCでも解放されない。
      .containerThreads(400)          // Set the number of request handling threads in Jetty. Defaults to 10.

    val config = if (https) {
      port.fold(baseConfig.dynamicHttpsPort())(p => baseConfig.httpsPort(p))
    } else {
      port.fold(baseConfig.dynamicPort())(p => baseConfig.port(p))
    }
    val instance = new WireMockServer(config)
    instance.start()
    instance
  }

  override def close(): Unit = {
    if (server.isRunning) {
      server.stop()
    }
  }

  /** Import the stubs.
    *
    * If a stub in `stubs` which key already exists in the mock server, the existing stub are overwritten.
    * If an existing stub which key doesn't exist in `stubs`, the existing stub remains.
    *
    * @see [[http://wiremock.org/docs/stubbing/ Stubbing - WireMock]]
    * @param stubs the stubs that are imported.
    * @note If you want to customize the above behavior, use [[com.github.tomakehurst.wiremock.stubbing.StubImport]] and `server.importStubs()` directly.
    */
  def importStubs(stubs: MappingBuilder*): Unit = {
    val imports = stubs.foldLeft(StubImport.stubImport())((builder, stub) => builder.stub(stub)).build()
    server.importStubs(imports)
  }
}
