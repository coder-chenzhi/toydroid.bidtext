package edu.purdue.cs.toydroid.bidtext.graph.neo;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

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
		if (newSDG.getNumberOfNodes() == 0) {
			newSDG = null;
		}
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
				if (!this.containsNode(succ)) {
					this.addNode(succ);
					worklist.add(succ);
				}
				this.addEdge(statement, succ);
			}
			// pred
			iter = oldSDG.getPredNodes(statement);
			while (iter.hasNext()) {
				Statement pred = iter.next();
				if (!this.containsNode(pred)) {
					this.addNode(pred);
					worklist.add(pred);
				}
				this.addEdge(pred, statement);
			}
		}
	}
}
