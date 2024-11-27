/*
 * Copyright 2024 QuiltMC
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

package org.quiltmc.loader.impl.transformer;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.quiltmc.loader.api.MappingResolver;
import org.quiltmc.loader.api.QuiltLoader;
import org.quiltmc.loader.impl.launch.common.QuiltLauncherBase;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;

import net.fabricmc.mappingio.tree.MappingTreeView;
import net.fabricmc.mappingio.tree.MappingTreeView.ClassMappingView;
import net.fabricmc.mappingio.tree.MappingTreeView.FieldMappingView;
import net.fabricmc.mappingio.tree.MappingTreeView.MethodMappingView;

/** Internal class. For technical reasons this class cannot be fully private, but it's usage is not recommended.
 * <p>
 * Specifically the methods in this class are added before calls to java reflection methods to fix issues with
 * mappings. */
@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_NO_WARN)
// The ReflectiveFixer is applied before the InternalsHider
// which means we don't know if a mod uses this directly, or if it's been added by ReflectiveFixer
// So we must make this LEGACY_NO_WARN to prevent unavoidable warnings or errors.
public class QuiltReflectiveFixUtil {

	private static Map<String, Set<String>> namedOuterToInnerClassCache;

	/** Fixes a class name that is about to be passed to {@link Class#forName(String)} to ensure it's correctly
	 * mapped. */
	public static String fixClassName(String name) {

		// Handle improperly named inner classes
		// This mostly occurs when the class is anonymous (and so has a "$1" name in intermediary)
		// but has a full name in hashed "$C_abcdef"

		// Then if a mod does something like MinecraftType.class.getName() + "$1"
		// and we're currently running in hashed (or other mapped that's based on hashed)
		// then it won't find the real class name (since it will be "MinecraftType$C_abcdef")

		// The way we deal with this is by finding all of the inner-classes of the outermost type,
		// then mapping every inner-class step with different mappings until we unmap to the current namespace.

		int dollarIndex = name.indexOf('$');

		if (dollarIndex < 0) {
			// Not an inner class, not something we deal with here
			return name;
		}

		if (namedOuterToInnerClassCache == null) {
			Map<String, Set<String>> map = new HashMap<>();

			MappingResolver mappings = QuiltLoader.getMappingResolver();
			MappingTreeView tree = QuiltLauncherBase.getLauncher().getMappingConfiguration().getMappings();
			int namespace = tree.getNamespaceId(mappings.getCurrentRuntimeNamespace());

			for (ClassMappingView cls : tree.getClasses()) {
				String className = cls.getName(namespace);
				if (className == null) {
					continue;
				}

				boolean replaced = false;
				while (true) {
					int idx = className.lastIndexOf('$');
					if (idx < 0) {
						break;
					}

					if (!replaced) {
						// Slight optimisation: only replace class names
						// if we actually need to process them
						className = className.replace('/', '.');
						replaced = true;
					}

					String outer = className.substring(0, idx);
					map.computeIfAbsent(outer, o -> new HashSet<>()).add(className);
					className = outer;
				}
			}

			namedOuterToInnerClassCache = map;
		}

		// Now we have:
		// name = "net.OuterClass$InnerClass"
		// but "net.OuterClass" and "$InnerClass" will be using different namespaces
		// (Specifically "net.OuterClass" will be in the correct namespace)

		String correctName = name.substring(0, dollarIndex);
		MappingResolver mappings = QuiltLoader.getMappingResolver();

		subNameLoop: while (dollarIndex > 0) {

			Set<String> innerClasses = namedOuterToInnerClassCache.get(correctName);

			if (innerClasses == null) {
				// Not a mapped class
				return name;
			}

			int nextDollarIndex = name.indexOf("$", dollarIndex + 1);

			String incorrectSubName;
			if (nextDollarIndex < 0) {
				incorrectSubName = name.substring(dollarIndex + 1, name.length());
			} else {
				incorrectSubName = name.substring(dollarIndex + 1, nextDollarIndex);
			}

			for (String namespace : mappings.getNamespaces()) {
				String testNamePrefix = mappings.unmapClassName(namespace, correctName);
				String testName = testNamePrefix + "$" + incorrectSubName;
				String correctTestName = mappings.mapClassName(namespace, testName);

				if (innerClasses.contains(correctTestName)) {
					correctName = correctTestName;
					dollarIndex = nextDollarIndex;
					continue subNameLoop;
				}
			}

			// We didn't find a correct mapping
			return name;
		}

		return correctName;
	}

	public static String fixDeclaredFieldName(String fieldName, Class<?> classIn) {
		try {
			classIn.getDeclaredField(fieldName);
			// No need to fix it if it already exists
			return fieldName;
		} catch (NoSuchFieldException e) {
			// Ignored - this just means we should
		}

		MappingResolver resolver = QuiltLoader.getMappingResolver();
		String runtimeNamespace = resolver.getCurrentRuntimeNamespace();

		MappingTreeView mappings = QuiltLauncherBase.getLauncher().getMappingConfiguration().getMappings();
		String name = getMappingsName(classIn);
		ClassMappingView classMappings = mappings.getClass(name, mappings.getNamespaceId(runtimeNamespace));

		if (classMappings == null) {
			return fieldName;
		}

		Collection<String> namespaces = resolver.getNamespaces();

		for (FieldMappingView f : classMappings.getFields()) {
			for (String namespace : namespaces) {
				if (f.getName(namespace).equals(fieldName)) {
					return f.getName(runtimeNamespace);
				}
			}
		}

		return fieldName;
	}

	private static String getMappingsName(Class<?> classIn) {
		return classIn.getName().replace('.', '/');
	}

	public static String fixFieldName(String fieldName, Class<?> classIn) {
		try {
			classIn.getField(fieldName);
			// No need to fix it if it already exists
			return fieldName;
		} catch (NoSuchFieldException e) {
			// Ignored - this just means we should
		}

		MappingTreeView mappings = QuiltLauncherBase.getLauncher().getMappingConfiguration().getMappings();
		MappingResolver resolver = QuiltLoader.getMappingResolver();
		String runtimeNamespace = resolver.getCurrentRuntimeNamespace();
		int namespaceId = mappings.getNamespaceId(runtimeNamespace);

		Set<Class<?>> testedClasses = new HashSet<>();

		Map<String, List<Field>> possibleFields = new HashMap<>();
		Field[] fieldArray = classIn.getFields();
		for (Field field : fieldArray) {
			possibleFields.computeIfAbsent(field.getName(), f -> new ArrayList<>()).add(field);
		}

		for (Field field : fieldArray) {
			Class<?> owner = field.getDeclaringClass();
			if (!testedClasses.add(owner)) {
				continue;
			}
			ClassMappingView ownerMappings = mappings.getClass(getMappingsName(owner), namespaceId);
			if (ownerMappings == null) {
				continue;
			}

			for (FieldMappingView f : ownerMappings.getFields()) {
				String currentName = f.getName(namespaceId);
				for (Field possible : possibleFields.getOrDefault(currentName, Collections.emptyList())) {
					if (possible.getDeclaringClass() != owner) {
						continue;
					}

					for (String namespace : resolver.getNamespaces()) {
						if (f.getName(namespace).equals(fieldName)) {
							return currentName;
						}
					}
				}
			}
		}

		return fieldName;
	}

	public static MethodIntermediateArguments fixDeclaredMethodName(Class<?> classIn, String methodName, Class<?>[] args) {
		try {
			classIn.getDeclaredMethod(methodName, args);
			// No need to fix it if it already exists
			return new MethodIntermediateArguments(classIn, methodName, args);
		} catch (NoSuchMethodException e) {
			// Ignored - this just means we should
		}

		MappingResolver resolver = QuiltLoader.getMappingResolver();
		String runtimeNamespace = resolver.getCurrentRuntimeNamespace();

		MappingTreeView mappings = QuiltLauncherBase.getLauncher().getMappingConfiguration().getMappings();
		String name = getMappingsName(classIn);
		ClassMappingView classMappings = mappings.getClass(name, mappings.getNamespaceId(runtimeNamespace));

		if (classMappings == null) {
			return new MethodIntermediateArguments(classIn, methodName, args);
		}

		Collection<String> namespaces = resolver.getNamespaces();

		String partialArgsDescriptor = MethodType.methodType(void.class, args).toMethodDescriptorString();
		partialArgsDescriptor = partialArgsDescriptor.substring(1, partialArgsDescriptor.indexOf(')'));

		for (MethodMappingView m : classMappings.getMethods()) {
			String desc = m.getDesc(runtimeNamespace);
			if (desc == null) {
				// ?
				continue;
			}
			// check descriptor equality, ignoring return type
			desc = desc.substring(1, desc.indexOf(')'));

			if (!desc.equals(partialArgsDescriptor)) {
				continue;
			}

			for (String namespace : namespaces) {
				if (m.getName(namespace).equals(methodName)) {
					return new MethodIntermediateArguments(classIn, m.getName(runtimeNamespace), args);
				}
			}
		}

		return new MethodIntermediateArguments(classIn, methodName, args);
	}

	public static MethodIntermediateArguments fixMethodName(Class<?> classIn, String name, Class<?>[] args) {
		try {
			classIn.getMethod(name, args);
			// No need to fix it if it already exists
			return new MethodIntermediateArguments(classIn, name, args);
		} catch (NoSuchMethodException e) {
			// Ignored - this just means we should
		}

		MappingResolver resolver = QuiltLoader.getMappingResolver();

		Collection<String> possibleNamespaces = new LinkedHashSet<>(resolver.getNamespaces());
		possibleNamespaces.remove(resolver.getCurrentRuntimeNamespace());

		for (Class<?> in : getMappableClassHierarchy(classIn)) {
			for (Method m : in.getMethods()) {
				if (!Arrays.equals(args, m.getParameterTypes())) {
					// Skip expensive name checking
					continue;
				}

				MethodType type = MethodType.methodType(m.getReturnType(), m.getParameterTypes());

				for (String namespace : possibleNamespaces) {
					String className = resolver.unmapClassName(namespace, in.getName());
					String unMappedDesc = mapMethodTypeDescriptor(namespace, type);
					String mappedName = resolver.mapMethodName(namespace, className, name, unMappedDesc);
					if (m.getName().equals(mappedName)) {
						return new MethodIntermediateArguments(classIn, mappedName, args);
					}
				}
			}
		}

		return new MethodIntermediateArguments(classIn, name, args);
	}

	@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_NO_WARN)
	public static final class MethodIntermediateArguments {
		private final Class<?> owner;
		private final String methodName;
		private final Class<?>[] args;

		private MethodIntermediateArguments(Class<?> owner, String methodName, Class<?>[] args) {
			this.owner = owner;
			this.methodName = methodName;
			this.args = args;
		}

		public Class<?> getOwner() {
			return owner;
		}

		public String getMethodName() {
			return methodName;
		}

		public Class<?>[] getArgs() {
			return args;
		}
	}

	/** Used to avoid needing to deal with local variables in {@link Lookup} redirects. */
	@QuiltLoaderInternal(QuiltLoaderInternalType.LEGACY_NO_WARN)
	public static final class LookupIntermediateArguments {
		private final Lookup lookup;
		private final Class<?> owner;
		private final String name;
		private final Class<?> fieldType;
		private final MethodType methodType;
		private Class<?> specialCaller;

		private LookupIntermediateArguments(Lookup lookup, Class<?> owner, String name, Class<?> fieldType) {
			this.lookup = lookup;
			this.owner = owner;
			this.name = name;
			this.fieldType = fieldType;
			this.methodType = null;
		}

		private LookupIntermediateArguments(Lookup lookup, Class<?> owner, String name, MethodType methodType) {
			this.lookup = lookup;
			this.owner = owner;
			this.name = name;
			this.fieldType = null;
			this.methodType = methodType;
		}

		public Lookup getLookup() {
			return lookup;
		}

		public Class<?> getOwner() {
			return owner;
		}

		public String getName() {
			return name;
		}

		public Class<?> getFieldType() {
			return fieldType;
		}

		public MethodType getMethodType() {
			return methodType;
		}

		public Class<?> getSpecialCaller() {
			return specialCaller;
		}
	}

	private static Set<Class<?>> getMappableClassHierarchy(Class<?> type) {
		Set<Class<?>> set = new LinkedHashSet<>();
		getMappableClassHierarchy(type, set);
		return set;
	}

	private static void getMappableClassHierarchy(Class<?> type, Set<Class<?>> dst) {
		String pkg = type.getPackage().getName();

		if (pkg.startsWith("java.") || pkg.startsWith("org.quiltmc.loader.")) {
			return;
		}

		// FIXME: Validate that we can just iterate through all classes!
		// Like, what is the order for superinterfaces vs superclasses?

		MappingResolver resolver = QuiltLoader.getMappingResolver();
		MappingTreeView mappings = QuiltLauncherBase.getLauncher().getMappingConfiguration().getMappings();

		int namespace = mappings.getNamespaceId(resolver.getCurrentRuntimeNamespace());
		// Test if the given class is mappable
		if (mappings.getClass(type.getName().replace('.', '/'), namespace) != null) {
			if (!dst.add(type)) {
				// Already visited
				return;
			}
		}

		Class<?> sup = type.getSuperclass();
		if (sup != null) {
			getMappableClassHierarchy(sup, dst);
		}

		for (Class<?> itf : type.getInterfaces()) {
			getMappableClassHierarchy(itf, dst);
		}
	}

	public static LookupIntermediateArguments fixLookupFindField(Lookup lookup, Class<?> owner, String name, Class<?> type) {

		MappingResolver resolver = QuiltLoader.getMappingResolver();
		String descriptor = Type.getDescriptor(type);

		Collection<String> possibleNamespaces = new LinkedHashSet<>(resolver.getNamespaces());
		possibleNamespaces.remove(resolver.getCurrentRuntimeNamespace());

		for (Class<?> in : getMappableClassHierarchy(owner)) {
			Lookup forTarget = lookup.in(in);
			// Only return fields that the actual lookup is able to access
			for (Field f : in.getDeclaredFields()) {
				if (f.getType() != type) {
					// Skip expensive name checking
					continue;
				}
				// TODO: Handle modules
				int mods = f.getModifiers() & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE);
				if (mods == 0) {
					mods = MethodHandles.Lookup.PACKAGE;
				}

				if ((mods & forTarget.lookupModes()) != 0) {
					// We can access the field

					for (String namespace : possibleNamespaces) {
						String className = resolver.unmapClassName(namespace, in.getName());
						String unMappedDesc = descriptor;
						int idxL = unMappedDesc.indexOf('L');
						if (idxL >= 0) {
							String mappedDescType = unMappedDesc.substring(idxL + 1, unMappedDesc.length() - 1);
							unMappedDesc = unMappedDesc.substring(0, idxL + 1) //
								+ resolver.unmapClassName(namespace, mappedDescType.replace('/', '.')).replace('.', '/')
								+ ";";
						}
						String mappedName = resolver.mapFieldName(namespace, className, name, unMappedDesc);
						if (f.getName().equals(mappedName)) {
							return new LookupIntermediateArguments(lookup, owner, mappedName, type);
						}
					}
				}
			}
		}

		return new LookupIntermediateArguments(lookup, owner, name, type);
	}

	private static String mapMethodTypeDescriptor(String namespaceTo, MethodType type) {
		String desc = type.toMethodDescriptorString();
		MappingResolver resolver = QuiltLoader.getMappingResolver();

		StringBuilder result = new StringBuilder();
		int idxObjDescStart = -1;
		int idxSemicolon = 0;
		while ((idxObjDescStart = desc.indexOf('L', idxObjDescStart + 1)) >= 0) {
			int previousSemicolon = idxSemicolon;
			idxSemicolon = desc.indexOf(';', idxObjDescStart);
			String sub = desc.substring(idxObjDescStart + 1, idxSemicolon).replace('/', '.');

			result.append(desc.substring(previousSemicolon, idxObjDescStart + 1));
			result.append(resolver.unmapClassName(namespaceTo, sub).replace('.', '/'));
		}

		if (idxSemicolon > 0) {
			result.append(desc.substring(idxSemicolon));
		}

		if (result.length() == 0) {
			return desc;
		} else {
			return result.toString();
		}
	}

	public static LookupIntermediateArguments fixLookupFindMethod(Lookup lookup, Class<?> owner, String name,
		MethodType type) {

		MappingResolver resolver = QuiltLoader.getMappingResolver();

		Collection<String> possibleNamespaces = new LinkedHashSet<>(resolver.getNamespaces());
		possibleNamespaces.remove(resolver.getCurrentRuntimeNamespace());

		for (Class<?> in : getMappableClassHierarchy(owner)) {
			Lookup forTarget = lookup.in(in);
			// Only return methods that the actual lookup is able to access
			for (Method m : in.getDeclaredMethods()) {
				if (m.getReturnType() != type.returnType() || !Arrays.equals(type.parameterArray(), m.getParameterTypes())) {
					// Skip expensive name checking
					continue;
				}
				// TODO: Handle modules
				int mods = m.getModifiers() & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE);
				if (mods == 0) {
					mods = MethodHandles.Lookup.PACKAGE;
				}

				if ((mods & forTarget.lookupModes()) != 0) {
					// We can access the methods

					for (String namespace : possibleNamespaces) {
						String className = resolver.unmapClassName(namespace, in.getName());
						String unMappedDesc = mapMethodTypeDescriptor(namespace, type);
						String mappedName = resolver.mapMethodName(namespace, className, name, unMappedDesc);
						if (m.getName().equals(mappedName)) {
							return new LookupIntermediateArguments(lookup, owner, mappedName, type);
						}
					}
				}
			}
		}

		return new LookupIntermediateArguments(lookup, owner, name, type);
	}

	public static LookupIntermediateArguments fixLookupFindMethodSpecial(Lookup lookup, Class<?> owner, String name,
		MethodType type, Class<?> specialCaller) {
		LookupIntermediateArguments args = fixLookupFindMethod(lookup, owner, name, type);
		args.specialCaller = specialCaller;
		return args;
	}
}
