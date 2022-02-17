package net.fabricmc.loader.launch.knot;

import net.fabricmc.api.EnvType;

import org.quiltmc.loader.impl.launch.knot.Knot;

public class KnotServer {
	public static void main(String[] args) {
		Knot.launch(args, EnvType.SERVER);
	}
}
