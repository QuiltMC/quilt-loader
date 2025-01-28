package org.quiltmc.loader.impl.util.mappings;

import net.fabricmc.mappingio.MappedElementKind;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * A {@link ForwardingMappingVisitor} that completes a given namespace with names from another one,
 * but <b>only</b> if the conditional namespace is also empty.
 */
// copied from QM 25 Jan 2025:
// https://github.com/QuiltMC/quilt-mappings/blob/4ecb5a40fe170756e7b9e798fe5c83d092bf722f/buildSrc/src/main/java/quilt/internal/mappingio/DoubleNsCompleterVisitor.java
public class DoubleNsCompleterVisitor extends ForwardingMappingVisitor {
	private final String targetNamespace;
	private final String conditioningNamespace;
	private final String fallbackNamespace;

	private boolean superVisitHeader;
	private String[] dstNames;
	private int targetNs;
	private int condNs;
	private int fallbackNs;

	private String srcName;

	/**
	 * @param next the visitor to which the output of this one should be forwarded.
	 * @param targetNamespace the namespace to fill in
	 * @param conditioningNamespace the namespace that determines whether to fill or not
	 * @param fallbackNamespace the namespace from which to get the fallback names
	 */
	public DoubleNsCompleterVisitor(MappingVisitor next, String targetNamespace, String conditioningNamespace, String fallbackNamespace) {
		super(next);
		this.targetNamespace = targetNamespace;
		this.conditioningNamespace = conditioningNamespace;
		this.fallbackNamespace = fallbackNamespace;
	}

	@Override
	public boolean visitHeader() throws IOException {
		superVisitHeader = super.visitHeader();

		return true;
	}

	private int namespaceId(String namespace, String srcNamespace, List<String> dstNamespaces) {
		if (srcNamespace.equals(namespace)) {
			return -1;
		} else {
			int i = dstNamespaces.indexOf(namespace);
			if (i == -1) {
				throw new IllegalArgumentException("Invalid namespace " + namespace + ": is not " + srcNamespace + " or in " + dstNamespaces);
			}

			return i;
		}
	}

	@Override
	public void visitNamespaces(String srcNamespace, List<String> dstNamespaces) throws IOException {
		dstNames = new String[dstNamespaces.size()];

		targetNs = namespaceId(targetNamespace, srcNamespace, dstNamespaces);
		condNs = namespaceId(conditioningNamespace, srcNamespace, dstNamespaces);
		fallbackNs = namespaceId(fallbackNamespace, srcNamespace, dstNamespaces);

		if (superVisitHeader) {
			super.visitNamespaces(srcNamespace, dstNamespaces);
		}
	}

	@Override
	public void visitMetadata(String key, String value) throws IOException {
		if (superVisitHeader) {
			super.visitMetadata(key, value);
		}
	}

	@Override
	public boolean visitContent() throws IOException {
		return super.visitContent();
	}

	@Override
	public boolean visitClass(String srcName) throws IOException {
		this.srcName = srcName;

		return super.visitClass(srcName);
	}

	@Override
	public boolean visitField(String srcName, String srcDesc) throws IOException {
		this.srcName = srcName;

		return super.visitField(srcName, srcDesc);
	}

	@Override
	public boolean visitMethod(String srcName, String srcDesc) throws IOException {
		this.srcName = srcName;

		return super.visitMethod(srcName, srcDesc);
	}

	@Override
	public boolean visitMethodArg(int argPosition, int lvIndex, String srcName) throws IOException {
		this.srcName = srcName;

		return super.visitMethodArg(argPosition, lvIndex, srcName);
	}

	@Override
	public boolean visitMethodVar(int lvtRowIndex, int lvIndex, int startOpIdx, int endOpIdx, @Nullable String srcName) throws IOException {
		this.srcName = srcName;

		return super.visitMethodVar(lvtRowIndex, lvIndex, startOpIdx, endOpIdx, srcName);
	}

	@Override
	public void visitDstName(MappedElementKind targetKind, int namespace, String name) {
		dstNames[namespace] = name;
	}

	@Override
	public boolean visitElementContent(MappedElementKind targetKind) throws IOException {
		for (int i = 0; i < dstNames.length; i++) {
			String name = dstNames[i];

			if (i == targetNs && name == null && dstNames[condNs] == null) {
				name = fallbackNs == -1 ? srcName : dstNames[fallbackNs];
			}

			super.visitDstName(targetKind, i, name);
		}

		Arrays.fill(dstNames, null);

		return super.visitElementContent(targetKind);
	}
}