package edu.purdue.cs.toydroid.utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SymbolTable;

public class Stat {
	private static int nConstInLayout = 0;
	private static int nConstInCode = 0;

	public static void addNConstInLayout() {
		nConstInLayout++;
	}

	public static void addNConstInCode() {
		nConstInCode++;
	}

	public static int getNConstInLayout() {
		return nConstInLayout;
	}

	public static int getNConstInCode() {
		return nConstInCode;
	}

	private static Set<String> visitedCGNode = new HashSet<String>();

	public static boolean statCG(CallGraph cg) {
		Iterator<CGNode> iter = cg.iterator();
		while (iter.hasNext()) {
			CGNode n = iter.next();
			String sig = n.getMethod().getSignature();
			if (visitedCGNode.contains(sig)) {
				continue;
			}
			visitedCGNode.add(sig);
			SSAInstruction[] insts = n.getIR().getInstructions();
			SymbolTable symTab = n.getIR().getSymbolTable();
			for (SSAInstruction inst : insts) {
				if (inst == null)
					continue;
				int nUses = inst.getNumberOfUses();
				for (int i = 0; i < nUses; i++) {
					int use = inst.getUse(i);
					if (symTab.isStringConstant(use)) {
						addNConstInCode();
					}
				}
			}
		}
		return true;
	}

	static boolean append = false;

	public static void dumpStat(String apk) {
		try {
			BufferedWriter writer = new BufferedWriter(new FileWriter(
					"stat.2000.result", true));
			File f = new File(apk);
			String fName = f.getName();
			String r = String.format("%s\t%d\t%d\n", fName,
					getNConstInLayout(), getNConstInCode());
			writer.write(r);
			writer.flush();
			writer.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		append = true;
	}
}
