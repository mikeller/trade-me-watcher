package ch.ike.trademe_scanner;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map.Entry;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Protocol;
import argo.jdom.JsonNode;
import argo.jdom.JsonRootNode;

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
	public void stop() {
	}

	@Override
	public void clearCache() {
		Jedis jedis = pool.getResource();
		jedis.del(prefix + SEEN_ITEMS, prefix + LATEST_START_DATES, prefix
				+ SEEN_QUESTIONS);

		pool.returnResource(jedis);
	}

	@Override
	public Entry<String, String> getAccessToken() {
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
	public void setAccessToken(String token, String secret) {
		Jedis jedis = pool.getResource();

		jedis.hset(prefix + ACCESS_TOKEN, TOKEN, token);
		jedis.hset(prefix + ACCESS_TOKEN, SECRET, secret);

		pool.returnResource(jedis);
	}

	@Override
	public void deleteAccessToken() {
		Jedis jedis = pool.getResource();

		jedis.del(prefix + ACCESS_TOKEN);

		pool.returnResource(jedis);
	}

	@Override
	public PersistenceObject getSeenQuestions() {
		if (seenQuestions == null) {
			seenQuestions = new RedisPersistenceObject(pool, prefix
					+ SEEN_QUESTIONS);
		}
		return seenQuestions;
	}

	@Override
	public PersistenceObject getSeenItems() {
		if (seenItems == null) {
			seenItems = new RedisPersistenceObject(pool, prefix + SEEN_ITEMS);
		}
		return seenItems;
	}

	@Override
	public PersistenceObject getLatestStartDates() {
		if (latestStartDates == null) {
			latestStartDates = new RedisPersistenceObject(pool, prefix
					+ LATEST_START_DATES);
		}
		return latestStartDates;
	}

}
