# Lerna Log

*Lerna Log* library provides logging related features like below.

- Loggers 
    - for classic actor (`lerna.log.AppActorLogging` trait)
    - for typed actor (`lerna.log.AppTypedActorLogging` trait)
    - for non-actors (`lerna.log.AppLogging` trait)
- Custom log converters for [Logback](http://logback.qos.ch/)

## The logger for typed actor
Mixin `lerna.log.AppTypedActorLogging` to the object or class that defines Behavior, and use `withLogger` which is a logger factory.

The name of the Logger created by withLogger `% logger` uses the name of the subclass.
In the example below, the logger name would be `Echo$`.

```scala mdoc:reset
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, Behavior }
import lerna.log.AppTypedActorLogging
import lerna.util.trace.RequestContext

object Echo extends AppTypedActorLogging {
  final case class Ping(message: String, replyTo: ActorRef[Pong])(implicit val logContext: RequestContext)
  final case class Pong(message: String)

  def apply(): Behavior[Ping] = Behaviors.setup { context =>
    withLogger { logger =>
      Behaviors.receiveMessage[Ping] { ping: Ping =>
        import ping.logContext
        logger.info("msg: {}", ping)
        ping.replyTo ! Pong(ping.message)
        Behaviors.same
      }
    }
  }
}
```


## Using *Logback* custom converters

The library provides *Logback* extensions in the package `lerna.log.logback`.
If you use classes/traits in the package, you must add `logback-classic` into `libraryDependencies`.

```sbt
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3"
```

### OneLineEventConverter

`OneLineEventConverter` is an implementation of [ClassicConverter](http://logback.qos.ch/apidocs/ch/qos/logback/classic/pattern/ClassicConverter.html) that converts log message to a one-line string.
You can use this class in your Logback configuration file. You can see how to use the converter in the [Official Document](https://logback.qos.ch/manual/layouts.html#customConversionSpecifier).

Here is an example of how to use it.
```xml
<conversionRule conversionWord="msg" converterClass="lerna.log.logback.converter.OneLineEventConverter" />
```

### OneLineExtendedStackTraceConverter
`OneLineExtendedStackTraceConverter` is an implementation of [ExtendedThrowableProxyConverter](http://logback.qos.ch/apidocs/ch/qos/logback/classic/pattern/ExtendedThrowableProxyConverter.html) that converts a stack trace to a one-line string.
You can use this class in your Logback configuration file. You can see how to use the converter in the [Official Document](https://logback.qos.ch/manual/layouts.html#customConversionSpecifier).

Here is an example of how to use it.
```xml
<conversionRule conversionWord="xEx" converterClass="lerna.log.logback.converter.OneLineExtendedStackTraceConverter" />
```
