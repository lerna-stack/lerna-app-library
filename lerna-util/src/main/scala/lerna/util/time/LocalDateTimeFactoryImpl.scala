package lerna.util.time

import java.time.Clock

private[time] final class LocalDateTimeFactoryImpl extends LocalDateTimeFactory {
  override val clock: Clock = Clock.systemDefaultZone
}
