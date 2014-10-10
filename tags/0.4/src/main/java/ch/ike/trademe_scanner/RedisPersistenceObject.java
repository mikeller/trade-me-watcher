package ch.ike.trademe_scanner;

import java.util.Collection;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class RedisPersistenceObject implements PersistenceObject {
	private final String keyName;
	private final JedisPool pool;
	
	private Jedis jedis;

	public RedisPersistenceObject(JedisPool pool, String keyName) {
		this.pool = pool;
		this.keyName = keyName;
	}

	@Override
	public Collection<String> getKeys() {
		return getJedis().hkeys(keyName);
	}

	private Jedis getJedis() {
		if (jedis == null) {
			jedis = pool.getResource();
		}
		return jedis;
	}

	@Override
	public void put(String key, String value) {
		getJedis().hset(keyName, key, value);
	}

	@Override
	public String get(String key) {
		return getJedis().hget(keyName, key);
	}

	@Override
	public void remove(String key) {
		getJedis().del(keyName, key);

	}

	@Override
	public void commit() {
		if (jedis != null) {
			pool.returnResource(jedis);
			jedis = null;
		}
	}

}
