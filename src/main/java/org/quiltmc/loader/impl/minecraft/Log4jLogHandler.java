package org.quiltmc.loader.impl.minecraft;


import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;
import org.quiltmc.loader.impl.util.log.LogHandler;
import org.quiltmc.loader.impl.util.log.LogLevel;

public final class Log4jLogHandler implements LogHandler {
	@Override
	public boolean shouldLog(LogLevel level, LogCategory category) {
		return getLogger(category).isEnabled(translateLogLevel(level));
	}

	@Override
	public void log(long time, LogLevel level, LogCategory category, String msg, Throwable exc, boolean isReplayedBuiltin) {
		// TODO: suppress console log output if isReplayedBuiltin is true to avoid duplicate output
		getLogger(category).log(translateLogLevel(level), msg, exc);
	}

	private static Logger getLogger(LogCategory category) {
		Logger ret = (Logger) category.data;

		if (ret == null) {
			String name = category.name.isEmpty() ? Log.NAME : String.format("%s/%s", Log.NAME, category.name);
			category.data = ret = LogManager.getLogger(name);
		}

		return ret;
	}

	private static Level translateLogLevel(LogLevel level) {
		switch (level) {
			case ERROR: return Level.ERROR;
			case WARN: return Level.WARN;
			case INFO: return Level.INFO;
			case DEBUG: return Level.DEBUG;
			case TRACE: return Level.TRACE;
		}

		throw new IllegalArgumentException("unknown log level: "+level);
	}

	@Override
	public void close() { }
}
