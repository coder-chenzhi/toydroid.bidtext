package edu.purdue.cs.toydroid.bidtext.graph.nodes;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.ibm.wala.ipa.slicer.Statement;

public class ConstantNode {

	private Object constant;
	private List<Statement> fPath, bPath;

	public ConstantNode(Object o) {
		constant = o;

		fPath = new LinkedList<Statement>();
		bPath = new LinkedList<Statement>();
	}

	public Object value() {
		return constant;
	}

	public boolean addForwardPath(Statement p) {
		return fPath.add(p);
	}

	public boolean addBackwardPath(Statement p) {
		return bPath.add(p);
	}

	public Iterator<Statement> iterateForwardPath() {
		return fPath.iterator();
	}

	public Iterator<Statement> iterateBackwardPath() {
		return bPath.iterator();
	}

	public boolean equals(Object obj) {
		if (obj instanceof ConstantNode) {
			ConstantNode that = (ConstantNode) obj;
			if (constant != null && constant.equals(that.constant)
					&& fPath.equals(that.fPath) && bPath.equals(that.bPath)) {
				return true;
			}
		}
		return false;
	}

	public int hashCode() {
		return constant != null ? (constant.hashCode() + fPath.hashCode() * 7 + bPath.hashCode() * 11)
				: (fPath.hashCode() + bPath.hashCode() * 7);
	}
}
