package lerna.util.sequence

private[sequence] final class CassandraStatements(config: SequenceFactoryCassandraConfig) {

  val createKeyspace: String =
    s"""
     |CREATE KEYSPACE IF NOT EXISTS ${config.cassandraKeyspace}
     |  WITH REPLICATION = {
     |    ${config.cassandraReplication}
     |  }
     """.stripMargin

  val useKeyspace: String =
    s"""
       |USE ${config.cassandraKeyspace}
     """.stripMargin

  val createTable: String =
    s"""
    |CREATE TABLE IF NOT EXISTS ${config.cassandraTable} (
    | sequence_id             varchar,
    | sequence_sub_id         varchar,
    | node_id                 int,
    | max_reserved_value      varint,
    | PRIMARY KEY ((sequence_id, sequence_sub_id, node_id))
    |)
    """.stripMargin

  val selectSequenceReservation: String =
    s"""
    |SELECT max_reserved_value FROM ${config.cassandraTable}
    |  WHERE sequence_id = ?
    |    AND sequence_sub_id = ?
    |    AND node_id = ?
    """.stripMargin

  val insertSequenceReservation: String =
    s"""
    |INSERT INTO ${config.cassandraTable} (sequence_id, sequence_sub_id, node_id, max_reserved_value) VALUES (?, ?, ?, ?)
    """.stripMargin

}
