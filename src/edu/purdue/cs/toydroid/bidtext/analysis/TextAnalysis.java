package edu.purdue.cs.toydroid.bidtext.analysis;

import java.io.StringReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ibm.wala.ipa.slicer.ParamCaller;
import com.ibm.wala.ipa.slicer.Statement;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeGraphNode;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.TypedDependency;

public class TextAnalysis {

	private static Logger logger = LogManager.getLogger(TextAnalysis.class);

	private final static String GRAMMAR = "edu/stanford/nlp/models/lexparser/englishPCFG.ser.gz";

	private final static Pattern keywordPattern = Pattern.compile("\\b(lat|lng|lon|latlng|latitude|longitude|imei|imsi|u(ser)?((\\s|_)?name|(\\s|_)?id)|e-?mail|pin(code|\\s(code|number|no|#))?|password|pwd|passwd)\\b");

	private static LexicalizedParser lexParser;
	public String tag;
	private StringBuilder tagBuilder;
	private boolean isGUIText;
	private Map<String, List<Statement>> text2Path;

	public TextAnalysis() {
		text2Path = new HashMap<String, List<Statement>>();
	}

	public static boolean maybeKeyword(String str) {
		if (str == null || str.isEmpty()) {
			return false;
		}
		if (str.equals("location") || str.equals("gps")
				|| str.equals("network") || str.equals("android_id")) {
			return true;
		}
		if (str.startsWith("android.")) {
			return false;
		}
		if (str.startsWith("http:") || str.startsWith("https:")
				|| str.startsWith("/")) {
			int idx = str.indexOf('?');
			if (idx > 0 && str.length() > idx) {
				String s = str.substring(idx + 1);
				if (keywordPattern.matcher(s).find()) {
					return true;
				}
			}
		} else if (!str.contains(" ")) {
			str = splitWord(str);
			if (keywordPattern.matcher(str).find()) {
				return true;
			}
		} else if (keywordPattern.matcher(str).find()) {
			return true;
		}
		return false;
	}

	public Map<String, List<Statement>> getText2Path() {
		return text2Path;
	}

	public String analyze(Map<String, List<Statement>> texts, boolean isGUIText) {
		this.isGUIText = isGUIText;
		tagBuilder = new StringBuilder();
		if (!isGUIText) {
			if (texts.containsKey("location")) {
				text2Path.put("location", texts.remove("location"));
				if (texts.containsKey("gps")) {
					text2Path.put("gps", texts.remove("gps"));
					record("location/gps");
				}
				if (texts.containsKey("network")) {
					text2Path.put("network", texts.remove("network"));
					record("location/network");
				}
			}
			if (texts.containsKey("latitude")) {
				text2Path.put("latitude", texts.remove("latitude"));
				record("latitude");
			}
			if (texts.containsKey("longitude")) {
				text2Path.put("longitude", texts.remove("longitude"));
				record("longitude");
			}
			if (texts.containsKey("lat")) {
				text2Path.put("lat", texts.remove("lat"));
				record("lat");
				if (texts.containsKey("lng")) {
					text2Path.put("lng", texts.remove("lng"));
					record("lng");
				} else if (texts.containsKey("long")) {
					text2Path.put("long", texts.remove("long"));
					record("long");
				} else if (texts.containsKey("lon")) {
					text2Path.put("lon", texts.remove("lon"));
					record("lon");
				}
			}
			if (texts.containsKey("android_id")) {
				List<Statement> p = texts.get("android_id");
				boolean skip = false;
				if (p != null && p.size() == 2) {
					Statement s = p.get(0);
					if (s.getKind() == Statement.Kind.PARAM_CALLER) {
						ParamCaller pc = (ParamCaller)s;
						if (pc.getInstruction().getDeclaredTarget().getSignature().startsWith("com.facebook.internal.Utility.logd")){
							skip = true;
						}
					}
				}
				if (!skip) {
				text2Path.put("android_id", texts.remove("android_id"));
				record("android_id");
				}
			}
		}
		List<String> f = purify(texts);
		// logger.debug("  {}", f.toString());
		check(f, texts);
		return tagBuilder.toString();
	}

	private String rebuildString(List<? extends HasWord> sentence) {
		StringBuilder builder = new StringBuilder();
		for (HasWord w : sentence) {
			builder.append(w.word());
			builder.append(' ');
		}
		return builder.toString().trim();
	}

	private void check(List<String> f, Map<String, List<Statement>> texts) {
		if (f.isEmpty())
			return;
		if (lexParser == null) {
			lexParser = LexicalizedParser.loadModel(GRAMMAR);
		}
		LexicalizedParser lp = lexParser;
		TreebankLanguagePack tlp = lp.getOp().langpack();
		GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory();
		for (String s : f) {
			String origStr = s;
			// Tokenizer<? extends HasWord> toke = tlp.getTokenizerFactory()
			// .getTokenizer(new StringReader(s));
			// List<? extends HasWord> sentence = toke.tokenize();
			DocumentPreprocessor tokenizer = new DocumentPreprocessor(
					new StringReader(s));
			for (List<? extends HasWord> sentence : tokenizer) {
				s = rebuildString(sentence);
				if (!containsKeywords(s.toLowerCase())) {
					continue;
				}
				Tree parse = lp.parse(sentence);
				// System.err.println(s);
				GrammaticalStructure gs = gsf.newGrammaticalStructure(parse);
				List<TypedDependency> tdl = gs.typedDependenciesCCprocessed();
				// System.out.println(tdl);
				// parse.pennPrint();
				Tree c = parse.firstChild();
				Label l = c.label();
				// System.err.println(c.getClass());
				// if not a sentence
				if (!l.value().equals("S")) {
					record(s);
					text2Path.put(origStr, texts.get(origStr));
				} else {
					int negIdx = 0;
					for (TypedDependency td : tdl) {
						if ("neg".equals(td.reln().toString())) {
							break;
						}
						negIdx++;
					}

					if (negIdx > 0 && negIdx < tdl.size()) {
						TypedDependency td = tdl.get(negIdx - 1);
						if (td.reln().toString().equals("aux")) {
							TreeGraphNode n = td.dep();
							String ns = n.label().value();
							if ("should".equals(ns) || "shall".equals(ns)
							/* || "could".equals(ns) || "can".equals(ns) */) {
								logger.info("    * Negation detected: <<{}>>",
										s);
							} else if ("do".equals(ns)
									&& (negIdx == 1 || (negIdx > 1 && !tdl.get(
											negIdx - 2)
											.reln()
											.toString()
											.equals("nsubj")))) {
								logger.info("    * Negation detected: <<{}>>",
										s);
							} else {
								record(s);
								text2Path.put(origStr, texts.get(origStr));
							}
						}
					} else {
						record(s);
						text2Path.put(origStr, texts.get(origStr));
					}
				}
			}
		}
		// System.err.println(tagBuilder.toString());
	}

	private void record(String str) {
		if (tagBuilder.length() > 0) {
			tagBuilder.append(',');
		}
		tagBuilder.append('[');
		tagBuilder.append(str);
		tagBuilder.append("]");
	}

	private List<String> purify(Map<String, List<Statement>> texts) {
		List<String> f = new LinkedList<String>();
		Set<String> toRemove = new HashSet<String>();
		Set<Map.Entry<String, List<Statement>>> textSet = texts.entrySet();
		for (Map.Entry<String, List<Statement>> entry : textSet) {
			String str = entry.getKey();
			List<Statement> path = entry.getValue();
			if (str.startsWith("http:") || str.startsWith("https:")
					|| str.startsWith("/")) {
				int idx = str.indexOf('?');
				if (idx > 0 && str.length() > idx) {
					String s = str.substring(idx + 1);
					if (containsKeywords(s.toLowerCase())) {
						record(s);
						text2Path.put(str, path);
						toRemove.add(str);
					}
				}
			} else if (str.startsWith("&") && str.endsWith("=")) {
				if (containsKeywords(str.toLowerCase())) {
					record(str);
					text2Path.put(str, path);
					toRemove.add(str);
				}
			} else if (str.length() == 1
					|| (!str.isEmpty() && Character.isDigit(str.charAt(0)))) {
				continue;
			} else if (str.startsWith("android.action.")
					|| str.startsWith("android.permission.")
					|| str.startsWith("android.intent.")) {
				continue;
			} else if (!str.contains(" ")) {
				String splited = splitWord(str);
				if (containsKeywords(str)
						|| containsKeywords(splited.toLowerCase())) {
					record(str);
					text2Path.put(str, path);
					toRemove.add(str);
				}
			} else if (containsKeywords(str.toLowerCase())) {
				f.add(str);
				// toRemove.add(str);//comment for path retrieve
			}
		}
		for (String s : toRemove) {
			texts.remove(s);
		}
		return f;
	}

	private static String splitWord(String src) {
		StringBuilder builder = new StringBuilder(src);
		int s = builder.length();
		int idx = 0;
		boolean continuousUpper = false;
		boolean nonLetter = false;
		while (s-- > 0) {
			char ch = builder.charAt(idx);
			if (Character.isUpperCase(ch)) {
				if (!continuousUpper) {
					if (idx > 0 && !Character.isSpace(builder.charAt(idx - 1))) {
						builder.insert(idx, ' ');
						idx++;
					}
					continuousUpper = true;
				}
				nonLetter = false;
			} else {
				if (Character.isLowerCase(ch)) {
					if (continuousUpper) {
						if (idx > 0
								&& !Character.isSpace(builder.charAt(idx - 1))) {
							builder.insert(idx - 1, ' ');
							idx++;
						}
					} else if (nonLetter) {
						builder.insert(idx, ' ');
						idx++;
					}
					nonLetter = false;
				} else if (!nonLetter) {
					builder.insert(idx, ' ');
					idx++;
					nonLetter = true;
				}
				continuousUpper = false;
			}
			idx++;
		}
		return builder.toString().trim();
	}

	private boolean containsKeywords(String str) {
		// System.err.println("STR = " + str);
		if (isGUIText) {
			Iterator<Map.Entry<String, Pattern>> iter = SensitiveTerms.iterateSensitiveTerms();
			while (iter.hasNext()) {
				Map.Entry<String, Pattern> entry = iter.next();
				Pattern p = entry.getValue();
				if (p.matcher(str).find()) {
					return true;
				}
			}
		} else {
			if (keywordPattern.matcher(str).find()) {
				// System.err.println(" STR = " + str + "  OK.");
				return true;
			}
		}
		return false;
	}

	public static void main(String[] args) {
		TextAnalysis a = new TextAnalysis();
		Map<String, List<Statement>> l = new HashMap<String, List<Statement>>();
		// List<String> l = new LinkedList<String>();
		l.put("enterPassword.", null);
		l.put("include user_id. do not include username. ", null);
		l.put(".email", null);
		// a.check(l);
		a.analyze(l, false);
		System.out.println(l);
	}

}
