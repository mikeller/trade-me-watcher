package ch.ike.trademe_scanner;

import java.util.Map.Entry;


public interface TradeMeScannerPersistence {

	static final String LATEST_START_DATES = "LatestStartDates";
	static final String SEEN_ITEMS = "SeenItems";
	static final String SEEN_QUESTIONS = "SeenQuestions";
	static final String ACCESS_TOKEN = "AccessToken";
	static final String SECRET = "Secret";
	static final String TOKEN = "Token";

	void stop();

	void clearCache();

	Entry<String, String> getAccessToken();

	void setAccessToken(String token, String secret);

	void deleteAccessToken();

	PersistenceObject getSeenQuestions();

	PersistenceObject getSeenItems();

	PersistenceObject getLatestStartDates();

}
