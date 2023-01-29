package org.quiltmc.loader.impl.transformer;

import org.quiltmc.loader.impl.launch.knot.IllegalQuiltInternalAccessError;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class QuiltInternalExceptionUtil {

	public static void throwInternalFieldAccess(String msg) {
		throw new IllegalQuiltInternalAccessError(msg);
	}

	public static void throwInternalMethodAccess(String msg) {
		throw new IllegalQuiltInternalAccessError(msg);
	}

	public static void throwInternalClassAccess(String msg) {
		throw new IllegalQuiltInternalAccessError(msg);
	}
}
