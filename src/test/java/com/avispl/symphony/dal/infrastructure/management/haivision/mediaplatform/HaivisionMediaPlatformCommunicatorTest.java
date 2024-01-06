/*
 *  Copyright (c) 2024 AVI-SPL, Inc. All Rights Reserved.
 */

package com.avispl.symphony.dal.infrastructure.management.haivision.mediaplatform;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.Assert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty;
import com.avispl.symphony.api.dal.dto.control.ControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.aggregator.AggregatedDevice;

/**
 * HaivisionMediaPlatformCommunicatorTest
 *
 * @author Harry / Symphony Dev Team<br>
 * Created on 9/8/2023
 * @since 1.0.0
 */
public class HaivisionMediaPlatformCommunicatorTest {
	private HaivisionMediaPlatformCommunicator haivisionMediaPlatformCommunicator;

	private ExtendedStatistics extendedStatistic;

	@BeforeEach
	void setUp() throws Exception {
		haivisionMediaPlatformCommunicator = new HaivisionMediaPlatformCommunicator();
		haivisionMediaPlatformCommunicator.setHost("");
		haivisionMediaPlatformCommunicator.setLogin("");
		haivisionMediaPlatformCommunicator.setPassword("");
		haivisionMediaPlatformCommunicator.setPort(443);
		haivisionMediaPlatformCommunicator.init();
		haivisionMediaPlatformCommunicator.connect();
	}

	@AfterEach
	void destroy() throws Exception {
		haivisionMediaPlatformCommunicator.disconnect();
		haivisionMediaPlatformCommunicator.destroy();
	}

	/**
	 * Tests the retrieval of aggregated data, ensuring the number of statistics retrieved and their specific values.
	 *
	 * @throws Exception if there's an error in the test process.
	 */
	@Test
	void testGetAggregatorData() throws Exception {
		extendedStatistic = (ExtendedStatistics) haivisionMediaPlatformCommunicator.getMultipleStatistics().get(0);
		Map<String, String> statistics = extendedStatistic.getStatistics();
		List<AdvancedControllableProperty> advancedControllablePropertyList = extendedStatistic.getControllableProperties();
		Assert.assertEquals(3, statistics.size());
	}

	/**
	 * Tests the retrieval of aggregated data, verifying specific statistics such as Build, Version, and NumberOfDevices.
	 *
	 * @throws Exception if there's an error during the test process.
	 */
	@Test
	void testAggregatorData() throws Exception {
		extendedStatistic = (ExtendedStatistics) haivisionMediaPlatformCommunicator.getMultipleStatistics().get(0);
		Map<String, String> statistics = extendedStatistic.getStatistics();
		List<AdvancedControllableProperty> advancedControllablePropertyList = extendedStatistic.getControllableProperties();
		Assert.assertEquals("11823", statistics.get("Build"));
		Assert.assertEquals("3.9.0", statistics.get("Version"));
		Assert.assertEquals("45", statistics.get("NumberOfDevices"));
	}

	/**
	 * Tests the retrieval of aggregated data with a specific device type filter applied, verifying the statistics accordingly.
	 *
	 * @throws Exception if there's an error during the test process.
	 */
	@Test
	void testAggregatorWithFiltering() throws Exception {
		haivisionMediaPlatformCommunicator.setFilterByDeviceType("Play 2000");
		extendedStatistic = (ExtendedStatistics) haivisionMediaPlatformCommunicator.getMultipleStatistics().get(0);
		Map<String, String> statistics = extendedStatistic.getStatistics();
		List<AdvancedControllableProperty> advancedControllablePropertyList = extendedStatistic.getControllableProperties();
		Assert.assertEquals(3, statistics.size());
		Assert.assertEquals("0", statistics.get("NumberOfDevices"));
	}

	/**
	 * Tests the retrieval of aggregated data with a specific device type filter applied, verifying the statistics accordingly.
	 *
	 * @throws Exception if there's an error during the test process.
	 */
	@Test
	void testAggregatorWithFilteringTagName() throws Exception {
		haivisionMediaPlatformCommunicator.setFilterByTagName("H2");
		extendedStatistic = (ExtendedStatistics) haivisionMediaPlatformCommunicator.getMultipleStatistics().get(0);
		Map<String, String> statistics = extendedStatistic.getStatistics();
		List<AdvancedControllableProperty> advancedControllablePropertyList = extendedStatistic.getControllableProperties();
		Assert.assertEquals(3, statistics.size());
		Assert.assertEquals("3", statistics.get("NumberOfDevices"));
	}

	/**
	 * Tests the retrieval of aggregated data with multiple device type filters applied, verifying the resulting statistics.
	 *
	 * @throws Exception if there's an error during the test process.
	 */
	@Test
	void testAggregatorWithMultipleFilteringValue() throws Exception {
		haivisionMediaPlatformCommunicator.setFilterByDeviceType("Play 2000A, Play 2000B");
		extendedStatistic = (ExtendedStatistics) haivisionMediaPlatformCommunicator.getMultipleStatistics().get(0);
		Map<String, String> statistics = extendedStatistic.getStatistics();
		List<AdvancedControllableProperty> advancedControllablePropertyList = extendedStatistic.getControllableProperties();
		Assert.assertEquals(3, statistics.size());
		Assert.assertEquals("33", statistics.get("NumberOfDevices"));
	}

	/**
	 * Tests the retrieval of multiple statistics with specific device type filters applied, verifying the number of devices in the aggregated list.
	 *
	 * @throws Exception if there's an error during the test process.
	 */
	@Test
	void testGetMultipleStatisticsWithFiltering() throws Exception {
		haivisionMediaPlatformCommunicator.setFilterByDeviceType("Play 2000A, Play 2000B");
		haivisionMediaPlatformCommunicator.getMultipleStatistics();
		haivisionMediaPlatformCommunicator.retrieveMultipleStatistics();
		Thread.sleep(30000);
		List<AggregatedDevice> aggregatedDeviceList = haivisionMediaPlatformCommunicator.retrieveMultipleStatistics();
		Assert.assertEquals(33, aggregatedDeviceList.size());
	}

	/**
	 * Tests the retrieval of multiple statistics with specific device type filters applied, verifying the number of devices in the aggregated list.
	 *
	 * @throws Exception if there's an error during the test process.
	 */
	@Test
	void testGetMultipleStatisticsWithTagNameFilter() throws Exception {
		haivisionMediaPlatformCommunicator.setFilterByTagName("  H2 ");
		haivisionMediaPlatformCommunicator.getMultipleStatistics();
		haivisionMediaPlatformCommunicator.retrieveMultipleStatistics();
		Thread.sleep(30000);
		List<AggregatedDevice> aggregatedDeviceList = haivisionMediaPlatformCommunicator.retrieveMultipleStatistics();
		Assert.assertEquals(3, aggregatedDeviceList.size());
	}

	/**
	 * Tests the retrieval of multiple statistics from the Haivision Media Platform, verifying the aggregated device list size.
	 *
	 * @throws Exception if there's an error during the test process.
	 */
	@Test
	void testGetMultipleStatistics() throws Exception {
		haivisionMediaPlatformCommunicator.getMultipleStatistics();
		haivisionMediaPlatformCommunicator.retrieveMultipleStatistics();
		Thread.sleep(30000);
		List<AggregatedDevice> aggregatedDeviceList = haivisionMediaPlatformCommunicator.retrieveMultipleStatistics();
		Assert.assertEquals(45, aggregatedDeviceList.size());
	}

	/**
	 * Tests the aggregated data retrieved from the Haivision Media Platform, including status, MAC address, IP address, firmware, and hostname.
	 *
	 * @throws Exception if there's an error during the test process.
	 */
	@Test
	void testAggregatedData() throws Exception {
		haivisionMediaPlatformCommunicator.getMultipleStatistics();
		haivisionMediaPlatformCommunicator.retrieveMultipleStatistics();
		Thread.sleep(30000);
		List<AggregatedDevice> aggregatedDeviceList = haivisionMediaPlatformCommunicator.retrieveMultipleStatistics();
		Assert.assertEquals(45, aggregatedDeviceList.size());
		Assert.assertEquals("Offline", aggregatedDeviceList.get(0).getProperties().get("Status"));
		Assert.assertEquals("90:ac:3f:17:7d:a5", aggregatedDeviceList.get(0).getProperties().get("MACAddress"));
		Assert.assertEquals("10.254.29.198", aggregatedDeviceList.get(0).getProperties().get("IPAddress"));
		Assert.assertEquals("8.3.46", aggregatedDeviceList.get(0).getProperties().get("Firmware"));
		Assert.assertEquals("64D96H001234", aggregatedDeviceList.get(0).getProperties().get("Hostname"));
	}

	/**
	 * Tests the control of the 'Content#Type' property for a specific device, verifying the updated value.
	 *
	 * @throws Exception if there's an error during the test process.
	 */
	@Test
	void testContentTypeControl() throws Exception {
		haivisionMediaPlatformCommunicator.getMultipleStatistics();
		haivisionMediaPlatformCommunicator.retrieveMultipleStatistics();
		Thread.sleep(30000);
		haivisionMediaPlatformCommunicator.retrieveMultipleStatistics();
		ControllableProperty controllableProperty = new ControllableProperty();
		String property = "Content#Type";
		String value = "Videos";
		String deviceId = "yjdeqZ5k02-P3glODo4GvA";
		controllableProperty.setProperty(property);
		controllableProperty.setValue(value);
		controllableProperty.setDeviceId(deviceId);
		haivisionMediaPlatformCommunicator.controlProperty(controllableProperty);

		List<AggregatedDevice> aggregatedDeviceList = haivisionMediaPlatformCommunicator.retrieveMultipleStatistics();
		Optional<AdvancedControllableProperty> advancedControllableProperty = aggregatedDeviceList.get(1).getControllableProperties().stream().filter(item ->
				property.equals(item.getName())).findFirst();
		Assert.assertEquals(value, advancedControllableProperty.get().getValue());
	}

	/**
	 * Tests the application of change control for the 'Content#ApplyChange' property of a specific device, ensuring the value is set successfully.
	 *
	 * @throws Exception if there's an error during the test process.
	 */
	@Test
	void testApplyChangeControl() throws Exception {
		haivisionMediaPlatformCommunicator.getMultipleStatistics();
		haivisionMediaPlatformCommunicator.retrieveMultipleStatistics();
		Thread.sleep(30000);
		haivisionMediaPlatformCommunicator.retrieveMultipleStatistics();
		ControllableProperty controllableProperty = new ControllableProperty();
		String property = "Content#SaveChanges";
		String value = "1";
		String deviceId = "yjdeqZ5k02-P3glODo4GvA";
		controllableProperty.setProperty(property);
		controllableProperty.setValue(value);
		controllableProperty.setDeviceId(deviceId);
		haivisionMediaPlatformCommunicator.controlProperty(controllableProperty);

		List<AggregatedDevice> aggregatedDeviceList = haivisionMediaPlatformCommunicator.retrieveMultipleStatistics();
		Optional<AdvancedControllableProperty> advancedControllableProperty = aggregatedDeviceList.get(1).getControllableProperties().stream().filter(item ->
				property.equals(item.getName())).findFirst();
		Assert.assertEquals(value, advancedControllableProperty.get().getValue());
	}

	/**
	 * Tests the application of change control for the Volume property of a specific device, ensuring the value is set successfully.
	 *
	 * @throws Exception if there's an error during the test process.
	 */
	@Test
	void testVolumeControl() throws Exception {
		haivisionMediaPlatformCommunicator.getMultipleStatistics();
		haivisionMediaPlatformCommunicator.retrieveMultipleStatistics();
		Thread.sleep(30000);
		haivisionMediaPlatformCommunicator.retrieveMultipleStatistics();
		ControllableProperty controllableProperty = new ControllableProperty();
		String property = "Controls#Volume(%)";
		String value = "50.0";
		String deviceId = "TCfg3p5HYOzrekyVISMYXg";
		controllableProperty.setProperty(property);
		controllableProperty.setValue(value);
		controllableProperty.setDeviceId(deviceId);
		haivisionMediaPlatformCommunicator.controlProperty(controllableProperty);

		List<AggregatedDevice> aggregatedDeviceList = haivisionMediaPlatformCommunicator.retrieveMultipleStatistics();
		Optional<AdvancedControllableProperty> advancedControllableProperty = aggregatedDeviceList.get(1).getControllableProperties().stream().filter(item ->
				property.equals(item.getName())).findFirst();
		Assert.assertEquals(value, advancedControllableProperty.get().getValue());
	}

	/**
	 * Tests the application of change control for the Standby property of a specific device, ensuring the value is set successfully.
	 *
	 * @throws Exception if there's an error during the test process.
	 */
	@Test
	void testMuteControl() throws Exception {
		haivisionMediaPlatformCommunicator.getMultipleStatistics();
		haivisionMediaPlatformCommunicator.retrieveMultipleStatistics();
		Thread.sleep(30000);
		haivisionMediaPlatformCommunicator.retrieveMultipleStatistics();
		ControllableProperty controllableProperty = new ControllableProperty();
		String property = "Controls#Mute";
		String value = "0";
		String deviceId = "TCfg3p5HYOzrekyVISMYXg";
		controllableProperty.setProperty(property);
		controllableProperty.setValue(value);
		controllableProperty.setDeviceId(deviceId);
		haivisionMediaPlatformCommunicator.controlProperty(controllableProperty);

		List<AggregatedDevice> aggregatedDeviceList = haivisionMediaPlatformCommunicator.retrieveMultipleStatistics();
		Optional<AdvancedControllableProperty> advancedControllableProperty = aggregatedDeviceList.get(1).getControllableProperties().stream().filter(item ->
				property.equals(item.getName())).findFirst();
		Assert.assertEquals(value, advancedControllableProperty.get().getValue());
	}
}
