package net.fabricmc.loader.util;

import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.impl.quiltmc.Quilt2FabricModContainer;

/** @deprecated Exposed since some fabric mods depend on this. Please don't use internal quilt classes, but if you need
 *             to then use quilt's {@link org.quiltmc.loader.impl.util.DefaultLanguageAdapter} instead. */
@Deprecated
public final class DefaultLanguageAdapter implements LanguageAdapter {
	public static final DefaultLanguageAdapter INSTANCE = new DefaultLanguageAdapter();

	private DefaultLanguageAdapter() {}

	@Override
	public <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException {
		org.quiltmc.loader.api.ModContainer quiltMod = ((Quilt2FabricModContainer) mod).getQuiltModContainer();
		try {
			return org.quiltmc.loader.impl.util.DefaultLanguageAdapter.INSTANCE.create(quiltMod, value, type);
		} catch (org.quiltmc.loader.api.LanguageAdapterException e) {
			throw new LanguageAdapterException(e);
		}
	}
}
