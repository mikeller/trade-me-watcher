package ch.ike.trademe_scanner;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
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
					URL fileUrl = getClass().getResource("/" + configFile);
					if (fileUrl != null) {
						in = new StreamSource(fileUrl.toString()).getInputStream();
					}
				}
				
				if (in != null) {
					try {
						loadFromXML(in);
					} catch (InvalidPropertiesFormatException e) {
						throw new RuntimeException(e);
					}
				}
			} finally {
				if (in != null) {
					in.close();
				}
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
