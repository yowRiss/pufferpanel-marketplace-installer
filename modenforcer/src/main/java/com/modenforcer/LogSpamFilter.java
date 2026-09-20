package com.modenforcer;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.apache.logging.log4j.core.filter.Filterable;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class LogSpamFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModEnforcerMod.MOD_ID);
    private static volatile Filter activeFilter;

    public static void install() {
        try {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            Configuration config = ctx.getConfiguration();

            activeFilter = new AbstractFilter() {
                private boolean matches(String text) {
                    if (text == null) return false;
                    List<String> patterns = ModEnforcerConfig.get().suppressedLogPatterns;
                    if (patterns == null) return false;
                    for (String pattern : patterns) {
                        if (pattern != null && !pattern.isBlank() && text.contains(pattern)) {
                            return true;
                        }
                    }
                    return false;
                }

                @Override
                public Result filter(LogEvent event) {
                    if (event != null && event.getMessage() != null) {
                        if (matches(event.getMessage().getFormattedMessage())) {
                            return Result.DENY;
                        }
                    }
                    return Result.NEUTRAL;
                }

                @Override
                public Result filter(org.apache.logging.log4j.core.Logger logger, Level level, Marker marker, String msg, Object... params) {
                    if (matches(msg)) return Result.DENY;
                    if (params != null && params.length > 0) {
                        try {
                            String formatted = ParameterizedMessage.format(msg, params);
                            if (matches(formatted)) return Result.DENY;
                        } catch (Exception ignored) {}
                    }
                    return Result.NEUTRAL;
                }

                @Override
                public Result filter(org.apache.logging.log4j.core.Logger logger, Level level, Marker marker, Object msg, Throwable t) {
                    if (msg != null && matches(msg.toString())) {
                        return Result.DENY;
                    }
                    return Result.NEUTRAL;
                }

                @Override
                public Result filter(org.apache.logging.log4j.core.Logger logger, Level level, Marker marker, Message msg, Throwable t) {
                    if (msg != null && matches(msg.getFormattedMessage())) {
                        return Result.DENY;
                    }
                    return Result.NEUTRAL;
                }
            };

            config.getRootLogger().addFilter(activeFilter);
            for (LoggerConfig lc : config.getLoggers().values()) {
                lc.addFilter(activeFilter);
            }
            for (Appender appender : config.getAppenders().values()) {
                if (appender instanceof Filterable filterable) {
                    filterable.addFilter(activeFilter);
                }
            }

            ctx.updateLoggers();
            LOGGER.info("[ModEnforcer] Log spam filter installed successfully. Suppressing {} pattern(s).",
                    ModEnforcerConfig.get().suppressedLogPatterns.size());
        } catch (Throwable t) {
            LOGGER.warn("[ModEnforcer] Could not install log filter: {}", t.getMessage());
        }
    }
}
