package ch.ike.trademe_scanner;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;

public class PostgresPersistenceObject implements PersistenceObject,
		PostgresConstants {
	private final String tableName;
	private final Connection connection;

	private PreparedStatement keysStatement;
	private PreparedStatement getStatement;
	private PreparedStatement updateStatement;
	private PreparedStatement insertStatement;
	private PreparedStatement deleteStatement;

	public PostgresPersistenceObject(Connection connection, String tableName) {
		this.connection = connection;
		this.tableName = tableName;
	}

	@Override
	public Collection<String> getKeys() {
		Collection<String> result = new HashSet<String>();
		try {
			if (keysStatement == null) {
				keysStatement = connection.prepareStatement("select " + PK
						+ " from " + tableName);
			}

			ResultSet resultSet = keysStatement.executeQuery();
			while (resultSet.next()) {
				result.add(resultSet.getString(PK));
			}
			resultSet.close();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}

		return result;
	}

	@Override
	public void put(String key, String value) {
		try {
			if (updateStatement == null) {
				updateStatement = connection.prepareStatement("update "
						+ tableName + " set " + VALUE + " = ? where " + PK
						+ " = ?");
			}

			updateStatement.setString(1, value);
			updateStatement.setString(2, key);
			int rows = updateStatement.executeUpdate();
			if (rows == 0) {
				if (insertStatement == null) {
					insertStatement = connection
							.prepareStatement("insert into " + tableName + " ("
									+ VALUE + ", " + PK + ") values (?, ?)");
				}
				insertStatement.setString(1, value);
				insertStatement.setString(2, key);
				insertStatement.executeUpdate();
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String get(String key) {
		String result = null;
		try {
			if (getStatement == null) {
				getStatement = connection.prepareStatement("select " + VALUE
						+ " from " + tableName + " where " + PK + " = ?");
			}

			getStatement.setString(1, key);
			ResultSet resultSet = getStatement.executeQuery();
			if (resultSet.next()) {
				result = resultSet.getString(VALUE);
			}
			resultSet.close();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}

		return result;
	}

	@Override
	public void remove(String key) {
		try {
			if (deleteStatement == null) {
				deleteStatement = connection.prepareStatement("delete from "
						+ tableName + " where " + PK + " = ?");
			}

			deleteStatement.setString(1, key);
			deleteStatement.executeUpdate();
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void commit() {
		try {
			if (keysStatement != null) {
				keysStatement.close();
				keysStatement = null;
			}

			if (getStatement != null) {
				getStatement.close();
				getStatement = null;
			}

			if (updateStatement != null) {
				updateStatement.close();
				updateStatement = null;
			}

			if (insertStatement != null) {
				insertStatement.close();
				insertStatement = null;
			}

			if (deleteStatement != null) {
				deleteStatement.close();
				deleteStatement = null;
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}

}
