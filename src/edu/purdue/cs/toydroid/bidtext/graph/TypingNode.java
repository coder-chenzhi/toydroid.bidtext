package edu.purdue.cs.toydroid.bidtext.graph;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.util.graph.impl.NodeWithNumber;

public class TypingNode extends NodeWithNumber {
	public static final int SIMPLE = 0x0;
	public static final int STRING = 0x1;
	public static final int CONSTANT = 0x2;
	public static final int PARAM = 0x4;
	public static final int FIELD = 0x8;
	public static final int IFIELD = 0x10; // instance field
	public static final int SFIELD = 0x20; // static field
	public static final int FAKE_STRING = 0x30;
	private boolean isSpecial = false;

	public int kind;
	public CGNode cgNode; // the enclosing CGNode
	public int value; // value number of variable. for fieldRef, a "fake"
						// value number is assigned for easy access in map
	public int obj; // obj value in instance field
	public FieldReference fieldRef;

	private TypingNode(CGNode node, int v, int k) {
		cgNode = node;
		value = v;
		kind = k;
	}

	public TypingNode(CGNode node, int v) {
		this(node, v, SIMPLE);
	}

	// static field
	public TypingNode(CGNode node, int v, FieldReference f) {
		this(node, v, FIELD | SFIELD);
		fieldRef = f;
	}

	// instance field
	public TypingNode(CGNode node, int fv, int v, FieldReference f) {
		this(node, fv, FIELD | IFIELD);
		obj = v;
		fieldRef = f;
	}

	public boolean isField() {
		return FIELD == (FIELD & kind);
	}

	public boolean isStaticField() {
		return SFIELD == (SFIELD & kind);
	}

	public boolean isInstanceField() {
		return IFIELD == (IFIELD & kind);
	}

	public boolean isConstant() {
		return (CONSTANT == (CONSTANT & kind));
	}

	public boolean isString() {
		return (STRING == (STRING & kind));
	}

	public boolean isFakeString() {
		return (FAKE_STRING == (FAKE_STRING & kind));
	}

	public void markStringKind() {
		kind = CONSTANT | STRING;
	}

	public void markConstantKind() {
		kind = CONSTANT;
	}

	public void markFakeStringKind() {
		kind = CONSTANT | FAKE_STRING;
	}

	public String toString() {
		StringBuilder builder = new StringBuilder("[ID: ");
		builder.append(getGraphNodeId());
		builder.append("]\n<");
		builder.append(cgNode.getMethod().getSignature());
		builder.append(">\n");
		builder.append("v");
		builder.append(value);
		if (isField()) {
			builder.append(": ");
			if (isStaticField()) {
				builder.append(fieldRef.toString());
			} else {
				builder.append('v');
				builder.append(obj);
				builder.append('.');
				builder.append(fieldRef.getName().toString());
			}
		}

		return builder.toString();
	}

	public boolean equals(Object obj) {
		if (obj instanceof TypingNode) {
			TypingNode tgn = (TypingNode) obj;
			return (tgn.value == this.value && tgn.cgNode.equals(this.cgNode));
		}
		return false;
	}

	public void changeKind(int k) {
		kind = k;
	}

	public int hashCode() {
		return value + cgNode.hashCode() * 79;
	}

	public void joke() {
		// do nothing
	}

	public void markSpecial() {
		isSpecial = true;
	}

	public boolean isSpecialNode() {
		return isSpecial;
	}
}
