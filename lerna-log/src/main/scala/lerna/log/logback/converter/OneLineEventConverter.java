package lerna.log.logback.converter;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * An implementation of {@link ch.qos.logback.classic.pattern.ClassicConverter ClassicConverter}
 * that converts log message to a one-line string
 */
public class OneLineEventConverter extends ClassicConverter {
    public String convert(ILoggingEvent event) {

        return ConverterUtil.toOneline(event.getFormattedMessage());
    }
}
