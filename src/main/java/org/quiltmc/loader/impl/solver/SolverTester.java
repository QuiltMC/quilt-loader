/*
 * Copyright 2023 QuiltMC
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

package org.quiltmc.loader.impl.solver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.quiltmc.loader.api.gui.QuiltLoaderText;
import org.quiltmc.loader.api.plugin.solver.LoadOption;
import org.quiltmc.loader.api.plugin.solver.Rule;
import org.quiltmc.loader.api.plugin.solver.RuleDefiner;
import org.quiltmc.loader.impl.discovery.ModSolvingError;
import org.quiltmc.loader.impl.solver.RuleSet.ProcessedRuleSet;
import org.quiltmc.loader.impl.util.QuiltLoaderInternal;
import org.quiltmc.loader.impl.util.QuiltLoaderInternalType;
import org.quiltmc.loader.impl.util.log.Log;
import org.quiltmc.loader.util.sat4j.specs.TimeoutException;

@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
public class SolverTester {

	static final boolean JUST_PRE_PROCESS = true;
	static final boolean PRINT_COMPACT_OPTIONS = true;

	public static void main(String[] args) throws ModSolvingError, IOException, TimeoutException {
		Log.configureBuiltin(false, true);
		Log.disableBuiltinFormatting();

		Path input = Paths.get(args[0]);
		Map<LoadOption, Boolean> constants = new HashMap<>();
		Map<LoadOption, LoadOption> aliases = new HashMap<>();
		Map<LoadOption, Integer> options = new HashMap<>();
		Map<String, LoadOption> option2def = new HashMap<>();
		List<RuleDefinition> list = new ArrayList<>();

		boolean inProblem = false;
		List<String> problemLines = new ArrayList<>();

		for (String line : Files.readAllLines(input)) {
			if ("== Problem Start ==".equals(line)) {
				// Only handle the last problem
				constants.clear();
				aliases.clear();
				options.clear();
				list.clear();
				option2def.clear();
				problemLines.clear();
				problemLines.add(line);
				inProblem = true;
				continue;
			} else if ("== Problem End ==".equals(line)) {
				problemLines.add(line);
				inProblem = false;
				continue;
			}

			if (!inProblem) {
				continue;
			}

			if (line.isEmpty()) {
				continue;
			}
			problemLines.add(line);

			if (line.startsWith("At Most")) {
				list.add(read1(line, option2def, (array, count) -> new RuleDefinition.AtMost(null, count, array)));
			} else if (line.startsWith("AtLeast")) {
				list.add(read1(line, option2def, (array, count) -> new RuleDefinition.AtLeast(null, count, array)));
			} else if (line.startsWith("Exactly")) {
				list.add(read1(line, option2def, (array, count) -> new RuleDefinition.Exactly(null, count, array)));
			} else if (line.startsWith("Between")) {
				list.add(
					read2(
						line, option2def, (array, count1, count2) -> new RuleDefinition.Between(
							null, count1, count2, array
						)
					)
				);
			} else {
				int colon1 = line.indexOf(':');
				if (colon1 < 0) {
					throw new Error("Malformed line '" + line + "'");
				}
				String optionKey = line.substring(0, colon1);
				String valueStr = line.substring(colon1 + 2, colon1 + 3);
				Boolean value;
				switch (valueStr) {
					case "?": {
						value = null;
						break;
					}
					case "t": {
						value = true;
						break;
					}
					case "f": {
						value = false;
						break;
					}
					default: {
						throw new Error("Malformed line '" + line + "'");
					}
				}
				int weightIdx = line.lastIndexOf("weight");
				String weightStr = line.substring(weightIdx + "weight".length());
				String text = line.substring(colon1 + 6, weightIdx);
				int weight;
				try {
					weight = Integer.parseInt(weightStr.trim());
				} catch (NumberFormatException e) {
					throw new Error("Malformed line '" + line + "'");
				}
				ReadOption option = new ReadOption(optionKey, text);
				option2def.put(optionKey, option);
				options.put(option, weight);
			}
		}

		for (String line : problemLines) {
			System.out.println(line);
		}

		if (JUST_PRE_PROCESS) {
			RuleSet ruleSet = new RuleSet.ProcessedRuleSet(constants, aliases, options, list);
			ProcessedRuleSet processed = SolverPreProcessor.preProcess(true, ruleSet);
			System.out.println("Pre Process Success!");
			System.out.println("Constants:");
			Comparator<Entry<LoadOption, Boolean>> cmp = Comparator.comparing(entry -> optionKey(entry.getKey()));
			for (Entry<LoadOption, Boolean> entry : processed.constants.entrySet().stream().sorted(cmp).collect(
				Collectors.toList()
			)) {
				LoadOption option = entry.getKey();
				String key = optionKey(option);
				System.out.println(
					key + " = " + (entry.getValue() ? "true " : "false") + "   " + ((ReadOption) option).text
				);
			}
			System.out.println("Output Solution:");
			List<String> soln = new ArrayList<>();
			for (LoadOption option : processed.getConstantSolution()) {
				soln.add(" - " + option.describe());
			}
			soln.sort(null);
			soln.forEach(System.out::println);
			System.out.println("Remaining problem:");
			SolverPreProcessor.appendRuleSet(processed, ruleSet, System.out::println);
		} else {
			Sat4jWrapper wrapper = new Sat4jWrapper();

			for (Map.Entry<LoadOption, Integer> option : options.entrySet()) {
				wrapper.addOption(option.getKey());
				wrapper.setWeight(option.getKey(), null, option.getValue());
			}

			for (RuleDefinition def : list) {
				wrapper.addRule(new PreDefinedRule(def));
			}

			if (wrapper.hasSolution()) {
				System.out.println("Has Solution!");
				Collection<LoadOption> solution = wrapper.getSolution();
				List<String> out = new ArrayList<>();
				solution.forEach(option -> {
					final boolean load;
					if (LoadOption.isNegated(option)) {
						option = option.negate();
						load = false;
					} else {
						load = true;
					}
					String key = optionKey(option);
					out.add(key + " = " + (load ? "true " : "false") + "   " + ((ReadOption) option).text);
				});
				out.sort(null);
				out.forEach(System.out::println);
			} else {
				System.out.println("No solution!");
				// TODO: Add debugging here?
				// (Not sure its necessary, as we don't normally test that here)
			}
		}
	}

	private static String optionKey(LoadOption option) {
		if (option instanceof ReadOption) {
			return ((ReadOption) option).key;
		} else if (LoadOption.isNegated(option)) {
			return optionKey(option.negate());
		} else {
			throw new IllegalStateException("Unknown LoadOption " + option.getClass());
		}
	}

	@FunctionalInterface
	interface Rule1Constructor {
		RuleDefinition construct(LoadOption[] array, int count);
	}

	@FunctionalInterface
	interface Rule2Constructor {
		RuleDefinition construct(LoadOption[] array, int min, int max);
	}

	private static RuleDefinition read1(String line, Map<String, LoadOption> option2def, Rule1Constructor ctor) {
		line = line.substring(7).trim();
		LoadOption[] array = readArray(line, option2def);

		int ofIdx = line.indexOf("of");
		if (ofIdx < 0) {
			throw new Error("Malformed line '" + line + "'");
		}
		int count = Integer.parseInt(line.substring(0, ofIdx).trim());
		return ctor.construct(array, count);
	}

	private static RuleDefinition read2(String line, Map<String, LoadOption> option2def, Rule2Constructor ctor) {
		line = line.substring(7).trim();
		LoadOption[] array = readArray(line, option2def);

		int ofIdx = line.indexOf("of");
		if (ofIdx < 0) {
			throw new Error("Malformed line '" + line + "'");
		}

		int andIdx = line.indexOf("and");
		if (andIdx < 0) {
			throw new Error("Malformed line '" + line + "'");
		}
		int count1 = Integer.parseInt(line.substring(0, andIdx).trim());
		int count2 = Integer.parseInt(line.substring(andIdx + 3, ofIdx).trim());
		return ctor.construct(array, count1, count2);
	}

	private static LoadOption[] readArray(String line, Map<String, LoadOption> option2def) {
		int open = line.lastIndexOf('{');
		if (open < 0) {
			throw new Error("Malformed line '" + line + "'");
		}

		List<LoadOption> options = new ArrayList<>();

		for (String part : Pattern.compile(",", Pattern.LITERAL).split(line.substring(open + 1, line.length() - 1))) {
			part = part.trim();
			char start = part.charAt(0);
			boolean negate;
			if (start == '-') {
				negate = true;
			} else if (start == '+') {
				negate = false;
			} else {
				throw new Error("Malformed line '" + line + "'");
			}
			String key = part.substring(1);
			LoadOption option = option2def.get(key);
			if (option == null) {
				throw new Error("Unknown key '" + key + "' in malformed line '" + line + "'");
			}
			if (negate) {
				option = option.negate();
			}
			options.add(option);
		}

		return options.toArray(new LoadOption[0]);
	}

	@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
	static final class ReadOption extends LoadOption {

		final String key;
		final String text;

		public ReadOption(String key, String text) {
			this.key = key;
			this.text = text;
		}

		@Override
		public String toString() {
			return PRINT_COMPACT_OPTIONS ? key : text;
		}

		@Override
		public QuiltLoaderText describe() {
			return QuiltLoaderText.of(text);
		}
	}

	@QuiltLoaderInternal(QuiltLoaderInternalType.NEW_INTERNAL)
	static class PreDefinedRule extends Rule {

		final RuleDefinition definition;

		public PreDefinedRule(RuleDefinition definition) {
			this.definition = definition;
		}

		@Override
		public boolean onLoadOptionAdded(LoadOption option) {
			return false;
		}

		@Override
		public boolean onLoadOptionRemoved(LoadOption option) {
			return false;
		}

		@Override
		public void define(RuleDefiner definer) {
			if (definition instanceof RuleDefinition.AtLeastOneOf) {
				definer.atLeast(1, ((RuleDefinition.AtLeastOneOf) definition).options);
			} else if (definition instanceof RuleDefinition.AtLeast) {
				RuleDefinition.AtLeast atLeast = (RuleDefinition.AtLeast) definition;
				definer.atLeast(atLeast.count, atLeast.options);
			} else if (definition instanceof RuleDefinition.AtMost) {
				RuleDefinition.AtMost atMost = (RuleDefinition.AtMost) definition;
				definer.atMost(atMost.count, atMost.options);
			} else if (definition instanceof RuleDefinition.Exactly) {
				RuleDefinition.Exactly exactly = (RuleDefinition.Exactly) definition;
				definer.exactly(exactly.count, exactly.options);
			} else {
				RuleDefinition.Between between = (RuleDefinition.Between) definition;
				definer.between(between.min, between.max, between.options);
			}
		}

		@Override
		public String toString() {
			return definition.toString();
		}

		@Override
		public Collection<? extends LoadOption> getNodesFrom() {
			// TODO Auto-generated method stub
			throw new AbstractMethodError("// TODO: Implement this!");
		}

		@Override
		public Collection<? extends LoadOption> getNodesTo() {
			// TODO Auto-generated method stub
			throw new AbstractMethodError("// TODO: Implement this!");
		}

		@Override
		public void fallbackErrorDescription(StringBuilder errors) {
			errors.append(toString());
		}

		@Override
		public void appendRuleDescription(Consumer<QuiltLoaderText> to) {
			// TODO Auto-generated method stub
			throw new AbstractMethodError("// TODO: Implement this!");
		}
	}
}
