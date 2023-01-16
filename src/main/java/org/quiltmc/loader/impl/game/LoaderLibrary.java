package org.quiltmc.loader.impl.game;

import java.net.URL;
import java.nio.file.Path;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.util.CheckClassAdapter;
import org.quiltmc.config.api.Config;
import org.quiltmc.json5.JsonReader;
import org.quiltmc.loader.impl.util.UrlConversionException;
import org.quiltmc.loader.impl.util.UrlUtil;
import org.quiltmc.loader.util.sat4j.minisat.SolverFactory;
import org.quiltmc.loader.util.sat4j.specs.ContradictionException;
import org.spongepowered.asm.launch.MixinBootstrap;

import net.fabricmc.accesswidener.AccessWidener;
import net.fabricmc.api.EnvType;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.tinyremapper.TinyRemapper;

enum LoaderLibrary {
	QUILT_LOADER(UrlUtil.LOADER_CODE_SOURCE),
	ASM(ClassReader.class),
	ASM_ANALYSIS(Analyzer.class),
	ASM_COMMONS(Remapper.class),
	ASM_TREE(ClassNode.class),
	ASM_UTIL(CheckClassAdapter.class),
	SPONGE_MIXIN(MixinBootstrap.class),
	TINY_MAPPINGS_PARSER(TinyMappingFactory.class),
	TINY_REMAPPER(TinyRemapper.class),
	ACCESS_WIDENER(AccessWidener.class),
	QUILT_JSON5(JsonReader.class),
	QUILT_CONFIG(Config.class),


//
//	SAT4J_CORE(ContradictionException.class),
//	SAT4J_PB(SolverFactory.class),
	SERVER_LAUNCH("quilt-server-launch.properties", EnvType.SERVER); // installer generated jar to run setup loader's class path
//	SERVER_LAUNCHER("net/fabricmc/installer/ServerLauncher.class", EnvType.SERVER); // installer based launch-through method

	final Path path;
	final EnvType env;

	LoaderLibrary(Class<?> cls) {
		this(UrlUtil.getCodeSource(cls));
	}

	LoaderLibrary(Path path) {
		if (path == null) throw new RuntimeException("missing loader library "+name());

		this.path = path;
		this.env = null;
	}

	LoaderLibrary(String file, EnvType env) {
		URL url = LoaderLibrary.class.getClassLoader().getResource(file);

		try {
			this.path = url != null ? UrlUtil.getCodeSource(url, file) : null;
			this.env = env;
		} catch (UrlConversionException e) {
			throw new RuntimeException(e);
		}
	}

	boolean isApplicable(EnvType env) {
		return this.env == null || this.env == env;
	}
}
