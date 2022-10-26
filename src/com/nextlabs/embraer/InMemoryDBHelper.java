package com.nextlabs.embraer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hsqldb.Server;

public class InMemoryDBHelper {
	private static final Log LOG = LogFactory.getLog(InMemoryDBHelper.class.getName());
	private static final String DB_NAME = "main";
	private static final String DB_PATH = "mem:main";
	private static final Integer DB_PORT = 9001;
	private static final String DB_USER = "sa";
	private static final String DB_PASSWORD = "";
	private static final String URL = "jdbc:hsqldb:mem:main";
	private static final String DRIVER = "org.hsqldb.jdbc.JDBCDriver";
	private static BasicDataSource dataSource;
	private static Server server;
	private static Set<String> licensesWithUSSublicensees;

	/**
	 * Initialize the in memory db server
	 */
	public synchronized static void initializeServer() {
		if (server == null) {
			server = new Server();
		}
		server.setDatabaseName(0, DB_NAME);
		server.setDatabasePath(0, DB_PATH);
		server.setPort(DB_PORT);

		LOG.info("initalizeServer() Server state is " + server.getStateDescriptor());

		try {
			server.checkRunning(false);
		} catch (Exception e) {
			server.stop();
		}

		server.start();
		LOG.info("initializeServer() completed");
	}

	/**
	 * Initialize the datasource and the connection pool to connect to the in
	 * memory db
	 */
	public synchronized static void initializeConnectionPool() {
		dataSource = new BasicDataSource();

		// basic properties
		dataSource.setDriverClassName(DRIVER);
		dataSource.setUsername(DB_USER);
		dataSource.setPassword(DB_PASSWORD);
		dataSource.setUrl(URL);

		// additional properties
		dataSource.setInitialSize(10);
		dataSource.setMinIdle(10);
		dataSource.setMaxIdle(20);
		dataSource.setMaxWaitMillis(10000);
		dataSource.setTestOnBorrow(true);
		dataSource.setTestWhileIdle(true);
		dataSource.setValidationQuery("select 1 from INFORMATION_SCHEMA.SYSTEM_USERS");
		dataSource.setMaxTotal(30);
		dataSource.setRemoveAbandonedTimeout(300);
		dataSource.setRemoveAbandonedOnBorrow(true);

		LOG.info("initializeConnectionPool() completed");
	}

	/**
	 * Get a database connection from the pool
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
	 * Stop the in memory db server
	 */
	public static void stopServer() {
		if (server != null) {
			server.stop();
		}
	}

	/**
	 * Create the necessary tables in the in memory db
	 */
	public synchronized static void initializeDatabase() {
		Connection connection = null;
		try {
			connection = getDatabaseConnection();

			InputStream is = InMemoryDBHelper.class.getResourceAsStream("databaseInit.sql");

			if (is != null) {
				BufferedReader reader = new BufferedReader(new InputStreamReader(is));

				String line;
				StringBuffer command = new StringBuffer();

				while ((line = reader.readLine()) != null) {
					String trimmedLine = line.trim();

					if (trimmedLine.startsWith("//") || trimmedLine.startsWith("/*") || trimmedLine.startsWith("\\*")) {
						// ignore comment
					} else {
						command.append(trimmedLine);
						command.append(" ");

						if (trimmedLine.endsWith(";")) {
							/*
							 * LOG.
							 * info("initializeDatabase() Executing command " +
							 * command.toString());
							 */

							Statement statement = connection.createStatement();

							statement.execute(command.toString());
							statement.close();

							command = new StringBuffer();
						}
					}
				}
			} else {
				LOG.error("initializeDatabase() Cannot parse the SQL script");
			}

		} catch (IOException e) {
			LOG.error("initializeDatabase() Cannot read the SQL script", e);
		} catch (SQLException e) {
			LOG.error("initializeDatabase() Error encountered when executing SQL script", e);
		} finally {
			if (connection != null) {
				try {
					connection.close();
				} catch (SQLException e) {
				}
			}
		}

		LOG.info("initializeDatabase() completed");
	}

	/**
	 * Get the type of the given license
	 * 
	 * @param license
	 * @return
	 */
	public static String getLicenseType(String license) {
		Connection connection = null;
		PreparedStatement statement = null;
		ResultSet resultSet = null;
		String result = null;
		try {
			connection = getDatabaseConnection();
			statement = connection.prepareStatement("SELECT Type FROM Licenses WHERE Name = ?");
			statement.setString(1, license);
			resultSet = statement.executeQuery();

			if (resultSet.next()) {
				result = resultSet.getString("Type");
			}
		} catch (SQLException e) {
			LOG.error("getLicenseType() " + e.getMessage(), e);
		} finally {
			try {
				if (resultSet != null) {
					resultSet.close();
				}
				if (statement != null) {
					statement.close();
				}
				if (connection != null) {
					connection.close();
				}

			} catch (SQLException ex) {
				LOG.error("getLicenseType() " + ex.getMessage(), ex);
			}
		}
		return result;
	}

	/**
	 * Get licenses that have their approved nationalities containing at least
	 * one of the user citizenship
	 * 
	 * @param citizenship
	 * @return
	 */
	public static List<String> getLicensesByApprovedNationalities(List<String> citizenship, String excludeCitizenship) {
		Connection connection = null;
		PreparedStatement statement = null;
		ResultSet resultSet = null;
		List<String> results = new ArrayList<String>();

		try {
			StringBuilder query = new StringBuilder("SELECT Name from Licenses ");
			connection = getDatabaseConnection();

			query.append("WHERE ID IN (");
			for (int i = 0; i < citizenship.size(); i++) {

				if (citizenship.get(i).equalsIgnoreCase("usa")) {
					continue;
				}

				if (excludeCitizenship != null && citizenship.get(i).equalsIgnoreCase(excludeCitizenship)) {
					continue;
				}

				query.append("SELECT LicenseID FROM ApprovedNationalities ");
				query.append("WHERE NationalityID = (SELECT ID FROM Nationalities WHERE UPPER(Code) = UPPER(?)))");

				if (i != citizenship.size() - 1) {
					query.append(" AND ID IN (");
				}
			}

			String queryString = query.toString();
			if (queryString.endsWith(" AND ID IN (")) {
				queryString = queryString.substring(0, queryString.length() - 12);
			}
			
			if (queryString.endsWith("WHERE ID IN (" )) {
				queryString = queryString.substring(0, queryString.length() - 13);
			}

			//LOG.info("Query is " + queryString);

			statement = connection.prepareStatement(queryString);

			int paramCount = 1;

			for (int i = 0; i < citizenship.size(); i++) {
				if (citizenship.get(i).equalsIgnoreCase("usa")) {
					continue;
				}
				
				if (excludeCitizenship != null && citizenship.get(i).equalsIgnoreCase(excludeCitizenship)) {
					continue;
				}
				
				statement.setString(paramCount, citizenship.get(i).toUpperCase());
				paramCount++;
			}

			resultSet = statement.executeQuery();
			while (resultSet.next()) {
				String license = resultSet.getString("Name");
				results.add(license);
			}
		} catch (SQLException e) {
			LOG.error("getLicensesByApprovedNationalities() " + e.getMessage(), e);
		} finally {
			try {
				if (resultSet != null) {
					resultSet.close();
				}
				if (statement != null) {
					statement.close();
				}
				if (connection != null) {
					connection.close();
				}

			} catch (SQLException ex) {
				LOG.error("getLicensesByApprovedNationalities() " + ex.getMessage(), ex);
			}
		}
		return results;
	}

	/**
	 * Get licenses that have their approved nationalities containing no user
	 * citizenship
	 * 
	 * @param citizenship
	 * @return
	 */
	public static List<String> getLicensesByDeniedNationalities(List<String> citizenship) {
		Connection connection = null;
		PreparedStatement statement = null;
		ResultSet resultSet = null;
		List<String> results = new ArrayList<String>();
		try {
			StringBuilder query = new StringBuilder("SELECT Name from Licenses WHERE Name NOT IN (");
			query.append("SELECT DISTINCT l.Name FROM Licenses l, DeniedNationalities dn, Nationalities n ");
			query.append("WHERE l.ID = dn.LicenseID ");
			query.append("AND dn.NationalityID = n.ID ");
			query.append("AND UPPER(n.Code) IN (");

			for (int i = 0; i < citizenship.size(); i++) {
				query.append("?");
				if (i != citizenship.size() - 1) {
					query.append(",");
				}
			}
			query.append("))");
			// LOG.debug("Query is " + query.toString());

			connection = getDatabaseConnection();
			statement = connection.prepareStatement(query.toString());
			for (int i = 0; i < citizenship.size(); i++) {
				statement.setString(i + 1, citizenship.get(i).toUpperCase());
			}

			resultSet = statement.executeQuery();
			while (resultSet.next()) {
				String license = resultSet.getString("Name");
				results.add(license);
			}
		} catch (SQLException e) {
			LOG.error("getLicensesByDeniedNationalities() " + e.getMessage(), e);
		} finally {
			try {
				if (resultSet != null) {
					resultSet.close();
				}
				if (statement != null) {
					statement.close();
				}
				if (connection != null) {
					connection.close();
				}

			} catch (SQLException ex) {
				LOG.error("getLicensesByDeniedNationalities() " + ex.getMessage(), ex);
			}
		}
		return results;
	}

	/**
	 * Get licenses that their approved entities has a match with the user
	 * employer information and the user citizenship contain the employer
	 * country
	 * 
	 * @param citizenship
	 * @param employer
	 * @param employerCountry
	 * @param type
	 * @return
	 */
	public static List<String> getLicensesByApprovedEntities(List<String> citizenship, String employer,
			String employerCountry, String type) {
		Connection connection = null;
		PreparedStatement statement = null;
		ResultSet resultSet = null;
		List<String> results = new ArrayList<String>();
		Set<String> tempResults = new HashSet<String>();

		try {
			StringBuilder query = new StringBuilder(
					"SELECT DISTINCT l.Name, ae.Type, ae.NDA from Licenses l, ApprovedEntities ae, Entities e, Nationalities n ");
			query.append("WHERE l.ID = ae.LicenseID ");
			query.append("AND ae.EntityID = e.ID ");
			query.append("AND UPPER(e.Code) = UPPER(?) ");
			query.append("AND UPPER(e.Country) = UPPER(n.Code) ");
			query.append("AND UPPER(n.Code) = UPPER(?)");
			if (type != null) {
				query.append(" AND UPPER(ae.Type) = UPPER(?)");
			}

			/* LOG.info("Query is " + query.toString()); */

			connection = getDatabaseConnection();
			statement = connection.prepareStatement(query.toString());
			statement.setString(1, employer);
			statement.setString(2, employerCountry);
			if (type != null) {
				statement.setString(3, type);
			}

			resultSet = statement.executeQuery();
			while (resultSet.next()) {
				String license = resultSet.getString("Name");
				String dType = resultSet.getString("Type");
				int nDA = resultSet.getInt("NDA");

				if (!(dType.equalsIgnoreCase("S") && nDA == 0)) {
					tempResults.add(license);
				}
				// tempResults.add(license);
			}
		} catch (SQLException e) {
			LOG.error("getLicensesByApprovedEntities() " + e.getMessage(), e);
		} finally {
			try {
				if (resultSet != null) {
					resultSet.close();
				}
				if (statement != null) {
					statement.close();
				}
				if (connection != null) {
					connection.close();
				}

			} catch (SQLException ex) {
				LOG.error("getLicensesByApprovedEntities() " + ex.getMessage(), ex);
			}
		}

		try {
			for (String c : citizenship) {
				if (c.equalsIgnoreCase("usa")) {
					if (employerCountry.equalsIgnoreCase("usa")) {
						LOG.debug(
								"US citizen working for US company will be able to access all licenses with 'US-Sublicensees'");
						tempResults.addAll(licensesWithUSSublicensees);
						break;
					}
				}
			}
		} catch (Exception e) {
			LOG.error("getLicensesByApprovedEntities() " + e.getMessage(), e);
		}

		for (String license : tempResults) {
			results.add(license);
		}

		return results;
	}

	/**
	 * Get licenses that the given user has signed an NDA with the user's
	 * employer
	 * 
	 * @param userID
	 * @return
	 */
	public static List<String> getLicensesWithNDA(String userID) {
		Connection connection = null;
		PreparedStatement statement = null;
		ResultSet resultSet = null;
		List<String> results = new ArrayList<String>();
		try {
			StringBuilder query = new StringBuilder("SELECT DISTINCT l.Name from Licenses l, NDA n ");
			query.append("WHERE l.ID = n.LicenseID ");
			query.append("AND UPPER(n.userID) = UPPER(?) ");

			// LOG.debug("Query is " + query.toString());

			connection = getDatabaseConnection();
			statement = connection.prepareStatement(query.toString());
			statement.setString(1, userID);

			resultSet = statement.executeQuery();
			while (resultSet.next()) {
				String license = resultSet.getString("Name");
				results.add(license);
			}
		} catch (SQLException e) {
			LOG.error("getLicensesWithNDA() " + e.getMessage(), e);
		} finally {
			try {
				if (resultSet != null) {
					resultSet.close();
				}
				if (statement != null) {
					statement.close();
				}
				if (connection != null) {
					connection.close();
				}

			} catch (SQLException ex) {
				LOG.error("getLicensesWithNDA() " + ex.getMessage(), ex);
			}
		}
		return results;
	}

	public static void updateLicensesWithUSSublicensees() {
		if (licensesWithUSSublicensees == null) {
			licensesWithUSSublicensees = new HashSet<String>();
		} else {
			licensesWithUSSublicensees.clear();
		}

		Connection connection = null;
		PreparedStatement statement = null;
		ResultSet resultSet = null;
		try {
			StringBuilder query = new StringBuilder("SELECT l.Name from ApprovedEntities ae, Entities e, Licenses  l ");
			query.append(
					"WHERE ae.EntityID = e.ID AND l.ID = ae.LicenseID AND UPPER(e.Code) = UPPER('US-Sublicensees')");
			// LOG.debug("Query is " + query.toString());

			connection = getDatabaseConnection();
			statement = connection.prepareStatement(query.toString());

			resultSet = statement.executeQuery();
			while (resultSet.next()) {
				String license = resultSet.getString("Name");
				licensesWithUSSublicensees.add(license);
			}
		} catch (SQLException e) {
			LOG.error("updateLicensesWithUSSublicensees() " + e.getMessage(), e);
		} finally {
			try {
				if (resultSet != null) {
					resultSet.close();
				}
				if (statement != null) {
					statement.close();
				}
				if (connection != null) {
					connection.close();
				}

			} catch (SQLException ex) {
				LOG.error("updateLicensesWithUSSublicensees() " + ex.getMessage(), ex);
			}
		}

		LOG.info("updateLicensesWithUSSublicensees() There are " + licensesWithUSSublicensees.size()
				+ " licenses with 'US-Sublicensees'");
	}
}
