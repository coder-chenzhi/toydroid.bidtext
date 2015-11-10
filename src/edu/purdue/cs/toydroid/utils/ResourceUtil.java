package edu.purdue.cs.toydroid.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.antlr.runtime.Parser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xmlpull.v1.XmlPullParser;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.strings.StringStuff;

import android.util.TypedValue;
import brut.androlib.AndrolibException;
import brut.androlib.ApkOptions;
import brut.androlib.res.AndrolibResources;
import brut.androlib.res.data.ResID;
import brut.androlib.res.data.ResPackage;
import brut.androlib.res.data.ResResSpec;
import brut.androlib.res.data.ResResource;
import brut.androlib.res.data.ResTable;
import brut.androlib.res.data.ResType;
import brut.androlib.res.data.value.ResStringValue;
import brut.androlib.res.data.value.ResValue;
import brut.androlib.res.decoder.AXmlResourceParser;
import brut.androlib.res.decoder.ResAttrDecoder;
import brut.androlib.res.util.ExtFile;

public class ResourceUtil {
	private static Logger logger = LogManager.getLogger(ResourceUtil.class);

	public static final String DefaultLanguage = "**";

	private static final String tagActivity = "activity";
	private static final String tagService = "service";
	private static final String tagProvider = "provider";
	private static final String tagReceiver = "receiver";
	private static final String tagApplicaiton = "application";

	private static IClass classTextView, classEditText;

	private static ClassHierarchy cha;

	private static String apkFile;
	private static String packageName;
	private static Map<String, String> components = new HashMap<String, String>();
	private static ResPackage mainPackage, frwkPackage;
	private static Map<Integer, String> id2Name;
	private static Map<String, Integer> name2Id;
	private static Set<String> layouts;
	private static Map<Integer, Map<String, String>> stringId2Value;
	private static Map<Integer, String> layout2Text;

	public static String getPackageName() {
		return packageName;
	}

	public static Set<String> getComponentClasses() {
		return components.keySet();
	}

	public static boolean isActivity(String n) {
		return tagActivity.equals(components.get(n));
	}

	public static String getLayoutText(Integer id) {
		return layout2Text == null ? null : layout2Text.get(id);
	}

	public static Map<String, String> stringValues(int id) {
		return stringId2Value == null ? null : stringId2Value.get(id);
	}

	public static void parse(String apk, ClassHierarchy ich) {
		apkFile = apk;
		cha = ich;
		File file = new File(apk);
		if (!file.exists() || !file.canRead()) {
			logger.error("Error reading file {}", apk);
			return;
		}

		ZipFile apkAchive = null;
		ZipEntry arscEntry = null;
		ZipEntry manifestentry = null;
		Map<String, ZipEntry> layoutEntries = new HashMap<String, ZipEntry>();
		logger.info("Begin parsing apk: {}", apk);
		try {
			apkAchive = new ZipFile(apk);

			AndrolibResources libRes = new AndrolibResources();
			libRes.apkOptions = new ApkOptions();
			ExtFile eFile = new ExtFile(apkFile);
			// already load main package in getResTable(). calling loadMainPkg()
			// causes errors.
			ResTable resTable = libRes.getResTable(eFile);
			mainPackage = resTable.getCurrentResPackage();
			// no fwk pkg will cause some decoding errors
			frwkPackage = libRes.loadFrameworkPkg(resTable, 1, null);

			Enumeration<?> enums = apkAchive.entries();
			while (enums.hasMoreElements()) {
				ZipEntry entry = (ZipEntry) enums.nextElement();
				String entryName = entry.getName();
				if ("AndroidManifest.xml".equals(entryName)) {
					manifestentry = entry;
				} else if ("resources.arsc".equals(entryName)) {
					arscEntry = entry;
				} else if (entryName.startsWith("res/layout/")
						&& entryName.endsWith(".xml")) {
					layoutEntries.put(entryName, entry);
				}
			}
			// parseARSC(apkAchive, arscEntry);
			parseARSC(resTable);
			parseManifest(apkAchive, manifestentry);
			parseLayoutXMLs(apkAchive, layoutEntries);
		} catch (IOException e) {
			logger.error("Fail parsing file {}", apk);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				apkAchive.close();
			} catch (IOException e) {

			}
		}
		logger.info("End parsing apk: {}", apk);
	}

	private static void parseARSC(ResTable resTable) {
		id2Name = new HashMap<Integer, String>();
		name2Id = new HashMap<String, Integer>();
		stringId2Value = new HashMap<Integer, Map<String, String>>();
		logger.debug("Parsing Main Package.");
		List<ResType> resTypes = mainPackage.listTypes();
		for (ResType type : resTypes) {
			String typeName = type.getName();
			logger.debug(" + Found ResType: {}", typeName);
			Set<ResResSpec> specs = type.listResSpecs();
			for (ResResSpec spec : specs) {
				ResID resId = spec.getId();
				String resName = String.format("%s/%s", typeName,
						spec.getName());
				id2Name.put(resId.id, resName);
				name2Id.put(resName, resId.id);
				logger.debug("  - Found Res: [{}] - {}",
						Integer.toHexString(resId.id), resName);
				if (typeName.equals("layout")) {
					if (layouts == null) {
						layouts = new HashSet<String>();
					}
					layouts.add("res/" + resName + ".xml");
				} else if (typeName.equals("string")) {
					Set<ResResource> resources = spec.listResources();
					Map<String, String> lang2Value = new HashMap<String, String>();
					for (ResResource res : resources) {
						ResValue resVal = res.getValue();
						if (!(resVal instanceof ResStringValue)) {
							continue;
						}
						ResStringValue strVal = (ResStringValue) resVal;
						char[] language = res.getConfig().getFlags().language;
						String lang;
						if (language[0] != '\0') {
							lang = new String(language);
						} else {
							lang = DefaultLanguage;
						}
						String value = strVal.encodeAsResXmlValue();
						value = value.replace("&amp;", "&")
								.replace("&lt;", "<")
								.replace("&gt;", ">");
						lang2Value.put(lang, value);
					}
					logger.debug("    * Found {} values for [{}]",
							lang2Value.size(), resName);
					if (!lang2Value.isEmpty()) {
						stringId2Value.put(resId.id, lang2Value);
					}
				}
			}

		}
	}

	private static void parseManifest(ZipFile apkAchive, ZipEntry manifest) {
		logger.info("Begin parsing AndroidManifest.xml");
		try {
			InputStream is = apkAchive.getInputStream(manifest);
			AXmlResourceParser parser = new AXmlResourceParser(is);
			ResAttrDecoder attrDecoder = new ResAttrDecoder();
			attrDecoder.setCurrentPackage(mainPackage);
			parser.setAttrDecoder(attrDecoder);
			int type = -1;

			while ((type = parser.next()) != XmlPullParser.END_DOCUMENT) {
				if (type == XmlPullParser.START_TAG) {
					String tagName = parser.getName();
					if (tagName.equals("manifest")) {
						packageName = getAttributeText(parser, "package");
						logger.info("Parsed package: {}", packageName);
					} else if (tagName.equals("activity")
							|| tagName.equals("service")
							|| tagName.equals("provider")
							|| tagName.equals("receiver")
							|| tagName.equals("application")) {
						String clazz = getAttributeText(parser, "name");
						if (clazz == null) {
							logger.warn(
									"Empty 'name' attribute found for <{}>",
									tagName);
							continue;
						}
						if (clazz.contains(".")) {
							if (clazz.startsWith(".")) {
								clazz = packageName + clazz;
							}
						} else {
							clazz = packageName + "." + clazz;
						}
						clazz = "L" + clazz.replace('.', '/');
						components.put(clazz, tagName);
						logger.info("Parsed <{}>: '{}'", tagName, clazz);
					}
				}
			}
			parser.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void parseLayoutXMLs(ZipFile apkAchive,
			Map<String, ZipEntry> layoutEntries) {
		if (cha != null) {
			try {
				classTextView = cha.lookupClass(TypeReference.findOrCreate(
						ClassLoaderReference.Primordial,
						"Landroid/widget/TextView"));
				classEditText = cha.lookupClass(TypeReference.findOrCreate(
						ClassLoaderReference.Primordial,
						"Landroid/widget/EditText"));
			} catch (Exception e) {
			}
		}
		layout2Text = new HashMap<Integer, String>();
		Set<Map.Entry<String, ZipEntry>> entrySet = layoutEntries.entrySet();
		for (Map.Entry<String, ZipEntry> entry : entrySet) {
			String name = entry.getKey();
			ZipEntry xml = entry.getValue();
			// sometimes, the XML file is not recorded in ARSC.
			if (recordedLayout(name)) {
				parseSingleLayoutXML(apkAchive, name, xml);
			}
		}
	}

	private static void parseSingleLayoutXML(ZipFile apkAchive, String name,
			ZipEntry xml) {
		logger.info("Begin parsing layout: {}", name);
		InputStream is;
		StringBuilder builder = new StringBuilder();
		try {
			is = apkAchive.getInputStream(xml);
			AXmlResourceParser parser = new AXmlResourceParser(is);
			ResAttrDecoder attrDecoder = new ResAttrDecoder();
			attrDecoder.setCurrentPackage(mainPackage);
			parser.setAttrDecoder(attrDecoder);
			int type = -1;
			while ((type = parser.next()) != XmlPullParser.END_DOCUMENT) {
				switch (type) {
					case XmlPullParser.START_TAG:
						parseOneTag(name, parser, builder);
						break;
					default:
						break;
				}
			}
			parser.close();
		} catch (Throwable e) {
			logger.warn("Error parsing layout: {}", name);
		}
		String layoutText = builder.toString();
		if (!layoutText.isEmpty()) {
			layout2Text.put(resolveIdForFullLayoutName(name), layoutText);
		}
		logger.info("End parsing layout: {}", name);
	}

	private static void parseOneTag(String xmlFile, AXmlResourceParser parser,
			StringBuilder builder) {
		String tagName = parser.getName();
		if (tagName.equals("selector")) {
			return;
		}
		if (tagName.equals("view")) {
			String clazz = getAttributeText(parser, "class");
			if (!clazz.isEmpty()) {
				tagName = clazz;
			}
		}
		String className;
		ClassLoaderReference ref;
		if (!tagName.contains(".")) {
			className = String.format("Landroid/widget/%s", tagName);
			ref = ClassLoaderReference.Primordial;
		} else {
			className = StringStuff.deployment2CanonicalTypeString(tagName);
			ref = ClassLoaderReference.Application;
		}

		if (cha == null) {
			return;
		}
		IClass clazz = null;
		try {
			clazz = cha.lookupClass(TypeReference.find(ref, className));
		} catch (Exception e) {
		}
		if (clazz != null) {
			String text = null;
			if (cha.isSubclassOf(clazz, classEditText)) {
				String hint = getAttributeText(parser, "hint");
				text = getAttributeText(parser, "text");
				if (hint != null) {
					builder.append(hint);
					builder.append('\n');
					//Stat.addNConstInLayout();
				}
			} else if (cha.isSubclassOf(clazz, classTextView)) {
				text = getAttributeText(parser, "text");
			}
			if (text != null) {
				builder.append(text);
				builder.append('\n');
				//Stat.addNConstInLayout();
			}
		}
	}

	private static Integer resolveIdForFullLayoutName(String name) {
		String layout = name.substring(4, name.length() - 4);
		return resolveIdForName(layout);
	}

	private static boolean recordedLayout(String layoutFile) {
		return (layouts != null && layouts.contains(layoutFile));
	}

	private static String getAttributeText(AXmlResourceParser parser,
			String attr) {
		String ret = null;
		for (int i = 0; i < parser.getAttributeCount(); i++) {
			String name = parser.getAttributeName(i);
			if (attr.equals(name)) {
				int vType = parser.getAttributeValueType(i);
				if (vType == TypedValue.TYPE_REFERENCE) {
					int ID = parser.getAttributeResourceValue(i, 0);
					Map<String, String> lang2Value = stringValues(ID);
					if (null != lang2Value) {
						String v = lang2Value.get("en");
						if (v == null)
							v = lang2Value.get(DefaultLanguage);
						ret = v;
						break;
					}
				} else if (vType == TypedValue.TYPE_STRING) {
					ret = parser.getAttributeValue(i);
					break;
				}
			}
		}
		if (ret != null) {
			ret = ret.trim();
			if (ret.isEmpty()) {
				ret = null;
			}
		}
		return ret;
	}

	public static String resolveNameForId(int id) {
		String name = null;
		if (id2Name != null)
			name = id2Name.get(id);
		return name;
	}

	/**
	 * For a given (full) resource name, return the corresponding ID or null if
	 * not exists.
	 * 
	 * @param name
	 *            - a full resource name, e.g. "string/hello"
	 * @return - null or ID
	 */
	public static Integer resolveIdForName(String name) {
		Integer ID = null;
		if (name2Id != null)
			ID = name2Id.get(name);
		return ID;
	}

	/**
	 * For a given (full) resource name, return the corresponding ID or null if
	 * not exists.
	 * 
	 * @param type
	 *            - type name, e.g. "string"
	 * @param name
	 *            - resource name, e.g. "hello"
	 * @return - null or ID
	 */
	public Integer resolveIdForName(String type, String name) {
		return resolveIdForName(type + "/" + name);
	}

	public static void main(String[] args) {
		String apk = "E:\\Eclipse-Workspace\\TestAndroidAct\\bin\\TestAndroidAct.apk";
		parse(apk, null);
	}
}
