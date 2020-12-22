package lerna.log

/** A trait that represents a logging context
  */
trait LogContext {
  protected[log] def mdc: Map[String, String]
}

/** An implementation of [[LogContext]] that is for system-wide events
  */
object SystemComponentLogContext extends LogContext {
  implicit def logContext: LogContext = this

  override protected[log] def mdc: Map[String, String] = Map.empty
}
