package edu.purdue.cs.toydroid.bidtext.graph.nodes;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.ibm.wala.ipa.slicer.Statement;

public class FieldNode {

	private int field;
	private List<Statement> fPath, bPath;

	public FieldNode(int s) {
		field = s;
		fPath = new LinkedList<Statement>();
		bPath = new LinkedList<Statement>();
	}

	public int value() {
		return field;
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
		if (obj instanceof FieldNode) {
			FieldNode that = (FieldNode) obj;
			return field == that.field;
		}
		return false;
	}

	public int hashCode() {
		return field;
	}
}
