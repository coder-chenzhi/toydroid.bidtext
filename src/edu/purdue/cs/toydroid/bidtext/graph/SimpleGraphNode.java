package edu.purdue.cs.toydroid.bidtext.graph;

public class SimpleGraphNode {
	public int node;

	public SimpleGraphNode(int n) {
		node = n;
	}

	public boolean equals(Object obj) {
		if (obj instanceof SimpleGraphNode) {
			return node == ((SimpleGraphNode) obj).node;
		}
		return false;
	}

	public int hashCode() {
		return node;
	}

	public static SimpleGraphNode make(int n) {
		return new SimpleGraphNode(n);
	}
}
