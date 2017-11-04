package ch.ike.trademe_scanner;

import java.net.URI;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map.Entry;

import argo.jdom.JsonNode;
import argo.jdom.JsonRootNode;
import it.sauronsoftware.cron4j.Scheduler;

public class PostgresPersistence implements TradeMeScannerPersistence,
		PostgresConstants, Runnable {
	
	private final String prefix;

	private final Scheduler scheduler;
	
	private final String url;
	private final String username;
	private final String password;

	public PostgresPersistence(String prefix, JsonRootNode vcapServices) {
		this.prefix = prefix + "_";

		JsonNode rediscloudNode = vcapServices.getNode("elephantsql");
		JsonNode credentials = rediscloudNode.getNode(0).getNode("credentials");

		URI uri = URI.create(credentials.getStringValue("uri"));
		url = "jdbc:postgresql://" + uri.getHost() + ":" + uri.getPort()
				+ uri.getPath();
		username = uri.getUserInfo().split(":")[0];
		password = uri.getUserInfo().split(":")[1];

		try {
			Class.forName("org.postgresql.Driver");
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
		
		Connection connection = getConnection().getConnection();

		try {
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

		scheduler = new Scheduler();
		String schedule = "5 3 * * *";
		scheduler.schedule(schedule, this);
		scheduler.start();
		System.out.println("Set database cleanup schedule to \"" + schedule + "\".");

		System.out.println("Set up persistence with postgres SQL on "
				+ uri.getHost() + ":" + uri.getPort() + ".");
	}
	
	@Override
	public PostgresPersistenceConnection getConnection() {
		return new PostgresPersistenceConnection(url, username, password);
	}
	
	public void run() {
		Connection connection = getConnection().getConnection();
		clearCache(false, connection);
		System.out.println("Cleared database cache.");
	}

	private void checkCreateTable(String tableName, Statement statement)
			throws SQLException {
		ResultSet resultSet = statement
				.executeQuery("select count(*) table_count from information_schema.tables where table_name = '"
						+ tableName.toLowerCase() + "'");
		boolean tableExists = resultSet.next() && (resultSet.getInt("table_count") != 0);
		resultSet.close();
		if (!tableExists) {
			statement.executeUpdate("create table " + tableName + " ("
						+ PK + " varchar(256) primary key, "
						+ VALUE + " varchar(1024), "
						+ CREATED_TIMESTAMP + " timestamp default now()"
						+ ")");

			System.out.println("Created postgres table " + tableName + ".");
		}
	}

	public void stop() {
		scheduler.stop();
		System.out.println("Stopped database cleanup schedule.");
	}

	@Override
	public void clearCache(TradeMeScannerPersistenceConnection connection) {
		if (connection instanceof PostgresPersistenceConnection) {
			clearCache(true, ((PostgresPersistenceConnection)connection).getConnection());
		} else {
			throw new RuntimeException("Not a PostgresPersistenceConnection: " + connection.getClass().getCanonicalName());
		}
	}

	private void clearCache(boolean removeAll, Connection connection) {
		String whereClause = "";
		if (!removeAll) {
			whereClause = whereClause + " where " + CREATED_TIMESTAMP + " < now() - interval '1 month'";
		}

		try {
			Statement statement = connection.createStatement();
			try {
				statement.executeUpdate("delete from " + prefix + SEEN_ITEMS + whereClause);

				statement.executeUpdate("delete from " + prefix + SEEN_QUESTIONS + whereClause);

				statement.executeUpdate("delete from " + prefix
						+ LATEST_START_DATES + whereClause);

				statement.executeUpdate("delete from " + prefix + ACCESS_TOKEN + whereClause);
			} finally {
				statement.close();
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Entry<String, String> getAccessToken(TradeMeScannerPersistenceConnection connection) {
		if (connection instanceof PostgresPersistenceConnection) {
			Entry<String, String> result = null;

			String token = null;
			String secret = null;
			try {
				Statement statement = ((PostgresPersistenceConnection) connection).getConnection().createStatement();
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
		} else {
			throw new RuntimeException("Not a PostgresPersistenceConnection: " + connection.getClass().getCanonicalName());
		}
	}

	@Override
	public void setAccessToken(String token, String secret, TradeMeScannerPersistenceConnection connection) {
		if (connection instanceof PostgresPersistenceConnection) {
			try {
				PreparedStatement update = ((PostgresPersistenceConnection) connection).getConnection().prepareStatement("update "
						+ prefix + ACCESS_TOKEN + " set " + VALUE + " = ? where "
						+ PK + " = ?");
				PreparedStatement insert = ((PostgresPersistenceConnection) connection).getConnection()
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
		} else {
			throw new RuntimeException("Not a PostgresPersistenceConnection: " + connection.getClass().getCanonicalName());
		}
	}

	@Override
	public void deleteAccessToken(TradeMeScannerPersistenceConnection connection) {
		if (connection instanceof PostgresPersistenceConnection) {
			try {
				Statement statement = ((PostgresPersistenceConnection) connection).getConnection().createStatement();
				try {
					statement.executeUpdate("delete from " + prefix + ACCESS_TOKEN);
				} finally {
					statement.close();
				}
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
		} else {
			throw new RuntimeException("Not a PostgresPersistenceConnection: " + connection.getClass().getCanonicalName());
		}
	}

	@Override
	public PersistenceObject getSeenQuestions(TradeMeScannerPersistenceConnection connection) {
		if (connection instanceof PostgresPersistenceConnection) {
			return new PostgresPersistenceObject((PostgresPersistenceConnection) connection, prefix
						+ SEEN_QUESTIONS);
		} else {
			throw new RuntimeException("Not a PostgresPersistenceConnection: " + connection.getClass().getCanonicalName());
		}
	}

	@Override
	public PersistenceObject getSeenItems(TradeMeScannerPersistenceConnection connection) {
		if (connection instanceof PostgresPersistenceConnection) {
			return new PostgresPersistenceObject((PostgresPersistenceConnection) connection, prefix
						+ SEEN_ITEMS);
		} else {
			throw new RuntimeException("Not a PostgresPersistenceConnection: " + connection.getClass().getCanonicalName());
		}
	}

	@Override
	public PersistenceObject getLatestStartDates(TradeMeScannerPersistenceConnection connection) {
		if (connection instanceof PostgresPersistenceConnection) {
			return new PostgresPersistenceObject((PostgresPersistenceConnection) connection, prefix
						+ LATEST_START_DATES);
		} else {
			throw new RuntimeException("Not a PostgresPersistenceConnection: " + connection.getClass().getCanonicalName());
		}
	}

}
