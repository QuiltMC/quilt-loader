/*
 * Copyright 2016 FabricMC
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

package org.quiltmc.loader.impl.metadata.qmj;

public enum ModLoadType {
	
	
	/*
	 * 
	 * If a mod is directly in the mods folder then it must be loaded
	 * 
	 * If a mod is nested then it is loaded according to one of the following rules:
	 * 
	 *   - dependencies and breaks
	 *   
	 * Influencing this is useful for:
	 * 	- library mods (only load modules that are needed)
	 *  - compatibility mods (only load compat modules if possible)
	 *  - sub-modules of a big mod
	 * 
	 * Choosing afterwards, while useful, is also impractical. So that's dropped.
	 * 
	 */
	
	
	ALWAYS,
	IF_POSSIBLE,
	IF_REQUIRED;
}
