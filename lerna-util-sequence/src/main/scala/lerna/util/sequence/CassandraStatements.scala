package lerna.util.sequence

import com.datastax.oss.driver.api.core.cql.SimpleStatement

private[sequence] final class CassandraStatements(config: SequenceFactoryCassandraConfig) {

  val createKeyspaceCQL: String =
    s"""
     |CREATE KEYSPACE IF NOT EXISTS ${config.cassandraKeyspace}
     |  WITH REPLICATION = {
     |    ${config.cassandraReplication}
     |  }
     """.stripMargin
  val createKeyspace: SimpleStatement =
    SimpleStatement
      .newInstance(createKeyspaceCQL)
      .setIdempotent(true)

  val useKeyspaceCQL: String =
    s"""
       |USE ${config.cassandraKeyspace}
     """.stripMargin
  val useKeyspace: SimpleStatement =
    SimpleStatement
      .newInstance(useKeyspaceCQL)
      .setIdempotent(true)

  val createTableCQL: String =
    s"""
    |CREATE TABLE IF NOT EXISTS ${config.cassandraTable} (
    | sequence_id             varchar,
    | sequence_sub_id         varchar,
    | node_id                 int,
    | max_reserved_value      varint,
    | PRIMARY KEY ((sequence_id, sequence_sub_id, node_id))
    |)
    """.stripMargin
  val createTable: SimpleStatement =
    SimpleStatement
      .newInstance(createTableCQL)
      .setIdempotent(true)

  val selectSequenceReservationCQL: String =
    s"""
    |SELECT max_reserved_value FROM ${config.cassandraTable}
    |  WHERE sequence_id = ?
    |    AND sequence_sub_id = ?
    |    AND node_id = ?
    """.stripMargin
  val selectSequenceReservation: SimpleStatement =
    SimpleStatement
      .newInstance(selectSequenceReservationCQL)
      .setIdempotent(true)

  val insertSequenceReservationCQL: String =
    s"""
    |INSERT INTO ${config.cassandraTable} (sequence_id, sequence_sub_id, node_id, max_reserved_value) VALUES (?, ?, ?, ?)
    """.stripMargin
  val insertSequenceReservation: SimpleStatement =
    SimpleStatement
      .newInstance(insertSequenceReservationCQL)
      .setIdempotent(true)

}
