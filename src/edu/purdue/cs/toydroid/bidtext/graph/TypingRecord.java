package edu.purdue.cs.toydroid.bidtext.graph;

import java.util.*;

import com.ibm.wala.ipa.slicer.Statement;

public class TypingRecord {
	public static final String APPEND_PREFIX = "{[<";
	public static final String APPEND_POSTFIX = ">]}";
	public static final String APPEND_VAR_PREFIX = "+*^";
	public static final String APPEND_VAR_POSTFIX = "^*+";
	public int initialId;
	private Map<String, List<Statement>> typingTexts;
	private Set<Object> typingConstants;
	private Map<SimpleGraphNode, List<Statement>> inputFields;
	private Map<SimpleGraphNode, List<Statement>> outputFields;
	private Set<TypingConstraint> forwardConstraints;
	private Set<TypingConstraint> backwardConstraints;
	private Set<StringBuilder> appendResults;

	public TypingRecord(int id) {
		initialId = id;
		typingTexts = new HashMap<String, List<Statement>>();
		typingConstants = new HashSet<Object>();
		inputFields = new HashMap<SimpleGraphNode, List<Statement>>();
		outputFields = new HashMap<SimpleGraphNode, List<Statement>>();
		forwardConstraints = new HashSet<TypingConstraint>();
		backwardConstraints = new HashSet<TypingConstraint>();
		appendResults = new HashSet<StringBuilder>();
	}

	// True - changed; False - unchanged.
	@Deprecated
	public boolean merge(TypingRecord rec) {
		int tSize = typingTexts.size();
		int cSize = typingConstants.size();
		int ifSize = inputFields.size();
		int ofSize = outputFields.size();
		typingTexts.putAll(rec.typingTexts);
		typingConstants.addAll(rec.typingConstants);
		inputFields.putAll(rec.inputFields);
		outputFields.putAll(rec.outputFields);
		if (tSize != typingTexts.size() || cSize != typingConstants.size()
				|| ifSize != inputFields.size()
				|| ofSize != outputFields.size()) {
			return true;
		}
		return false;
	}

	/**
	 * Merge typings (and path) to current record.
	 * 
	 * @param rec
	 * @param path
	 * @return True - changed; False - unchanged.
	 */
	public boolean merge(TypingRecord rec, List<Statement> path) {
		Map<String, List<Statement>> localTexts = typingTexts;
		Map<SimpleGraphNode, List<Statement>> localInputs = inputFields;
		Map<SimpleGraphNode, List<Statement>> localOutputs = outputFields;
		int tSize = localTexts.size();
		int cSize = typingConstants.size();
		int ifSize = localInputs.size();
		int ofSize = localOutputs.size();
		Set<Map.Entry<String, List<Statement>>> set = rec.typingTexts.entrySet();
		for (Map.Entry<String, List<Statement>> entry : set) {
			String key = entry.getKey();
			if (!localTexts.containsKey(key)) {
				List<Statement> list = new LinkedList<Statement>();
				list.addAll(entry.getValue());
				list.addAll(path);
				localTexts.put(key, list);
			}
		}
		typingConstants.addAll(rec.typingConstants);
		// input fields
		Set<Map.Entry<SimpleGraphNode, List<Statement>>> fieldSet = localInputs.entrySet();
		for (Map.Entry<SimpleGraphNode, List<Statement>> entry : fieldSet) {
			SimpleGraphNode key = entry.getKey();
			if (!localInputs.containsKey(key)) {
				List<Statement> list = new LinkedList<Statement>();
				list.addAll(entry.getValue());
				list.addAll(path);
				localInputs.put(key, list);
			}
		}
		// output fields
		fieldSet = localOutputs.entrySet();
		for (Map.Entry<SimpleGraphNode, List<Statement>> entry : fieldSet) {
			SimpleGraphNode key = entry.getKey();
			if (!localOutputs.containsKey(key)) {
				List<Statement> list = new LinkedList<Statement>();
				list.addAll(entry.getValue());
				list.addAll(path);
				localOutputs.put(key, list);
			}
		}
		if (tSize != localTexts.size() || cSize != typingConstants.size()
				|| ifSize != localInputs.size()
				|| ofSize != localOutputs.size()) {
			return true;
		}
		return false;
	}

	public boolean mergeIfEmptyTexts(TypingRecord rec, List<Statement> path) {
		if (typingTexts.isEmpty()) {
			return merge(rec, path);
		}
		return false;
	}

	@Deprecated
	public boolean mergeIfEmptyTexts(TypingRecord rec) {
		if (typingTexts.isEmpty()) {
			return merge(rec);
		}
		return false;
	}

	public Iterator<String> iteratorAppendResults() {
		return new Iterator<String>() {
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
				// builder.append(APPEND_PREFIX);
			}

			if (emptyBuilder) {
				builder.append(str);
				// builder.append(APPEND_POSTFIX);
			} else {
				// builder.insert(builder.length() - APPEND_POSTFIX.length(),
				// str);
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
				// builder.append(APPEND_PREFIX);
			}

			if (emptyBuilder) {
				builder.append(APPEND_VAR_PREFIX);
				builder.append(nodeId);
				builder.append(APPEND_VAR_POSTFIX);
				// builder.append(APPEND_POSTFIX);
			} else {
				String str = String.format("%s%d%s", APPEND_VAR_PREFIX, nodeId,
						APPEND_VAR_POSTFIX);
				// builder.insert(builder.length() - APPEND_POSTFIX.length(),
				// str);
				builder.append(str);
			}
		}
	}

	public boolean addTypingText(String s) {
		Map<String, List<Statement>> m = typingTexts;
		if (!m.containsKey(s)) {
			List<Statement> l = new LinkedList<Statement>();
			m.put(s, l);
			return true;
		}
		return false;
	}

	public boolean addTypingConstant(Object i) {
		return typingConstants.add(i);
	}

	public boolean addInputField(int nodeId) {
		SimpleGraphNode sgn = SimpleGraphNode.make(nodeId);
		Map<SimpleGraphNode, List<Statement>> m = inputFields;
		if (!m.containsKey(sgn)) {
			List<Statement> l = new LinkedList<Statement>();
			m.put(sgn, l);
			return true;
		}
		return false;
	}

	public boolean addOutputField(int nodeId) {
		SimpleGraphNode sgn = SimpleGraphNode.make(nodeId);
		Map<SimpleGraphNode, List<Statement>> m = outputFields;
		if (!m.containsKey(sgn)) {
			List<Statement> l = new LinkedList<Statement>();
			m.put(sgn, l);
			return true;
		}
		return false;
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

	public Map<String, List<Statement>> getTypingTexts() {
		return typingTexts;
	}

	public Set<Object> getTypingConstants() {
		return typingConstants;
	}

	public Map<SimpleGraphNode, List<Statement>> getInputFields() {
		return inputFields;
	}

	public Map<SimpleGraphNode, List<Statement>> getOutputFields() {
		return outputFields;
	}
}
