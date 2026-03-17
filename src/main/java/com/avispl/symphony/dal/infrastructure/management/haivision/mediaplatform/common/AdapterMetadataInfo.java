/** Copyright (c) 2026 AVI-SPL, Inc. All Rights Reserved. */
package com.avispl.symphony.dal.infrastructure.management.haivision.mediaplatform.common;

/**
 * Enum representing adapter metadata fields for the General of Aggregator.
 *
 * @author Kevin / Symphony Dev Team
 * @since 1.0.0
 */
public enum AdapterMetadataInfo {
	ADAPTER_BUILD_DATE("AdapterBuildDate"),
	ADAPTER_UPTIME("AdapterUptime"),
	ADAPTER_UPTIME_MIN("AdapterUptime(min)"),
	ADAPTER_VERSION("AdapterVersion"),
	LAST_MONITORING_CYCLE_DURATION("LastMonitoringCycleDuration(sec)"),
	MONITORED_DEVICES_TOTAL("MonitoredDevicesTotal"),
	MONITORED_CYCLE_INTERVAL("MonitoringCycleInterval(min)")
	;

	private final String name;

	AdapterMetadataInfo(String name) {
		this.name = name;
	}

	public String getName() {
		return name;
	}
}
