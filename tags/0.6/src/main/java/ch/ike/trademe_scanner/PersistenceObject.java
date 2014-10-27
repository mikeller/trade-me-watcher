package ch.ike.trademe_scanner;

import java.util.Collection;

public interface PersistenceObject {

	Collection<String> getKeys();

	void put(String key, String value);

	String get(String key);

	void remove(String key);

	void commit();

}
