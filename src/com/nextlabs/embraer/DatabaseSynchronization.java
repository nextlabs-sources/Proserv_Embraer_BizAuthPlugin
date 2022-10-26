package com.nextlabs.embraer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class DatabaseSynchronization {
	private static final Log LOG = LogFactory.getLog(ExternalDBHelper.class.getName());
	private static final String CLEAR_TABLES_SQL = "DELETE FROM ";
	private static final String INSERT_LICENSES_SQL = "INSERT INTO Licenses (ID, Name, Type) VALUES (?, ?, ?)";
	private static final String INSERT_ENTITIES_SQL = "INSERT INTO Entities (ID, Code, Name, Country) VALUES (?, ?, ?, ?)";
	private static final String INSERT_NATIONALTIES_SQL = "INSERT INTO Nationalities (ID, Name, Code) VALUES (?, ?, ?)";
	private static final String INSERT_APPROVED_ENTITIES_SQL = "INSERT INTO ApprovedEntities (ID, LicenseID, EntityID, Type, NDA) VALUES (?, ?, ?, ?, ?)";
	private static final String INSERT_APPROVED_NATINALITIES_SQL = "INSERT INTO ApprovedNationalities (ID, LicenseID, NationalityID) VALUES (?, ?, ?)";
	private static final String INSERT_DENIED_NATINALITIES_SQL = "INSERT INTO DeniedNationalities (ID, LicenseID, NationalityID) VALUES (?, ?, ?)";
	private static final String INSERT_NDA_SQL = "INSERT INTO NDA (ID, UserID, LicenseID) VALUES (?, ?, ?)";
	private static final String SELECT_LICENSES_SQL = "SELECT * FROM Licenses";
	private static final String SELECT_ENTITIES_SQL = "SELECT * FROM Entities";
	private static final String SELECT_NATIONALITIES_SQL = "SELECT * FROM Nationalities";
	private static final String SELECT_APPROVED_ENTITIES_SQL = "SELECT * FROM ApprovedEntities";
	private static final String SELECT_APPROVED_NATIONALITIES_SQL = "SELECT * FROM ApprovedNationalities";
	private static final String SELECT_DENIED_NATIONALITIES_SQL = "SELECT * FROM DeniedNationalities";
	private static final String SELECT_NDA_SQL = "SELECT * FROM NDA";

	/**
	 * Synchronize the main db and the in memory db
	 * 
	 * @param newSyncTime
	 *            The last sync time retrieved from the main db
	 * @throws SQLException
	 *             When the rollback transaction cannot complete
	 */
	public synchronized static void sync(long newSyncTime) throws SQLException {
		Connection eConnection = null;
		PreparedStatement eStatement = null;
		ResultSet resultSet = null;

		Connection iConnection = null;
		PreparedStatement iStatement = null;

		try {
			iConnection = InMemoryDBHelper.getDatabaseConnection();
			eConnection = ExternalDBHelper.getDatabaseConnection();

			iConnection.setAutoCommit(false);

			// clear old data from table
			iStatement = iConnection.prepareStatement(CLEAR_TABLES_SQL + "ApprovedEntities");
			iStatement.executeUpdate();
			iStatement.close();

			iStatement = iConnection.prepareStatement(CLEAR_TABLES_SQL + "ApprovedNationalities");
			iStatement.executeUpdate();
			iStatement.close();

			iStatement = iConnection.prepareStatement(CLEAR_TABLES_SQL + "DeniedNationalities");
			iStatement.executeUpdate();
			iStatement.close();

			iStatement = iConnection.prepareStatement(CLEAR_TABLES_SQL + "NDA");
			iStatement.executeUpdate();
			iStatement.close();

			iStatement = iConnection.prepareStatement(CLEAR_TABLES_SQL + "Licenses");
			iStatement.executeUpdate();
			iStatement.close();

			iStatement = iConnection.prepareStatement(CLEAR_TABLES_SQL + "Entities");
			iStatement.executeUpdate();
			iStatement.close();

			iStatement = iConnection.prepareStatement(CLEAR_TABLES_SQL + "Nationalities");
			iStatement.executeUpdate();
			iStatement.close();

			LOG.info("sync() Finish clearing old tables");

			boolean emptyBatch = true;

			// update licenses
			eStatement = eConnection.prepareStatement(SELECT_LICENSES_SQL);
			resultSet = eStatement.executeQuery();
			iStatement = iConnection.prepareStatement(INSERT_LICENSES_SQL);

			while (resultSet.next()) {
				iStatement.setLong(1, resultSet.getLong("ID"));
				iStatement.setString(2, resultSet.getString("Name"));
				iStatement.setString(3, resultSet.getString("Type"));
				iStatement.addBatch();
				emptyBatch = false;
			}

			if (!emptyBatch) {
				iStatement.executeBatch();
			}
			iStatement.close();
			resultSet.close();
			eStatement.close();

			LOG.info("sync() Finish updating licenses");

			// update entities
			emptyBatch = true;
			eStatement = eConnection.prepareStatement(SELECT_ENTITIES_SQL);
			resultSet = eStatement.executeQuery();
			iStatement = iConnection.prepareStatement(INSERT_ENTITIES_SQL);

			while (resultSet.next()) {
				iStatement.setLong(1, resultSet.getLong("ID"));
				iStatement.setString(2, resultSet.getString("Code"));
				iStatement.setString(3, resultSet.getString("Name"));
				iStatement.setString(4, resultSet.getString("Country"));
				iStatement.addBatch();
				emptyBatch = false;
			}

			if (!emptyBatch) {
				iStatement.executeBatch();
			}
			iStatement.close();
			resultSet.close();
			eStatement.close();

			LOG.info("sync() Finish updating entities");

			// update nationalities
			emptyBatch = true;
			eStatement = eConnection.prepareStatement(SELECT_NATIONALITIES_SQL);
			resultSet = eStatement.executeQuery();
			iStatement = iConnection.prepareStatement(INSERT_NATIONALTIES_SQL);

			while (resultSet.next()) {
				iStatement.setLong(1, resultSet.getLong("ID"));
				iStatement.setString(2, resultSet.getString("Name"));
				iStatement.setString(3, resultSet.getString("Code"));
				iStatement.addBatch();
				emptyBatch = false;
			}

			if (!emptyBatch) {
				iStatement.executeBatch();
			}
			iStatement.close();
			resultSet.close();
			eStatement.close();

			LOG.info("sync() Finish updating nationalities");

			// update approved entities
			emptyBatch = true;
			eStatement = eConnection.prepareStatement(SELECT_APPROVED_ENTITIES_SQL);
			resultSet = eStatement.executeQuery();
			iStatement = iConnection.prepareStatement(INSERT_APPROVED_ENTITIES_SQL);

			while (resultSet.next()) {
				iStatement.setLong(1, resultSet.getLong("ID"));
				iStatement.setLong(2, resultSet.getLong("LicenseID"));
				iStatement.setLong(3, resultSet.getLong("EntityID"));
				iStatement.setString(4, resultSet.getString("Type"));
				iStatement.setBoolean(5, (resultSet.getInt("NDA") == 0) ? false : true);
				iStatement.addBatch();
				emptyBatch = false;
			}

			if (!emptyBatch) {
				iStatement.executeBatch();
			}
			iStatement.close();
			resultSet.close();
			eStatement.close();

			LOG.info("sync() Finish updating approved entities");

			// update approved nationalities
			emptyBatch = true;
			eStatement = eConnection.prepareStatement(SELECT_APPROVED_NATIONALITIES_SQL);
			resultSet = eStatement.executeQuery();
			iStatement = iConnection.prepareStatement(INSERT_APPROVED_NATINALITIES_SQL);

			while (resultSet.next()) {
				iStatement.setLong(1, resultSet.getLong("ID"));
				iStatement.setLong(2, resultSet.getLong("LicenseID"));
				iStatement.setLong(3, resultSet.getLong("NationalityID"));
				iStatement.addBatch();
				emptyBatch = false;
			}

			if (!emptyBatch) {
				iStatement.executeBatch();
			}
			iStatement.close();
			resultSet.close();
			eStatement.close();

			LOG.info("sync() Finish updating approved nationalities");

			// update denied nationalities
			emptyBatch = true;
			eStatement = eConnection.prepareStatement(SELECT_DENIED_NATIONALITIES_SQL);
			resultSet = eStatement.executeQuery();
			iStatement = iConnection.prepareStatement(INSERT_DENIED_NATINALITIES_SQL);

			while (resultSet.next()) {
				iStatement.setLong(1, resultSet.getLong("ID"));
				iStatement.setLong(2, resultSet.getLong("LicenseID"));
				iStatement.setLong(3, resultSet.getLong("NationalityID"));
				iStatement.addBatch();
				emptyBatch = false;
			}

			if (!emptyBatch) {
				iStatement.executeBatch();
			}
			iStatement.close();
			resultSet.close();
			eStatement.close();

			LOG.info("sync() Finish updating denied nationalities");

			// update NDA
			emptyBatch = true;
			eStatement = eConnection.prepareStatement(SELECT_NDA_SQL);
			resultSet = eStatement.executeQuery();
			iStatement = iConnection.prepareStatement(INSERT_NDA_SQL);

			while (resultSet.next()) {
				iStatement.setLong(1, resultSet.getLong("ID"));
				iStatement.setString(2, resultSet.getString("UserID"));
				iStatement.setLong(3, resultSet.getLong("LicenseID"));
				iStatement.addBatch();
				emptyBatch = false;
			}

			if (!emptyBatch) {
				iStatement.executeBatch();
			}
			iStatement.close();
			resultSet.close();
			eStatement.close();

			LOG.info("sync() Finish updating nda");

			iConnection.commit();

			LOG.info("sync() Commit completed");

			ExternalDBHelper.lastSyncTime = newSyncTime;

			LOG.info("sync() Sync time updated");

		} catch (SQLException e) {
			LOG.error("sync() Could not sync the two database. Transaction will be rolled back: " + e.getMessage(), e);
			iConnection.rollback();

		} finally {
			try {
				if (resultSet != null && !resultSet.isClosed()) {
					resultSet.close();
				}

				if (eStatement != null && !eStatement.isClosed()) {
					eStatement.close();
				}

				if (iStatement != null && !iStatement.isClosed()) {
					iStatement.close();
				}

				if (iConnection != null && !iConnection.isClosed()) {
					iConnection.close();
				}

				if (eConnection != null && !eConnection.isClosed()) {
					eConnection.close();
				}
			} catch (SQLException ex) {
				LOG.error("sync(): " + ex.getMessage(), ex);
			}
		}
	}
}
