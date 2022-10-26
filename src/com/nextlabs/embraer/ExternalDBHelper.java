package com.nextlabs.embraer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Properties;

import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class ExternalDBHelper {
	private static final Log LOG = LogFactory.getLog(ExternalDBHelper.class.getName());
	private static BasicDataSource dataSource;
	public static long lastSyncTime = -1;

	/**
	 * Initialize the datasource and the connection pool to the main db
	 * 
	 * @param props
	 */
	public synchronized static void initializeConnectionPool(Properties props) {
		dataSource = new BasicDataSource();

		// basic properties
		dataSource.setDriverClassLoader(oracle.jdbc.driver.OracleDriver.class.getClassLoader());
		dataSource.setDriverClassName(props.getProperty("db-driver"));
		dataSource.setUsername(props.getProperty("db-user"));
		dataSource.setPassword(props.getProperty("db-password"));
		dataSource.setUrl(props.getProperty("db-url"));

		// additional properties
		dataSource.setInitialSize(10);
		dataSource.setMinIdle(10);
		dataSource.setMaxIdle(20);
		dataSource.setMaxWaitMillis(10000);
		dataSource.setTestOnBorrow(true);
		dataSource.setTestWhileIdle(true);

		String dataProvider = props.getProperty("db-provider", "oracle").toString();

		if (dataProvider.equalsIgnoreCase("oracle")) {
			dataSource.setValidationQuery("select 1 from dual");
		} else {
			// work for MySQL, Microsoft SQL Server, PostgresSQL, Ingres, Derby,
			// H2 and Firebird
			dataSource.setValidationQuery("select 1");
		}
		dataSource.setMaxTotal(30);
		dataSource.setRemoveAbandonedTimeout(300);
		dataSource.setRemoveAbandonedOnBorrow(true);

		LOG.info("initializeConnectionPool() completed");
	}

	/**
	 * Get a connection from the pool
	 * 
	 * @return
	 * @throws SQLException
	 */
	public static Connection getDatabaseConnection() throws SQLException {
		if (dataSource == null) {
			LOG.error("getDatabaseConnection() dataSource is not configured");
			throw new SQLException("dataSource is not configured");
		}

		Connection connection = null;
		connection = dataSource.getConnection();

		return connection;
	}

	/**
	 * Get the last sync from from the main db
	 * 
	 * @return
	 */
	public static long getLastSyncTime() {
		long result = -1;
		Connection connection = null;
		PreparedStatement statement = null;
		ResultSet resultSet = null;

		try {
			connection = ExternalDBHelper.getDatabaseConnection();
			statement = connection.prepareStatement("SELECT * FROM Status");
			resultSet = statement.executeQuery();
			if (resultSet.next()) {
				result = resultSet.getLong("LastSyncTime");

				LOG.info("getLastSyncTime() Last sync time is " + new Timestamp(result));
			}
		} catch (SQLException e) {
			LOG.error("getLastSyncTime() " + e.getMessage(), e);

		} finally {
			try {
				if (resultSet != null && !resultSet.isClosed()) {
					resultSet.close();
				}

				if (statement != null && !statement.isClosed()) {
					statement.close();
				}

				if (connection != null && !connection.isClosed()) {
					connection.close();
				}

			} catch (SQLException ex) {
				LOG.error("sync(): " + ex.getMessage(), ex);
			}
		}
		return result;
	}

}
