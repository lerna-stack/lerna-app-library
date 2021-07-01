package lerna.log

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.{ typed, ActorRef, ActorSystem, DiagnosticActorLogging }
import akka.event.{ DiagnosticLoggingAdapter, LogMarker }
import org.slf4j.helpers.MessageFormatter
import org.slf4j.{ Logger, LoggerFactory, MDC }

import scala.math.ScalaNumber

/** A trait that provides a common logger
  */
trait AppLogging {
  lazy val logger: AppLogger = new CommonLogger(LoggerFactory.getLogger(this.getClass))
}

/** A trait that provides a common logger especially for Akka Actor
  */
trait AppActorLogging extends DiagnosticActorLogging {
  lazy val logger: AppLogger = new CommonActorLogger(log, context.system, getClass, self)
}

trait AppTypedActorLogging { // Made a trait so that the actor's class can be referenced in this
  def withLogger[T](factory: AppLogger => Behavior[T]): Behavior[T] = Behaviors.setup[T](context => {
    val logger    = LoggerFactory.getLogger(this.getClass)
    val appLogger = new TypedActorLogger(logger, context.self)
    factory(appLogger)
  })
}

/** A trait that defines logging APIs
  */
trait AppLogger {

  def debug(msg: String)(implicit logContext: LogContext): Unit
  def debug(format: String, arguments: Any*)(implicit logContext: LogContext): Unit

  def info(msg: String)(implicit logContext: LogContext): Unit
  def info(format: String, arguments: Any*)(implicit logContext: LogContext): Unit

  def warn(msg: String)(implicit logContext: LogContext): Unit
  def warn(format: String, arguments: Any*)(implicit logContext: LogContext): Unit
  def warn(cause: Throwable, msg: String)(implicit logContext: LogContext): Unit
  def warn(cause: Throwable, format: String, arguments: Any*)(implicit logContext: LogContext): Unit

  def error(msg: String)(implicit logContext: LogContext): Unit
  def error(format: String, arguments: Any*)(implicit logContext: LogContext): Unit
  def error(cause: Throwable, msg: String)(implicit logContext: LogContext): Unit
  def error(cause: Throwable, format: String, arguments: Any*)(implicit logContext: LogContext): Unit

  @SuppressWarnings(Array("org.wartremover.warts.AsInstanceOf"))
  protected def unwrapArg(arg: Any): AnyRef = arg match {
    case x: ScalaNumber => x.underlying
    case x              => x.asInstanceOf[AnyRef]
  }

  protected def messageFormat(format: String, arguments: Any*): String = {
    MessageFormatter.arrayFormat(format, arguments.toArray map unwrapArg).getMessage
  }

}

/** An implementation of the [[AppLogger]]
  */
class CommonLogger(log: Logger) extends AppLogger {

  override def debug(msg: String)(implicit logContext: LogContext): Unit = {
    if (log.isDebugEnabled()) {
      decorate(logContext) {
        log.debug(msg)
      }
    }
  }

  override def debug(format: String, arguments: Any*)(implicit logContext: LogContext): Unit = {
    if (log.isDebugEnabled()) {
      decorate(logContext) {
        log.debug(messageFormat(format, arguments: _*))
      }
    }
  }

  override def info(msg: String)(implicit logContext: LogContext): Unit = {
    if (log.isInfoEnabled()) {
      decorate(logContext) {
        log.info(msg)
      }
    }
  }

  override def info(format: String, arguments: Any*)(implicit logContext: LogContext): Unit = {
    if (log.isInfoEnabled()) {
      decorate(logContext) {
        log.info(messageFormat(format, arguments: _*))
      }
    }
  }

  override def warn(msg: String)(implicit logContext: LogContext): Unit = {
    if (log.isWarnEnabled()) {
      decorate(logContext) {
        log.warn(msg)
      }
    }
  }

  override def warn(format: String, arguments: Any*)(implicit logContext: LogContext): Unit = {
    if (log.isWarnEnabled()) {
      decorate(logContext) {
        log.warn(messageFormat(format, arguments: _*))
      }
    }
  }

  override def warn(cause: Throwable, msg: String)(implicit logContext: LogContext): Unit = {
    if (log.isWarnEnabled()) {
      decorate(logContext) {
        log.warn(msg, cause)
      }
    }
  }

  override def warn(cause: Throwable, format: String, arguments: Any*)(implicit logContext: LogContext): Unit = {
    if (log.isWarnEnabled()) {
      decorate(logContext) {
        log.warn(messageFormat(format, arguments: _*), cause)
      }
    }
  }

  override def error(msg: String)(implicit logContext: LogContext): Unit = {
    if (log.isErrorEnabled()) {
      decorate(logContext) {
        log.error(msg)
      }
    }
  }

  override def error(format: String, arguments: Any*)(implicit logContext: LogContext): Unit = {
    if (log.isErrorEnabled()) {
      decorate(logContext) {
        log.error(messageFormat(format, arguments: _*))
      }
    }
  }

  override def error(cause: Throwable, msg: String)(implicit logContext: LogContext): Unit = {
    if (log.isErrorEnabled()) {
      decorate(logContext) {
        log.error(msg, cause)
      }
    }
  }

  override def error(cause: Throwable, format: String, arguments: Any*)(implicit logContext: LogContext): Unit = {
    if (log.isErrorEnabled()) {
      decorate(logContext) {
        log.error(messageFormat(format, arguments: _*), cause)
      }
    }
  }

  private[log] def decorate(logContext: LogContext)(logOut: => Unit): Unit = {
    decorate(logContext.mdc)(logOut)
  }

  private[log] def decorate(mdc: Map[String, String])(logOut: => Unit): Unit = {
    val maybeCurrentContextMap = Option(MDC.getCopyOfContextMap)
    try {
      import scala.jdk.CollectionConverters._
      maybeCurrentContextMap match {
        case Some(map) => MDC.setContextMap((map.asScala.toMap ++ mdc).asJava)
        case None      => MDC.setContextMap(mdc.asJava)
      }
      logOut
    } finally {
      maybeCurrentContextMap match {
        case Some(map) => MDC.setContextMap(map)
        case None      => MDC.clear()
      }
    }
  }
}

/** An implementation of [[AppLogger]] that is especially for Akka Typed Actor
  */
class TypedActorLogger(log: Logger, logSource: typed.ActorRef[Nothing]) extends CommonLogger(log) with AppLogger {
  override def decorate(logContext: LogContext)(logOut: => Unit): Unit = {
    decorate(logContext.mdc + ("actorPath" -> logSource.path.toString))(logOut)
  }
}

/** An implementation of [[AppLogger]] that is especially for Akka Actor
  */
class CommonActorLogger(adapter: DiagnosticLoggingAdapter, system: ActorSystem, logClass: Class[_], logSource: ActorRef)
    extends AppLogger {

  import akka.event.Logging._

  override def debug(msg: String)(implicit logContext: LogContext): Unit = {
    if (adapter.isDebugEnabled) {
      decorate(logContext) {
        adapter.debug(msg)
      }
    }
  }

  override def debug(format: String, arguments: Any*)(implicit logContext: LogContext): Unit = {
    if (adapter.isDebugEnabled) {
      decorate(logContext) {
        adapter.debug(adapter.format(format, arguments: _*))
      }
    }
  }

  override def info(msg: String)(implicit logContext: LogContext): Unit = {
    if (adapter.isInfoEnabled) {
      decorate(logContext) {
        adapter.info(msg)
      }
    }
  }

  override def info(format: String, arguments: Any*)(implicit logContext: LogContext): Unit = {
    if (adapter.isInfoEnabled) {
      decorate(logContext) {
        adapter.info(adapter.format(format, arguments: _*))
      }
    }
  }

  override def warn(msg: String)(implicit logContext: LogContext): Unit = {
    if (adapter.isWarningEnabled) {
      decorate(logContext) {
        adapter.warning(msg)
      }
    }
  }

  override def warn(format: String, arguments: Any*)(implicit logContext: LogContext): Unit = {
    if (adapter.isWarningEnabled) {
      decorate(logContext) {
        adapter.warning(adapter.format(format, arguments: _*))
      }
    }
  }

  override def warn(cause: Throwable, msg: String)(implicit logContext: LogContext): Unit = {
    if (adapter.isWarningEnabled) {
      decorate(logContext) {
        publishWarnWithCause(cause, msg)
      }
    }
  }

  override def warn(cause: Throwable, format: String, arguments: Any*)(implicit logContext: LogContext): Unit = {
    if (adapter.isWarningEnabled) {
      decorate(logContext) {
        publishWarnWithCause(cause, adapter.format(format, arguments: _*))
      }
    }
  }

  override def error(msg: String)(implicit logContext: LogContext): Unit = {
    if (adapter.isErrorEnabled) {
      decorate(logContext) {
        adapter.error(msg)
      }
    }
  }

  override def error(format: String, arguments: Any*)(implicit logContext: LogContext): Unit = {
    if (adapter.isErrorEnabled) {
      decorate(logContext) {
        adapter.error(adapter.format(format, arguments: _*))
      }
    }
  }

  override def error(cause: Throwable, msg: String)(implicit logContext: LogContext): Unit = {
    if (adapter.isErrorEnabled) {
      decorate(logContext) {
        adapter.error(cause, msg)
      }
    }
  }

  override def error(cause: Throwable, format: String, arguments: Any*)(implicit logContext: LogContext): Unit = {
    if (adapter.isErrorEnabled) {
      decorate(logContext) {
        adapter.error(cause, adapter.format(format, arguments: _*))
      }
    }
  }

  private[this] def decorate(logContext: LogContext)(logOut: => Unit): Unit = {
    val currentMdc = adapter.mdc
    try {
      adapter.mdc(
        currentMdc ++ logContext.mdc + ("actorPath" -> logSource.path.toString),
      )
      logOut
    } finally {
      adapter.mdc(currentMdc)
    }
  }

  /** Akka のバグ（？）で指定しないと NullPointerException が発生する
    */
  private[this] val marker = LogMarker("Application")

  private[this] def publishWarnWithCause(cause: Throwable, message: String): Unit = {
    system.eventStream.publish(Warning(cause, logSource.path.toString, logClass, message, emptyMDC, marker))
  }
}
