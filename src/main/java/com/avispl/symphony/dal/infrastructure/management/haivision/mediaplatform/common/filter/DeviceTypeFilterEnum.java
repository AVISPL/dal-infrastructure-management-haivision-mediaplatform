/*
 *  Copyright (c) 2023 AVI-SPL, Inc. All Rights Reserved.
 */

package com.avispl.symphony.dal.infrastructure.management.haivision.mediaplatform.common.filter;

/**
 * Enum representing function filters used for filtering devices.
 *
 * @author Harry / Symphony Dev Team<br>
 * Created on 9/28/2023
 * @since 1.0.0
 */
public enum DeviceTypeFilterEnum {
	PLAY_1000("Play 1000", "calypso_stb"),
	PLAY_2000_A("Play 2000A", "play_stb_2000"),
	PLAY_2000_B("Play 2000B", "play_stb_2000_B"),
	PLAY_4000("Play 4000", "play_stb_4000"),
			;
	private final String name;
	private final String value;

	/**
	 * Constructor for DeviceTypeFilterEnum.
	 *
	 * @param name  The name representing the interval.
	 * @param value The numeric value representing the interval.
	 */
	DeviceTypeFilterEnum(String name, String value) {
		this.name = name;
		this.value = value;
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
	 * Retrieves {@link #value}
	 *
	 * @return value of {@link #value}
	 */
	public String getValue() {
		return value;
	}
}
