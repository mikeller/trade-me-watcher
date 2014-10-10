package ch.ike.trademe_scanner;

import java.util.Arrays;
import java.util.Collection;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public class PreferencesPersistenceObject implements PersistenceObject {

	private final Preferences prefs;

	public PreferencesPersistenceObject(Preferences prefs) {
		this.prefs = prefs;
	}

	@Override
	public Collection<String> getKeys() {
		try {
			return Arrays.asList(prefs.keys());
		} catch (BackingStoreException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void put(String key, String value) {
		prefs.put(key, value);		
	}

	@Override
	public String get(String key) {
		return prefs.get(key, null);
	}

	@Override
	public void remove(String key) {
		prefs.remove(key);
	}

	@Override
	public void commit() {
		try {
			prefs.flush();
		} catch (BackingStoreException e) {
			throw new RuntimeException(e);
		}
	}

}
