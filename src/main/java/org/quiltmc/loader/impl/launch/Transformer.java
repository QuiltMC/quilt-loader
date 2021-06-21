package org.quiltmc.loader.impl.launch;

public interface Transformer {
	byte[] transform(String name, byte[] in);
}
