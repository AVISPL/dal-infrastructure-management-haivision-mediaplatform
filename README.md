# Haivision Media Platform Integration - Capabilities & Configuration
This document covers Haivision Media Platform (HMP) Aggregator Capabilities and Configuration.

Symphony integrates with Haivision Media Platform to provide monitoring and control of Haivision Play Set-Top Boxes connected to the HMP server. The adapter communicates with the HMP API and exposes device health, content source selection, power and audio controls, and firmware tracking.

Main features are: real-time Play device monitoring, content source and type control, volume and mute control, power state management, and device filtering by type or tag.

## Haivision Media Platform - Main use cases
- **Monitor** Play device online status, power state, firmware version, IP/MAC address, NTP server, and connection timestamps
- **Control** content type and source selection, volume, mute, power state, and reboot
- **Track** device status details and tags across the HMP environment
- **Inventory** Haivision Play Set-Top Boxes (Play 1000, Play 2000A, Play 2000B, Play 4000) registered in HMP

## Haivision Media Platform - Device Configuration and Provisioning

### Haivision Media Platform - Connection Setup

| Field | Description |
|---|---|
| Device Type | Infrastructure |
| Category | Management |
| Manufacturer | Haivision |
| Model | Media Platform |
| Monitoring Service | Advanced Monitoring |
| Monitoring Source | Direct |
| Management Address | Hostname of the Haivision Media Platform server |
| Protocol | HTTP or HTTPS |
| Username | HMP account username |
| Password | HMP account password |
| Port Number | 80 (HTTP) or 443 (HTTPS) by default — may differ depending on server or proxy configuration |

### Haivision Media Platform - Device Provisioning

By default, unprovisioned Play devices appear on Aggregated Devices → Unprovisioned Devices tab.

To import a Haivision Play device for monitoring:
1. Click the (+) icon on the unprovisioned device
2. Fill in required values: Type (AV Devices), Category (Players), Manufacturer (Haivision), Model (e.g., Play 2000A)
3. Click Import, then OK to confirm

### Haivision Media Platform - Adapter configuration properties

| Property | Description |
|---|---|
| filterByDeviceType | Limits monitoring to specific Play device models. Comma-separated values: Play 1000, Play 2000A, Play 2000B, Play 4000. Default: no filtering (all device types shown) |
| filterByTagName | Limits monitoring to devices with specified HMP tags. Comma-separated tag names as defined in HMP. Default: no filtering |

For detailed information on the aggregator and its configuration, please refer to our knowledgebase -> https://symphony.knowledgeowl.com/help/haivision-media-platform-aggregator

## Haivision Media Platform - Available Monitored Data

### Aggregator Properties
AdapterBuildDate, AdapterVersion, AdapterUptime, AdapterUptime(min), LastMonitoringCycleDuration(s), MonitoredDevicesTotal, MonitoringCycleInterval(min), Build (HMP build number), Version (HMP version)

### Aggregated Device Properties

**General:**

| Property | Description |
|---|---|
| deviceName, deviceModel, deviceId | Device identity |
| deviceOnline, Status, StatusDetails | Online state and status detail |
| Power | Power state (On/Off) — also controllable |
| Firmware | Installed firmware version |
| IPAddress, MACAddress, Hostname | Network identity |
| NTPServer | Configured NTP server |
| Extension | Device phone extension |
| LastConnectedAt | Last successful connection timestamp |
| Tags | HMP tags assigned to the device |

**Status values:**

| Value | Meaning |
|---|---|
| Online | Device is connected normally or in DWS Mode |
| Standby | Device is in Standby mode but has connected recently |
| Warning | No recent notify, no event stream connection, or firmware version mismatch |
| Offline | Device has not been able to connect for a period of time |
| Never | Device has never connected to the HMP server |

## Haivision Media Platform - Control Capabilities

**Content group** — select what the device displays:
- ContentType (dropdown) and Source (dropdown)
- Changes require ApplyChanges to commit or CancelChanges to revert

**Controls group:**
- Mute (On/Off toggle)
- Volume(%) (slider 0–100); VolumeCurrentValue(%) shows the current reading
- Reboot
- Power (On/Off)

**Notes:**
- The Source dropdown may show duplicate names — these are distinct sources with different IDs
- Some Source options may display as None in Symphony when the HMP Web UI shows an empty name
- When a device is in Standby (Power Off), source names other than Content Type Layout will appear as None

## Haivision Media Platform - Troubleshooting

**Login Error**
- Verify the HMP account username and password are correct
- Confirm the account has sufficient permissions to access device data in HMP

**API Error**
- Check the API error description in the aggregator extended properties
- Confirm the Management Address is the correct hostname of the HMP server
- Verify the Protocol and Port match the HMP server configuration (HTTP/80 or HTTPS/443)

**Link Error / Ping Timeout**
- Confirm the Cloud Connector can reach the HMP server on the configured port
- Check the Ping Protocol in the Symphony device configuration

**Devices Not Appearing**
- Check filterByDeviceType and filterByTagName — if set, only matching devices are retrieved
- Confirm the device has been provisioned (imported) in Symphony

If none of the recommended steps help, please enter an SOS ticket at {https://avi-spl.atlassian.net/servicedesk/customer/portals}

## Haivision Media Platform - What AI Assistant can do with it:
- Find Haivision Media Platform Aggregated Devices (HMP as Monitoring Proxy) in Symphony
- Verify Haivision Media Platform Aggregator configuration and adapter property settings
- Report on Play device status, power state, firmware version, and content source

## Haivision Media Platform - What AI Assistant cannot do with it:
- Provision devices
- Manage HMP user accounts or permissions
- Configure content sources or channels directly in HMP
