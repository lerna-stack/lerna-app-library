package lerna.util.sequence

import com.datastax.driver.core.{ Session, SimpleStatement }

import scala.concurrent.duration.DurationInt

object SequenceTestkit {
  def dropKeyspaceIfExists(session: Session, cassandraConfig: SequenceFactoryCassandraConfig): Unit = {
    // `cassandra.yaml`にあるCassandraサーバ側のタイムアウト
    // `truncate_request_timeout_in_ms: 60000` よりも十分に長い時間を設定する。
    // https://docs.datastax.com/en/developer/java-driver/3.6/manual/socket_options/#driver-read-timeout
    val ReadTimeoutForDropKeyspace = 65000.millis
    session.execute(
      new SimpleStatement(s"DROP KEYSPACE IF EXISTS ${cassandraConfig.cassandraKeyspace}")
        .setReadTimeoutMillis(ReadTimeoutForDropKeyspace.toMillis.toInt),
    )
  }
}
