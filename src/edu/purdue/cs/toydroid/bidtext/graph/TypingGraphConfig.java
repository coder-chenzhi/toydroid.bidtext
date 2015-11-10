package edu.purdue.cs.toydroid.bidtext.graph;

import java.util.HashMap;
import java.util.Map;

public class TypingGraphConfig {
	
	public final static String SEPERATOR = ",";
	/**
	 * signature -> X, where X has format "x1,x2,..." denoting all possible
	 * parameter locations.
	 */
	private static Map<String, String> sig2PotentialGString;

	static {
		sig2PotentialGString = new HashMap<String, String>();
		sig2PotentialGString.put(
				"android.widget.Toast.makeText(Landroid/content/Context;Ljava/lang/CharSequence;I)Landroid/widget/Toast;",
				"1");
		sig2PotentialGString.put(
				"java.io.PrintStream.println(Ljava/lang/String;)V", "1");
	}
	
	public static String getPotentialGStringLoc(String sig) {
		return sig2PotentialGString.get(sig);
	}
}
