quilt-loader
===========

The loader for mods under Quilt. It provides mod loading facilities and useful abstractions for other mods to use.

## License

Licensed under the Apache License 2.0.

The `license.json` included in loader to generate SPDX license instances is licensed under Creative Commons Attribution
3.0 (SPDX License ID CC-BY-3.0) from SPDX.

When adding NEW classes (that you wrote yourself) they should use the quilt-only header file (`/codeformat/HEADER`)
When adding classes that contain code from fabric-loader they should use the modified header file (`/codeformat/FABRIC_MODIFIED_HEADER`)

## Source folder layout

* `src/main/java` contains all "normal" loader source code.
    * `org.quiltmc.loader.api` is considered to be quilt-loader's public api - mods can freely make use of any of these classes and interfaces
    * `org.quiltmc.loader.impl` contains quilt-loader internals - these can change at any time, and so mods should NOT use any of these.
* `src/test/java` contains test sources - these aren't built into the main jar file.
* `src/fabric/api/java` contains all fabric-loader apis (Non deprecated apis that any fabric mods can use)
* `src/fabric/impl/java` contains fabric-loader internal code, but is used by mods (even though this is discouraged).
* `src/fabric/legacy/java` contains fabric-loader internal deprecated code, but is used by mods (even though this is discouraged). Unlike `fabric/impl` this is for classes and interfaces that fabric-loader itself has deprecated.

When adding fabric internal compatibility classes they should always be annotated with `@Deprecated`, to discourage quilt mods from accidently using them.
