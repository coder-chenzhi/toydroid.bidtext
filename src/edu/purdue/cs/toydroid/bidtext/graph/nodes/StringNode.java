package edu.purdue.cs.toydroid.bidtext.graph.nodes;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.ibm.wala.ipa.slicer.Statement;

public class StringNode {

	private String string;
	private List<Statement> fPath, bPath;

	public StringNode(String s) {
		string = s;
		fPath = new LinkedList<Statement>();
		bPath = new LinkedList<Statement>();
	}

	public String value() {
		return string;
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
		if (obj instanceof StringNode) {
			StringNode that = (StringNode) obj;
			if (string != null && string.equals(that.string)
					&& fPath.equals(that.fPath) && bPath.equals(that.bPath)) {
				return true;
			}
		}
		return false;
	}

	public int hashCode() {
		return string != null ? (string.hashCode() + fPath.hashCode() * 7 + bPath.hashCode() * 11)
				: (fPath.hashCode() + bPath.hashCode() * 7);
	}
}
