/*
 * Copyright 2016 FabricMC
 * Copyright 2022-2023 QuiltMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.quiltmc.loader.impl.launch.knot;

import org.quiltmc.loader.api.FasterFiles;
import org.quiltmc.loader.api.ModContainer;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.impl.QuiltLoaderImpl;
import org.quiltmc.loader.impl.launch.common.QuiltLauncherBase;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.impl.util.log.LogCategory;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.platform.container.ContainerHandleURI;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory;
import org.spongepowered.asm.service.IClassBytecodeProvider;
import org.spongepowered.asm.service.IClassProvider;
import org.spongepowered.asm.service.IClassTracker;
import org.spongepowered.asm.service.IMixinAuditTrail;
import org.spongepowered.asm.service.IMixinInternal;
import org.spongepowered.asm.service.IMixinService;
import org.spongepowered.asm.service.ITransformer;
import org.spongepowered.asm.service.ITransformerProvider;
import org.spongepowered.asm.util.ReEntranceLock;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_EXPOSED)
public class MixinServiceKnot implements IMixinService, IClassProvider, IClassBytecodeProvider, ITransformerProvider, IClassTracker {
	static IMixinTransformer transformer;

	private final ReEntranceLock lock;

	public MixinServiceKnot() {
		lock = new ReEntranceLock(1);
	}

	public byte[] getClassBytes(String name, String transformedName) throws IOException {
		return QuiltLauncherBase.getLauncher().getClassByteArray(name, true);
	}

	public byte[] getClassBytes(String name, boolean runTransformers) throws ClassNotFoundException, IOException {
		byte[] classBytes = QuiltLauncherBase.getLauncher().getClassByteArray(name, runTransformers);

		if (classBytes != null) {
			return classBytes;
		} else {
			throw new ClassNotFoundException(name);
		}
	}

	@Override
	public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException {
		return getClassNode(name, true);
	}

	@Override
	public ClassNode getClassNode(String name, boolean runTransformers) throws ClassNotFoundException, IOException {
		ClassReader reader = new ClassReader(getClassBytes(name, runTransformers));
		ClassNode node = new ClassNode();
		reader.accept(node, 0);
		return node;
	}

	@Override
	public URL[] getClassPath() {
		// Mixin 0.7.x only uses getClassPath() to find itself; we implement CodeSource correctly,
		// so this is unnecessary.
		return new URL[0];
	}

	@Override
	public Class<?> findClass(String name) throws ClassNotFoundException {
		return QuiltLauncherBase.getLauncher().getTargetClassLoader().loadClass(name);
	}

	@Override
	public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
		return Class.forName(name, initialize, QuiltLauncherBase.getLauncher().getTargetClassLoader());
	}

	@Override
	public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException {
		return Class.forName(name, initialize, Knot.class.getClassLoader());
	}

	@Override
	public String getName() {
		return QuiltLauncherBase.getLauncher() instanceof Knot ? "Knot/Quilt" : "Launchwrapper/Quilt";
	}

	@Override
	public boolean isValid() {
		return true;
	}

	@Override
	public void prepare() { }

	@Override
	public MixinEnvironment.Phase getInitialPhase() {
		return MixinEnvironment.Phase.PREINIT;
	}

	@Override
	public void offer(IMixinInternal internal) {
		if (internal instanceof IMixinTransformerFactory) {
			transformer = ((IMixinTransformerFactory) internal).createTransformer();
		}
	}

	@Override
	public void init() {
	}

	@Override
	public void beginPhase() { }

	@Override
	public void checkEnv(Object bootSource) { }

	@Override
	public ReEntranceLock getReEntranceLock() {
		return lock;
	}

	@Override
	public IClassProvider getClassProvider() {
		return this;
	}

	@Override
	public IClassBytecodeProvider getBytecodeProvider() {
		return this;
	}

	@Override
	public ITransformerProvider getTransformerProvider() {
		return this;
	}

	@Override
	public IClassTracker getClassTracker() {
		return this;
	}

	@Override
	public IMixinAuditTrail getAuditTrail() {
		return null;
	}

	@Override
	public Collection<String> getPlatformAgents() {
		return Collections.singletonList("org.spongepowered.asm.launch.platform.MixinPlatformAgentDefault");
	}

	@Override
	public IContainerHandle getPrimaryContainer() {
		try {
			return new ContainerHandleURI(this.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Collection<IContainerHandle> getMixinContainers() {
		return Collections.emptyList();
	}

	@Override
	public InputStream getResourceAsStream(String name) {
		if (name.startsWith("#")) {
			// Probably a mod specific resource
			int colon = name.indexOf(':');
			if (colon > 0) {
				String mod = name.substring(1, colon);
				String resource = name.substring(colon + 1);
				Optional<ModContainer> modContainer = QuiltLoader.getModContainer(mod);
				if (modContainer.isPresent()) {
					Path modResource = modContainer.get().rootPath().resolve(resource);
					try {
						if (!FasterFiles.exists(modResource)) {
							URL url = QuiltLauncherBase.getLauncher().getResourceURL(name);
							if (url != null) {
								Log.warn(LogCategory.GENERAL, "Failed to find the resource '" + resource + "' in mod '" + mod + "', but did find it in a different place: " + url);
								return url.openStream();
							}
							return null;
						}
						return Files.newInputStream(modResource);
					} catch (IOException e) {
						throw new RuntimeException("Failed to read file '" + resource + "' from mod '" + mod + "'!", e);
					}
				}
			}
		}
		return QuiltLauncherBase.getLauncher().getResourceAsStream(name);
	}

	@Override
	public void registerInvalidClass(String className) { }

	@Override
	public boolean isClassLoaded(String className) {
		return QuiltLauncherBase.getLauncher().isClassLoaded(className);
	}

	@Override
	public String getClassRestrictions(String className) {
		return "";
	}

	@Override
	public Collection<ITransformer> getTransformers() {
		return Collections.emptyList();
	}

	@Override
	public Collection<ITransformer> getDelegatedTransformers() {
		return Collections.emptyList();
	}

	@Override
	public void addTransformerExclusion(String name) { }

	@Override
	public String getSideName() {
		return QuiltLauncherBase.getLauncher().getEnvironmentType().name();
	}

	@Override
	public MixinEnvironment.CompatibilityLevel getMinCompatibilityLevel() {
		return MixinEnvironment.CompatibilityLevel.JAVA_8;
	}

	@Override
	public MixinEnvironment.CompatibilityLevel getMaxCompatibilityLevel() {
		return MixinEnvironment.CompatibilityLevel.JAVA_17;
	}

	@Override
	public ILogger getLogger(String name) {
		return MixinLogger.get(name);
	}

	static IMixinTransformer getTransformer() {
		return transformer;
	}
}
