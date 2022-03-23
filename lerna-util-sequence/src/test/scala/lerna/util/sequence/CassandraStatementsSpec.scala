package lerna.util.sequence

import com.datastax.oss.driver.api.core.CqlIdentifier
import com.datastax.oss.driver.api.core.cql.{ Row, SimpleStatement }
import com.typesafe.config.{ Config, ConfigFactory }
import lerna.testkit.akka.ScalaTestWithTypedActorTestKit
import lerna.tests.LernaBaseSpec
import lerna.util.tenant.Tenant
import org.scalatest.BeforeAndAfterEach

import scala.annotation.nowarn
import scala.jdk.CollectionConverters._
import scala.compat.java8.OptionConverters._

object CassandraStatementsSpec {

  @nowarn
  private val throwsException: Int = {
    throw new RuntimeException("initialization exception for some reasons")
    1
  }

  private val tenant: Tenant = new Tenant {
    override def id: String = "example"
  }

  private val config: Config = ConfigFactory
    .parseString(s"""
                    |akka.actor {
                    |  provider = local
                    |}
                    |lerna.util.sequence {
                    |  cassandra.tenants.${tenant.id} = $${lerna.util.sequence.cassandra.default}
                    |}
    """.stripMargin)
    .withFallback(ConfigFactory.defaultReferenceUnresolved())
    .resolve()
}

final class CassandraStatementsSpec
    extends ScalaTestWithTypedActorTestKit(CassandraStatementsSpec.config)
    with LernaBaseSpec
    with BeforeAndAfterEach {

  import CassandraStatementsSpec._

  private lazy val cassandraConfig =
    new SequenceFactoryConfig(system.settings.config).cassandraConfig(tenant)

  private lazy val session =
    CqlSessionProvider.connect(system, cassandraConfig).futureValue

  private lazy val statements =
    new CassandraStatements(cassandraConfig)

  override def beforeEach(): Unit = {
    super.beforeEach()
    SequenceTestkit.dropKeyspaceIfExists(session, cassandraConfig)
  }

  override def afterAll(): Unit = {
    try session.close()
    finally super.afterAll()
  }

  def executeSelectSequenceReservation(sequenceId: String, sequenceSubId: String, nodeId: Int): BigInt = {
    session
      .execute(
        statements.selectSequenceReservation.setPositionalValues(
          Seq[AnyRef](
            sequenceId,
            sequenceSubId,
            Int.box(nodeId),
          ).asJava,
        ),
      )
      .one()
      .getBigInteger("max_reserved_value")
  }

  def executeInsertSequenceReservation(
      sequenceId: String,
      sequenceSubId: String,
      nodeId: Int,
      newMaxReservedValue: BigInt,
  ): Unit = {
    session
      .execute(
        statements.insertSequenceReservation.setPositionalValues(
          Seq[AnyRef](
            sequenceId,
            sequenceSubId,
            Int.box(nodeId),
            newMaxReservedValue.bigInteger,
          ).asJava,
        ),
      )
  }

  "createKeyspace is idempotent" in {

    expect(statements.createKeyspace.isIdempotent)

    // See also https://docs.datastax.com/en/cql-oss/3.x/cql/cql_using/useQuerySystemTable.html
    def fetchKeyspaceInfo(keyspaceName: String): Row = {
      val statement =
        SimpleStatement
          .newInstance("SELECT * FROM system_schema.keyspaces WHERE keyspace_name = ?")
          .setPositionalValues(Seq[AnyRef](keyspaceName).asJava)
      session.execute(statement).one()
    }

    // Ensure the statement is idempotent by executing the statement twice
    // (1)
    session.execute(statements.createKeyspace)
    val firstResult = fetchKeyspaceInfo(cassandraConfig.cassandraKeyspace)
    // (2)
    session.execute(statements.createKeyspace)
    val secondResult = fetchKeyspaceInfo(cassandraConfig.cassandraKeyspace)

    expect(firstResult.getFormattedContents === secondResult.getFormattedContents)

  }

  "createTable is idempotent" in {

    expect(statements.createTable.isIdempotent)

    // See also https://docs.datastax.com/en/cql-oss/3.x/cql/cql_using/useQuerySystemTable.html
    def fetchTableInfo(keyspaceName: String, tableName: String): Row = {
      val statement =
        SimpleStatement
          .newInstance("SELECT * FROM system_schema.tables WHERE keyspace_name = ? AND table_name = ?")
          .setPositionalValues(Seq[AnyRef](keyspaceName, tableName).asJava)
      session.execute(statement).one()
    }

    // Prepare
    session.execute(statements.createKeyspace)
    session.execute(statements.useKeyspace)

    // Ensure the statement is idempotent by executing the statement twice
    // (1)
    session.execute(statements.createTable)
    val firstResult = fetchTableInfo(cassandraConfig.cassandraKeyspace, cassandraConfig.cassandraTable)
    // (2)
    session.execute(statements.createTable)
    val secondResult = fetchTableInfo(cassandraConfig.cassandraKeyspace, cassandraConfig.cassandraTable)

    expect(firstResult.getFormattedContents === secondResult.getFormattedContents)

  }

  "useKeyspace is idempotent" in {

    expect(statements.useKeyspace.isIdempotent)

    // Prepare
    session.execute(statements.createKeyspace)

    // Ensure the statement is idempotent by executing the statement twice
    // (1)
    session.execute(statements.useKeyspace)
    val firstResult = session.getKeyspace.asScala
    // (2)
    session.execute(statements.useKeyspace)
    val secondResult = session.getKeyspace.asScala

    expect(firstResult === secondResult)
    expect(firstResult === Option(CqlIdentifier.fromCql(cassandraConfig.cassandraKeyspace)))

  }

  "selectSequenceReservation is idempotent" in {

    expect(statements.selectSequenceReservation.isIdempotent)

    // Prepare
    session.execute(statements.createKeyspace)
    session.execute(statements.useKeyspace)
    session.execute(statements.createTable)
    executeInsertSequenceReservation("seq-id", "sub-seq-id", 1, 10)

    // Ensure the statement is idempotent by executing the statement twice
    // (1)
    val firstResult =
      executeSelectSequenceReservation("seq-id", "sub-seq-id", 1)
    // (2)
    val secondResult =
      executeSelectSequenceReservation("seq-id", "sub-seq-id", 1)

    expect(firstResult === secondResult)
    expect(firstResult === BigInt(10))

  }

  "insertSequenceReservation is idempotent" in {

    expect(statements.insertSequenceReservation.isIdempotent)

    // Prepare
    session.execute(statements.createKeyspace)
    session.execute(statements.useKeyspace)
    session.execute(statements.createTable)

    // Ensure the statement is idempotent by executing the statement twice
    // (1)
    executeInsertSequenceReservation("seq-id", "sub-seq-id", 1, 10)
    val firstResult =
      executeSelectSequenceReservation("seq-id", "sub-seq-id", 1)
    // (2)
    executeInsertSequenceReservation("seq-id", "sub-seq-id", 1, 10)
    val secondResult =
      executeSelectSequenceReservation("seq-id", "sub-seq-id", 1)

    expect(firstResult === secondResult)
    expect(firstResult === BigInt(10))

  }

}
