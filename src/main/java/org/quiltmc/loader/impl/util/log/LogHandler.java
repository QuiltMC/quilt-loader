package org.quiltmc.loader.impl.util.log;

public interface LogHandler {
	void log(long time, LogLevel level, LogCategory category, String msg, Throwable exc, boolean isReplayedBuiltin);
	boolean shouldLog(LogLevel level, LogCategory category);
	void close();
}
