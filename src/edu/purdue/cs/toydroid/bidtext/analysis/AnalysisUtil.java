package edu.purdue.cs.toydroid.bidtext.analysis;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeReference;

import edu.purdue.cs.toydroid.bidtext.Main;
import edu.purdue.cs.toydroid.bidtext.graph.SimpleGraphNode;
import edu.purdue.cs.toydroid.bidtext.graph.TypingConstraint;
import edu.purdue.cs.toydroid.bidtext.graph.TypingGraph;
import edu.purdue.cs.toydroid.bidtext.graph.TypingGraphUtil;
import edu.purdue.cs.toydroid.bidtext.graph.TypingNode;
import edu.purdue.cs.toydroid.bidtext.graph.TypingRecord;
import edu.purdue.cs.toydroid.bidtext.graph.TypingSubGraph;
import edu.purdue.cs.toydroid.utils.AnalysisConfig;
import edu.purdue.cs.toydroid.utils.ResourceUtil;
import edu.purdue.cs.toydroid.utils.WalaUtil;

public class AnalysisUtil {
	private static Logger logger = LogManager.getLogger(AnalysisUtil.class);

	private static Set<InterestingNode> sinks = new HashSet<InterestingNode>();

	private static Map<String, Integer> activity2Layout = new HashMap<String, Integer>();

	public static void associateLayout2Activity(
			SSAAbstractInvokeInstruction instr, CGNode cgNode) {
		String act = instr.getDeclaredTarget()
				.getDeclaringClass()
				.getName()
				.toString();
		String selector = instr.getDeclaredTarget().getSelector().toString();
		if (ResourceUtil.isActivity(act)
				&& "setContentView(I)V".equals(selector)) {
			SymbolTable symTable = cgNode.getIR().getSymbolTable();
			// only int constant is handled now.
			if (symTable.isIntegerConstant(instr.getUse(1))) {
				int layoutId = symTable.getIntValue(instr.getUse(1));
				activity2Layout.put(act, layoutId);
			}
		}
	}

	// 0 - nothing; 2 - sink
	public static int tryRecordInterestingNode(
			SSAAbstractInvokeInstruction instr, TypingSubGraph sg,
			ClassHierarchy cha) {
		String sig = WalaUtil.getSignature(instr);
		String intestringIndices = AnalysisConfig.getPotentialSink(sig);
		if (intestringIndices != null) {
			InterestingNode node = InterestingNode.getInstance(instr, sg,
					intestringIndices);
			sinks.add(node);
			logger.info("SINK: {}->{}() in [{}.{}()]",
					instr.getDeclaredTarget()
							.getDeclaringClass()
							.getName()
							.toString(), instr.getDeclaredTarget()
							.getName()
							.toString(), sg.cgNode.getMethod()
							.getDeclaringClass()
							.getName()
							.toString(), sg.cgNode.getMethod()
							.getName()
							.toString());
			return 2;
		}
		return 0;
	}

	/**
	 * Dump all associated texts for interesting sinks.
	 */
	public static void dumpTextForSinks() {
		logger.info("Dump text for all sinks.");
		if (sinks.isEmpty()) {
			logger.warn("No interesting sinks are found.");
			return;
		}
		int idx = 0;
		for (InterestingNode sink : sinks) {
			dumpTextForSink(sink, idx++);
		}
		logger.info("Dumped text for {} sinks.", idx);
	}

	private static void dumpTextForSink(InterestingNode sink, int idx) {
		logger.info(" - dump text for sink: {}", sink.sinkSignature());
		Iterator<TypingNode> args = sink.iterateInterestingArgs();

		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new FileWriter(idx + "." + sink.tag
					+ ".txt"));
			writer.write("SINK [");
			writer.write(sink.sinkSignature());
			writer.write(']');
			writer.newLine();
			writer.write(" in [");
			writer.write(sink.enclosingTypingSubGraph().cgNode.getMethod()
					.getSignature());
			writer.write(']');
			writer.newLine();
		} catch (IOException e) {
			logger.error("Fail to create dump file for [{}] {}", idx,
					sink.sinkSignature());
			if (writer != null) {
				try {
					writer.close();
				} catch (Exception ee) {
				}
			}
			return;
		}
		Map<Entrypoint, Set<TypingNode>> visited = new HashMap<Entrypoint, Set<TypingNode>>();
		Set<String> codeTexts = new HashSet<String>();
		Set<Integer> constants = new HashSet<Integer>();
		while (args.hasNext()) {
			TypingNode gNode = args.next();
			if (gNode.isConstant()) {
				continue;
			}
			TypingSubGraph sg = sink.enclosingTypingSubGraph();
			TypingGraph graph = sink.enclosingTypingGraph();
			dumpTextForNode(gNode, sg, graph, codeTexts, constants);
			dumpTextForFields(gNode, sg, graph, codeTexts, constants);
		}
		visited.clear();
		visited = null;

		TextAnalysis textAnalysis = new TextAnalysis();
		String sensitiveTag = textAnalysis.analyze(codeTexts, false);

		Set<String> guiTexts = new HashSet<String>();
		dumpTextForPossibleGUI(sink, writer, guiTexts);

		// for each widget we collected, if anyone exists in multiple layouts,
		// all those layouts are recorded. but if we can find more than one
		// widget, we may find out the correct layout.
		Map<Integer, IdCountPair> rankedLayout = new HashMap<Integer, IdCountPair>();
		Set<Integer> toRemove = new HashSet<Integer>();
		for (Integer iObj : constants) {
			String guiText = ResourceUtil.getLayoutText(iObj);
			if (guiText != null) {
				if (!guiText.isEmpty()) {
					try {
						BufferedReader reader = new BufferedReader(
								new StringReader(guiText));
						String line = null;
						while ((line = reader.readLine()) != null) {
							line = line.trim();
							if (!line.isEmpty()) {
								guiTexts.add(line);
							}
						}
					} catch (Exception e) {

					}
					toRemove.add(iObj);
				}
			} else {
				Set<Integer> s = ResourceUtil.getLayouts(iObj);
				if (s != null) {
					for (Integer i : s) {
						IdCountPair p = rankedLayout.get(i);
						if (p == null) {
							p = new IdCountPair(i);
							rankedLayout.put(i, p);
						} else {
							p.increment();
						}
					}
				}
			}
		}

		Set<Integer> interestingLayouts = new HashSet<Integer>();
		int nRankedLayouts = rankedLayout.size();
		if (nRankedLayouts == 1) {
			IdCountPair p = rankedLayout.values().iterator().next();
			Integer layoutId = p.id;
			interestingLayouts.add(layoutId);
		} else if (nRankedLayouts > 1) {
			Collection<IdCountPair> toRank = rankedLayout.values();
			Object[] objArray = toRank.toArray();
			Arrays.sort(objArray);
			int largestRank = -1;
			for (int i = nRankedLayouts - 1; i >= 0; i--) {
				IdCountPair p = (IdCountPair) objArray[i];
				if (largestRank <= p.count) {
					interestingLayouts.add(p.id);
					largestRank = p.count;
				} else {
					break;
				}
			}
		}
		for (Integer lId : interestingLayouts) {
			String guiText = ResourceUtil.getLayoutText(lId);
			if (guiText != null && !guiText.isEmpty()) {
				try {
					BufferedReader reader = new BufferedReader(
							new StringReader(guiText));
					String line = null;
					while ((line = reader.readLine()) != null) {
						line = line.trim();
						if (!line.isEmpty()) {
							guiTexts.add(line);
						}
					}
				} catch (Exception e) {

				}
			}
		}

		String guiSensitiveTag = textAnalysis.analyze(guiTexts, true);

		if (!sensitiveTag.isEmpty()) {
			logger.debug("   $[CODE] {}", sensitiveTag);
			try {
				writer.write(" ^[CODE]: ");
				writer.write(sensitiveTag);
				writer.newLine();
			} catch (IOException e) {

			}
		}

		if (!guiSensitiveTag.isEmpty()) {
			logger.debug("   $[GUI] {}", guiSensitiveTag);
			try {
				writer.write(" ^[GUI]: ");
				writer.write(guiSensitiveTag);
				writer.newLine();
			} catch (IOException e) {

			}
		}

		for (String t : codeTexts) {
			try {
				writer.write(" - ");
				writer.write(t);
				writer.newLine();
			} catch (IOException e) {
			}
		}
		for (String t : guiTexts) {
			try {
				writer.write(" + ");
				writer.write(t);
				writer.newLine();
			} catch (IOException e) {
			}
		}
		constants.removeAll(toRemove);
		for (Integer iObj : constants) {
			try {
				writer.write(" # 0x");
				writer.write(Integer.toHexString(iObj.intValue()));
				writer.newLine();
				String guiText = ResourceUtil.getLayoutText(iObj);
				if (guiText != null && !guiText.isEmpty()) {
					writer.write(guiText);
					writer.newLine();
				}
			} catch (IOException e) {
			}
		}
		// dumpTextForPossibleGUI(sink, writer);
		try {
			writer.flush();
			writer.close();
			writer = null;
		} catch (IOException e) {
		} finally {
			if (null != writer) {
				try {
					writer.close();
				} catch (IOException e) {
				}
			}
		}
	}

	public static void dumpTextForNode(TypingNode n, TypingSubGraph sg,
			TypingGraph graph, Set<String> texts, Set<Integer> constants) {
		TypingRecord record = graph.getTypingRecord(n.getGraphNodeId());
		if (record == null) {
			return;
		}
		texts.addAll(record.getTypingTexts());
		for (Object o : constants) {
			if (o instanceof Integer) {
				constants.add((Integer) o);
			}
		}
	}

	// Fields that across entrypoints
	private static void dumpTextForFields(TypingNode n, TypingSubGraph sg,
			TypingGraph graph, Set<String> texts, Set<Integer> constants) {
		TypingRecord record = graph.getTypingRecord(n.getGraphNodeId());
		if (record == null) {
			return;
		}
		Stack<TypingGraph> visited = new Stack<TypingGraph>();
		List<Object> worklist = new LinkedList<Object>();
		dumpTextForFieldsHelper(graph, record, 0, visited, worklist, texts,
				constants, true);
		visited.clear();
		worklist.clear();
		dumpTextForFieldsHelper(graph, record, 0, visited, worklist, texts,
				constants, false);
		visited = null;
		worklist = null;
	}

	private static void dumpTextForFieldsHelper(TypingGraph graph,
			TypingRecord record, int permLevel, Stack<TypingGraph> visited,
			List<Object> worklist, Set<String> texts, Set<Integer> constants,
			boolean isBackward) {
		if (permLevel >= 2) {
			return;
		}
		int worklistSize = worklist.size();
		Set<SimpleGraphNode> sources;
		if (isBackward) {
			sources = record.getInputFields();
		} else {
			sources = record.getOutputFields();
		}
		visited.push(graph);
		for (SimpleGraphNode sgn : sources) {
			TypingNode tn = graph.getNode(sgn.node);
			if (tn.isField()) {
				String sig = tn.fieldRef.getSignature();// System.err.println(sig);
				Map<Entrypoint, TypingGraph> entry2Graph = TypingGraphUtil.entry2Graph;
				Set<Map.Entry<Entrypoint, TypingGraph>> entrySet = entry2Graph.entrySet();
				for (Map.Entry<Entrypoint, TypingGraph> entry : entrySet) {
					// Entrypoint ep = entry.getKey();
					TypingGraph g = entry.getValue();
					if (visited.contains(g)) {
						continue;
					}
					Set<TypingRecord> targets = new HashSet<TypingRecord>();
					Iterator<TypingNode> iter;
					if (isBackward) {
						iter = g.iterateAllOutgoingFields(sig);
					} else {
						iter = g.iterateAllIncomingFields(sig);
					}
					while (iter.hasNext()) {
						TypingNode d = iter.next();
						TypingRecord r = g.getTypingRecord(d.getGraphNodeId());
						if (r != null) {
							targets.add(r);
						}
					}
					if (!targets.isEmpty()) {
						worklist.add(g);
						worklist.add(Integer.valueOf(permLevel + 1));
						worklist.add(targets);
					}
					targets = null;
				}
			}
		}
		dumpTextForFieldsViaWorklist(visited, worklist, worklistSize, texts,
				constants, isBackward);
		visited.pop();
	}

	private static void dumpTextForFieldsViaWorklist(
			Stack<TypingGraph> visited, List<Object> worklist, int initSize,
			Set<String> texts, Set<Integer> constants, boolean isBackward) {
		while (worklist.size() > initSize) {
			TypingGraph graph = (TypingGraph) worklist.remove(initSize);
			int permLevel = ((Integer) worklist.remove(initSize)).intValue();
			Set<TypingRecord> recSet = (Set<TypingRecord>) worklist.remove(initSize);
			for (TypingRecord rec : recSet) {
				texts.addAll(rec.getTypingTexts());
				Set<Object> consts = rec.getTypingConstants();
				for (Object c : consts) {
					if (c instanceof Integer) {
						constants.add((Integer) c);
					}
				}
				dumpTextForFieldsHelper(graph, rec, permLevel + 1, visited,
						worklist, texts, constants, isBackward);
			}
		}
	}

	// currently only for the GUI that triggers the sink operation
	private static void dumpTextForPossibleGUI(InterestingNode sink,
			BufferedWriter writer, Set<String> texts) {
		String epClass = sink.enclosingTypingGraph().entry.getMethod()
				.getDeclaringClass()
				.getName()
				.toString();
		Integer layoutId = activity2Layout.get(epClass);
		if (layoutId != null) {
			String text = ResourceUtil.getLayoutText(layoutId);
			if (text != null) {
				try {
					BufferedReader reader = new BufferedReader(
							new StringReader(text));
					String line = null;
					while ((line = reader.readLine()) != null) {
						// writer.write(" + ");
						// writer.write(line);
						// writer.newLine();
						texts.add(line);
					}
				} catch (IOException e) {

				}
			}
		}
	}

	static class IdCountPair implements Comparable<IdCountPair> {
		Integer id;
		int count;

		IdCountPair(Integer id) {
			this.id = id;
			count = 1;
		}

		void increment() {
			count++;
		}

		@Override
		public int compareTo(IdCountPair that) {
			return this.count - that.count;
		}
	}
}
