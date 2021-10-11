package org.quiltmc.loader.impl.util.log;


import java.io.PrintWriter;
import java.io.StringWriter;

public class ConsoleLogHandler implements LogHandler {
	private static final LogLevel MIN_STDERR_LEVEL = LogLevel.ERROR;
	private static final LogLevel MIN_STDOUT_LEVEL = LogLevel.getDefault();

	@Override
	public void log(long time, LogLevel level, LogCategory category, String msg, Throwable exc, boolean isRelayedBuiltin) {
		String formatted = formatLog(time, level, category, msg, exc);

		if (level.isLessThan(MIN_STDERR_LEVEL)) {
			System.out.print(formatted);
		} else {
			System.err.print(formatted);
		}
	}

	protected static String formatLog(long time, LogLevel level, LogCategory category, String msg, Throwable exc) {
		String ret = String.format("[%tT] [%s] [%s/%s]: %s%n", time, level.name(), Log.NAME, category.name, msg);

		if (exc != null) {
			StringWriter writer = new StringWriter(ret.length() + 500);

			try (PrintWriter pw = new PrintWriter(writer, false)) {
				pw.print(ret);
				exc.printStackTrace(pw);
			}

			ret = writer.toString();
		}

		return ret;
	}

	@Override
	public boolean shouldLog(LogLevel level, LogCategory category) {
		return !level.isLessThan(MIN_STDOUT_LEVEL);
	}

	@Override
	public void close() { }
}
