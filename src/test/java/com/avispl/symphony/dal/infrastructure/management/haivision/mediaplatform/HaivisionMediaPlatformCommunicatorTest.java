/*
 *  Copyright (c) 2023 AVI-SPL, Inc. All Rights Reserved.
 */

package com.avispl.symphony.dal.infrastructure.management.haivision.mediaplatform;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.avispl.symphony.api.dal.dto.control.AdvancedControllableProperty;
import com.avispl.symphony.api.dal.dto.monitor.ExtendedStatistics;
import com.avispl.symphony.api.dal.dto.monitor.aggregator.AggregatedDevice;

/**
 * MagicInfoCommunicatorTest
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
}
