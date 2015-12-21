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
import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
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

	private static InterestingNode latestInterestingNode = null;

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
		latestInterestingNode = null;
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
			latestInterestingNode = node;
			return 2;
		}
		return 0;
	}

	public static InterestingNode getLatestInterestingNode() {
		return latestInterestingNode;
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
		Map<String, List<Statement>> codeTexts = new HashMap<String, List<Statement>>();
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

		Map<String, List<Statement>> guiTexts = new HashMap<String, List<Statement>>();
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
								guiTexts.put(line, null);
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
							guiTexts.put(line, null);
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

		for (String t : codeTexts.keySet()) {
			try {
				writer.write(" - ");
				writer.write(t);
				writer.newLine();
			} catch (IOException e) {
			}
		}
		for (String t : guiTexts.keySet()) {
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

		// dump paths
		Map<String, List<Statement>> text2Path = textAnalysis.getText2Path();
		Set<Map.Entry<String, List<Statement>>> pathSet = text2Path.entrySet();
		for (Map.Entry<String, List<Statement>> entry : pathSet) {
			String text = entry.getKey();
			List<Statement> path = entry.getValue();
			if (path == null/* || path.isEmpty() */) {
				continue;
			}
			try {
				writer.newLine();
				writer.newLine();
				writer.write("********");
				writer.write(text.trim());
				writer.write("********");
				writer.newLine();
				for (Statement stmt : path) {
					writer.write(stmt.toString());
					writer.newLine();
				}
				writer.write("[[ ");
				writer.write(sink.instruction());
				writer.write(" ]]");
				writer.newLine();
				writer.flush();
			} catch (IOException e) {

			}
		}

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
			TypingGraph graph, Map<String, List<Statement>> texts,
			Set<Integer> constants) {
		TypingRecord record = graph.getTypingRecord(n.getGraphNodeId());
		if (record == null) {
			return;
		}
		texts.putAll(record.getTypingTexts());
		for (Object o : record.getTypingConstants()) {
			if (o instanceof Integer) {
				constants.add((Integer) o);
			}
		}
	}

	// Fields that across entrypoints
	private static void dumpTextForFields(TypingNode n, TypingSubGraph sg,
			TypingGraph graph, Map<String, List<Statement>> texts,
			Set<Integer> constants) {
		TypingRecord record = graph.getTypingRecord(n.getGraphNodeId());
		if (record == null) {
			return;
		}
		List<Statement> fieldPath = new LinkedList<Statement>();
		Stack<TypingGraph> visited = new Stack<TypingGraph>();
		List<Object> worklist = new LinkedList<Object>();
		dumpTextForFieldsHelper(graph, record, 0, visited, fieldPath, worklist,
				texts, constants, true);
		visited.clear();
		worklist.clear();
		dumpTextForFieldsHelper(graph, record, 0, visited, fieldPath, worklist,
				texts, constants, false);
		visited = null;
		worklist = null;
	}

	private static void dumpTextForFieldsHelper(TypingGraph graph,
			TypingRecord record, int permLevel, Stack<TypingGraph> visited,
			List<Statement> fieldPath, List<Object> worklist,
			Map<String, List<Statement>> texts, Set<Integer> constants,
			boolean isBackward) {
		if (permLevel >= 2) {
			return;
		}
		int worklistSize = worklist.size();
		Map<SimpleGraphNode, List<Statement>> sources;
		if (isBackward) {
			sources = record.getInputFields();
		} else {
			sources = record.getOutputFields();
		}
		visited.push(graph);
		for (SimpleGraphNode sgn : sources.keySet()) {
			TypingNode tn = graph.getNode(sgn.node);
			if (tn.isField()) {
				List<Statement> tempPath = new LinkedList<Statement>();
				boolean startAdd = false, endAdd = false;
				Statement connector = null;
				String connectorSig = "";
				if (!fieldPath.isEmpty()) {
					connector = fieldPath.get(0);
					if (connector.getKind() == Statement.Kind.NORMAL) {
						NormalStatement nstmt = (NormalStatement) connector;
						SSAInstruction inst = nstmt.getInstruction();
						if (isBackward && inst instanceof SSAGetInstruction) {
							connectorSig = ((SSAGetInstruction) inst).getDeclaredField()
									.getSignature();
						} else if (!isBackward
								&& inst instanceof SSAPutInstruction) {
							connectorSig = ((SSAPutInstruction) inst).getDeclaredField()
									.getSignature();
						}
					}
				}
				List<Statement> sgnPath = sources.get(sgn);
				for (Statement p : sgnPath) {
					if (p.getKind() == Statement.Kind.NORMAL) {
						NormalStatement nstmt = (NormalStatement) p;
						SSAInstruction inst = nstmt.getInstruction();
						if (isBackward
								&& inst instanceof SSAGetInstruction
								&& tn.fieldRef.getSignature()
										.equals((((SSAGetInstruction) inst).getDeclaredField().getSignature()))) {
							startAdd = true;
						} else if (!isBackward
								&& inst instanceof SSAPutInstruction
								&& tn.fieldRef.getSignature()
										.equals((((SSAPutInstruction) inst).getDeclaredField().getSignature()))) {
							startAdd = true;
						}
					}
					if (startAdd) {
						tempPath.add(p);
						if (!fieldPath.isEmpty()
								&& p.getKind() == Statement.Kind.NORMAL) {
							NormalStatement nstmt = (NormalStatement) p;
							SSAInstruction inst = nstmt.getInstruction();
							if (!isBackward
									&& inst instanceof SSAGetInstruction
									&& connectorSig.equals((((SSAGetInstruction) inst).getDeclaredField().getSignature()))) {
								endAdd = true;
							} else if (isBackward
									&& inst instanceof SSAPutInstruction
									&& connectorSig.equals((((SSAPutInstruction) inst).getDeclaredField().getSignature()))) {
								endAdd = true;
							}
						}
						if (endAdd) {
							break;
						}
					}
				}
				if (endAdd) {
					tempPath.addAll(fieldPath);
				} else if (!fieldPath.isEmpty()) {
					tempPath.clear();
				}
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
						worklist.add(tempPath);// record field path
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
			Map<String, List<Statement>> texts, Set<Integer> constants,
			boolean isBackward) {
		while (worklist.size() > initSize) {
			TypingGraph graph = (TypingGraph) worklist.remove(initSize);
			int permLevel = ((Integer) worklist.remove(initSize)).intValue();
			Set<TypingRecord> recSet = (Set<TypingRecord>) worklist.remove(initSize);
			List<Statement> fs = (List<Statement>) worklist.remove(initSize);
			if (fs.isEmpty()) {
				continue;
			}
			for (TypingRecord rec : recSet) {
				Map<String, List<Statement>> recTexts = rec.getTypingTexts();
				Set<Map.Entry<String, List<Statement>>> set = recTexts.entrySet();
				for (Map.Entry<String, List<Statement>> entry : set) {
					String key = entry.getKey();
					List<Statement> path = entry.getValue();
					if (path == null) {
						texts.put(key, null);// insensitive text
						continue;
					}
					List<Statement> tempPath = new LinkedList<Statement>();
					Statement connector = fs.get(0);
					String connectorSig = "";
					if (connector.getKind() == Statement.Kind.NORMAL) {
						NormalStatement nstmt = (NormalStatement) connector;
						SSAInstruction inst = nstmt.getInstruction();
						if (isBackward && inst instanceof SSAGetInstruction) {
							connectorSig = ((SSAGetInstruction) inst).getDeclaredField()
									.getSignature();
						} else if (!isBackward
								&& inst instanceof SSAPutInstruction) {
							connectorSig = ((SSAPutInstruction) inst).getDeclaredField()
									.getSignature();
						}
					}
					boolean endAdd = false;
					for (Statement p : path) {
						tempPath.add(p);
						if (p.getKind() == Statement.Kind.NORMAL) {
							NormalStatement nstmt = (NormalStatement) p;
							SSAInstruction inst = nstmt.getInstruction();
							if (!isBackward
									&& inst instanceof SSAGetInstruction
									&& connectorSig.equals((((SSAGetInstruction) inst).getDeclaredField().getSignature()))) {
								endAdd = true;
							} else if (isBackward
									&& inst instanceof SSAPutInstruction
									&& connectorSig.equals((((SSAPutInstruction) inst).getDeclaredField().getSignature()))) {
								endAdd = true;
							}
						}
						if (endAdd) {
							break;
						}
					}
					if (endAdd) {
						tempPath.addAll(fs);
						texts.put(key, tempPath);
					}
				}
				Set<Object> consts = rec.getTypingConstants();
				for (Object c : consts) {
					if (c instanceof Integer) {
						constants.add((Integer) c);
					}
				}
				dumpTextForFieldsHelper(graph, rec, permLevel + 1, visited, fs,
						worklist, texts, constants, isBackward);
			}
		}
	}

	// currently only for the GUI that triggers the sink operation
	private static void dumpTextForPossibleGUI(InterestingNode sink,
			BufferedWriter writer, Map<String, List<Statement>> texts) {
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
						texts.put(line, null);
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
