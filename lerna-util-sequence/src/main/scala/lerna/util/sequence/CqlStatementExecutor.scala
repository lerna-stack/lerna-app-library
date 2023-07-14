package lerna.util.sequence

import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.{ AsyncResultSet, PreparedStatement, SimpleStatement, Statement }

import scala.compat.java8.FutureConverters.CompletionStageOps
import scala.concurrent.Future

/** Prepares and executes CQL statements.
  *
  * It is helpful for tests, for example, mocking or stubbing a CQL statement execution.
  * It delegates actual tasks to [[com.datastax.oss.driver.api.core.CqlSession]].
  */
private[sequence] trait CqlStatementExecutor {

  /** Executes a CQL statement asynchronously */
  def executeAsync[T <: Statement[T]](
      statement: Statement[T],
  )(implicit session: CqlSession): Future[AsyncResultSet] = {
    session.executeAsync(statement).toScala
  }

  /** Prepares a CQL statement asynchronously */
  def prepareAsync(
      statement: SimpleStatement,
  )(implicit session: CqlSession): Future[PreparedStatement] = {
    session.prepareAsync(statement).toScala
  }

}

/** The default instance of [[CqlStatementExecutor]]. */
private[sequence] object CqlStatementExecutor extends CqlStatementExecutor
