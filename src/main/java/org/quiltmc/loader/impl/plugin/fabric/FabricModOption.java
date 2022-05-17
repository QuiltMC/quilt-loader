package org.quiltmc.loader.impl.plugin.fabric;

import java.nio.file.Path;

import org.quiltmc.loader.api.plugin.QuiltPluginContext;
import org.quiltmc.loader.api.plugin.gui.PluginGuiIcon;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;
import org.quiltmc.loader.impl.metadata.FabricLoaderModMetadata;
import org.quiltmc.loader.impl.metadata.qmj.InternalModMetadata;
import org.quiltmc.loader.impl.plugin.gui.GuiManagerImpl;

public class FabricModOption extends ModLoadOption {

	final QuiltPluginContext pluginContext;
	final InternalModMetadata metadata;
	final Path from, resourceRoot;
	final boolean mandatory;

	public FabricModOption(QuiltPluginContext pluginContext, FabricLoaderModMetadata meta, Path from, Path resourceRoot,
		boolean mandatory) {

		this.pluginContext = pluginContext;
		this.metadata = meta.asQuiltModMetadata();
		this.from = from;
		this.resourceRoot = resourceRoot;
		this.mandatory = mandatory;
	}

	@Override
	public InternalModMetadata metadata() {
		return metadata;
	}

	@Override
	public Path from() {
		return from;
	}

	@Override
	public Path resourceRoot() {
		return resourceRoot;
	}

	@Override
	public boolean isMandatory() {
		return mandatory;
	}

	@Override
	public String toString() {
		return "{FabricModOption '" + metadata.id() + "' from " + pluginContext.manager().describePath(from) + "}";
	}

	@Override
	public PluginGuiIcon modTypeIcon() {
		return GuiManagerImpl.ICON_FABRIC;
	}

	@Override
	public QuiltPluginContext loader() {
		return pluginContext;
	}

	@Override
	public String shortString() {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}

	@Override
	public String getSpecificInfo() {
		// TODO Auto-generated method stub
		throw new AbstractMethodError("// TODO: Implement this!");
	}
}
