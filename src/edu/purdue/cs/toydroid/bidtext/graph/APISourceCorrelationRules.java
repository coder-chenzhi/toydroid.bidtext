package edu.purdue.cs.toydroid.bidtext.graph;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class APISourceCorrelationRules {
	private final static String RuleFile = "dat/TraditionalSources.txt";
	private static boolean ruleCollected = false;
	private static Map<String, String> sig2rules;

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
				if (parts.length < 2) {
					continue;
				}
				sig2rules.put(parts[0], parts[1]);
			}
		} catch (Exception e) {

		}
	}
}
