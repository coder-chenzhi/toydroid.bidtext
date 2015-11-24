package edu.purdue.cs.toydroid.bidtext.analysis;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;

import edu.purdue.cs.toydroid.bidtext.graph.TypingGraph;
import edu.purdue.cs.toydroid.bidtext.graph.TypingNode;
import edu.purdue.cs.toydroid.bidtext.graph.TypingSubGraph;
import edu.purdue.cs.toydroid.utils.AnalysisConfig;

public class InterestingNode {
	private SSAAbstractInvokeInstruction instr;
	private TypingSubGraph subGraph;
	private int[] interestingIdx;
	public String tag; // tag for SINK

	InterestingNode(SSAAbstractInvokeInstruction i, TypingSubGraph sg,
			String interestingIdx) {
		instr = i;
		subGraph = sg;
		parseInterestingIndices(interestingIdx);
	}

	public static InterestingNode getInstance(SSAAbstractInvokeInstruction i,
			TypingSubGraph sg, String interestingIdx) {
		return new InterestingNode(i, sg, interestingIdx);
	}

	public String sinkSignature() {
		return instr.getDeclaredTarget().getSignature();
	}

	public TypingSubGraph enclosingTypingSubGraph() {
		return subGraph;
	}

	public TypingGraph enclosingTypingGraph() {
		return subGraph.typingGraph;
	}

	public Iterator<TypingNode> iterateInterestingArgs() {
		List<TypingNode> list = new ArrayList<TypingNode>();
		for (int i : interestingIdx) {
			int use = instr.getUse(i);
			list.add(subGraph.getByValue(use));
		}
		return list.iterator();
	}

	private void parseInterestingIndices(String s) {
		String[] a = s.split(AnalysisConfig.SEPERATOR);
		interestingIdx = new int[a.length - 1];
		tag = a[0];
		int idx = 0;
		for (int i = 1; i < a.length; i++) {
			interestingIdx[idx] = Integer.parseInt(a[i]);
			idx++;
		}
	}

	public int hashCode() {
		return instr.hashCode();
	}

	public boolean equals(Object obj) {
		if (obj instanceof InterestingNode) {
			return instr.equals(((InterestingNode) obj).instr)
					&& subGraph.cgNode.equals(((InterestingNode) obj).subGraph.cgNode);
		}
		return false;
	}

	public String instruction() {
		return instr.toString();
	}
}
