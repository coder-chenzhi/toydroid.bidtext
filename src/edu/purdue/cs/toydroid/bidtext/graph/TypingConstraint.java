package edu.purdue.cs.toydroid.bidtext.graph;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.ibm.wala.ipa.slicer.Statement;

public class TypingConstraint {
	public static final int EQ = 0x8;
	public static final int GE = 0xC;
	public static final int GE_ASSIGN = 0x10;
	public static final int GE_UNIDIR = 0x14;// uni-directional for certain APIs
												// (no backward propagation)
	public static final int GE_APPEND = 0x18;

	public int lhs, rhs, sym;

	private List<Statement> path;// propagation path

	public TypingConstraint(int l, int s, int r) {
		lhs = l;
		sym = s;
		rhs = r;
		path = new LinkedList<Statement>();
	}

	public boolean addPath(Statement stmt) {
		return path.add(stmt);
	}

	public Iterator<Statement> iteratePath() {
		return path.iterator();
	}

	public List<Statement> getPath() {
		return path;
	}

	public boolean equals(Object o) {
		if (o instanceof TypingConstraint) {
			TypingConstraint c = (TypingConstraint) o;
			if (c.sym == sym && c.lhs == lhs && c.rhs == rhs) {
				return true;
			}
		}
		return false;
	}

	public int hashCode() {
		return lhs * 65537 + rhs * 129 + sym;
	}
}
