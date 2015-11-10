package edu.purdue.cs.toydroid.bidtext.analysis;

import java.io.StringReader;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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

	private final static Pattern keywordPattern = Pattern.compile("\\b(lat|lng|lon|long|latlng|latitude|longitude|imei|imsi|u(ser)?((\\s|_)?name|(\\s|_)?id)|e-?mail|pin(code|\\s(code|number|no|#))?|password|pwd|passwd)\\b");

	private static LexicalizedParser lexParser;
	public String tag;
	private StringBuilder tagBuilder;

	public TextAnalysis() {
		tagBuilder = new StringBuilder();
	}

	public String analyze(Set<String> texts) {
		if (texts.contains("location")) {
			texts.remove("location");
			if (texts.contains("gps")) {
				texts.remove("gps");
				record("location/gps");
			}
			if (texts.contains("network")) {
				texts.remove("network");
				record("location/network");
			}
		}
		if (texts.contains("latitude")) {
			texts.remove("latitude");
			record("latitude");
		}
		if (texts.contains("longitude")) {
			texts.remove("longitude");
			record("longitude");
		}
		if (texts.contains("lat")) {
			texts.remove("lat");
			record("lat");
			if (texts.contains("lng")) {
				texts.remove("lng");
				record("lng");
			} else if (texts.contains("long")) {
				texts.remove("long");
				record("long");
			} else if (texts.contains("lon")) {
				texts.remove("lon");
				record("lon");
			}
		}
		if (texts.contains("android_id")) {
			texts.remove("android_id");
			record("android_id");
		}
		List<String> f = puralize(texts);
		// logger.debug("  {}", f.toString());
		check(f);
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

	private void check(List<String> f) {
		if (f.isEmpty())
			return;
		if (lexParser == null) {
			lexParser = LexicalizedParser.loadModel(GRAMMAR);
		}
		LexicalizedParser lp = lexParser;
		TreebankLanguagePack tlp = lp.getOp().langpack();
		GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory();
		for (String s : f) {
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
									|| "could".equals(ns) || "can".equals(ns)) {
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
							}
						}
					} else {
						record(s);
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

	private List<String> puralize(Set<String> texts) {
		List<String> f = new LinkedList<String>();
		Set<String> toRemove = new HashSet<String>();
		for (String str : texts) {
			if (str.startsWith("http:") || str.startsWith("https:")
					|| str.startsWith("/")) {
				int idx = str.indexOf('?');
				if (idx > 0 && str.length() > idx) {
					String s = str.substring(idx + 1);
					if (containsKeywords(s.toLowerCase())) {
						record(s);
						toRemove.add(str);
					}
				}
			} else if (str.startsWith("&") && str.endsWith("=")) {
				if (containsKeywords(str.toLowerCase())) {
					record(str);
					toRemove.add(str);
				}
			} else if (str.length() == 1 || (!str.isEmpty() && Character.isDigit(str.charAt(0)))) {
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
					toRemove.add(str);
				}
			} else if (containsKeywords(str.toLowerCase())) {
				f.add(str);
			}
		}
		texts.removeAll(toRemove);
		return f;
	}

	private String splitWord(String src) {
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
		if (keywordPattern.matcher(str).find()) {
			// System.err.println(" STR = " + str + "  OK.");
			return true;
		}
		if (patternIdentity.matcher(str).find()) {
			return true;
		} else if (patternCredential.matcher(str).find()) {
			return true;
		} else if (patternContact.matcher(str).find()) {
			return true;
		} else if (patternAccount.matcher(str).find()) {
			return true;
		} else if (patternCreditCard.matcher(str).find()) {
			return true;
		} else if (patternSSN.matcher(str).find()) {
			return true;
		} else if (patternProtection.matcher(str).find()) {
			return true;
		} else if (patternPersonalInfo.matcher(str).find()) {
			return true;
		} else if (patternHealth.matcher(str).find()) {
			return true;
		} else if (patternFinancialInfo.matcher(str).find()) {
			return true;
		}
		return false;
	}

	private final static Pattern patternIdentity = Pattern.compile("ÏïÑÏù¥Îîî|Áî®Êà∑Âêç|\\b(user(\\s?name|\\sid)((\\sor\\s|/)e-?mail)?|e-?mail(\\sor\\s|/)user(\\s)?name|nick\\s*name|moniker|cognomen|sobriquet|soubriquet|byname)\\b");
	private final static Pattern patternCredential = Pattern.compile("ÎπÑÎ∞ÄÎ≤àÌò∏|ÂØ?„Ä?\\s)*Á†Å|ÂØ?„Ä?\\s)*Á¢º|\\b(pin(code|\\s(code|number|no|#))?|personal\\sidentification\\s(number|no)|password(s)?|passwort|watchword|parole|countersign|(security\\s)?passcode)\\b");
	private final static Pattern patternContact = Pattern.compile("Ïù¥Î©îÏùº|ÈõªÂ≠êÈÉµ‰ª∂|(ÁîµÂ≠ê)?ÈÇ?„Ä?\\s)*ÁÆ±|ÊâãÊú∫Âè?Á†??|ÊâãÊ©üËô?Á¢??|\\b((phone:)?e-?mail|e-?mail(\\s)?address(es)?|(mobile\\s|tele|cell|your\\s)?phone(\\s(no|number|#))?|mobile\\s(no|number|#)|gmail|contact(s|\\sname)|fax)\\b");
	private final static Pattern patternAccount = Pattern.compile("Áô?„Ä?\\s)*ÂΩï|Áô?„Ä?\\s)*ÂÖ•|\\b((your\\s)?login(\\s(credential|certificat(e|ion))(s)?)?|regist(er|ration|ry)|user\\s(authentication|hallmark|assay(\\s|-)mark)|sign(ing)?\\s(in|up)|check\\sin|log(-|\\s+)(in|on)(to)?)\\b");
	private final static Pattern patternCreditCard = Pattern.compile("Èì∂Ë°å(Âç??Âç°Âè∑|\\b(((credit|charge|my|your)(„Ä?\\s)?)?card(„Ä?\\s)?(number|no|#|information|statement)|(credit|charge)(„Ä?\\s)?card|cvc((„Ä?\\s)+code)?)\\b");;
	private final static Pattern patternSSN = Pattern.compile("Ë∫?‰ªΩ|Âà?Ë≠?Â≠??Ëôü|Ë∫´‰ªΩË≠âÂæå‰∫îÁ¢º|Ë∫´‰ªΩËØ?Âè?Á†??)?|\\b(((digits\\s)?of\\s)?ssn|tin|(federal|national)\\s(id|identity)|(your\\s)?social\\ssec(urity)?(\\s(number|no|#))?)\\b");
	private final static Pattern patternProtection = Pattern.compile("\\b(security\\s(answer|code|token|item)|enter\\syour\\s(answer|reply|response)|(identification|designation)\\s(code|number|no)|activation\\s(code|number|no)|financial\\sinstitution)\\b");
	private final static Pattern patternPersonalInfo = Pattern.compile("\\b((first|last)(\\s)?name|age|sex|gender|birth(\\s)?(date|day)?|date\\sof\\birth|interests|dropbox|facebook|address(es)?)\\b");
	private final static Pattern patternHealth = Pattern.compile("\\b(weight|height|health|cholesterol|glucose|obese|calories|kcal|doctor|blood(\\stype)?)\\b");
	private final static Pattern patternFinancialInfo = Pattern.compile("\\b(repayment|(payment(s)?|deposit|loan)(\\samount)?|income|expir(y|ation)(\\sdate)?|paypal|banking|debit|mortgage|taxable|(down|monthly)\\spayment|payment\\s(information|details)|cardholder's\\sname|billing\\saddress|opening\\sbalance)\\b");

	public static void main(String[] args) {
		TextAnalysis a = new TextAnalysis();
		Set<String> l = new HashSet<String>();
		// List<String> l = new LinkedList<String>();
		l.add("enterPassword.");
		l.add("include user_id. do not include username. ");
		l.add(".email");
		// a.check(l);
		a.analyze(l);
		System.out.println(l);
	}

}
