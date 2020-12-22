package lerna.util.trace

/** A class that represents trace ID
  */
final case class TraceId(id: String) extends AnyVal

/** An object that provides a default trace ID
  */
object TraceId {

  /** A trace ID that represents an unknown trace context
    */
  val unknown: TraceId = TraceId("_unknown")
}
