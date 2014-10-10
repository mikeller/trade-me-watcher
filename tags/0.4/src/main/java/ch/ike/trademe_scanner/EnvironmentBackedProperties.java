package ch.ike.trademe_scanner;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.InvalidPropertiesFormatException;
import java.util.Properties;

import javax.xml.transform.stream.StreamSource;

public class EnvironmentBackedProperties extends Properties {
	
	private static final long serialVersionUID = -8379283027449585376L;

	private final String environmentPrefix;

	public EnvironmentBackedProperties(String configFile, String environmentPrefix) {
		try {
			InputStream in = null;
			try {
				try {
					in = new FileInputStream(configFile);
				} catch (FileNotFoundException e) {
					in = new StreamSource(getClass().getResource(
							"/" + configFile).toString())
							.getInputStream();
				}
				try {
					loadFromXML(in);
				} catch (InvalidPropertiesFormatException e) {
					throw new RuntimeException(e);
				}
			} finally {
				in.close();
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
		this.environmentPrefix = environmentPrefix;
	}

	@Override
	public String getProperty(String key) {
		String result = System.getenv(environmentPrefix + "_" + key.replace(".", "_"));
				
		if (result == null) {			
			result = super.getProperty(key);
		}
		return result;
	}

	@Override
	@Deprecated
	public synchronized boolean containsKey(Object key) {
		return super.containsKey(key);
	}
}
