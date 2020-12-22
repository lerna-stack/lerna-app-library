package lerna.log

import akka.actor.{ ActorRef, ActorSystem, DiagnosticActorLogging }
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

  protected def unwrapArg(arg: Any): AnyRef = arg match {
    case x: ScalaNumber => x.underlying
    case x: AnyRef      => x
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

  private[this] def decorate(logContext: LogContext)(logOut: => Unit): Unit = {
    try {
      import collection.JavaConverters._
      MDC.setContextMap(logContext.mdc.asJava)
      logOut
    } finally {
      MDC.clear()
    }
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
    try {
      adapter.mdc(
        logContext.mdc + ("actorPath" -> logSource.path.toString),
      )
      logOut
    } finally {
      adapter.clearMDC()
    }
  }

  /** Akka のバグ（？）で指定しないと NullPointerException が発生する
    */
  private[this] val marker = LogMarker("Application")

  private[this] def publishWarnWithCause(cause: Throwable, message: String): Unit = {
    system.eventStream.publish(Warning(cause, logSource.path.toString, logClass, message, emptyMDC, marker))
  }
}
