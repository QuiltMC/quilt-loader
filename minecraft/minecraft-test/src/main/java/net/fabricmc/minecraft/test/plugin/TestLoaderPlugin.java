package net.fabricmc.minecraft.test.plugin;

import java.util.Map;

import org.quiltmc.loader.api.LoaderValue;
import org.quiltmc.loader.api.plugin.QuiltLoaderPlugin;
import org.quiltmc.loader.api.plugin.QuiltPluginContext;
import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.api.plugin.solver.ModLoadOption;

public class TestLoaderPlugin implements QuiltLoaderPlugin {

    private static boolean DISABLE_QFAPI = Boolean.getBoolean("quick_test_disable_qfapi");

    private QuiltPluginContext context;

    @Override
    public void load(QuiltPluginContext ctx, Map<String, LoaderValue> dataOut) {
        context = ctx;
    }

    @Override
    public void unload(Map<String, LoaderValue> dataIn) {

    }

    @Override
    public void onLoadOptionAdded(LoadOption option) {
        if (!DISABLE_QFAPI) {
            return;
        }
        if (option instanceof ModLoadOption) {
            ModLoadOption mod = (ModLoadOption) option;
            String source = context.manager().describePath(mod.from());
            if (source.contains("qfapi-")) {
                context.ruleContext().removeOption(mod);
            }
        }
    }

}
