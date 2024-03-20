package org.quiltmc.loader.impl.util;

public class PackageStrippingDataContainer {

	private boolean stripEntirePackage = false;

	public void enableStripEntirePackage() {
		this.stripEntirePackage = true;
	}

	public boolean stripEntirePackage() {
		return this.stripEntirePackage;
	}
}
