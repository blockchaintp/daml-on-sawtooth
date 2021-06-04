package com.blockchaintp.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

/**
 * Utility functions that provide support for log4j functionality that is unavailable when using a facade
 * like SLF4J or Apache Commons Logging.
 */
public final class LogUtils {

  private static final Logger LOG = LogManager.getLogger();

  private static final int WARN_LOG = 0;
  private static final int INFO_LOG = 1;
  private static final int DEBUG_LOG = 2;
  private static final int TRACE_LOG = 3;

  /**
   * Sets the log4j log {@link Level level} for all loggers given a log level name. The log level name
   * is not case sensitive, so, for example, "warn", "WARN" and "Warn" are all valid names for the log4j
   * <code>WARN</code> log level.
   *
   * @param levelName the name of the new log4j log level for all loggers
   */
  public static void setRootLogLevel(final String levelName) {
    final Level level = Level.valueOf(levelName.toUpperCase());
    LOG.always().log("Requested log level {}", levelName);
    if (level == null) {
      LOG.always().log("Invalid log level {}, root log level still set to the default value {}", levelName,
          LogManager.getRootLogger().getLevel().name());
      return;
    }
    setRootLogLevel(level);
  }

  /**
   * Sets the log4j log {@link Level level} for all loggers given a log level
   * name. The log level name is not case sensitive, so, for example, "warn",
   * "WARN" and "Warn" are all valid names for the log4j <code>WARN</code> log
   * level.
   *
   * @param levelCount the count level of new log4j log level for all loggers
   */
  public static void setRootLogLevel(final int levelCount) {
    int vCount;
    if (levelCount > TRACE_LOG) {
      vCount = TRACE_LOG;
    } else {
      vCount = levelCount;
    }
    switch (vCount) {
      case INFO_LOG:
        LogUtils.setRootLogLevel(Level.INFO);
        break;
      case DEBUG_LOG:
        LogUtils.setRootLogLevel(Level.DEBUG);
        break;
      case TRACE_LOG:
        LogUtils.setRootLogLevel(Level.TRACE);
        break;
      case WARN_LOG:
      default:
        LogUtils.setRootLogLevel(Level.WARN);
        break;
    }
  }

  /**
   * Sets the log4j log {@link Level level} for all loggers.
   *
   * @param level the new log4j log level for all loggers
   */
  public static void setRootLogLevel(final Level level) {
    Configurator.setRootLevel(level);
    LOG.always().log("Root log level set to {}", level);
  }

  /**
   * Sets the log4j log {@link Level level} for a class.
   *
   * @param clazz     the class name
   * @param levelName the name of the new log4j log level for the named logger
   */
  public static void setLogLevel(final Class<?> clazz, final String levelName) {
    setLogLevel(clazz.getName(), levelName);
  }

  /**
   * Sets the log4j log {@link Level level} for a named logger given a log level
   * name. The level name is not case sensitive, so, for example, "warn", "WARN"
   * and "Warn" are all valid names for the log4j <code>WARN</code> log level.
   *
   * @param loggerName the logger name
   * @param levelName  the name of the new log4j log level for the named logger
   */
  public static void setLogLevel(final String loggerName, final String levelName) {
    LogManager.getLogger(LogUtils.class);
    final Level level = Level.getLevel(levelName.toUpperCase());
    if (level == null) {
      LOG.always().log("Invalid log level {}, logger {} log level still set to the default value {}",
          levelName, LogManager.getLogger(loggerName).getLevel().name());
      return;
    }
    setLogLevel(loggerName, level);
  }

  /**
   * Sets the log4j log {@link Level level} for a named logger.
   *
   * @param loggerName the logger name
   * @param level the new log4j log level for the named logger
   */
  public static void setLogLevel(final String loggerName, final Level level) {
    Configurator.setLevel(loggerName, level);
    LOG.always().log("Logger {} log level set to {}", loggerName, level);
  }

  private LogUtils() {
  }
}
