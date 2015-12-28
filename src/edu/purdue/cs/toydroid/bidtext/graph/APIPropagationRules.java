package edu.purdue.cs.toydroid.bidtext.graph;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class APIPropagationRules {

	private final static String RuleFile = "dat/APIRules.txt";
	private final static Pattern pattern = Pattern.compile("^(-?\\d+)([<>]*=[<>]*)(-?\\d+)$");
	private static boolean ruleCollected = false;
	private static Map<String, String> sig2rules;
	public final static int NOTHING = 0;
	public final static int LEFT_PROP = 1;// <=
	public final static int RIGHT_PROP = 2;// =>
	public final static int DUAL_PROP = 3;// <=>

	public static String getRule(String sig) {
		collectRules();
		return sig2rules.get(sig);
	}

	private static void collectRules() {
		if (ruleCollected) {
			return;
		}
		ruleCollected = true;
		sig2rules = new HashMap<String, String>();
		BufferedReader reader = null;
		try {
			String line = null;
			reader = new BufferedReader(new FileReader(RuleFile));
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}
				String[] parts = line.split(" ");
				if (parts.length < 3) {
					continue;
				}
				sig2rules.put(parts[1], parts[2]);
			}
		} catch (Exception e) {

		}
	}

	public static void parseRule(String rule, int[] output) {
		Matcher m = pattern.matcher(rule);
		if (m.find() && m.groupCount() == 3) {
			String left = m.group(1);
			String op = m.group(2);
			String right = m.group(3);
			output[0] = Integer.parseInt(left);
			output[2] = Integer.parseInt(right);
			if ("<=".equals(op)) {
				output[1] = LEFT_PROP;
			} else if ("=>".equals(op)) {
				output[1] = RIGHT_PROP;
			} else if ("<=>".equals(op)) {
				output[1] = DUAL_PROP;
			} else {
				output[1] = NOTHING;
			}
		}
	}
}
