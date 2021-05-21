package org.quiltmc.loader.impl.metadata.qmj;

/**
 * Represents a class entry inside of that specifies a language adapter to use to load the class.
 */
public final class AdapterLoadableClassEntry {
	private final String adapter;
	private final String value;

	public AdapterLoadableClassEntry(String adapter, String value) {
		this.adapter = adapter;
		this.value = value;
	}

	public String getAdapter() {
		return this.adapter;
	}

	public String getValue() {
		return this.value;
	}
}
