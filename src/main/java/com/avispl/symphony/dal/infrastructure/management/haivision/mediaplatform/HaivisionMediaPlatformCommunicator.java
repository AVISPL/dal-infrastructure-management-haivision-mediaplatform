/*
 *  Copyright (c) 2024 AVI-SPL, Inc. All Rights Reserved.
 */

package com.avispl.symphony.dal.infrastructure.management.haivision.mediaplatform;

import java.io.IOException;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.util.CollectionUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javax.security.auth.login.FailedLoginException;

import com.avispl.symphony.api.dal.control.Controller;
import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.Statistics;
import com.avispl.symphony.api.dal.dto.monitor.aggregator.AggregatedDevice;
import com.avispl.symphony.api.dal.error.CommandFailureException;
import com.avispl.symphony.api.dal.error.ResourceNotReachableException;
import com.avispl.symphony.api.dal.monitor.Monitorable;
import com.avispl.symphony.api.dal.monitor.aggregator.Aggregator;
import com.avispl.symphony.dal.aggregator.parser.AggregatedDeviceProcessor;
import com.avispl.symphony.dal.aggregator.parser.PropertiesMapping;
import com.avispl.symphony.dal.aggregator.parser.PropertiesMappingParser;
import com.avispl.symphony.dal.communicator.RestCommunicator;
import com.avispl.symphony.dal.infrastructure.management.haivision.mediaplatform.common.AggregatedInfo;
import com.avispl.symphony.dal.infrastructure.management.haivision.mediaplatform.common.ChannelTypeEnum;
import com.avispl.symphony.dal.infrastructure.management.haivision.mediaplatform.common.EnumTypeHandler;
import com.avispl.symphony.dal.infrastructure.management.haivision.mediaplatform.common.HaivisionMediaPlatformCommand;
import com.avispl.symphony.dal.infrastructure.management.haivision.mediaplatform.common.HaivisionMediaPlatformConstant;
import com.avispl.symphony.dal.infrastructure.management.haivision.mediaplatform.common.SystemInfo;
import com.avispl.symphony.dal.infrastructure.management.haivision.mediaplatform.common.dto.Content;
import com.avispl.symphony.dal.infrastructure.management.haivision.mediaplatform.common.filter.DeviceTypeFilterEnum;
import com.avispl.symphony.dal.util.StringUtils;

/**
 * HaivisionMediaPlatformCommunicator
 * Supported features are:
 * Monitoring Aggregator Device:
 *  <ul>
 *  <li> - Build</li>
 *  <li> - NumberOfDevices</li>
 *  <li> - Version</li>
 *  <ul>
 *
 * General Info Aggregated Device:
 * <ul>
 * <li> - deviceId</li>
 * <li> - deviceModel</li>
 * <li> - deviceName</li>
 * <li> - deviceOnline</li>
 * <li> - Extension</li>
 * <li> - Firmware</li>
 * <li> - Hostname</li>
 * <li> - IPAddress</li>
 * <li> - LastAcceptedUpdate</li>
 * <li> - LastConnectedAt</li>
 * <li> - MACAddress</li>
 * <li> - Mute</li>
 * <li> - NTPServer</li>
 * <li> - Reboot</li>
 * <li> - Standby</li>
 * <li> - Status</li>
 * <li> - StatusDetails</li>
 * <li> - Tags</li>
 * <li> - Volume(%)</li>
 * <li> - VolumeCurrentValue(%)</li>
 * </ul>
 *
 * Content Group:
 * <ul>
 * <li> - Id</li>
 * <li> - Source</li>
 * <li> - Type</li>
 * </ul>
 *
 * @author Harry / Symphony Dev Team<br>
 * Created on 20/12/2023
 * @since 1.0.0
 */
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
	 * A mapper for reading and writing JSON using Jackson library.
	 * ObjectMapper provides functionality for converting between Java objects and JSON.
	 * It can be used to serialize objects to JSON format, and deserialize JSON data to objects.
	 */
	private final ObjectMapper objectMapper = new ObjectMapper();

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
	 * Aggregator inactivity timeout. If the {@link com.avispl.symphony.dal.infrastructure.management.haivision.mediaplatform.HaivisionMediaPlatformCommunicator#retrieveMultipleStatistics()}  method is not
	 * called during this period of time - device is considered to be paused, thus the Cloud API
	 * is not supposed to be called
	 */
	private static final long retrieveStatisticsTimeOut = 3 * 60 * 1000;

	/**
	 * Executor that runs all the async operations, that is posting and
	 */
	private ExecutorService executorService;

	/**
	 * A private field that represents an instance of the HaivisionDataLoader class, which is responsible for loading device data for Haivision
	 */
	private HaivisionDataLoader deviceDataLoader;

	/**
	 * An instance of the AggregatedDeviceProcessor class used to process and aggregate device-related data.
	 */
	private AggregatedDeviceProcessor aggregatedDeviceProcessor;

	/**
	 * List of aggregated device
	 */
	private List<AggregatedDevice> cachedAggregatedDeviceList = Collections.synchronizedList(new ArrayList<>());

	/**
	 * List of aggregated device
	 */
	private List<AggregatedDevice> aggregatedDeviceList = Collections.synchronizedList(new ArrayList<>());

	/**
	 * List of content value
	 */
	private List<Content> contentValues = Collections.synchronizedList(new ArrayList<>());

	/**
	 * List of content value
	 */
	private Map<String, Map<String, String>> cachedContentValue = new HashMap<>();

	/**
	 * check control
	 */
	private boolean checkControl;

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
	private String filterByDeviceType;

	/**
	 * A filter for tag.
	 */
	private String filterByTagName;

	/**
	 * Retrieves {@link #filterByDeviceType}
	 *
	 * @return value of {@link #filterByDeviceType}
	 */
	public String getFilterByDeviceType() {
		return filterByDeviceType;
	}

	/**
	 * Sets {@link #filterByDeviceType} value
	 *
	 * @param filterByDeviceType new value of {@link #filterByDeviceType}
	 */
	public void setFilterByDeviceType(String filterByDeviceType) {
		this.filterByDeviceType = filterByDeviceType;
	}

	/**
	 * Retrieves {@link #filterByTagName}
	 *
	 * @return value of {@link #filterByTagName}
	 */
	public String getFilterByTagName() {
		return filterByTagName;
	}

	/**
	 * Sets {@link #filterByTagName} value
	 *
	 * @param filterByTagName new value of {@link #filterByTagName}
	 */
	public void setFilterByTagName(String filterByTagName) {
		this.filterByTagName = filterByTagName;
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
	 * Uptime time stamp to valid one
	 */
	private synchronized void updateValidRetrieveStatisticsTimestamp() {
		validRetrieveStatisticsTimestamp = System.currentTimeMillis() + retrieveStatisticsTimeOut;
		updateAggregatorStatus();
	}

	/**
	 * Constructs a new instance of the HaivisionMediaPlatformCommunicator class.
	 * This constructor initializes the communicator with the necessary components and settings to interact with HaivisionMediaPlatform.
	 *
	 * @throws IOException if an I/O error occurs during the initialization process.
	 */
	public HaivisionMediaPlatformCommunicator() throws IOException {
		Map<String, PropertiesMapping> mapping = new PropertiesMappingParser().loadYML(HaivisionMediaPlatformConstant.MODEL_MAPPING_AGGREGATED_DEVICE, getClass());
		aggregatedDeviceProcessor = new AggregatedDeviceProcessor(mapping);
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
				throw new FailedLoginException("Please enter valid password and username field.");
			}
			Map<String, String> statistics = new HashMap<>();
			ExtendedStatistics extendedStatistics = new ExtendedStatistics();
			getContentValue();
			retrieveDeviceInformationByPage(1);
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
		String property = controllableProperty.getProperty();
		String deviceId = controllableProperty.getDeviceId();
		String value = String.valueOf(controllableProperty.getValue());

		String[] propertyList = property.split(HaivisionMediaPlatformConstant.HASH);
		String propertyName = property;
		if (property.contains(HaivisionMediaPlatformConstant.HASH)) {
			propertyName = propertyList[1];
		}
		reentrantLock.lock();
		try {
			Optional<AggregatedDevice> aggregatedDevice = aggregatedDeviceList.stream().filter(item -> item.getDeviceId().equals(deviceId)).findFirst();
			if (aggregatedDevice.isPresent()) {
				Map<String, String> stats = aggregatedDevice.get().getProperties();
				List<AdvancedControllableProperty> advancedControllableProperties = aggregatedDevice.get().getControllableProperties();
				boolean controlPropagated = true;
				ObjectNode body = objectMapper.createObjectNode();
				AggregatedInfo propertyItem = AggregatedInfo.getByName(propertyName);
				switch (propertyItem) {
					case MUTE:
						String commandValue = HaivisionMediaPlatformConstant.UN_MUTE;
						if (HaivisionMediaPlatformConstant.NUMBER_ONE.equals(value)) {
							commandValue = HaivisionMediaPlatformConstant.MUTE;
						}
						body.put(HaivisionMediaPlatformConstant.COMMAND, commandValue);
						sendSTBCommandToDevice(deviceId, body, propertyName, getSwitchStatus(value));
						break;
					case STANDBY:
						commandValue = HaivisionMediaPlatformConstant.STANDBY_OFF;
						if (HaivisionMediaPlatformConstant.NUMBER_ONE.equals(value)) {
							commandValue = HaivisionMediaPlatformConstant.STANDBY_ON;
						}
						body.put(HaivisionMediaPlatformConstant.COMMAND, commandValue);
						sendSTBCommandToDevice(deviceId, body, propertyName, getSwitchStatus(value));
						break;
					case VOLUME:
						double newValue = Double.parseDouble(value) / 100.0;
						body.put(HaivisionMediaPlatformConstant.COMMAND, HaivisionMediaPlatformConstant.SET_VOLUME);
						ObjectNode childNode = objectMapper.createObjectNode();
						childNode.put(HaivisionMediaPlatformConstant.VOLUME, newValue);
						body.set(HaivisionMediaPlatformConstant.PARAMETERS, childNode);
						sendSTBCommandToDevice(deviceId, body, propertyName, value);
						stats.put(HaivisionMediaPlatformConstant.VOLUME_CURRENT_VALUE, value);
						break;
					case REBOOT:
						commandValue = HaivisionMediaPlatformConstant.REBOOT_COMMAND;
						body.put(HaivisionMediaPlatformConstant.COMMAND, commandValue);
						sendSTBCommandToDevice(deviceId, body, propertyName, HaivisionMediaPlatformConstant.EMPTY);
						break;
					case CONTENT_TYPE:
						if (HaivisionMediaPlatformConstant.NONE.equals(value)) {
							throw new IllegalArgumentException("Please choose the valid value");
						}
						List<String> namesWithMatchingType = contentValues.stream().filter(content -> content.getType().equals(EnumTypeHandler.getValueByName(ChannelTypeEnum.class, value)))
								.map(Content::getName).collect(Collectors.toList());
						addAdvanceControlProperties(advancedControllableProperties, stats,
								createDropdown(HaivisionMediaPlatformConstant.CONTENT_GROUP + AggregatedInfo.CONTENT_SOURCE.getName(), namesWithMatchingType.toArray(new String[0]), namesWithMatchingType.get(0)),
								namesWithMatchingType.get(0));
						putValueIntoMap(deviceId, AggregatedInfo.CONTENT_TYPE.getName(), EnumTypeHandler.getValueByName(ChannelTypeEnum.class, value));
						putValueIntoMap(deviceId, AggregatedInfo.CONTENT_SOURCE.getName(), namesWithMatchingType.get(0));
						break;
					case CONTENT_SOURCE:
						putValueIntoMap(deviceId, AggregatedInfo.CONTENT_SOURCE.getName(), value);
						break;
					case APPLY_CHANGE:
						String name = cachedContentValue.get(deviceId).get(AggregatedInfo.CONTENT_SOURCE.getName());
						String type = cachedContentValue.get(deviceId).get(AggregatedInfo.CONTENT_TYPE.getName());
						Optional<String> foundId = contentValues.stream()
								.filter(content -> content.getName().equals(name) && content.getType().equals(type))
								.map(Content::getId).findFirst();
						if (foundId.isPresent()) {
							body.put(HaivisionMediaPlatformConstant.COMMAND, HaivisionMediaPlatformConstant.SET_CHANNEL);
							childNode = objectMapper.createObjectNode();
							childNode.put(HaivisionMediaPlatformConstant.ID, foundId.get());
							childNode.put(HaivisionMediaPlatformConstant.NAME, name);
							childNode.put(HaivisionMediaPlatformConstant.TYPE, type);
							body.set(HaivisionMediaPlatformConstant.PARAMETERS, childNode);
							//Send command
							sendSTBCommandToDevice(deviceId, body, propertyName, HaivisionMediaPlatformConstant.EMPTY);
						} else {
							throw new IllegalArgumentException(String.format("Error when control Content with type is %s and source is %s", EnumTypeHandler.getNameByValue(ChannelTypeEnum.class, type), name));
						}
						break;
					default:
						if (logger.isWarnEnabled()) {
							logger.warn(String.format("Unable to execute %s command on device %s: Not Supported", property, deviceId));
						}
						controlPropagated = false;
						break;
				}
				if (controlPropagated) {
					checkControl = true;
					updateLocalControlValue(stats, advancedControllableProperties, property, value);
					updateListAggregatedDevice(deviceId, stats, advancedControllableProperties);
				}
			} else {
				throw new IllegalArgumentException(String.format("Unable to control property: %s as the device does not exist.", property));
			}
		} finally {
			reentrantLock.unlock();
		}
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
		if (aggregatorResponse != null) {
			if (!checkValidCookieSession()) {
				throw new FailedLoginException("Please enter valid password and username field.");
			}
			if (executorService == null) {
				executorService = Executors.newFixedThreadPool(1);
				executorService.submit(deviceDataLoader = new HaivisionDataLoader());
			}
			nextDevicesCollectionIterationTimestamp = System.currentTimeMillis();
			updateValidRetrieveStatisticsTimestamp();
			if (cachedAggregatedDeviceList.isEmpty()) {
				return cachedAggregatedDeviceList;
			}
			return cloneAndPopulateAggregatedDeviceList();
		}
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
			//Send request to check valid cookie
			this.doGet(HaivisionMediaPlatformCommand.LOGIN_COMMAND);
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
			throw new FailedLoginException("Failed to retrieve the cookie for account with from username and password");
		}
		return token;
	}

	/**
	 * Retrieves system information and populates the provided stats map.
	 *
	 * @param stats The map to store the retrieved system information.
	 */
	private void retrieveAndPopulateSystemInfo(Map<String, String> stats) {
		try {
			JsonNode buildVersionResponse = this.doGet(HaivisionMediaPlatformCommand.SYSTEM_INFO_COMMAND, JsonNode.class);
			for (SystemInfo systemInfo : SystemInfo.values()) {
				String propertyName = systemInfo.getName();
				String value = systemInfo.getValue();
				switch (systemInfo) {
					case VERSION:
					case BUILD:
						if (buildVersionResponse != null && buildVersionResponse.has(HaivisionMediaPlatformConstant.DATA)) {
							stats.put(propertyName, buildVersionResponse.get(HaivisionMediaPlatformConstant.DATA).get(value).asText());
						} else {
							stats.put(propertyName, HaivisionMediaPlatformConstant.NONE);
						}
						break;
					case NUMBER_OF_DEVICES:
						if (aggregatorResponse != null && aggregatorResponse.has(HaivisionMediaPlatformConstant.PAGING)) {
							stats.put(propertyName, aggregatorResponse.get(HaivisionMediaPlatformConstant.PAGING).get(value).asText());
						}
						if (aggregatorResponse == null || aggregatorResponse.has(HaivisionMediaPlatformConstant.HTTP_STATUS_CODE)
								&& aggregatorResponse.get(HaivisionMediaPlatformConstant.HTTP_STATUS_CODE).asInt() == 404) {
							stats.put(propertyName, HaivisionMediaPlatformConstant.ZERO);
						}
						break;
					default:
						stats.put(propertyName, HaivisionMediaPlatformConstant.NONE);
				}
			}
		} catch (Exception e) {
			throw new ResourceNotReachableException("Error when get system information.", e);
		}
	}

	/**
	 * Get content value of HaivisionMediaPlatform
	 */
	private void getContentValue() {
		try {
			JsonNode response;
			String name;
			String type;
			String id;
			List<String> commands = new ArrayList<>();
			commands.add(HaivisionMediaPlatformCommand.SOURCE_COMMAND);
			commands.add(HaivisionMediaPlatformCommand.VIDEO_COMMAND);
			commands.add(HaivisionMediaPlatformCommand.SESSION_COMMAND);
			commands.add(String.format(HaivisionMediaPlatformCommand.LAYOUT_COMMAND, "1"));
			commands.add(String.format(HaivisionMediaPlatformCommand.LAYOUT_COMMAND, "2"));
			contentValues.clear();
			for (String command : commands) {
				response = doGetContentCommand(command);
				if (response != null && response.has(HaivisionMediaPlatformConstant.DATA)) {
					for (JsonNode item : response.get(HaivisionMediaPlatformConstant.DATA)) {
						switch (command) {
							case HaivisionMediaPlatformCommand.SOURCE_COMMAND:
								name = item.get(HaivisionMediaPlatformConstant.NAME).asText().trim();
								id = item.get(HaivisionMediaPlatformConstant.ID).asText().trim();
								type = HaivisionMediaPlatformConstant.SOURCE;
								break;
							case HaivisionMediaPlatformCommand.VIDEO_COMMAND:
							case HaivisionMediaPlatformCommand.SESSION_COMMAND:
								name = item.get(HaivisionMediaPlatformConstant.TITLE).asText().trim();
								id = item.get(HaivisionMediaPlatformConstant.ID).asText().trim();
								type = item.get(HaivisionMediaPlatformConstant.ITEM_TYPE).asText().trim();
								break;
							default:
								name = item.get(HaivisionMediaPlatformConstant.TITLE).asText().trim();
								id = item.get(HaivisionMediaPlatformConstant.ID).asText().trim();
								type = HaivisionMediaPlatformConstant.COMPOSITION;
								break;
						}
						Content contentObject = new Content(id, name, type);
						contentValues.add(contentObject);
					}
				}
			}
		} catch (Exception e) {
			logger.error(String.format("Error when get content value, %s", e));
		}
	}

	/**
	 * Performs a GET request for content using the provided command and returns the JSON response.
	 *
	 * @param command The command string to retrieve content.
	 * @return JSON response obtained from the GET request, or null if an error occurs.
	 */
	private JsonNode doGetContentCommand(String command) {
		try {
			return this.doGet(command, JsonNode.class);
		} catch (Exception e) {
			logger.error(String.format("Error when retrieve content command %s, %s", command, e));
			return null;
		}
	}

	/**
	 * Retrieves device information for a specific page from the Haivision Media Platform API.
	 *
	 * @param page The page number for device information retrieval.
	 */
	private void retrieveDeviceInformationByPage(int page) {
		try {
			String command = String.format(HaivisionMediaPlatformCommand.GET_DEVICE_INFO_COMMAND, page, HaivisionMediaPlatformConstant.PAGE_SIZE);
			if (StringUtils.isNotNullOrEmpty(filterByDeviceType)) {
				command += "&deviceTypes=" + Arrays.toString(convertToArray(filterByDeviceType));
			}

			if (StringUtils.isNotNullOrEmpty(filterByTagName)) {
				command += "&tags=" + removeExtraSpaces(filterByTagName);
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
		return input.replaceAll("\\s++", HaivisionMediaPlatformConstant.EMPTY).replaceAll(",\\s+", HaivisionMediaPlatformConstant.COMMA);
	}

	/**
	 * Converts a formatted string to an array of strings.
	 *
	 * @param input The input string to be converted to an array
	 * @return An array of strings obtained from the input string after formatting
	 */
	private String[] convertToArray(String input) {
		String[] output = removeExtraSpaces(input).split(HaivisionMediaPlatformConstant.COMMA);
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
		try {
			retrieveDeviceInformationByPage(1);
			if (aggregatorResponse != null && aggregatorResponse.has(HaivisionMediaPlatformConstant.DATA) && aggregatorResponse.get(HaivisionMediaPlatformConstant.DATA).isArray()) {
				cachedAggregatedDeviceList.clear();
				int numPages = aggregatorResponse.get(HaivisionMediaPlatformConstant.PAGING).get(HaivisionMediaPlatformConstant.NUM_PAGES).asInt();
				for (int i = 1; i < numPages + 1; i++) {
					if (i != 1) {
						retrieveDeviceInformationByPage(i);
					}
					for (JsonNode jsonNode : aggregatorResponse.get(HaivisionMediaPlatformConstant.DATA)) {
						String id = jsonNode.get("_id").asText();
						JsonNode node = objectMapper.createArrayNode().add(jsonNode);
						cachedAggregatedDeviceList.removeIf(item -> item.getDeviceId().equals(id));
						cachedAggregatedDeviceList.addAll(aggregatedDeviceProcessor.extractDevices(node));
					}
				}
			}
		} catch (Exception e) {
			cachedAggregatedDeviceList.clear();
			logger.error("Error while populate aggregated device", e);
		}
	}

	/**
	 * Clone an aggregated device list that based on aggregatedDeviceList variable
	 * populate monitoring and controlling for aggregated device
	 *
	 * @return List<AggregatedDevice> aggregated device list
	 */
	private List<AggregatedDevice> cloneAndPopulateAggregatedDeviceList() {
		if (!checkControl) {
			synchronized (cachedAggregatedDeviceList) {
				aggregatedDeviceList.clear();
				for (AggregatedDevice aggregatedDevice : cachedAggregatedDeviceList) {
					List<AdvancedControllableProperty> advancedControllableProperties = new ArrayList<>();
					String model = EnumTypeHandler.getNameByValue(DeviceTypeFilterEnum.class, aggregatedDevice.getDeviceModel());
					if (!HaivisionMediaPlatformConstant.NONE.equals(model)) {
						aggregatedDevice.setDeviceModel(model);
					}
					Map<String, String> stats = new HashMap<>();
					mapStatsAndControlForAggregatedDevice(aggregatedDevice.getDeviceId(), aggregatedDevice.getProperties(), stats, advancedControllableProperties);

					aggregatedDevice.setProperties(stats);
					aggregatedDevice.setControllableProperties(advancedControllableProperties);
					aggregatedDeviceList.add(aggregatedDevice);
				}
			}
		}
		checkControl = false;
		return aggregatedDeviceList;
	}

	/**
	 * Maps general information properties from a mapping statistic to a target statistics map.
	 * This method processes specific properties from the provided localCachedStatistic and updates the stats map accordingly.
	 *
	 * @param deviceId The ID of the device.
	 * @param mappingStatistic The mapping statistics.
	 * @param stats The device statistics.
	 * @param advancedControllableProperties The list of advanced controllable properties.
	 */
	private void mapStatsAndControlForAggregatedDevice(String deviceId, Map<String, String> mappingStatistic, Map<String, String> stats,
			List<AdvancedControllableProperty> advancedControllableProperties) {
		String value;
		String propertyName;
		for (AggregatedInfo item : AggregatedInfo.values()) {
			propertyName = item.getName();
			value = getDefaultValueForNullData(mappingStatistic.get(propertyName));
			switch (item) {
				case REBOOT:
					addAdvanceControlProperties(advancedControllableProperties, stats, createButton(propertyName, HaivisionMediaPlatformConstant.REBOOT, HaivisionMediaPlatformConstant.REBOOTING, HaivisionMediaPlatformConstant.GRACE_PERIOD),
							HaivisionMediaPlatformConstant.NONE);
					break;
				case APPLY_CHANGE:
					addAdvanceControlProperties(advancedControllableProperties, stats, createButton(HaivisionMediaPlatformConstant.CONTENT_GROUP + AggregatedInfo.APPLY_CHANGE.getName(), HaivisionMediaPlatformConstant.SAVE, HaivisionMediaPlatformConstant.SAVING, HaivisionMediaPlatformConstant.GRACE_PERIOD),
							HaivisionMediaPlatformConstant.NONE);
					break;
				case VOLUME:
					if (!HaivisionMediaPlatformConstant.NONE.equals(value)) {
						float volume = Float.parseFloat(value) * 100;
						addAdvanceControlProperties(advancedControllableProperties, stats, createSlider(stats, propertyName, "0", "100", 0f, 100f, volume), String.valueOf(volume));
						stats.put(HaivisionMediaPlatformConstant.VOLUME_CURRENT_VALUE, String.valueOf((int) volume));
					} else {
						stats.put(propertyName, HaivisionMediaPlatformConstant.NONE);
					}
					break;
				case MUTE:
				case STANDBY:
					addAdvanceControlProperties(advancedControllableProperties, stats,
							createSwitch(propertyName, HaivisionMediaPlatformConstant.TRUE.equals(value) ? 1 : 0, HaivisionMediaPlatformConstant.OFF, HaivisionMediaPlatformConstant.ON),
							HaivisionMediaPlatformConstant.TRUE.equals(value) ? HaivisionMediaPlatformConstant.NUMBER_ONE : HaivisionMediaPlatformConstant.ZERO);
					break;
				case LAST_CONNECT_AT:
				case LAST_ACCEPTED_UPDATE:
					stats.put(propertyName, convertMillisecondsToDate(value));
					break;
				case TAGS:
					value = getDefaultValueForNullData(value.replace("[", "").replace("]", "").replace("\"", ""));
					stats.put(propertyName, value);
					break;
				case CONTENT_ID:
					stats.put(HaivisionMediaPlatformConstant.CONTENT_GROUP + AggregatedInfo.CONTENT_ID.getName(), value);
					break;
				case CONTENT_SOURCE:
					String type = getDefaultValueForNullData(mappingStatistic.get(AggregatedInfo.CONTENT_TYPE.getName()));
					if (HaivisionMediaPlatformConstant.NONE.equals(type)) {
						stats.put(HaivisionMediaPlatformConstant.CONTENT_GROUP + AggregatedInfo.CONTENT_SOURCE.getName(), HaivisionMediaPlatformConstant.NONE);
					} else {
						List<String> namesWithMatchingType = contentValues.stream().filter(content -> content.getType().equals(type))
								.map(Content::getName).collect(Collectors.toList());
						addAdvanceControlProperties(advancedControllableProperties, stats,
								createDropdown(HaivisionMediaPlatformConstant.CONTENT_GROUP + AggregatedInfo.CONTENT_SOURCE.getName(), namesWithMatchingType.toArray(new String[0]), value.trim()), value);
					}
					putValueIntoMap(deviceId, AggregatedInfo.CONTENT_SOURCE.getName(), value);
					break;
				case CONTENT_TYPE:
					List<String> arrayValues = Arrays.stream(ChannelTypeEnum.values()).map(ChannelTypeEnum::getName).collect(Collectors.toList());
					arrayValues.add(0, HaivisionMediaPlatformConstant.NONE);
					addAdvanceControlProperties(advancedControllableProperties, stats,
							createDropdown(HaivisionMediaPlatformConstant.CONTENT_GROUP + AggregatedInfo.CONTENT_TYPE.getName(), arrayValues.toArray(new String[0]),
									EnumTypeHandler.getNameByValue(ChannelTypeEnum.class, value.trim())), value);
					putValueIntoMap(deviceId, AggregatedInfo.CONTENT_TYPE.getName(), value);
					break;
				default:
					stats.put(propertyName, uppercaseFirstCharacter(value));
					break;
			}
		}
	}

	/**
	 * Sends a command to the set-top box (STB) device.
	 *
	 * @param deviceId The ID of the target device
	 * @param body The JSON body of the command to be sent
	 * @param name The name of the property being controlled
	 * @param value The value to set for the specified property
	 * @throws IllegalArgumentException If an issue occurs while sending the command or handling the response
	 */
	private void sendSTBCommandToDevice(String deviceId, JsonNode body, String name, String value) {
		try {
			String command = String.format(HaivisionMediaPlatformCommand.STB_COMMAND, deviceId);
			JsonNode response = this.doPost(command, body, JsonNode.class);
			if (response == null || !response.has(HaivisionMediaPlatformConstant.DATA) || !response.get(HaivisionMediaPlatformConstant.DATA).has(HaivisionMediaPlatformConstant.STATUS)
					|| !HaivisionMediaPlatformConstant.OK.equals(response.get(HaivisionMediaPlatformConstant.DATA).get(HaivisionMediaPlatformConstant.STATUS).asText())) {
				throw new IllegalArgumentException("The failed response when send request");
			}
		} catch (CommandFailureException ex) {
			if (ex.getMessage() != null && ex.getMessage().contains("Device stream not found")) {
				throw new IllegalArgumentException(String.format("Can't control property %s with value %s. The device must be online and connected (status Online or Standby)", name, value), ex);
			} else if (ex.getMessage() != null && ex.getMessage().contains("Device not found")) {
				throw new IllegalArgumentException(String.format("Can't control property %s with value %s. Unknown device ID.", name, value), ex);
			} else {
				throw new IllegalArgumentException(String.format("Can't control property %s with value %s", name, value), ex);
			}
		} catch (Exception e) {
			throw new IllegalArgumentException(String.format("Can't control property %s with value %s", name, value), e);
		}
	}

	/**
	 * Puts a value into the cached content value map for a specific device and property.
	 *
	 * @param deviceId The unique identifier of the device.
	 * @param propertyName The name of the property to be stored.
	 * @param value The value to be associated with the property.
	 */
	private void putValueIntoMap(String deviceId, String propertyName, String value) {
		Map<String, String> contentValue = cachedContentValue.get(deviceId);
		if (contentValue == null) {
			contentValue = new HashMap<>();
		}
		contentValue.put(propertyName, value);
		cachedContentValue.put(deviceId, contentValue);
	}

	/**
	 * Gets the status of a switch based on the provided value.
	 *
	 * @param value The value indicating the switch status
	 * @return The status of the switch (either "ON" or "OFF")
	 */
	private String getSwitchStatus(String value) {
		return HaivisionMediaPlatformConstant.NUMBER_ONE.equals(value) ? HaivisionMediaPlatformConstant.ON : HaivisionMediaPlatformConstant.OFF;
	}

	/**
	 * check value is null or empty
	 *
	 * @param value input value
	 * @return value after checking
	 */
	private String getDefaultValueForNullData(String value) {
		return StringUtils.isNotNullOrEmpty(value) ? value : HaivisionMediaPlatformConstant.NONE;
	}

	/**
	 * Converts a value from milliseconds to a formatted date string.
	 *
	 * @param value the value in milliseconds
	 * @return the formatted date string in the format "MMM yyyy", or "none" if an error occurs
	 */
	private String convertMillisecondsToDate(String value) {
		if (HaivisionMediaPlatformConstant.NONE.equals(value)) {
			return value;
		}
		try {
			long milliseconds = Long.parseLong(value);
			Date date = new Date(milliseconds);
			SimpleDateFormat dateFormat = new SimpleDateFormat(HaivisionMediaPlatformConstant.NEW_FORMAT_DATETIME);
			dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
			return dateFormat.format(date);
		} catch (Exception e) {
			logger.debug("Error when convert milliseconds to datetime ", e);
		}
		return HaivisionMediaPlatformConstant.NONE;
	}

	/**
	 * capitalize the first character of the string
	 *
	 * @param input input string
	 * @return string after fix
	 */
	private String uppercaseFirstCharacter(String input) {
		char firstChar = input.charAt(0);
		return Character.toUpperCase(firstChar) + input.substring(1);
	}

	/**
	 * Updates cached devices' control value, after the control command was executed with the specified value.
	 * It is done in order for aggregator to populate the latest control values, after the control command has been executed,
	 * but before the next devices details polling cycle was addressed.
	 *
	 * @param stats The updated device properties.
	 * @param advancedControllableProperties The updated list of advanced controllable properties.
	 * @param name of the control property
	 * @param value to set to the control property
	 */
	private void updateLocalControlValue(Map<String, String> stats, List<AdvancedControllableProperty> advancedControllableProperties, String name, String value) {
		stats.put(name, value);
		advancedControllableProperties.stream().filter(advancedControllableProperty ->
				name.equals(advancedControllableProperty.getName())).findFirst().ifPresent(advancedControllableProperty ->
				advancedControllableProperty.setValue(value));
	}

	/**
	 * Updates the properties and controllable properties of an aggregated device in the list.
	 *
	 * @param deviceId The unique identifier of the device to update.
	 * @param stats The updated device properties.
	 * @param advancedControllableProperties The updated list of advanced controllable properties.
	 */
	private void updateListAggregatedDevice(String deviceId, Map<String, String> stats, List<AdvancedControllableProperty> advancedControllableProperties) {
		Optional<AggregatedDevice> device = aggregatedDeviceList.stream().filter(aggregatedDevice ->
				deviceId.equals(aggregatedDevice.getDeviceId())).findFirst();
		if (device.isPresent()) {
			device.get().setControllableProperties(advancedControllableProperties);
			device.get().setProperties(stats);
		}
	}

	/**
	 * Add advancedControllableProperties if advancedControllableProperties different empty
	 *
	 * @param advancedControllableProperties advancedControllableProperties is the list that store all controllable properties
	 * @param stats store all statistics
	 * @param property the property is item advancedControllableProperties
	 * @throws IllegalStateException when exception occur
	 */
	private void addAdvanceControlProperties(List<AdvancedControllableProperty> advancedControllableProperties, Map<String, String> stats, AdvancedControllableProperty property, String value) {
		if (property != null) {
			for (AdvancedControllableProperty controllableProperty : advancedControllableProperties) {
				if (controllableProperty.getName().equals(property.getName())) {
					advancedControllableProperties.remove(controllableProperty);
					break;
				}
			}
			if (StringUtils.isNotNullOrEmpty(value)) {
				stats.put(property.getName(), value);
			} else {
				stats.put(property.getName(), HaivisionMediaPlatformConstant.EMPTY);
			}
			advancedControllableProperties.add(property);
		}
	}

	/**
	 * Create switch is control property for metric
	 *
	 * @param name the name of property
	 * @param status initial status (0|1)
	 * @return AdvancedControllableProperty switch instance
	 */
	private AdvancedControllableProperty createSwitch(String name, int status, String labelOff, String labelOn) {
		AdvancedControllableProperty.Switch toggle = new AdvancedControllableProperty.Switch();
		toggle.setLabelOff(labelOff);
		toggle.setLabelOn(labelOn);

		AdvancedControllableProperty advancedControllableProperty = new AdvancedControllableProperty();
		advancedControllableProperty.setName(name);
		advancedControllableProperty.setValue(status);
		advancedControllableProperty.setType(toggle);
		advancedControllableProperty.setTimestamp(new Date());

		return advancedControllableProperty;
	}

	/***
	 * Create AdvancedControllableProperty slider instance
	 *
	 * @param stats extended statistics
	 * @param name name of the control
	 * @param initialValue initial value of the control
	 * @return AdvancedControllableProperty slider instance
	 */
	private AdvancedControllableProperty createSlider(Map<String, String> stats, String name, String labelStart, String labelEnd, Float rangeStart, Float rangeEnd, Float initialValue) {
		stats.put(name, initialValue.toString());
		AdvancedControllableProperty.Slider slider = new AdvancedControllableProperty.Slider();
		slider.setLabelStart(labelStart);
		slider.setLabelEnd(labelEnd);
		slider.setRangeStart(rangeStart);
		slider.setRangeEnd(rangeEnd);

		return new AdvancedControllableProperty(name, new Date(), slider, initialValue);
	}

	/**
	 * Create a button.
	 *
	 * @param name name of the button
	 * @param label label of the button
	 * @param labelPressed label of the button after pressing it
	 * @param gracePeriod grace period of button
	 * @return This returns the instance of {@link AdvancedControllableProperty} type Button.
	 */
	private AdvancedControllableProperty createButton(String name, String label, String labelPressed, long gracePeriod) {
		AdvancedControllableProperty.Button button = new AdvancedControllableProperty.Button();
		button.setLabel(label);
		button.setLabelPressed(labelPressed);
		button.setGracePeriod(gracePeriod);
		return new AdvancedControllableProperty(name, new Date(), button, "");
	}

	/***
	 * Create dropdown advanced controllable property
	 *
	 * @param name the name of the control
	 * @param initialValue initial value of the control
	 * @return AdvancedControllableProperty dropdown instance
	 */
	private AdvancedControllableProperty createDropdown(String name, String[] values, String initialValue) {
		AdvancedControllableProperty.DropDown dropDown = new AdvancedControllableProperty.DropDown();
		dropDown.setOptions(values);
		dropDown.setLabels(values);

		return new AdvancedControllableProperty(name, new Date(), dropDown, initialValue);
	}
}
