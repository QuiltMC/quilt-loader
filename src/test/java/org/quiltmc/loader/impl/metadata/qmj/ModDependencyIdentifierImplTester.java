package org.quiltmc.loader.impl.metadata.qmj;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ModDependencyIdentifierImplTester {
	@Test
	void testRawConstructor() {
		String id = "quilt_loader";
		ModDependencyIdentifierImpl idIdentifier = new ModDependencyIdentifierImpl(id);
		Assertions.assertEquals("", idIdentifier.mavenGroup());
		Assertions.assertEquals(id, idIdentifier.id());
		Assertions.assertEquals(id, idIdentifier.toString());

		String groupId = "org.quiltmc:quilt_loader";
		ModDependencyIdentifierImpl groupIdIdentifier = new ModDependencyIdentifierImpl(groupId);
		Assertions.assertEquals("org.quiltmc", groupIdIdentifier.mavenGroup());
		Assertions.assertEquals("quilt_loader", groupIdIdentifier.id());
		Assertions.assertEquals(groupId, groupIdIdentifier.toString());
	}

	@Test
	void testIndividualConstructor() {
		String group = "org.quiltmc";
		String id = "quilt_loader";
		ModDependencyIdentifierImpl idIdentifier = new ModDependencyIdentifierImpl("", id);
		Assertions.assertEquals("", idIdentifier.mavenGroup());
		Assertions.assertEquals(id, idIdentifier.id());
		Assertions.assertEquals(id, idIdentifier.toString());

		ModDependencyIdentifierImpl groupIdIdentifier = new ModDependencyIdentifierImpl(group, id);
		Assertions.assertEquals(group, groupIdIdentifier.mavenGroup());
		Assertions.assertEquals(id, groupIdIdentifier.id());
		Assertions.assertEquals("org.quiltmc:quilt_loader", groupIdIdentifier.toString());
	}
}
