package org.quiltmc.loader.impl.plugin;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import org.quiltmc.loader.api.plugin.QuiltPluginContext;
import org.quiltmc.loader.api.plugin.QuiltPluginManager;

abstract class BasePluginContext implements QuiltPluginContext {

	final QuiltPluginManagerImpl manager;
	final String pluginId;
	final Set<Path> modFolderSet = new ModFolderSet();

	public BasePluginContext(QuiltPluginManagerImpl manager, String pluginId) {
		this.manager = manager;
		this.pluginId = pluginId;
	}

	@Override
	public QuiltPluginManager manager() {
		return manager;
	}

	class ModFolderSet implements Set<Path> {

		@Override
		public boolean isEmpty() {
			return manager.modFolders.isEmpty();
		}

		@Override
		public int size() {
			return manager.modFolders.size();
		}

		@Override
		public boolean contains(Object o) {
			return manager.modFolders.containsKey(o);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			return manager.modFolders.keySet().containsAll(c);
		}

		@Override
		public Iterator<Path> iterator() {
			return Arrays.asList(toArray(new Path[0])).iterator();
		}

		@Override
		public Object[] toArray() {
			return manager.modFolders.keySet().toArray();
		}

		@Override
		public <T> T[] toArray(T[] a) {
			return manager.modFolders.keySet().toArray(a);
		}

		@Override
		public boolean add(Path e) {
			return manager.addModFolder(e, BasePluginContext.this);
		}

		@Override
		public boolean addAll(Collection<? extends Path> c) {
			boolean changed = false;
			for (Path p : c) {
				changed |= add(p);
			}
			return changed;
		}

		@Override
		public boolean remove(Object o) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void clear() {
			throw new UnsupportedOperationException();
		}
	}
}
