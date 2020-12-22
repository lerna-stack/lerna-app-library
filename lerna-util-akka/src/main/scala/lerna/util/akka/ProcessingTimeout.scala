package lerna.util.akka

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

import akka.actor.NoSerializationVerificationNeeded
import com.typesafe.config.Config
import lerna.util.time.JavaDurationConverters._
import lerna.util.time.LocalDateTimeFactory
import lerna.util.trace.RequestContext

import scala.concurrent.duration.FiniteDuration

/** An object that provides factory methods of [[ProcessingTimeout]]
  */
object ProcessingTimeout {

  /** Create a [[ProcessingTimeout]]
    *
    * @param acceptedDateTime The baseline DateTime
    * @param askTimeout The underlying timeout
    * @param config The configuration to be used
    * @param requestContext The context to be used for logging
    * @return The ProcessingTimeout
    */
  def apply(acceptedDateTime: LocalDateTime, askTimeout: FiniteDuration, config: Config)(implicit
      requestContext: RequestContext,
  ): ProcessingTimeout = {
    // 許容するサーバー間の時刻差
    // サーバー間の時刻に差があると、graceful shutdownによって 処理中にActorが移動されると、 timeout していないのに timeout と判定される可能性があるため deadline に余裕をもたせる
    val failSafeMargin =
      config.getDuration("lerna.util.akka.processing-timeout.fail-safe-margin").asScala
    val failSafeTimeout = askTimeout + failSafeMargin

    val deadline = acceptedDateTime.plusNanos(failSafeTimeout.toNanos)

    new ProcessingTimeout(deadline) {}
  }
}

/** A class that represents processing timeout
  *
  * The `deadline` is required to distinguish each ProcessingTimeout
  * since the timer is invoked after actor restart flow (specifically receiveRecover -> updateState)
  *
  * @param deadline The datetime of timeout
  * @param requestContext The context to be used for logging
  */
sealed abstract case class ProcessingTimeout(deadline: LocalDateTime)(implicit
    val requestContext: RequestContext,
) extends NoSerializationVerificationNeeded {
  def timeLeft(implicit dateTimeFactory: LocalDateTimeFactory): FiniteDuration = {
    val now = dateTimeFactory.now()
    FiniteDuration(ChronoUnit.MILLIS.between(now, deadline), TimeUnit.MILLISECONDS)
  }
}
