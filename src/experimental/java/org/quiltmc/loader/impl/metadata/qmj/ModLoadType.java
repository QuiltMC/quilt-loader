package org.quiltmc.loader.impl.metadata.qmj;

public enum ModLoadType {
	
	
	/*
	 * 
	 * If a mod is directly in the mods folder then it must be loaded
	 * 
	 * If a mod is nested then it is loaded according to one of the following rules:
	 * 
	 *   - dependencies and breaks
	 *   
	 * Influencing this is useful for:
	 * 	- library mods (only load modules that are needed)
	 *  - compatibility mods (only load compat modules if possible)
	 *  - sub-modules of a big mod
	 * 
	 * Choosing afterwards, while useful, is also impractical. So that's dropped.
	 * 
	 */
	
	
	ALWAYS,
	IF_POSSIBLE,
	IF_REQUIRED;
}
