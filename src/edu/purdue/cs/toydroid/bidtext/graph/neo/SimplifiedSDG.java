package edu.purdue.cs.toydroid.bidtext.graph.neo;

import com.ibm.wala.util.graph.AbstractNumberedGraph;
import com.ibm.wala.util.graph.NumberedEdgeManager;
import com.ibm.wala.util.graph.NumberedNodeManager;
import com.ibm.wala.util.graph.impl.SlowNumberedNodeManager;
import com.ibm.wala.util.graph.impl.SparseNumberedEdgeManager;

public class SimplifiedSDG<T> extends AbstractNumberedGraph<T> {
	private NumberedNodeManager<T> nodeMgr = new SlowNumberedNodeManager<T>();
	private NumberedEdgeManager<T> edgeMgr = new SparseNumberedEdgeManager<T>(
			nodeMgr);

	@Override
	protected NumberedNodeManager<T> getNodeManager() {
		return nodeMgr;
	}

	@Override
	protected NumberedEdgeManager<T> getEdgeManager() {
		return edgeMgr;
	}
}
