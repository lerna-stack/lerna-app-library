/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
// 移行作業のため一時的にコピーして使用している。
// TODO 移行作業のため一時的にコピーしているので、このクラスを使わないように書き換えていくこと

package lerna.util.sequence

import com.datastax.driver.core.exceptions.DriverException
import com.datastax.driver.core.policies.RetryPolicy
import com.datastax.driver.core.policies.RetryPolicy.RetryDecision
import com.datastax.driver.core.{ Cluster, ConsistencyLevel, Statement, WriteType }

// Copied from
// https://github.com/akka/akka-persistence-cassandra/blob/v0.98/core/src/main/scala/akka/persistence/cassandra/journal/CassandraJournal.scala#L816-L878
/** The retry policy that is used for reads, writes and deletes with
  * configured number of retries before giving up.
  * See http://docs.datastax.com/en/developer/java-driver/3.1/manual/retries/
  */
@SuppressWarnings(
  // もともとのコードを書き換えたくないので、エラーが出たlintを無効にする。
  Array(
    "org.wartremover.warts.Equals",
  ),
)
private[sequence] class FixedRetryPolicy(number: Int) extends RetryPolicy {
  override def onUnavailable(
      statement: Statement,
      cl: ConsistencyLevel,
      requiredReplica: Int,
      aliveReplica: Int,
      nbRetry: Int,
  ): RetryDecision = {
    // Same implementation as in DefaultRetryPolicy
    // If this is the first retry it triggers a retry on the next host.
    // The rationale is that the first coordinator might have been network-isolated from all other nodes (thinking
    // they're down), but still able to communicate with the client; in that case, retrying on the same host has almost
    // no chance of success, but moving to the next host might solve the issue.
    if (nbRetry == 0)
      tryNextHost(cl, nbRetry) // see DefaultRetryPolicy
    else
      retry(cl, nbRetry)
  }

  override def onWriteTimeout(
      statement: Statement,
      cl: ConsistencyLevel,
      writeType: WriteType,
      requiredAcks: Int,
      receivedAcks: Int,
      nbRetry: Int,
  ): RetryDecision = {
    retry(cl, nbRetry)
  }

  override def onReadTimeout(
      statement: Statement,
      cl: ConsistencyLevel,
      requiredResponses: Int,
      receivedResponses: Int,
      dataRetrieved: Boolean,
      nbRetry: Int,
  ): RetryDecision = {
    retry(cl, nbRetry)
  }
  override def onRequestError(
      statement: Statement,
      cl: ConsistencyLevel,
      cause: DriverException,
      nbRetry: Int,
  ): RetryDecision = {
    tryNextHost(cl, nbRetry)
  }

  private def retry(cl: ConsistencyLevel, nbRetry: Int): RetryDecision = {
    if (nbRetry < number) RetryDecision.retry(cl) else RetryDecision.rethrow()
  }

  private def tryNextHost(cl: ConsistencyLevel, nbRetry: Int): RetryDecision = {
    if (nbRetry < number) RetryDecision.tryNextHost(cl)
    else RetryDecision.rethrow()
  }

  override def init(c: Cluster): Unit = ()
  override def close(): Unit          = ()

}
