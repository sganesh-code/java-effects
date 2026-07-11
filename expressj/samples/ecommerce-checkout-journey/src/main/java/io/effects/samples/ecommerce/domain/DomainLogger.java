package io.effects.samples.ecommerce.domain;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class DomainLogger {
    private DomainLogger() {
        /* This utility class should not be instantiated */
    }

    private static final Logger logger = Logger.getLogger("EcommerceSimulation");

    static {
        Logger rootLogger = Logger.getLogger("");
        for (var handler : rootLogger.getHandlers()) {
            rootLogger.removeHandler(handler);
        }

        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord record) {
                return record.getMessage() + "\n";
            }
        });
        logger.addHandler(handler);
        logger.setLevel(Level.INFO);
    }

    public static void info(String message) {
        logger.info(message);
    }

    public static void warn(String message) {
        logger.warning(message);
    }

    public static void error(String message, Throwable t) {
        logger.log(Level.SEVERE, message, t);
    }
}
