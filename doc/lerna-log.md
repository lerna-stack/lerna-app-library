# Lerna Log

*Lerna Log* library provides logging related features like below.

- Custom log converters for [Logback](http://logback.qos.ch/)

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
