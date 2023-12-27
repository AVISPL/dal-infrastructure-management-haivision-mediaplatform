package com.avispl.symphony.dal.infrastructure.management.haivision.mediaplatform.common;

/**
 * HaivisionMediaPlatformCommand
 *
 * @author Harry / Symphony Dev Team<br>
 * Created on 12/20/2023
 * @since 1.0.0
 */
public class HaivisionMediaPlatformCommand {
	public static final String LOGIN_COMMAND = "apis/authentication/login";
	public static final String SYSTEM_INFO_COMMAND = "apis/system/version";
	public static final String GET_DEVICE_INFO_COMMAND = "apis/devices/stbs?pageSize=1000";
	public static final String STB_COMMAND = "apis/devices/stbs/%s/commands";

}
