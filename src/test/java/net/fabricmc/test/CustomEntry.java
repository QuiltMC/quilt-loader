package net.fabricmc.test;

@FunctionalInterface
public interface CustomEntry {
	String name();
	default String describe() {
		return "Custom entry point \"" + name() + '"';
	}
}
