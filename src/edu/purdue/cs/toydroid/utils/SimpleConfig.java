package edu.purdue.cs.toydroid.utils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SimpleConfig {
	private static final Logger logger = LogManager.getLogger(SimpleConfig.class);

	private static String PROPERTIES = "dat/Config.properties";

	private static boolean configParsed = false;
	private static String androidJar;
	private static String exclusionFile;
	private static String additionalJars;

	private static void parseConfig() throws FileNotFoundException, IOException {
		if (configParsed) {
			return;
		}
		InputStream is = new FileInputStream(PROPERTIES);
		Properties prop = new Properties();
		prop.load(is);
		androidJar = prop.getProperty("ANDROID_JAR");
		exclusionFile = prop.getProperty("EXCLUSION_FILE");
		additionalJars = prop.getProperty("ADDITIONAL_JARS");
		is.close();
		configParsed = true;
	}

	public static void setPropertyFile(String pf) {
		PROPERTIES = pf;
		configParsed = false;
	}

	public static String getAndroidJar() throws FileNotFoundException,
			IOException {
		parseConfig();
		return androidJar;
	}

	public static String getExclusionFile() throws FileNotFoundException,
			IOException {
		parseConfig();
		return exclusionFile;
	}

	public static Iterator<String> iteratorAdditionalJars()
			throws FileNotFoundException, IOException {
		parseConfig();
		if (additionalJars == null) {
			return Collections.emptyIterator();
		} else {
			List<String> jarsList = new LinkedList<String>();
			String[] jars = additionalJars.split(";");
			for (String j : jars) {
				j = j.trim();
				if (!j.isEmpty()) {
					jarsList.add(j);
				}
			}
			return jarsList.iterator();
		}
	}
}
