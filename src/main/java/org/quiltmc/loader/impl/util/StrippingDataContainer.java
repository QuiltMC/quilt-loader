package org.quiltmc.loader.impl.util;

import java.util.Collection;
import java.util.HashSet;

public class StrippingDataContainer {

	private boolean stripEntireClass = false;
	private final Collection<String> stripInterfaces = new HashSet<>();
	private final Collection<String> stripFields = new HashSet<>();
	private final Collection<String> stripMethods = new HashSet<>();

	/** Every method contained in this will also be contained in {@link #stripMethods}. */
	final Collection<String> stripMethodLambdas = new HashSet<>();

	public void enableStripEntireClass() {
		this.stripEntireClass = true;
	}

	public boolean stripEntireClass() {
		return this.stripEntireClass;
	}

	public Collection<String> getStripInterfaces() {
		return this.stripInterfaces;
	}

	public Collection<String> getStripFields() {
		return this.stripFields;
	}

	public Collection<String> getStripMethods() {
		return this.stripMethods;
	}

	public Collection<String> getStripMethodLambdas() {
		return this.stripMethodLambdas;
	}

	public boolean isEmpty() {
		return this.stripInterfaces.isEmpty() && this.stripFields.isEmpty() && this.stripMethods.isEmpty();
	}
}
