package org.quiltmc.loader.impl.util.log;


public final class LogCategory {
	public static final LogCategory DISCOVERY = new LogCategory("Discovery");
	public static final LogCategory ENTRYPOINT = new LogCategory("Entrypoint");
	public static final LogCategory GAME_PATCH = new LogCategory("GamePatch");
	public static final LogCategory GAME_PROVIDER = new LogCategory("GameProvider");
	public static final LogCategory GAME_REMAP = new LogCategory("GameRemap");
	public static final LogCategory GENERAL = new LogCategory();
	public static final LogCategory KNOT = new LogCategory("Knot");
	public static final LogCategory LOG = new LogCategory("Log");
	public static final LogCategory MAPPINGS = new LogCategory("Mappings");
	public static final LogCategory METADATA = new LogCategory("Metadata");
	public static final LogCategory MOD_REMAP = new LogCategory("ModRemap");
	public static final LogCategory MIXIN = new LogCategory("Mixin");
	public static final LogCategory SOLVING = new LogCategory("Solving");
	public static final LogCategory TEST = new LogCategory("Test");

	public final String name;
	public Object data;

	public LogCategory(String... names) {
		this.name = String.join("/", names);
	}
	public LogCategory(LogCategory parent, String... names) {
		this.name = parent + String.join("/", names);
	}
}
