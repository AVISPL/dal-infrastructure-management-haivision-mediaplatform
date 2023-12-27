/*
 *  Copyright (c) 2023 AVI-SPL, Inc. All Rights Reserved.
 */

package com.avispl.symphony.dal.infrastructure.management.haivision.mediaplatform;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.util.CollectionUtils;

import com.fasterxml.jackson.databind.JsonNode;
import javax.security.auth.login.FailedLoginException;

import com.avispl.symphony.api.dal.control.Controller;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import com.avispl.symphony.api.dal.dto.monitor.aggregator.AggregatedDevice;
import com.avispl.symphony.api.dal.error.ResourceNotReachableException;
import com.avispl.symphony.api.dal.monitor.Monitorable;
import com.avispl.symphony.api.dal.monitor.aggregator.Aggregator;
import com.avispl.symphony.dal.communicator.RestCommunicator;
import com.avispl.symphony.dal.infrastructure.management.haivision.mediaplatform.common.EnumTypeHandler;
import com.avispl.symphony.dal.infrastructure.management.haivision.mediaplatform.common.HaivisionMediaPlatformCommand;
import com.avispl.symphony.dal.infrastructure.management.haivision.mediaplatform.common.HaivisionMediaPlatformConstant;
import com.avispl.symphony.dal.infrastructure.management.haivision.mediaplatform.common.SystemInfo;
import com.avispl.symphony.dal.infrastructure.management.haivision.mediaplatform.common.filter.DeviceTypeFilterEnum;
import com.avispl.symphony.dal.util.StringUtils;


public class HaivisionMediaPlatformCommunicator extends RestCommunicator implements Aggregator, Monitorable, Controller {
	/**
	 * Process that is running constantly and triggers collecting data from Haivision Media Platform API endpoints, based on the given timeouts and thresholds.
	 *
	 * @author Harry
	 * @since 1.0.0
	 */
	class HaivisionDataLoader implements Runnable {
		private volatile boolean inProgress;
		private volatile boolean flag = false;

		public HaivisionDataLoader() {
			inProgress = true;
		}

		@Override
		public void run() {
			loop:
			while (inProgress) {
				try {
					TimeUnit.MILLISECONDS.sleep(500);
				} catch (InterruptedException e) {
					// Ignore for now
				}

				if (!inProgress) {
					break loop;
				}

				// next line will determine whether Haivision monitoring was paused
				updateAggregatorStatus();
				if (devicePaused) {
					continue loop;
				}
				if (logger.isDebugEnabled()) {
					logger.debug("Fetching other than aggregated device list");
				}
				long currentTimestamp = System.currentTimeMillis();
				if (!flag && nextDevicesCollectionIterationTimestamp <= currentTimestamp) {
					populateDeviceDetails();
					flag = true;
				}

				while (nextDevicesCollectionIterationTimestamp > System.currentTimeMillis()) {
					try {
						TimeUnit.MILLISECONDS.sleep(1000);
					} catch (InterruptedException e) {
						//
					}
				}

				if (!inProgress) {
					break loop;
				}
				if (flag) {
					nextDevicesCollectionIterationTimestamp = System.currentTimeMillis() + 30000;
					flag = false;
				}

				if (logger.isDebugEnabled()) {
					logger.debug("Finished collecting devices statistics cycle at " + new Date());
				}
			}
			// Finished collecting
		}

		/**
		 * Triggers main loop to stop
		 */
		public void stop() {
			inProgress = false;
		}
	}

	/**
	 * Private variable representing the local extended statistics.
	 */
	private ExtendedStatistics localExtendedStatistics;

	/**
	 * A private final ReentrantLock instance used to provide exclusive access to a shared resource
	 * that can be accessed by multiple threads concurrently. This lock allows multiple reentrant
	 * locks on the same shared resource by the same thread.
	 */
	private final ReentrantLock reentrantLock = new ReentrantLock();

	/**
	 * Indicates whether a device is considered as paused.
	 * True by default so if the system is rebooted and the actual value is lost -> the device won't start stats
	 * collection unless the {@link com.avispl.symphony.dal.infrastructure.management.haivision.mediaplatform.HaivisionMediaPlatformCommunicator#retrieveMultipleStatistics()} method is called which will change it
	 * to a correct value
	 */
	private volatile boolean devicePaused = true;

	/**
	 * We don't want the statistics to be collected constantly, because if there's not a big list of devices -
	 * new devices' statistics loop will be launched before the next monitoring iteration. To avoid that -
	 * this variable stores a timestamp which validates it, so when the devices' statistics is done collecting, variable
	 * is set to currentTime + 30s, at the same time, calling {@link #retrieveMultipleStatistics()} and updating the
	 * {@link #cachedAggregatedDeviceList} resets it to the currentTime timestamp, which will re-activate data collection.
	 */
	private long nextDevicesCollectionIterationTimestamp;

	/**
	 * This parameter holds timestamp of when we need to stop performing API calls
	 * It used when device stop retrieving statistic. Updated each time of called #retrieveMultipleStatistics
	 */
	private volatile long validRetrieveStatisticsTimestamp;

	/**
	 * Executor that runs all the async operations, that is posting and
	 */
	private ExecutorService executorService;

	/**
	 * A private field that represents an instance of the HaivisionDataLoader class, which is responsible for loading device data for Haivision
	 */
	private HaivisionDataLoader deviceDataLoader;

	/**
	 * List of aggregated device
	 */
	private List<AggregatedDevice> cachedAggregatedDeviceList = Collections.synchronizedList(new ArrayList<>());

	/**
	 * List of aggregated device
	 */
	private List<AggregatedDevice> aggregatedDeviceList = Collections.synchronizedList(new ArrayList<>());

	/**
	 * API Token
	 */
	private String cookieSession;

	/**
	 * A JSON node containing the response from an aggregator.
	 */
	private JsonNode aggregatorResponse;

	/**
	 * A filter for device type.
	 */
	private String filterDeviceType;

	/**
	 * A filter for tag.
	 */
	private String filterTag;

	/**
	 * Retrieves {@link #filterDeviceType}
	 *
	 * @return value of {@link #filterDeviceType}
	 */
	public String getFilterDeviceType() {
		return filterDeviceType;
	}

	/**
	 * Sets {@link #filterDeviceType} value
	 *
	 * @param filterDeviceType new value of {@link #filterDeviceType}
	 */
	public void setFilterDeviceType(String filterDeviceType) {
		this.filterDeviceType = filterDeviceType;
	}

	/**
	 * Retrieves {@link #filterTag}
	 *
	 * @return value of {@link #filterTag}
	 */
	public String getFilterTag() {
		return filterTag;
	}

	/**
	 * Sets {@link #filterTag} value
	 *
	 * @param filterTag new value of {@link #filterTag}
	 */
	public void setFilterTag(String filterTag) {
		this.filterTag = filterTag;
	}

	/**
	 * Update the status of the device.
	 * The device is considered as paused if did not receive any retrieveMultipleStatistics()
	 * calls during {@link com.avispl.symphony.dal.infrastructure.management.haivision.mediaplatform.HaivisionMediaPlatformCommunicator}
	 */
	private synchronized void updateAggregatorStatus() {
		devicePaused = validRetrieveStatisticsTimestamp < System.currentTimeMillis();
	}

	/**
	 * Constructs a new instance of the HaivisionMediaPlatformCommunicator class.
	 * This constructor initializes the communicator with the necessary components and settings to interact with HaivisionMediaPlatform.
	 *
	 * @throws IOException if an I/O error occurs during the initialization process.
	 */
	public HaivisionMediaPlatformCommunicator() throws IOException {
		this.setTrustAllCertificates(true);
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 *
	 * Check for available devices before retrieving the value
	 * ping latency information to Symphony
	 */
	@Override
	public int ping() throws Exception {
		if (isInitialized()) {
			long pingResultTotal = 0L;

			for (int i = 0; i < this.getPingAttempts(); i++) {
				long startTime = System.currentTimeMillis();

				try (Socket puSocketConnection = new Socket(this.host, this.getPort())) {
					puSocketConnection.setSoTimeout(this.getPingTimeout());
					if (puSocketConnection.isConnected()) {
						long pingResult = System.currentTimeMillis() - startTime;
						pingResultTotal += pingResult;
						if (this.logger.isTraceEnabled()) {
							this.logger.trace(String.format("PING OK: Attempt #%s to connect to %s on port %s succeeded in %s ms", i + 1, host, this.getPort(), pingResult));
						}
					} else {
						if (this.logger.isDebugEnabled()) {
							logger.debug(String.format("PING DISCONNECTED: Connection to %s did not succeed within the timeout period of %sms", host, this.getPingTimeout()));
						}
						return this.getPingTimeout();
					}
				} catch (SocketTimeoutException | ConnectException tex) {
					throw new SocketTimeoutException("Socket connection timed out");
				} catch (UnknownHostException tex) {
					throw new SocketTimeoutException("Socket connection timed out" + tex.getMessage());
				} catch (Exception e) {
					if (this.logger.isWarnEnabled()) {
						this.logger.warn(String.format("PING TIMEOUT: Connection to %s did not succeed, UNKNOWN ERROR %s: ", host, e.getMessage()));
					}
					return this.getPingTimeout();
				}
			}
			return Math.max(1, Math.toIntExact(pingResultTotal / this.getPingAttempts()));
		} else {
			throw new IllegalStateException("Cannot use device class without calling init() first");
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<Statistics> getMultipleStatistics() throws Exception {
		reentrantLock.lock();
		try {
			if (!checkValidCookieSession()) {
				throw new FailedLoginException("please enter valid password and username field.");
			}
			Map<String, String> statistics = new HashMap<>();
			ExtendedStatistics extendedStatistics = new ExtendedStatistics();
			filterDevice();
			retrieveAndPopulateSystemInfo(statistics);
			extendedStatistics.setStatistics(statistics);
			localExtendedStatistics = extendedStatistics;
		} finally {
			reentrantLock.unlock();
		}
		return Collections.singletonList(localExtendedStatistics);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void controlProperty(ControllableProperty controllableProperty) throws Exception {

	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void controlProperties(List<ControllableProperty> controllableProperties) throws Exception {
		if (CollectionUtils.isEmpty(controllableProperties)) {
			throw new IllegalArgumentException("ControllableProperties can not be null or empty");
		}
		for (ControllableProperty p : controllableProperties) {
			try {
				controlProperty(p);
			} catch (Exception e) {
				logger.error(String.format("Error when control property %s", p.getProperty()), e);
			}
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<AggregatedDevice> retrieveMultipleStatistics() throws Exception {
		return Collections.emptyList();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public List<AggregatedDevice> retrieveMultipleStatistics(List<String> list) throws Exception {
		return retrieveMultipleStatistics().stream().filter(aggregatedDevice -> list.contains(aggregatedDevice.getDeviceId())).collect(Collectors.toList());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void authenticate() throws Exception {
		// Haivision Media Platform only require API token for each request.
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void internalInit() throws Exception {
		if (logger.isDebugEnabled()) {
			logger.debug("Internal init is called.");
		}
		executorService = Executors.newFixedThreadPool(1);
		executorService.submit(deviceDataLoader = new HaivisionDataLoader());
		super.internalInit();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void internalDestroy() {
		if (logger.isDebugEnabled()) {
			logger.debug("Internal destroy is called.");
		}
		if (deviceDataLoader != null) {
			deviceDataLoader.stop();
			deviceDataLoader = null;
		}

		if (executorService != null) {
			executorService.shutdownNow();
			executorService = null;
		}
		if (localExtendedStatistics != null && localExtendedStatistics.getStatistics() != null && localExtendedStatistics.getControllableProperties() != null) {
			localExtendedStatistics.getStatistics().clear();
			localExtendedStatistics.getControllableProperties().clear();
		}
		nextDevicesCollectionIterationTimestamp = 0;
		cachedAggregatedDeviceList.clear();
		aggregatedDeviceList.clear();
		super.internalDestroy();
	}

	/**
	 * {@inheritDoc}
	 * set cookie into Header of Request
	 */
	@Override
	protected HttpHeaders putExtraRequestHeaders(HttpMethod httpMethod, String uri, HttpHeaders headers) {
		headers.set("Cookie", "calypso-session-id=" + cookieSession);
		return headers;
	}

	/**
	 * Check API token validation
	 * If the token expires, we send a request to get a new token
	 *
	 * @return boolean
	 */
	private boolean checkValidCookieSession() throws Exception {
		try {
			this.doGet(HaivisionMediaPlatformCommand.LOGIN_COMMAND, JsonNode.class);
		} catch (Exception e) {
			cookieSession = getCookieSession();
			if (HaivisionMediaPlatformConstant.EMPTY.equals(cookieSession)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Retrieves a token using the provided username and password
	 *
	 * @return the token string
	 */
	private String getCookieSession() throws Exception {
		String token = HaivisionMediaPlatformConstant.EMPTY;
		try {
			Map<String, String> credentials = new HashMap<>();
			credentials.put(HaivisionMediaPlatformConstant.USERNAME, this.getLogin());
			credentials.put(HaivisionMediaPlatformConstant.PASSWORD, this.getPassword());
			JsonNode response = this.doPost(HaivisionMediaPlatformCommand.LOGIN_COMMAND, credentials, JsonNode.class);
			if (response != null && response.has(HaivisionMediaPlatformConstant.DATA) && response.get(HaivisionMediaPlatformConstant.DATA).has(HaivisionMediaPlatformConstant.SESSION_ID)) {
				token = response.get(HaivisionMediaPlatformConstant.DATA).get(HaivisionMediaPlatformConstant.SESSION_ID).asText();
			}
		} catch (Exception e) {
			throw new FailedLoginException("Failed to retrieve the cookie for account with from username and password. Please username id and password");
		}
		return token;
	}

	/**
	 * Get system information of HaivisionMediaPlatform
	 */
	private void retrieveAndPopulateSystemInfo(Map<String, String> stats) {
		try {
			JsonNode buildVersionResponse = this.doGet(HaivisionMediaPlatformCommand.SYSTEM_INFO_COMMAND, JsonNode.class);
			for (SystemInfo systemInfo : SystemInfo.values()) {
				switch (systemInfo) {
					case VERSION:
					case BUILD:
						if (buildVersionResponse != null && buildVersionResponse.has(HaivisionMediaPlatformConstant.DATA)) {
							stats.put(systemInfo.getName(), buildVersionResponse.get(HaivisionMediaPlatformConstant.DATA).get(systemInfo.getValue()).asText());
						} else {
							stats.put(systemInfo.getName(), HaivisionMediaPlatformConstant.NONE);
						}
						break;
					case NUMBER_OF_DEVICES:
						if (aggregatorResponse != null && aggregatorResponse.has(HaivisionMediaPlatformConstant.PAGING)) {
							stats.put(systemInfo.getName(), aggregatorResponse.get(HaivisionMediaPlatformConstant.PAGING).get(systemInfo.getValue()).asText());
						}
						if (aggregatorResponse == null || aggregatorResponse.has(HaivisionMediaPlatformConstant.HTTP_STATUS_CODE)
								&& aggregatorResponse.get(HaivisionMediaPlatformConstant.HTTP_STATUS_CODE).asInt() == 404) {
							stats.put(systemInfo.getName(), "0");
						}
						break;
					default:
						stats.put(systemInfo.getName(), HaivisionMediaPlatformConstant.NONE);
				}
			}
		} catch (Exception e) {
			throw new ResourceNotReachableException("Error when get system information.", e);
		}
	}

	/**
	 * Filters devices based on specified criteria such as device types and tags.
	 * Retrieves device information using the Haivision Media Platform.
	 * The method constructs a command to get device information based on the provided filters.
	 * It retrieves the response from the Haivision Media Platform and stores it in the aggregatorResponse attribute.
	 */
	private void filterDevice() {
		try {
			String command = HaivisionMediaPlatformCommand.GET_DEVICE_INFO_COMMAND;
			if (StringUtils.isNotNullOrEmpty(filterDeviceType)) {
				command += "&deviceTypes=" + Arrays.toString(convertToArray(filterDeviceType));
			}

			if (StringUtils.isNotNullOrEmpty(filterTag)) {
				command += "&tags=" + removeExtraSpaces(filterTag);
			}
			aggregatorResponse = this.doGet(command, JsonNode.class);

		} catch (Exception e) {
			aggregatorResponse = null;
			logger.error(String.format("Error when get system information, %s", e));
		}
	}

	/**
	 * Removes extra spaces and formats the input string.
	 *
	 * @param input The input string possibly containing extra spaces
	 * @return The string with extra spaces removed and consistent formatting
	 */
	private String removeExtraSpaces(String input) {
		return input.replaceAll("\\s++", " ").replaceAll(",\\s+", ",");
	}

	/**
	 * Converts a formatted string to an array of strings.
	 *
	 * @param input The input string to be converted to an array
	 * @return An array of strings obtained from the input string after formatting
	 */
	private String[] convertToArray(String input) {
		String[] output = removeExtraSpaces(input).split(",");
		for (int i = 0; i < output.length; i++) {
			output[i] = "\"" + EnumTypeHandler.getValueByName(DeviceTypeFilterEnum.class, output[i].trim()) + "\"";
		}
		return output;
	}

	/**
	 * populate detail aggregated device
	 * add aggregated device into aggregated device list
	 */
	private void populateDeviceDetails() {

	}
}
