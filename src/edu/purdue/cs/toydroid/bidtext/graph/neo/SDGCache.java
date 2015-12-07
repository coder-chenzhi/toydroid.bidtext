package edu.purdue.cs.toydroid.bidtext.graph.neo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.slicer.NormalReturnCaller;
import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.ParamCaller;
import com.ibm.wala.ipa.slicer.PhiStatement;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAArrayLoadInstruction;
import com.ibm.wala.ssa.SSAArrayStoreInstruction;
import com.ibm.wala.ssa.SSAFieldAccessInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.util.graph.Graph;

import edu.purdue.cs.toydroid.utils.WalaUtil;

public class SDGCache {
	private static Logger logger = LogManager.getLogger(SDGCache.class);
	private Entrypoint entry;
	private Map<CGNode, SDGSubCache> cg2cache;
	private Set<Statement> cachedStmt;

	public SDGCache(Entrypoint e) {
		entry = e;
		cg2cache = new HashMap<CGNode, SDGSubCache>();
		cachedStmt = new HashSet<Statement>();
	}

	public Entrypoint entrypoint() {
		return entry;
	}

	public void buildCache(Graph<Statement> sdg, ClassHierarchy cha) {
		logger.info("Build SDG Cache...");
		for (Statement stmt : sdg) {
			Statement.Kind k = stmt.getKind();
			CGNode cgn = stmt.getNode();
			SSAInstruction inst;
			int nUses, idx;
			SymbolTable symTable;
			switch (k) {
				case PHI:
					PhiStatement phi = (PhiStatement) stmt;
					symTable = cgn.getIR().getSymbolTable();
					inst = phi.getPhi();
					nUses = inst.getNumberOfUses();
					for (idx = 0; idx < nUses; idx++) {
						int v = inst.getUse(idx);
						if (symTable.isConstant(v)
								&& !symTable.isNullConstant(v)) {
							cachedStmt.add(stmt);
							break;
						}
					}
					break;
				case NORMAL:
					NormalStatement nstmt = (NormalStatement) stmt;
					symTable = cgn.getIR().getSymbolTable();
					inst = nstmt.getInstruction();
					if (inst instanceof SSAFieldAccessInstruction) {
						cachedStmt.add(stmt);
					} else if (inst instanceof SSAArrayStoreInstruction) {
						SSAArrayStoreInstruction ainst = (SSAArrayStoreInstruction) inst;
						int v = ainst.getValue();
						if (symTable.isConstant(v)
								&& !symTable.isNullConstant(v)) {
							cachedStmt.add(stmt);
						}
					} else if (!(inst instanceof SSAArrayLoadInstruction)) {
						nUses = inst.getNumberOfUses();
						for (idx = 0; idx < nUses; idx++) {
							int v = inst.getUse(idx);
							if (symTable.isConstant(v)
									&& !symTable.isNullConstant(v)) {
								cachedStmt.add(stmt);
								break;
							}
						}
					}
					break;
				case PARAM_CALLER:
					ParamCaller pcaller = (ParamCaller) stmt;
					int value = pcaller.getValueNumber();
					if (0 == sdg.getSuccNodeCount(stmt)
							&& WalaUtil.isAPI(pcaller)) {
						addCache(cgn, value, stmt);
					}
					symTable = cgn.getIR().getSymbolTable();
					if (symTable.isConstant(value)
							&& !symTable.isNullConstant(value)) {
						cachedStmt.add(stmt);
					}
					break;
				case NORMAL_RET_CALLER:
					NormalReturnCaller nrc = (NormalReturnCaller) stmt;
					inst = nrc.getInstruction();
					nUses = inst.getNumberOfUses();
					if (0 == sdg.getPredNodeCount(stmt) && WalaUtil.isAPI(nrc)) {
						symTable = cgn.getIR().getSymbolTable();
						for (idx = 0; idx < nUses; idx++) {
							int use = inst.getUse(idx);
							addCache(cgn, use, stmt);
							if (symTable.isConstant(use)
									&& !symTable.isNullConstant(use)) {
								cachedStmt.add(stmt);
							}
						}
					}
					break;
				default:
					break;
			}
		}
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

	public List<Statement> getCache(Statement stmt) {
		Statement.Kind k = stmt.getKind();
		CGNode cgn = stmt.getNode();
		List<Statement> cache = null;
		if (k == Statement.Kind.PARAM_CALLER) {
			ParamCaller pcaller = (ParamCaller) stmt;
			SDGSubCache sc = cg2cache.get(cgn);
			if (sc != null) {
				int value = pcaller.getValueNumber();
				cache = new LinkedList<Statement>();
				sc.moveCacheTo(value, cache);
			}
		} else if (k == Statement.Kind.NORMAL_RET_CALLER) {
			NormalReturnCaller nrc = (NormalReturnCaller) stmt;
			SDGSubCache sc = cg2cache.get(cgn);
			if (sc != null) {
				SSAAbstractInvokeInstruction inst = nrc.getInstruction();
				int nUses = inst.getNumberOfUses();
				cache = new LinkedList<Statement>();
				for (int i = 0; i < nUses; i++) {
					int use = inst.getUse(i);
					sc.moveCacheTo(use, cache);
				}
			}
		}
		return cache;
	}

	public Iterator<Statement> iterateCachedStmt() {
		return cachedStmt.iterator();
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

		/**
		 * Move corresponding cache to the given LIST. No cache exists for the
		 * variable after this operation.
		 * 
		 * @param var
		 * @param list
		 *            Holding the return cache.
		 */
		public void moveCacheTo(int var, List<Statement> list) {
			Integer iObj = Integer.valueOf(var);
			LinkedList<Statement> cache = var2use.get(iObj);
			if (cache != null) {
				int n = cache.size();
				while (!cache.isEmpty()) {
					Statement stmt = cache.remove();
					list.add(stmt);
				}
				var2use.remove(iObj);
			}
		}
	}
}
