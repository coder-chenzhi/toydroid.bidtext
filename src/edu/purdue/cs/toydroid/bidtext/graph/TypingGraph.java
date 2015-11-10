package edu.purdue.cs.toydroid.bidtext.graph;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.util.graph.impl.DelegatingNumberedNodeManager;

public class TypingGraph {
	private static Logger logger = LogManager.getLogger(TypingGraph.class);

	public Entrypoint entry;
	private Map<CGNode, TypingSubGraph> subGraphs;
	private Set<Integer> possibleExternalInput;
	private Map<String, Set<TypingNode>> fieldIncoming; // possible
														// incoming
														// fields
	private Map<String, Set<TypingNode>> fieldOutgoing; // possible
														// outgoing
														// fields
	private final DelegatingNumberedNodeManager<TypingNode> nodeManager;
	private Map<SimpleGraphNode, TypingRecord> node2Typing;

	public TypingGraph(Entrypoint e) {
		entry = e;
		subGraphs = new HashMap<CGNode, TypingSubGraph>();
		node2Typing = new HashMap<SimpleGraphNode, TypingRecord>();
		nodeManager = new DelegatingNumberedNodeManager<TypingNode>();
	}

	public void updateFieldTypingRecords() {
		if (fieldIncoming != null) {
			Set<Map.Entry<String, Set<TypingNode>>> s = fieldIncoming.entrySet();
			for (Map.Entry<String, Set<TypingNode>> e : s) {
				Set<TypingNode> v = e.getValue();
				for (TypingNode t : v) {
					TypingRecord rec = getTypingRecord(t.getGraphNodeId());
					if (rec != null) {
						rec.addInputField(t.getGraphNodeId());
					}
				}
			}
		}
		if (fieldOutgoing != null) {
			Set<Map.Entry<String, Set<TypingNode>>> s = fieldOutgoing.entrySet();
			for (Map.Entry<String, Set<TypingNode>> e : s) {
				Set<TypingNode> v = e.getValue();
				for (TypingNode t : v) {
					TypingRecord rec = getTypingRecord(t.getGraphNodeId());
					if (rec != null) {
						rec.addOutputField(t.getGraphNodeId());
					}
				}
			}
		}
	}

	public TypingSubGraph findOrCreateSubGraph(CGNode node) {
		TypingSubGraph sg = subGraphs.get(node);
		if (sg == null) {
			sg = new TypingSubGraph(node, this);
			subGraphs.put(node, sg);
		}
		return sg;
	}

	public Iterator<CGNode> iterateAllCGNodes() {
		return subGraphs.keySet().iterator();
	}

	public void addNode(TypingNode node) {
		nodeManager.addNode(node);
	}

	public TypingNode getNode(int n) {
		return nodeManager.getNode(n);
	}

	public Iterator<TypingNode> iterateNodes() {
		return nodeManager.iterator();
	}

	public Iterator<Map.Entry<SimpleGraphNode, TypingRecord>> iterateRecords() {
		return node2Typing.entrySet().iterator();
	}

	public void mergeClass(TypingNode node1, TypingNode node2) {

	}

	public TypingRecord findOrCreateTypingRecord(int nodeId) {
		SimpleGraphNode n = SimpleGraphNode.make(nodeId);
		TypingRecord r = node2Typing.get(n);
		if (r == null) {
			r = new TypingRecord(nodeId);
			node2Typing.put(n, r);
		}
		return r;
	}

	public TypingRecord getTypingRecord(int nodeId) {
		SimpleGraphNode n = SimpleGraphNode.make(nodeId);
		return node2Typing.get(n);
	}

	public void setTypingRecord(int nodeId, TypingRecord rec) {
		SimpleGraphNode n = SimpleGraphNode.make(nodeId);
		node2Typing.put(n, rec);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof TypingGraph) {
			TypingGraph g = (TypingGraph) obj;
			return entry.equals(g.entry);
		}
		return false;
	}

	/**
	 * Record possible incoming fields based on existing possible external
	 * input. It is called after the whole TypingGraph is built for an
	 * entrypoint.
	 */
	public void collectIncomingFields() {
		if (null != possibleExternalInput) {
			for (Integer iObj : possibleExternalInput) {
				TypingNode node = getNode(iObj.intValue());
				collectIncomingField(node);
			}
		}
	}

	private void collectIncomingField(TypingNode node) {
		if (null == fieldIncoming) {
			fieldIncoming = new HashMap<String, Set<TypingNode>>();
		}
		FieldReference ref = node.fieldRef;
		String sig = ref.getSignature();
		Set<TypingNode> nodeSet = fieldIncoming.get(sig);
		if (nodeSet == null) {
			nodeSet = new HashSet<TypingNode>();
			fieldIncoming.put(sig, nodeSet);
		}
		nodeSet.add(node);
	}

	public void collectOutgoingField(TypingNode node) {
		if (null == fieldOutgoing) {
			fieldOutgoing = new HashMap<String, Set<TypingNode>>();
		}
		FieldReference ref = node.fieldRef;
		String sig = ref.getSignature();
		Set<TypingNode> nodeSet = fieldOutgoing.get(sig);
		if (nodeSet == null) {
			nodeSet = new HashSet<TypingNode>();
			fieldOutgoing.put(sig, nodeSet);
		}
		nodeSet.add(node);
	}

	public Iterator<TypingNode> iterateAllOutgoingFields(String sig) {
		Set<TypingNode> nodeSet = null;
		if (fieldOutgoing == null || (nodeSet = fieldOutgoing.get(sig)) == null) {
			return Collections.emptyIterator();
		}
		return nodeSet.iterator();
	}

	public Iterator<TypingNode> iterateAllIncomingFields(String sig) {
		Set<TypingNode> nodeSet = null;
		if (fieldIncoming == null || (nodeSet = fieldIncoming.get(sig)) == null) {
			return Collections.EMPTY_SET.iterator();
		}
		return nodeSet.iterator();
	}

	public void setPossibleExternalInput(int nodeId) {
		if (null == possibleExternalInput) {
			possibleExternalInput = new HashSet<Integer>();
		}
		possibleExternalInput.add(nodeId);
	}

	public void unsetPossibleExternalInput(int nodeId) {
		if (null != possibleExternalInput) {
			possibleExternalInput.remove(nodeId);
		}
	}

	public boolean possibleExternalInput(TypingNode node) {
		if (!node.isField())
			return false;
		return possibleExternalInput(node.getGraphNodeId());
	}

	public boolean possibleExternalInput(int nodeId) {
		if (null == possibleExternalInput) {
			return false;
		}
		return possibleExternalInput.contains(nodeId);
	}

	public boolean possibleExternalOutput(TypingNode node) {
		if (!node.isField()) {
			return false;
		}
		return fieldOutgoing.containsKey(node.fieldRef.getSignature());
	}

	public Set<TypingNode> getTypingClass(TypingNode node) {
		return null;
	}
}
