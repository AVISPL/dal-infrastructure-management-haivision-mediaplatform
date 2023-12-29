/*
 *  Copyright (c) 2023 AVI-SPL, Inc. All Rights Reserved.
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

	@Test
	void testGetAggregatorData() throws Exception {
		haivisionMediaPlatformCommunicator.setFilterByDeviceType("Play 2000, Play 2000B");
		extendedStatistic = (ExtendedStatistics) haivisionMediaPlatformCommunicator.getMultipleStatistics().get(0);
		Map<String, String> statistics = extendedStatistic.getStatistics();
		List<AdvancedControllableProperty> advancedControllablePropertyList = extendedStatistic.getControllableProperties();
		Assert.assertEquals(3, statistics.size());
	}

	@Test
	void testGetMultipleStatistics() throws Exception {
		haivisionMediaPlatformCommunicator.getMultipleStatistics();
		haivisionMediaPlatformCommunicator.retrieveMultipleStatistics();
		Thread.sleep(30000);
		List<AggregatedDevice> aggregatedDeviceList = haivisionMediaPlatformCommunicator.retrieveMultipleStatistics();
		Assert.assertEquals(45, aggregatedDeviceList.size());
	}
}
