package com.nextlabs.embraer;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class PropertyLoader {

	private static final Log LOG = LogFactory.getLog(PropertyLoader.class);

	public static Properties loadProperties(String filePath) {

		// get the properties file

		String dpcInstallHome = System.getProperty("dpc.install.home");
		if (dpcInstallHome == null || dpcInstallHome.trim().length() < 1) {
			dpcInstallHome = ".";
		}
		LOG.info("DPC Install Home :" + dpcInstallHome);

		if (filePath == null || filePath.length() == 0)
			throw new IllegalArgumentException("Invalid file name");

		filePath = dpcInstallHome + filePath;

		LOG.info("Properties file path is " + filePath);

		Properties result = null;

		try {
			File file = new File(filePath);
			LOG.info("Properties File Path:: " + file.getAbsolutePath());
			if (file != null) {
				FileInputStream fis = new FileInputStream(file);
				result = new Properties();
				result.load(fis); // Can throw IOException
			}
		} catch (Exception e) {
			LOG.error("Error parsing properties file ", e);
			result = null;
		}
		return result;
	}
}
