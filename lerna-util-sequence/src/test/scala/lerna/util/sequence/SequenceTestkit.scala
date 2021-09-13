package lerna.util.sequence

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.SimpleStatement

import scala.compat.java8.DurationConverters._
import scala.concurrent.duration.DurationInt

object SequenceTestkit {
  def dropKeyspaceIfExists(session: CqlSession, cassandraConfig: SequenceFactoryCassandraConfig): Unit = {
    // `cassandra.yaml`にあるCassandraサーバ側のタイムアウト
    // `truncate_request_timeout_in_ms: 60000` よりも十分に長い時間を設定する。
    // https://docs.datastax.com/en/developer/java-driver/3.6/manual/socket_options/#driver-read-timeout
    val ReadTimeoutForDropKeyspace = 65000.millis
    session.execute(
      SimpleStatement
        .newInstance(s"DROP KEYSPACE IF EXISTS ${cassandraConfig.cassandraKeyspace}")
        .setTimeout(ReadTimeoutForDropKeyspace.toJava),
    )
  }
}
