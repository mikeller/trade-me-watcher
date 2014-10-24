package ch.ike.trademe_scanner;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map.Entry;

import argo.jdom.JsonNode;
import argo.jdom.JsonRootNode;

public class PostgresPersistence implements TradeMeScannerPersistence,
		PostgresConstants {
	private final String prefix;
	private final Connection connection;

	private PersistenceObject seenQuestions;
	private PersistenceObject seenItems;
	private PersistenceObject latestStartDates;

	public PostgresPersistence(String prefix, JsonRootNode vcapServices) {
		this.prefix = prefix + "_";

		JsonNode rediscloudNode = vcapServices.getNode("elephantsql");
		JsonNode credentials = rediscloudNode.getNode(0).getNode("credentials");

		URI uri = URI.create(credentials.getStringValue("uri"));
		String url = "jdbc:postgresql://" + uri.getHost() + ":" + uri.getPort()
				+ uri.getPath();
		String username = uri.getUserInfo().split(":")[0];
		String password = uri.getUserInfo().split(":")[1];

		try {
			Class.forName("org.postgresql.Driver");
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}

		try {
			connection = DriverManager.getConnection(url, username, password);

			Statement statement = connection.createStatement();
			try {
				checkCreateTable(this.prefix + SEEN_ITEMS, statement);

				checkCreateTable(this.prefix + LATEST_START_DATES, statement);

				checkCreateTable(this.prefix + SEEN_QUESTIONS, statement);

				checkCreateTable(this.prefix + ACCESS_TOKEN, statement);
			} finally {
				statement.close();
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}

		System.out.println("Set up persistence with postgres SQL on "
				+ uri.getHost() + ":" + uri.getPort() + ".");
	}

	private void checkCreateTable(String tableName, Statement statement)
			throws SQLException {
		ResultSet resultSet = statement
				.executeQuery("select count(*) table_count from information_schema.tables where table_name = '"
						+ tableName.toLowerCase() + "'");
		boolean tableExists = resultSet.next() && (resultSet.getInt("table_count") != 0);
		resultSet.close();
		if (!tableExists) {
			statement
					.executeUpdate("create table " + tableName + " (" + PK
							+ " varchar(256) primary key, " + VALUE
							+ " varchar(1024))");

			System.out.println("Created postgres table " + tableName + ".");
		}
	}

	@Override
	public void clearCache() {
		try {
			Statement statement = connection.createStatement();
			try {
				statement.executeUpdate("delete from " + prefix + SEEN_ITEMS);

				statement.executeUpdate("delete from " + prefix
						+ LATEST_START_DATES);

				statement.executeUpdate("delete from " + prefix + ACCESS_TOKEN);
			} finally {
				statement.close();
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Entry<String, String> getAccessToken() {
		Entry<String, String> result = null;

		String token = null;
		String secret = null;
		try {
			Statement statement = connection.createStatement();
			try {
				ResultSet resultSet = statement.executeQuery("select * from "
						+ prefix + ACCESS_TOKEN + " where " + PK + " in ('"
						+ TOKEN + "', '" + SECRET + "')");
				while (resultSet.next()) {
					if (TOKEN.equals(resultSet.getString(PK))) {
						token = resultSet.getString(VALUE);
					} else if (SECRET.equals(resultSet.getString(PK))) {
						secret = resultSet.getString(VALUE);
					}
				}
				resultSet.close();
			} finally {
				statement.close();
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}

		if ((token != null) && (secret != null)) {
			result = new SimpleImmutableEntry<String, String>(token, secret);
		}

		return result;
	}

	@Override
	public void setAccessToken(String token, String secret) {
		try {
			PreparedStatement update = connection.prepareStatement("update "
					+ prefix + ACCESS_TOKEN + " set " + VALUE + " = ? where "
					+ PK + " = ?");
			PreparedStatement insert = connection
					.prepareStatement("insert into " + prefix + ACCESS_TOKEN
							+ " (" + VALUE + ", " + PK + ") values  (?, ?)");
			try {
				update.setString(1, token);
				update.setString(2, TOKEN);
				int rows = update.executeUpdate();
				if (rows == 0) {
					insert.setString(1, token);
					insert.setString(2, TOKEN);
					insert.executeUpdate();
				}

				update.setString(1, secret);
				update.setString(2, SECRET);
				rows = update.executeUpdate();
				if (rows == 0) {
					insert.setString(1, secret);
					insert.setString(2, SECRET);
					insert.executeUpdate();
				}
			} finally {
				update.close();
				insert.close();
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void deleteAccessToken() {
		try {
			Statement statement = connection.createStatement();
			try {
				statement.executeUpdate("delete from " + prefix + ACCESS_TOKEN);
			} finally {
				statement.close();
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public PersistenceObject getSeenQuestions() {
		if (seenQuestions == null) {
			seenQuestions = new PostgresPersistenceObject(connection, prefix
					+ SEEN_QUESTIONS);
		}
		return seenQuestions;
	}

	@Override
	public PersistenceObject getSeenItems() {
		if (seenItems == null) {
			seenItems = new PostgresPersistenceObject(connection, prefix
					+ SEEN_ITEMS);
		}
		return seenItems;
	}

	@Override
	public PersistenceObject getLatestStartDates() {
		if (latestStartDates == null) {
			latestStartDates = new PostgresPersistenceObject(connection, prefix
					+ LATEST_START_DATES);
		}
		return latestStartDates;
	}

}
