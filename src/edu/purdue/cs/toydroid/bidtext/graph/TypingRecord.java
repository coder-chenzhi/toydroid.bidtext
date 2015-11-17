package edu.purdue.cs.toydroid.bidtext.graph;

import java.util.*;

import com.ibm.wala.ipa.slicer.Statement;

public class TypingRecord {
	public static final String APPEND_PREFIX = "{[<";
	public static final String APPEND_POSTFIX = ">]}";
	public static final String APPEND_VAR_PREFIX = "+*^";
	public static final String APPEND_VAR_POSTFIX = "^*+";
	public int initialId;
	private Set<String> typingTexts;
	private Set<Object> typingConstants;
	private Set<SimpleGraphNode> inputFields;
	private Set<SimpleGraphNode> outputFields;
	private Set<TypingConstraint> forwardConstraints;
	private Set<TypingConstraint> backwardConstraints;
	private Set<StringBuilder> appendResults;
	// in some instructions, LHS and RHS use the same typing record. 
	// then we need to record such stmts for path tracking. 
	private List<Statement> involvedStmts;

	public TypingRecord(int id) {
		initialId = id;
		typingTexts = new HashSet<String>();
		typingConstants = new HashSet<Object>();
		inputFields = new HashSet<SimpleGraphNode>();
		outputFields = new HashSet<SimpleGraphNode>();
		forwardConstraints = new HashSet<TypingConstraint>();
		backwardConstraints = new HashSet<TypingConstraint>();
		appendResults = new HashSet<StringBuilder>();
	}

	// True - changed; False - unchanged.
	public boolean merge(TypingRecord rec) {
		int tSize = typingTexts.size();
		int cSize = typingConstants.size();
		int ifSize = inputFields.size();
		int ofSize = outputFields.size();
		typingTexts.addAll(rec.typingTexts);
		typingConstants.addAll(rec.typingConstants);
		inputFields.addAll(rec.inputFields);
		outputFields.addAll(rec.outputFields);
		if (tSize != typingTexts.size() || cSize != typingConstants.size()
				|| ifSize != inputFields.size()
				|| ofSize != outputFields.size()) {
			return true;
		}
		return false;
	}

	public boolean mergeIfEmptyTexts(TypingRecord rec) {
		if (typingTexts.isEmpty()) {
			return merge(rec);
		}
		return false;
	}
	
	public boolean addInvolvedStmt(Statement stmt) {
		if (involvedStmts == null) {
			involvedStmts = new LinkedList<Statement>();
		}
		return involvedStmts.add(stmt);
	}
	
	/**
	 * Return might be null. Carefully check the return value at call sites.
	 */
	public List<Statement> getInvolvedStmts() {
		return involvedStmts;
	}
	
	public Iterator<String> iteratorAppendResults() {
		return new Iterator<String>(){
			Iterator<StringBuilder> iter;
			{
				iter = appendResults.iterator();
			}
			@Override
			public boolean hasNext() {
				return iter.hasNext();
			}

			@Override
			public String next() {
				return iter.next().toString();
			}

			@Override
			public void remove() {
				
			}
			
		};
	}

	public void addTypingAppend(TypingRecord rec) {
		for (StringBuilder b : rec.appendResults) {
			StringBuilder builder = new StringBuilder();
			builder.append(b.toString());
			appendResults.add(builder);
		}
	}

	public void addTypingAppend(String str) {
		if (appendResults.isEmpty()) {
			appendResults.add(new StringBuilder());
		}
		for (StringBuilder builder : appendResults) {
			boolean emptyBuilder = false;
			if (builder.length() == 0) {
				emptyBuilder = true;
				//builder.append(APPEND_PREFIX);
			}

			if (emptyBuilder) {
				builder.append(str);
				//builder.append(APPEND_POSTFIX);
			} else {
				//builder.insert(builder.length() - APPEND_POSTFIX.length(), str);
				builder.append(str);
			}
		}
	}

	public void addTypingAppend(int nodeId) {
		if (appendResults.isEmpty()) {
			appendResults.add(new StringBuilder());
		}
		for (StringBuilder builder : appendResults) {
			boolean emptyBuilder = false;
			if (builder.length() == 0) {
				emptyBuilder = true;
				//builder.append(APPEND_PREFIX);
			}

			if (emptyBuilder) {
				builder.append(APPEND_VAR_PREFIX);
				builder.append(nodeId);
				builder.append(APPEND_VAR_POSTFIX);
				//builder.append(APPEND_POSTFIX);
			} else {
				String str = String.format("%s%d%s", APPEND_VAR_PREFIX,
						nodeId, APPEND_VAR_POSTFIX);
				//builder.insert(builder.length() - APPEND_POSTFIX.length(), str);
				builder.append(str);
			}
		}
	}

	public boolean addTypingText(String s) {
		return typingTexts.add(s);
	}

	public boolean addTypingConstant(Object i) {
		return typingConstants.add(i);
	}

	public boolean addInputField(int nodeId) {
		return inputFields.add(new SimpleGraphNode(nodeId));
	}

	public boolean addOutputField(int nodeId) {
		return outputFields.add(new SimpleGraphNode(nodeId));
	}

	public boolean addForwardTypingConstraint(TypingConstraint c) {
		if (c.rhs != initialId) {
			c.rhs = initialId;
		}
		return forwardConstraints.add(c);
	}

	public boolean addBackwardTypingConstraint(TypingConstraint c) {
		if (c.lhs != initialId)
			c.lhs = initialId;
		return backwardConstraints.add(c);
	}

	public boolean hasConstants() {
		return !typingTexts.isEmpty() || !typingConstants.isEmpty();
	}

	public boolean hasExternalFields() {
		return !inputFields.isEmpty() || !outputFields.isEmpty();
	}

	public boolean hasForwardConstraints() {
		return !forwardConstraints.isEmpty();
	}

	public boolean hasBackwardConstraints() {
		return !backwardConstraints.isEmpty();
	}

	public Set<TypingConstraint> getForwardTypingConstraints() {
		return forwardConstraints;
	}

	public Set<TypingConstraint> getBackwardTypingConstraints() {
		return backwardConstraints;
	}

	public Set<String> getTypingTexts() {
		return typingTexts;
	}

	public Set<Object> getTypingConstants() {
		return typingConstants;
	}

	public Set<SimpleGraphNode> getInputFields() {
		return inputFields;
	}

	public Set<SimpleGraphNode> getOutputFields() {
		return outputFields;
	}
}
