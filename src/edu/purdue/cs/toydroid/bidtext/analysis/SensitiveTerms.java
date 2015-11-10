package edu.purdue.cs.toydroid.bidtext.analysis;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SensitiveTerms {
	private static Logger logger = LogManager.getLogger(SensitiveTerms.class);

	private final static String termFile = "dat/SensitiveTerms.txt";
	private final static Map<String, Pattern> terms = new HashMap<String, Pattern>();
	private static boolean collected = false;

	private static void collectTerms() {
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(
					new FileInputStream(termFile), "UTF-8"));
			String line = null;
			String tag = null, regex = null;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.isEmpty() || line.startsWith("#")) {
					continue;
				}
				if (tag == null) {
					tag = line;
				} else if (regex == null) {
					regex = line;
				} else {
					logger.warn("Ignored Line: {}", line);
				}
				if (tag != null && regex != null) {
					Pattern p = Pattern.compile(regex);
					terms.put(tag, p);
					tag = null;
					regex = null;
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (Exception e) {

				}
			}
		}
		collected = true;
	}

	public static Iterator<Map.Entry<String, Pattern>> iterateSensitiveTerms() {
		if (!collected) {
			collectTerms();
		}
		return terms.entrySet().iterator();
	}
	
	public static void main(String [] args) {
		Iterator<Map.Entry<String, Pattern>> iter = iterateSensitiveTerms();
		while (iter.hasNext()) {
			Map.Entry<String, Pattern> entry = iter.next();
			System.out.println(entry.getKey() + "\n" + entry.getValue().pattern() + "\n");
		}
	}
}
