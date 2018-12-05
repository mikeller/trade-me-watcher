package ch.ike.trademe_scanner;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class PostgresPersistenceConnection implements TradeMeScannerPersistenceConnection {
	private final String url;
	private final String username;
	private final String password;

	private Connection connection = null;
	
	public PostgresPersistenceConnection(String url, String username, String password) {
		this.url = url;
		this.username = username;
		this.password = password;
	}
	
	Connection getConnection() {
		try {
			if (connection == null) {
				synchronized (this) {
					if (connection == null) {
						System.out.println("Connecting to DB: " + url);
						
						connection = DriverManager.getConnection(url, username, password);
					}
				}
			}
		} catch (SQLException e) {
			connection = null;
			
			throw new RuntimeException(e);
		}
		
		return connection;
	}

	@Override
	public void close() {
		try {
			if (connection != null) {
				synchronized (this) {
					try {
						if (connection != null) {
							connection.close();
							
							System.out.println("Closed DB connection.");
						}
					} finally {
						connection = null;
					}
				}
			}
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}
	}
}