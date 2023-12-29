/*
 *  Copyright (c) 2023 AVI-SPL, Inc. All Rights Reserved.
 */

package com.avispl.symphony.dal.infrastructure.management.haivision.mediaplatform.common;

/**
 * Enum representing different types of channels.
 *
 * @author Harry / Symphony Dev Team<br>
 * Created on 12/27/2023
 * @since 1.0.0
 */
public enum ChannelTypeEnum {
	PLAY_1000("Channel", "source"),
	PLAY_2000_A("Videos", "asset"),
	PLAY_2000_B("Sessions", "session"),
	PLAY_4000("Layouts", "composition"),
	;
	private final String name;
	private final String value;

	/**
	 * Constructor for ChannelTypeEnum.
	 *
	 * @param name  The name representing the interval.
	 * @param value The numeric value representing the interval.
	 */
	ChannelTypeEnum(String name, String value) {
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
