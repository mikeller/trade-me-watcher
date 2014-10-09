package ch.ike.trademe_scanner;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public class PreferencesPersistence implements TradeMeScannerPersistence {
	private static final String LATEST_START_DATES = "latest_start_dates";
	private static final String SEEN_ITEMS = "SeenItems";
	private static final String SEEN_QUESTIONS = "SeenQuestions";
	private static final String ACCESS_TOKEN = "AccessToken";
	private static final String SECRET = "Secret";
	private static final String TOKEN = "Token";

	private final Preferences prefs;
	
	private PreferencesPersistenceObject seenQuestions;
	private PreferencesPersistenceObject seenItems;
	private PreferencesPersistenceObject latestStartDates;

	public PreferencesPersistence(Class<?> rootClass) {
		prefs = Preferences.userNodeForPackage(rootClass);
	}

	@Override
	public void clearCache() {
		try {
			prefs.node(SEEN_ITEMS).removeNode();
			prefs.node(LATEST_START_DATES).removeNode();
			prefs.node(SEEN_QUESTIONS).removeNode();
			prefs.flush();
		} catch (BackingStoreException e) {
			throw new RuntimeException(e);
		}
	}

	public PersistenceObject getSeenQuestions() {
		if (seenQuestions == null) {
			seenQuestions = new PreferencesPersistenceObject(prefs.node(SEEN_QUESTIONS));
		}
		return seenQuestions;
	}

	public PersistenceObject getSeenItems() {
		if (seenItems == null) {
			seenItems = new PreferencesPersistenceObject(prefs.node(SEEN_ITEMS));
		}
		return seenItems;
	}

	public PersistenceObject getLatestStartDates() {
		if (latestStartDates == null) {
			latestStartDates = new PreferencesPersistenceObject(prefs.node(LATEST_START_DATES));
		}
		return latestStartDates;
	}

	@Override
	public boolean hasAccessToken() {
		try {
			return prefs.nodeExists(ACCESS_TOKEN);
		} catch (BackingStoreException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String getAccessToken() {
		return prefs.node(ACCESS_TOKEN).get(TOKEN,
				null);
	}

	@Override
	public String getAccessTokenSecret() {
		return prefs.node(ACCESS_TOKEN).get(SECRET, null);
	}

	@Override
	public void setAccessToken(String token, String secret) {
		prefs.node(ACCESS_TOKEN).put(TOKEN, token);
		prefs.node(ACCESS_TOKEN).put(SECRET, secret);
		try {
			prefs.flush();
		} catch (BackingStoreException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void deleteAccessToken() {
		try {
			prefs.node(ACCESS_TOKEN).removeNode();
			prefs.flush();
		} catch (BackingStoreException e) {
			throw new RuntimeException(e);
		}
	}

}
