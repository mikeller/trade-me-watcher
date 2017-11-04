package ch.ike.trademe_scanner;

import java.util.Map.Entry;


public interface TradeMeScannerPersistence {
	static final String LATEST_START_DATES = "LatestStartDates";
	static final String SEEN_ITEMS = "SeenItems";
	static final String SEEN_QUESTIONS = "SeenQuestions";
	static final String ACCESS_TOKEN = "AccessToken";
	static final String SECRET = "Secret";
	static final String TOKEN = "Token";
	
	TradeMeScannerPersistenceConnection getConnection();

	void clearCache(TradeMeScannerPersistenceConnection connection);

	Entry<String, String> getAccessToken(TradeMeScannerPersistenceConnection connection);

	void setAccessToken(String token, String secret, TradeMeScannerPersistenceConnection connection);

	void deleteAccessToken(TradeMeScannerPersistenceConnection connection);

	PersistenceObject getSeenQuestions(TradeMeScannerPersistenceConnection connection);

	PersistenceObject getSeenItems(TradeMeScannerPersistenceConnection connection);

	PersistenceObject getLatestStartDates(TradeMeScannerPersistenceConnection connection);

}
