/*
 *  Copyright (c) 2023 AVI-SPL, Inc. All Rights Reserved.
 */

package com.avispl.symphony.dal.infrastructure.management.haivision.mediaplatform.common;

import java.util.Arrays;
import java.util.Optional;

/**
 * Enum representing aggregated information types related to devices.
 *
 * @author Harry / Symphony Dev Team<br>
 * Created on 12/21/2023
 * @since 1.0.0
 */
public enum AggregatedInfo {
	FIRMWARE("Firmware"),
	IPADDRESS("IPAddress"),
	MAC_ADDRESS("MACAddress"),
	LAST_CONNECT_AT("LastConnectedAt"),
	LAST_ACCEPTED_UPDATE("LastAcceptedUpdate"),
	HOSTNAME("Hostname"),
	CONTENT_ID("Id"),
	CONTENT_SOURCE("Source"),
	CONTENT_TYPE("Type"),
	TAGS("Tags"),
	MUTE("Mute"),
	REBOOT("Reboot"),
	STANDBY("Standby"),
	VOLUME("Volume(%)"),
	STATUS("Status"),
	APPLY_CHANGE("ApplyChange"),
	;
	private final String name;

	/**
	 * Constructor for SystemInfo.
	 *
	 * @param name The name representing the system information category.
	 */
	AggregatedInfo(String name) {
		this.name = name;
	}

	/**
	 * Retrieves {@link #name}
	 *
	 * @return value of {@link #name}
	 */
	public String getName() {
		return name;
	}

	/**
	 * Returns the {@link AggregatedInfo} enum constant with the specified name.
	 *
	 * @param name the name of the AggregatedInfo constant to retrieve
	 * @return the DisplayInfo constant with the specified name
	 * @throws IllegalStateException if no constant with the specified name is found
	 */
	public static AggregatedInfo getByName(String name) {
		Optional<AggregatedInfo> property = Arrays.stream(AggregatedInfo.values()).filter(group -> group.getName().equals(name)).findFirst();
		if (property.isPresent()) {
			return property.get();
		} else {
			throw new IllegalStateException(String.format("control group %s is not supported.", name));
		}
	}
}
