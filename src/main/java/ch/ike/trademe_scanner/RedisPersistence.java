package ch.ike.trademe_scanner;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map.Entry;

import argo.jdom.JsonNode;
import argo.jdom.JsonRootNode;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Protocol;

public class RedisPersistence implements TradeMeScannerPersistence {
	private final String prefix;
	private final JedisPool pool;

	private PersistenceObject seenQuestions;
	private PersistenceObject seenItems;
	private PersistenceObject latestStartDates;

	public RedisPersistence(String prefix, JsonRootNode vcapServices) {
		this.prefix = prefix + ":";

		JsonNode rediscloudNode = vcapServices.getNode("rediscloud");
		JsonNode credentials = rediscloudNode.getNode(0).getNode("credentials");

		JedisPoolConfig poolConfig = new JedisPoolConfig();
		poolConfig.setTestOnBorrow(true);
		pool = new JedisPool(poolConfig,
				credentials.getStringValue("hostname"),
				Integer.parseInt(credentials.getStringValue("port")),
				Protocol.DEFAULT_TIMEOUT,
				credentials.getStringValue("password"));

		System.out.println("Set up persistence with redis on "
				+ credentials.getStringValue("hostname") + ":"
				+ credentials.getStringValue("port") + ".");
	}

	@Override
	public void clearCache(TradeMeScannerPersistenceConnection connection) {
		Jedis jedis = pool.getResource();
		jedis.del(prefix + SEEN_ITEMS, prefix + LATEST_START_DATES, prefix
				+ SEEN_QUESTIONS);

		pool.returnResource(jedis);
	}

	@Override
	public Entry<String, String> getAccessToken(TradeMeScannerPersistenceConnection connection) {
		Entry<String, String> result = null;
		Jedis jedis = pool.getResource();

		if (jedis.exists(prefix + ACCESS_TOKEN)) {
			result = new SimpleImmutableEntry<String, String>(jedis.hget(prefix
					+ ACCESS_TOKEN, TOKEN), jedis.hget(prefix + ACCESS_TOKEN,
					SECRET));
		}
		pool.returnResource(jedis);

		return result;
	}

	@Override
	public void setAccessToken(String token, String secret, TradeMeScannerPersistenceConnection connection) {
		Jedis jedis = pool.getResource();

		jedis.hset(prefix + ACCESS_TOKEN, TOKEN, token);
		jedis.hset(prefix + ACCESS_TOKEN, SECRET, secret);

		pool.returnResource(jedis);
	}

	@Override
	public void deleteAccessToken(TradeMeScannerPersistenceConnection connection) {
		Jedis jedis = pool.getResource();

		jedis.del(prefix + ACCESS_TOKEN);

		pool.returnResource(jedis);
	}

	@Override
	public PersistenceObject getSeenQuestions(TradeMeScannerPersistenceConnection connection) {
		if (seenQuestions == null) {
			seenQuestions = new RedisPersistenceObject(pool, prefix
					+ SEEN_QUESTIONS);
		}
		return seenQuestions;
	}

	@Override
	public PersistenceObject getSeenItems(TradeMeScannerPersistenceConnection connection) {
		if (seenItems == null) {
			seenItems = new RedisPersistenceObject(pool, prefix + SEEN_ITEMS);
		}
		return seenItems;
	}

	@Override
	public PersistenceObject getLatestStartDates(TradeMeScannerPersistenceConnection connection) {
		if (latestStartDates == null) {
			latestStartDates = new RedisPersistenceObject(pool, prefix
					+ LATEST_START_DATES);
		}
		return latestStartDates;
	}

	@Override
	public TradeMeScannerPersistenceConnection getConnection() {
		return null;
	}

}
