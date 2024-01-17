/*
 * Copyright 2016 FabricMC
 * Copyright 2022-2023 QuiltMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.quiltmc.loader.impl.util.log;

import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

import java.io.PrintWriter;
import java.io.StringWriter;

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public class ConsoleLogHandler implements LogHandler {
	private static final LogLevel MIN_STDERR_LEVEL = LogLevel.ERROR;
	private static final LogLevel MIN_STDOUT_LEVEL = LogLevel.getDefault();

	private boolean useFormatting = true;

	void configureFormatting(boolean useFormatting) {
		this.useFormatting = useFormatting;
	}

	@Override
	public void log(long time, LogLevel level, LogCategory category, String msg, Throwable exc, boolean fromReplay, boolean wasSuppressed) {
		String formatted = formatLog(time, level, category, msg, exc);

		if (level.isLessThan(MIN_STDERR_LEVEL)) {
			System.out.print(formatted);
		} else {
			System.err.print(formatted);
		}
	}

	protected String formatLog(long time, LogLevel level, LogCategory category, String msg, Throwable exc) {
		String ret;
		if (useFormatting) {
			ret = String.format("[%tT] [%s] [%s/%s]: %s%n", time, level.name(), category.context, category.name, msg);
		} else {
			ret = msg + "\n";
		}

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
