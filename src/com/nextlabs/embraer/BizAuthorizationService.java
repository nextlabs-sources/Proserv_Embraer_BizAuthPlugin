package com.nextlabs.embraer;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.bluejungle.framework.expressions.EvalValue;
import com.bluejungle.framework.expressions.IEvalValue;
import com.bluejungle.framework.expressions.IMultivalue;
import com.bluejungle.framework.expressions.Multivalue;
import com.bluejungle.framework.expressions.ValueType;
import com.bluejungle.pf.domain.destiny.serviceprovider.IFunctionServiceProvider;
import com.bluejungle.pf.domain.destiny.serviceprovider.IHeartbeatServiceProvider;
import com.bluejungle.pf.domain.destiny.serviceprovider.ServiceProviderException;

public class BizAuthorizationService implements IFunctionServiceProvider, IHeartbeatServiceProvider {

	private static final Log LOG = LogFactory.getLog(BizAuthorizationService.class.getName());
	public static Properties properties = null;
	public static final String PROPS_FILE_PATH = "/jservice/config/BizAuthorizationPlugin.properties";

	@Override
	public void init() throws Exception {
		LOG.info("init() started");

		properties = PropertyLoader.loadProperties(PROPS_FILE_PATH);
		if (properties == null) {
			LOG.error("init() Cannot load properties file");
		}

		try {
			// initialize connection pool for external db
			ExternalDBHelper.initializeConnectionPool(properties);

			// initialize in-memory db
			InMemoryDBHelper.initializeServer();
			InMemoryDBHelper.initializeConnectionPool();
			InMemoryDBHelper.initializeDatabase();

		} catch (Exception e) {
			LOG.error("init() " + e.getMessage(), e);
		}

		LOG.info("init() finished");
	}

	@Override
	public Serializable prepareRequest(String arg0) {
		return null;
	}

	@Override
	public void processResponse(String pluginID, String data) {
		long time = ExternalDBHelper.getLastSyncTime();
		if (time != ExternalDBHelper.lastSyncTime) {
			LOG.info("processResponse() In-memory database is not up-to-date");
			try {
				DatabaseSynchronization.sync(time);
			} catch (SQLException e) {
				LOG.error(
						"processResponse() Could not sync the two databases and the transaction could not be rolled back"
								+ e.getMessage(),
						e);
			}

			InMemoryDBHelper.updateLicensesWithUSSublicensees();

		} else {
			LOG.info("processResponse() In-memory database is up-to-date");
		}
	}

	@Override
	public IEvalValue callFunction(String arg0, IEvalValue[] arg1) throws ServiceProviderException {
		LOG.info("callFunction() Function called is " + arg0);
		IEvalValue result = getNullReturn(arg0);
		long lCurrentTime = System.nanoTime();

		ArrayList<ArrayList<String>> params = null;

		// process parameter list
		try {
			params = processValues(arg1);
		} catch (Exception e) {
			LOG.error("callFunction() Unable to process the parameter list");
			return result;
		}

		if (params == null) {
			LOG.error("callFunction() Could not retrieve parameter list. Operation aborted");
			return result;
		}

		if (!validateDatabase()) {
			LOG.error("callFunction() In-memory database is in an invalid state");
			return result;
		}

		// call appropriate function
		switch (arg0) {
		case "getAllowedLicensesForUser":
			result = getAllowedLicensesForUser(params);

			break;
		case "getLicenses":
			if (params.size() != 2) {
				LOG.error("callFunction() Invalid parameter passed to function. Received " + params.size()
						+ " params. Expected 2");
				return result;
			}
			result = getLicenses(params.get(0), params.get(1));

			break;
		case "countLicenses":
			if (params.size() != 2) {
				LOG.error("callFunction() Invalid parameter passed to function. Received " + params.size()
						+ " params. Expected 2");
				return result;
			}
			result = countLicenses(params.get(0), params.get(1));

			break;
		case "validateLicenses":
			if (params.size() != 1) {
				LOG.error("callFunction() Invalid parameter passed to function. Received " + params.size()
						+ " params. Expected 2");
				return result;
			}
			result = validateLicenses(params.get(0));

			break;
		case "countCitizenship":
			if (params.size() != 1) {
				LOG.error("callFunction() Invalid parameter passed to function. Received " + params.size()
						+ " params. Expected 1");
				return result;
			}

			if (params.get(0) == null) {
				LOG.error("callFunction() The citizenship list received is null");
				return result;
			}

			LOG.info("Count returned: " + params.get(0).size());

			result = EvalValue.build(params.get(0).size());

			break;
		case "checkCitizenship":
			if (params.size() != 2) {
				LOG.error("callFunction() Invalid parameter passed to function. Received " + params.size()
						+ " params. Expected 2");
				return result;
			}

			result = checkCitizenship(params.get(0), params.get(1));

			break;

		default:
			LOG.warn("callFunction() Operation not support");
			break;
		}

		LOG.info("callfunction() completed without any interruption.  Time spent: "
				+ ((System.nanoTime() - lCurrentTime) / 1000000.00) + "ms");

		return result;
	}

	/**
	 * Get licenses that the user is allowed to used based on a specific
	 * condition
	 * 
	 * @param params
	 * @return
	 */
	private IEvalValue getAllowedLicensesForUser(ArrayList<ArrayList<String>> params) {
		IEvalValue result = getNullReturn("getAllowedLicensesForUser");

		String condition = null;
		try {
			condition = params.get(0).get(0);
		} catch (Exception e) {
			LOG.error("getAllowedLicensesForUser() Could not get the condition");
		}

		if (condition != null) {
			try {
				switch (condition) {
				case "approvedNationalities":
					if (params.size() == 2) {
						result = EvalValue.build(Multivalue
								.create(getLicensesByApprovedNationalities(params.get(1), null), ValueType.STRING));
					} else if (params.size() == 3) {
						result = EvalValue.build(Multivalue.create(
								getLicensesByApprovedNationalities(params.get(1), params.get(2)), ValueType.STRING));
					} else {
						LOG.error("getAllowedLicensesForUser() Invalid parameter passed to function. Received "
								+ params.size() + " params. Expected at least 2");
					}
					break;
				case "deniedNationalities":
					if (params.size() == 2) {
						result = EvalValue.build(
								Multivalue.create(getLicensesByDeniedNationalities(params.get(1)), ValueType.STRING));
					} else {
						LOG.error("getAllowedLicensesForUser() Invalid parameter passed to function. Received "
								+ params.size() + " params. Expected 2");
					}
					break;
				case "approvedEntities":
					if (params.size() == 3) {
						result = EvalValue.build(Multivalue.create(getLicensesByApprovedEntities(params.get(1),
								params.get(2).get(0), params.get(3).get(0), null), ValueType.STRING));
					} else {
						LOG.error("getAllowedLicensesForUser() Invalid parameter passed to function. Received "
								+ params.size() + " params. Expected 3");
					}
					break;
				case "approvedParties":
					if (params.size() == 3) {
						result = EvalValue.build(Multivalue.create(getLicensesByApprovedEntities(params.get(1),
								params.get(2).get(0), params.get(3).get(0), "P"), ValueType.STRING));
					} else {
						LOG.error("getAllowedLicensesForUser() Invalid parameter passed to function. Received "
								+ params.size() + " params. Expected 3");
					}
					break;
				case "approvedSublicensees":
					if (params.size() == 3) {
						result = EvalValue.build(Multivalue.create(getLicensesByApprovedEntities(params.get(1),
								params.get(2).get(0), params.get(3).get(0), "S"), ValueType.STRING));
					} else {
						LOG.error("getAllowedLicensesForUser() Invalid parameter passed to function. Received "
								+ params.size() + " params. Expected 3");
					}
					break;
				case "NDASigned":
					result = EvalValue.build(Multivalue.create(getLicensesWithNDA(params.get(1).get(0))));
					break;
				default:
					break;
				}
			} catch (IndexOutOfBoundsException e) {
				LOG.error("getAllowedLicensesForUser() Not enough paramerters were supplied");
			} catch (NullPointerException e) {
				LOG.error("getAllowedLicensesForUser() Parmeter supplied is null");
			}
		}

		return result;
	}

	/**
	 * Get licenses that the user is allowed to used based on the approved
	 * nationalities
	 * 
	 * @param citizenship
	 * @return
	 */
	private List<String> getLicensesByApprovedNationalities(List<String> citizenship, List<String> excludeCitizenship) {
		List<String> results = new ArrayList<String>();
		if (citizenship == null || citizenship.size() == 0) {
			LOG.error("getLicensesByApprovedNationalities() Cannot retrieve user citizenship");
			return results;
		}

		if (excludeCitizenship != null && excludeCitizenship.size() == 1) {
			LOG.debug("getLicensesByApprovedNationalities() excludeCitizenship is " + excludeCitizenship.get(0));
			results = InMemoryDBHelper.getLicensesByApprovedNationalities(citizenship, excludeCitizenship.get(0));
		} else {
			results = InMemoryDBHelper.getLicensesByApprovedNationalities(citizenship, null);
		}

		LOG.info("Licenses returned: " + results);

		return results;
	}

	/**
	 * Get licenses that the user is allowed to used based on the denied
	 * nationalities
	 * 
	 * @param citizenship
	 * @return
	 */
	private List<String> getLicensesByDeniedNationalities(List<String> citizenship) {
		List<String> results = new ArrayList<String>();
		if (citizenship == null || citizenship.size() == 0) {
			LOG.error("getLicensesByDeniedNationalities() Cannot retrieve user citizenship");
			return results;
		}

		results = InMemoryDBHelper.getLicensesByDeniedNationalities(citizenship);

		LOG.info("Licenses returned: " + results);

		return results;
	}

	/**
	 * Get licenses that the user is allowed to used based on the approved
	 * parties
	 * 
	 * @param citizenship
	 * @param employer
	 * @param employerCountry
	 * @param type
	 * @return
	 */
	private List<String> getLicensesByApprovedEntities(List<String> citizenship, String employer,
			String employerCountry, String type) {
		List<String> results = new ArrayList<String>();
		if (citizenship == null || citizenship.size() == 0) {
			LOG.error("getLicensesByApprovedEntities() Cannot retrieve user citizenship");
			return results;
		}

		if (employer == null) {
			LOG.error("getLicensesByApprovedEntities() Cannot retrieve user employer");
			return results;
		}

		if (employerCountry == null) {
			LOG.error("getLicensesByApprovedEntities() Cannot retrieve user employer country");
			return results;
		}

		boolean citizenshipMatches = false;
		for (String country : citizenship) {
			if (country.equalsIgnoreCase(employerCountry)) {
				citizenshipMatches = true;
			}
		}

		if (!citizenshipMatches) {
			LOG.info("getLicensesByApprovedEntities() User citizenship do not contain the employer country.");
			// return results;
		}

		results = InMemoryDBHelper.getLicensesByApprovedEntities(citizenship, employer, employerCountry, type);

		LOG.info("Licenses returned: " + results);

		return results;
	}

	/**
	 * Get licenses that the user is allowed to used based on NDA check
	 * 
	 * @param userID
	 * @return
	 */
	private List<String> getLicensesWithNDA(String userID) {
		List<String> results = new ArrayList<String>();
		if (userID == null) {
			LOG.error("getLicensesWithNDA() Cannot retrieve userID");
			return results;
		}

		results = InMemoryDBHelper.getLicensesWithNDA(userID);

		LOG.info("Licenses returned: " + results);

		return results;
	}

	/**
	 * Return the licenses among the input list based on input type
	 * 
	 * @param licenses
	 * @return
	 */
	private IEvalValue getLicenses(List<String> licenses, List<String> type) {
		IEvalValue result = getNullReturn("getLicenses");
		List<String> results = new ArrayList<String>();
		if (licenses == null || type == null || type.get(0) == null) {
			LOG.error("getLicenses() Cannot get the necessary input");
			return result;
		}

		for (String license : licenses) {
			String licenseType = InMemoryDBHelper.getLicenseType(license);
			if (licenseType != null && licenseType.equalsIgnoreCase(type.get(0))) {
				results.add(license);
			}
		}

		IMultivalue imv = Multivalue.create(results, ValueType.STRING);
		result = EvalValue.build(imv);

		LOG.info("Licenses returned: " + results);

		return result;
	}

	/**
	 * Return the number of licenses in the input list based on input type
	 * 
	 * @param licenses
	 * @return
	 */
	private IEvalValue countLicenses(List<String> licenses, List<String> type) {
		IEvalValue result = getNullReturn("countLicenses");
		int count = 0;
		if (licenses == null || type == null || type.get(0) == null) {
			LOG.error("countLicenses() Cannot get the necessary input");
			return result;
		}

		for (String license : licenses) {
			String licenseType = InMemoryDBHelper.getLicenseType(license);
			if (licenseType != null && licenseType.equalsIgnoreCase(type.get(0))) {
				count++;
			}
		}

		result = EvalValue.build(count);

		LOG.info("Count returned: " + count);

		return result;
	}

	/**
	 * Check if all licenses in the input list presented in the database
	 * 
	 * @param licenses
	 * @return invalid if not all licenses are found
	 */
	private IEvalValue validateLicenses(List<String> licenses) {
		IEvalValue result = EvalValue.build("valid");

		if (licenses == null) {
			LOG.error("validateLicenses() Cannot get the necessary input");
			return result;
		}

		for (String license : licenses) {
			if (InMemoryDBHelper.getLicenseType(license) == null) {
				result = EvalValue.build("invalid");
				return result;
			}
		}

		return result;

	}

	/**
	 * Return true if the citizenship contains the employer country
	 * 
	 * @param citizenship
	 * @param employerCountry
	 * @return
	 */
	private IEvalValue checkCitizenship(List<String> citizenship, List<String> employerCountry) {
		IEvalValue result = getNullReturn("checkCitizenship");

		if (citizenship == null || employerCountry == null || employerCountry.get(0) == null) {
			LOG.error("checkCitizenship() Cannot get the necessary input");
			return result;
		}

		for (String c : citizenship) {
			if (c.equalsIgnoreCase(employerCountry.get(0))) {
				LOG.debug("checkCitizenship() User citizenship contains " + employerCountry.get(0));
				return EvalValue.build("true");
			}
		}

		return EvalValue.build("false");
	}

	/**
	 * Check if the in memory database is valid by checking the last sync time
	 * 
	 * @return
	 */
	private boolean validateDatabase() {
		if (ExternalDBHelper.lastSyncTime == -1) {
			LOG.warn("validateDatabase() In-memory database has never been synchronized with the external database");
			return false;
		}
		return true;
	}

	/**
	 * Get the correct default return if not found to avoid pdp incompatible
	 * operation
	 * 
	 * @param function
	 * @return
	 */
	private IEvalValue getNullReturn(String function) {
		IEvalValue result = EvalValue.NULL;
		switch (function) {
		case "getAllowedLicensesForUser":
			result = EvalValue.build(IMultivalue.EMPTY);
			break;
		case "getLicenses":
			result = EvalValue.build(IMultivalue.EMPTY);
			break;
		case "countLicenses":
			result = EvalValue.NULL;
			break;
		case "countCitizenship":
			result = EvalValue.NULL;
			break;
		case "checkCitizenship":
			result = EvalValue.NULL;
			break;
		default:
			LOG.warn("getNullReturn() Operation not support");
			result = EvalValue.NULL;
			break;
		}

		return result;
	}

	/**
	 * Process the input arguments and return an array of arrays of strings
	 * 
	 * @param args
	 * @return
	 * @throws Exception
	 */
	private ArrayList<ArrayList<String>> processValues(IEvalValue[] args) throws Exception {
		LOG.info("processValues() entered ");
		ArrayList<ArrayList<String>> sOutData = new ArrayList<ArrayList<String>>();

		for (IEvalValue ieValue : args) {
			LOG.info("ieValue " + ieValue.toString());
			if (ieValue != null) {
				if (ieValue.getType() == ValueType.MULTIVAL) {
					ArrayList<String> list = new ArrayList<String>();
					IMultivalue value = (IMultivalue) ieValue.getValue();
					Iterator<IEvalValue> ievIter = value.iterator();

					while (ievIter.hasNext()) {
						IEvalValue iev = ievIter.next();

						if (iev != null) {
							if (!iev.getValue().toString().isEmpty()) {
								list.add(iev.getValue().toString());
								LOG.debug("processValues() Processed value:" + iev.getValue().toString());
							}
						}
					}
					sOutData.add(list);
				} else if (!ieValue.getValue().toString().isEmpty()) {
					ArrayList<String> list = new ArrayList<String>();
					list.add(ieValue.getValue().toString());
					sOutData.add(list);
				}
			}

		}
		LOG.info("processValues() Input Data: " + sOutData);
		return sOutData;
	}

	public static void main(String[] args) {
		try {
			Properties props = new Properties();
			props.setProperty("db-driver", "oracle.jdbc.driver.OracleDriver");
			props.setProperty("db-user", "embraeradmin");
			props.setProperty("db-password", "Nextlabs123");
			props.setProperty("db-url", "jdbc:oracle:thin:@10.23.58.112:1521:GENORCL");

			/*
			 * Test database
			 */
			ExternalDBHelper.initializeConnectionPool(props);
			InMemoryDBHelper.initializeServer();
			InMemoryDBHelper.initializeConnectionPool();
			try {
				InMemoryDBHelper.initializeDatabase();
			} catch (Exception e) {
				LOG.error(e.getMessage(), e);
				e.printStackTrace();
			}

			long time = ExternalDBHelper.getLastSyncTime();
			if (time != ExternalDBHelper.lastSyncTime) {
				LOG.info("processResponse() In-memory database is not up-to-date");
				try {
					DatabaseSynchronization.sync(time);
				} catch (SQLException e) {
					LOG.error(
							"processResponse() Could not sync the two databases and the transaction could not be rolled back"
									+ e.getMessage(),
							e);
				}
			} else {
				LOG.info("processResponse() In-memory database is up-to-date");
			}

			InMemoryDBHelper.updateLicensesWithUSSublicensees();

			/*
			 * Test approved nationalities
			 */
			String[] citizenshipArr1 = { "ITA", "USA" };
			List<String> citizenship = Arrays.asList(citizenshipArr1);
			String[] excludeCitizenshipArr = { "ITA" };
			List<String> excludeCitizenship = Arrays.asList(excludeCitizenshipArr);

			BizAuthorizationService service = new BizAuthorizationService();

			LOG.info("Approved Nationalities test");
			List<String> apResult = service.getLicensesByApprovedNationalities(citizenship, excludeCitizenship);
			LOG.info("License count : " + apResult.size());

			/*
			 * Test denied nationalities
			 */
			String[] citizenshipArr3 = { "BOL", "HND" };
			citizenship = Arrays.asList(citizenshipArr3);

			LOG.info("Denied Nationalities test");
			service.getLicensesByDeniedNationalities(citizenship);

			/*
			 * Test approved entities
			 */
			String[] citizenshipArr2 = { "BRA", "USA" };
			citizenship = Arrays.asList(citizenshipArr2);
			String employer = "MLB";
			String employerCountry = "USA";

			LOG.info("Approved Entities test");
			LOG.info(service.getLicensesByApprovedEntities(citizenship, employer, employerCountry, null).size());

			/*
			 * Test NDA
			 */
			String userID = "EMPLOYEE 1";

			LOG.info("NDA test");
			service.getLicensesWithNDA(userID);

			LOG.info("checkCitizenship test");
			List<String> companyCountry = new ArrayList<String>();
			companyCountry.add("bra");
			LOG.info(service.checkCitizenship(citizenship, companyCountry).getValue());
			
			/*
			 * Test validateLicenses
			 */
			String[] licensesArr = { "TA0372-12", "D10210814" };
			List<String >licenseList = Arrays.asList(licensesArr); 
			LOG.info(service.validateLicenses(licenseList).getValue());
			

			InMemoryDBHelper.stopServer();
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		}
	}
}
