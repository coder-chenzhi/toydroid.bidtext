package edu.purdue.cs.toydroid.bidtext.graph.neo;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.slicer.NormalReturnCaller;
import com.ibm.wala.ipa.slicer.ParamCaller;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;

public class SDGCache {
	private static Logger logger = LogManager.getLogger(SDGCache.class);
	private Entrypoint entry;
	private Map<CGNode, SDGSubCache> cg2cache;

	public SDGCache(Entrypoint e) {
		entry = e;
		cg2cache = new HashMap<CGNode, SDGSubCache>();
	}

	public Entrypoint entrypoint() {
		return entry;
	}

	/**
	 * 
	 * @param stmt
	 *            An API call statement, either ParamCaller or
	 *            NormalReturnCaller.
	 */
	public void addCache(Statement stmt) {
		Statement.Kind k = stmt.getKind();
		CGNode cgn = stmt.getNode();
		if (k == Statement.Kind.PARAM_CALLER) {
			ParamCaller pcaller = (ParamCaller) stmt;
			int value = pcaller.getValueNumber();
			addCache(cgn, value, stmt);
		} else if (k == Statement.Kind.NORMAL_RET_CALLER) {
			NormalReturnCaller nrc = (NormalReturnCaller) stmt;
			SSAAbstractInvokeInstruction inst = nrc.getInstruction();
			int nUses = inst.getNumberOfUses();
			for (int i = 0; i < nUses; i++) {
				int use = inst.getUse(i);
				addCache(cgn, use, stmt);
			}
		}
	}

	private void addCache(CGNode cgn, int value, Statement stmt) {
		SDGSubCache subCache = cg2cache.get(cgn);
		if (subCache == null) {
			subCache = new SDGSubCache(cgn);
			cg2cache.put(cgn, subCache);
		}
		subCache.addCache(value, stmt);
	}

	class SDGSubCache {
		private CGNode cgNode;
		private Map<Integer, LinkedList<Statement>> var2use;
		private Map<Integer, Statement> var2def;

		public SDGSubCache(CGNode cgn) {
			cgNode = cgn;
			var2use = new HashMap<Integer, LinkedList<Statement>>();
			var2def = new HashMap<Integer, Statement>();
		}

		public CGNode getNode() {
			return cgNode;
		}

		public void addCache(int value, Statement stmt) {
			Integer iObj = Integer.valueOf(value);
			LinkedList<Statement> uses = var2use.get(iObj);
			if (uses == null) {
				uses = new LinkedList<Statement>();
				var2use.put(iObj, uses);
			}
			uses.add(stmt);
		}
	}
}
