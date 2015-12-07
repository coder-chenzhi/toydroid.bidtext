package edu.purdue.cs.toydroid.bidtext.graph.neo;

import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ibm.wala.ipa.slicer.HeapStatement;
import com.ibm.wala.ipa.slicer.NormalReturnCaller;
import com.ibm.wala.ipa.slicer.ParamCaller;
import com.ibm.wala.ipa.slicer.PhiStatement;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.util.graph.AbstractNumberedGraph;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.NumberedEdgeManager;
import com.ibm.wala.util.graph.NumberedNodeManager;
import com.ibm.wala.util.graph.impl.SlowNumberedNodeManager;
import com.ibm.wala.util.graph.impl.SparseNumberedEdgeManager;

public class SimplifiedSDG extends AbstractNumberedGraph<Statement> {
	private static Logger logger = LogManager.getLogger(SimplifiedSDG.class);

	private NumberedNodeManager<Statement> nodeMgr = new SlowNumberedNodeManager<Statement>();
	private NumberedEdgeManager<Statement> edgeMgr = new SparseNumberedEdgeManager<Statement>(
			nodeMgr);

	@Override
	protected NumberedNodeManager<Statement> getNodeManager() {
		return nodeMgr;
	}

	@Override
	protected NumberedEdgeManager<Statement> getEdgeManager() {
		return edgeMgr;
	}

	public static SimplifiedSDG simplify(Graph<Statement> oldSDG, SDGCache cache) {
		SimplifiedSDG newSDG = new SimplifiedSDG();
		Iterator<Statement> iter = cache.iterateCachedStmt();
		while (iter.hasNext()) {
			Statement stmt = iter.next();
			newSDG.simplifyStmt(oldSDG, cache, stmt);
		}
		int num = newSDG.getNumberOfNodes();
		if (num == 0) {
			newSDG = null;
		}
		logger.info("SDG size after simplifying: {}", newSDG == null ? 0 : num);
		return newSDG;
	}

	public void simplifyStmt(Graph<Statement> oldSDG, SDGCache cache,
			Statement stmt) {
		// add STMT and correlated stmts to new SDG
		simplifyByBFS(oldSDG, cache, stmt);
		// handle (potential) disconnected API calls
		Statement.Kind k = stmt.getKind();
		if ((k == Statement.Kind.PARAM_CALLER && 0 == oldSDG.getSuccNodeCount(stmt))
				|| (k == Statement.Kind.NORMAL_RET_CALLER && 0 == oldSDG.getPredNodeCount(stmt))) {
			List<Statement> correlated = cache.getCache(stmt);
			if (correlated != null) {
				while (!correlated.isEmpty()) {
					simplifyStmt(oldSDG, cache, correlated.remove(0));
				}
			}
		}
	}

	private void simplifyByBFS(Graph<Statement> oldSDG, SDGCache cache,
			Statement stmt) {
		List<Statement> worklist = new LinkedList<Statement>();
		worklist.add(stmt);
		while (!worklist.isEmpty()) {
			Statement statement = worklist.remove(0);
			if (this.containsNode(statement)) {
				continue;
			}
			this.addNode(statement);
			// succ
			Iterator<Statement> iter = oldSDG.getSuccNodes(statement);
			while (iter.hasNext()) {
				Statement succ = iter.next();
				if (succ instanceof HeapStatement) {
					skipHeapStmt(oldSDG, cache, statement, succ, worklist, true);
				} else {
					if (!this.containsNode(succ)) {
						this.addNode(succ);
						worklist.add(succ);
					}
					this.addEdge(statement, succ);
				}
			}
			// pred
			iter = oldSDG.getPredNodes(statement);
			while (iter.hasNext()) {
				Statement pred = iter.next();
				if (pred instanceof HeapStatement) {
					skipHeapStmt(oldSDG, cache, statement, pred, worklist,
							false);
				} else {
					if (!this.containsNode(pred)) {
						this.addNode(pred);
						worklist.add(pred);
					}
					this.addEdge(pred, statement);
				}
			}
		}
	}

	private void skipHeapStmt(Graph<Statement> oldSDG, SDGCache cache,
			Statement src, Statement heapStmt, List<Statement> worklist,
			boolean forward) {
		List<Statement> heapWorklist = new LinkedList<Statement>();
		Set<Statement> visited = new HashSet<Statement>();
		heapWorklist.add(heapStmt);
		visited.add(heapStmt);
		while (!heapWorklist.isEmpty()) {
			Statement stmt = heapWorklist.remove(0);
			Iterator<Statement> iter;
			if (forward) {
				iter = oldSDG.getSuccNodes(stmt);
			} else {
				iter = oldSDG.getPredNodes(stmt);
			}
			while (iter.hasNext()) {
				stmt = iter.next();
				if (stmt instanceof HeapStatement) {
					if (!visited.contains(stmt)) {
						heapWorklist.add(stmt);
						visited.add(stmt);
					}
				} else {
					if (!this.containsNode(stmt)) {
						this.addNode(stmt);
						worklist.add(stmt);
					}
					if (forward) {
						this.addEdge(src, stmt);
					} else {
						this.addEdge(stmt, src);
					}
				}
			}
		}

	}
}
