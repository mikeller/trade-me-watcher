package ch.ike.trademe_scanner;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map.Entry;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public class PreferencesPersistence implements TradeMeScannerPersistence {
	private final Preferences prefs;
	
	private PreferencesPersistenceObject seenQuestions;
	private PreferencesPersistenceObject seenItems;
	private PreferencesPersistenceObject latestStartDates;

	public PreferencesPersistence(Class<?> rootClass) {
		prefs = Preferences.userNodeForPackage(rootClass);

		System.out.println("Set up persistence with java Preferences for " + rootClass.getName() + ".");
	}

	@Override
	public void clearCache(TradeMeScannerPersistenceConnection connection) {
		try {
			prefs.node(SEEN_ITEMS).removeNode();
			prefs.node(LATEST_START_DATES).removeNode();
			prefs.node(SEEN_QUESTIONS).removeNode();
			prefs.flush();
		} catch (BackingStoreException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public PersistenceObject getSeenQuestions(TradeMeScannerPersistenceConnection connection) {
		if (seenQuestions == null) {
			seenQuestions = new PreferencesPersistenceObject(prefs.node(SEEN_QUESTIONS));
		}
		return seenQuestions;
	}

	@Override
	public PersistenceObject getSeenItems(TradeMeScannerPersistenceConnection connection) {
		if (seenItems == null) {
			seenItems = new PreferencesPersistenceObject(prefs.node(SEEN_ITEMS));
		}
		return seenItems;
	}

	@Override
	public PersistenceObject getLatestStartDates(TradeMeScannerPersistenceConnection connection) {
		if (latestStartDates == null) {
			latestStartDates = new PreferencesPersistenceObject(prefs.node(LATEST_START_DATES));
		}
		return latestStartDates;
	}

	@Override
	public Entry<String, String> getAccessToken(TradeMeScannerPersistenceConnection connection) {
		Entry<String, String> result = null;
		try {
			if (prefs.nodeExists(ACCESS_TOKEN)) {
				result = new SimpleImmutableEntry<String, String>(prefs.node(ACCESS_TOKEN).get(TOKEN,
					null), prefs.node(ACCESS_TOKEN).get(SECRET, null));
			}
		} catch (BackingStoreException e) {
			throw new RuntimeException(e);
		}
		return result;
	}

	@Override
	public void setAccessToken(String token, String secret, TradeMeScannerPersistenceConnection connection) {
		prefs.node(ACCESS_TOKEN).put(TOKEN, token);
		prefs.node(ACCESS_TOKEN).put(SECRET, secret);
		try {
			prefs.flush();
		} catch (BackingStoreException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void deleteAccessToken(TradeMeScannerPersistenceConnection connection) {
		try {
			prefs.node(ACCESS_TOKEN).removeNode();
			prefs.flush();
		} catch (BackingStoreException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public TradeMeScannerPersistenceConnection getConnection() {
		return null;
	}
}
