package ch.ike.trademe_scanner;


public interface TradeMeScannerPersistence {

	void clearCache();

	boolean hasAccessToken();

	String getAccessToken();

	String getAccessTokenSecret();

	void setAccessToken(String token, String secret);

	void deleteAccessToken();

	PersistenceObject getSeenQuestions();

	PersistenceObject getSeenItems();

	PersistenceObject getLatestStartDates();

}
