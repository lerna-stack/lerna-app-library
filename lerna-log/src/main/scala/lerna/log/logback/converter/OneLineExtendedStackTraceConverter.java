package lerna.log.logback.converter;

import ch.qos.logback.classic.pattern.ExtendedThrowableProxyConverter;
import ch.qos.logback.classic.spi.IThrowableProxy;

/**
 * An implementation of {@link ch.qos.logback.classic.pattern.ExtendedThrowableProxyConverter ExtendedThrowableProxyConverter}
 * that converts a stack trace to a one-line string
 */
public class OneLineExtendedStackTraceConverter extends ExtendedThrowableProxyConverter {

    protected String throwableProxyToString(IThrowableProxy tp) {

        return ConverterUtil.toOneline(super.throwableProxyToString(tp));
    }
}
