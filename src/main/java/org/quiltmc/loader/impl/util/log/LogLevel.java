package org.quiltmc.loader.impl.util.log;

import org.quiltmc.loader.impl.util.SystemProperties;

import java.util.Locale;

public enum LogLevel {
	ERROR, WARN, INFO, DEBUG, TRACE;

	public boolean isLessThan(LogLevel level) {
		return ordinal() > level.ordinal();
	}

	public static LogLevel getDefault() {
		String val = System.getProperty(SystemProperties.LOG_LEVEL);
		if (val == null) return INFO;

		LogLevel ret = LogLevel.valueOf(val.toUpperCase(Locale.ENGLISH));
		if (ret == null) throw new IllegalArgumentException("invalid log level: "+val);

		return ret;
	}
}
