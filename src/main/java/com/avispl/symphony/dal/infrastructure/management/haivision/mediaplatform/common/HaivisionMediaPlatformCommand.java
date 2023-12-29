/*
 *  Copyright (c) 2023 AVI-SPL, Inc. All Rights Reserved.
 */

package com.avispl.symphony.dal.infrastructure.management.haivision.mediaplatform.common;

/**
 * Utility class containing constant strings representing commands for the Haivision Media Platform API.
 *
 * @author Harry / Symphony Dev Team<br>
 * Created on 12/20/2023
 * @since 1.0.0
 */
public class HaivisionMediaPlatformCommand {
	public static final String LOGIN_COMMAND = "apis/authentication/login";
	public static final String SYSTEM_INFO_COMMAND = "apis/system/version";
	public static final String GET_DEVICE_INFO_COMMAND = "apis/devices/stbs?page=%s&pageSize=%s";
	public static final String STB_COMMAND = "apis/devices/stbs/%s/commands";
	public static final String SOURCE_COMMAND = "apis/sources?pageSize=100";
	public static final String VIDEO_COMMAND = "apis/sections/content?itemType=asset&pageSize=100";
	public static final String SESSION_COMMAND = "apis/sections/content?itemType=session&pageSize=100";
	public static final String LAYOUT_COMMAND = "apis/_/compositions?page=%s&pageSize=100";
}
