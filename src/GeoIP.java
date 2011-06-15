/*

Tit Petric, Monotek d.o.o., (cc) 2010, tit.petric@monotek.net
http://creativecommons.org/licenses/by-sa/3.0/

*/

package com.monotek.wms.module;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.File;

import java.util.*;
import java.util.regex.*;

import com.wowza.wms.module.*;
import com.wowza.wms.application.*;
import com.wowza.wms.stream.*;
import com.wowza.wms.client.*;
import com.wowza.util.*;

import java.net.URL;
import java.net.URLConnection;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.*;

// override play
import com.wowza.wms.amf.*;
import com.wowza.wms.request.*;

import com.wowza.wms.httpstreamer.cupertinostreaming.httpstreamer.HTTPStreamerSessionCupertino;
import com.wowza.wms.httpstreamer.sanjosestreaming.httpstreamer.HTTPStreamerSessionSanJose;
import com.wowza.wms.httpstreamer.smoothstreaming.httpstreamer.HTTPStreamerSessionSmoothStreamer;
import com.wowza.wms.rtp.model.RTPSession;

public class GeoIP extends ModuleBase
{
	public WMSProperties ServerSideParameters;
	private long LocationInfoLastModified = 0;
	private Document LocationInfo;

	private String ConfigFile;
	private boolean debug = false;

	private static Map<String,Pattern> regex_pool = new HashMap<String,Pattern>();

	private GeoIPLookupService geoip_lookup;

	private static NetMaskLookupService netmask_lookup;

	private boolean streamShutdown = true;
	private String streamRemap = "";

	/** Check if IPAddress is allowed to access streamName */
	public boolean allowPlayback(String streamName, String IPAddress)
	{
		// default playback restrictions (if xml config is broken)
		boolean allowPlayback = true;
		if (ServerSideParameters.getPropertyInt("GeolocationDefaultRestrict",0)==1) {
			allowPlayback = false;
		}

		// read config file
		File file = new File(ConfigFile);
		long lastModified = 0;
		try {
			// reload if modified since last load
			lastModified = file.lastModified();
			if (lastModified != LocationInfoLastModified) {
				getLogger().info("geoip.allowPlayback.config: Reading "+ConfigFile+".");
				LocationInfo = null;
				DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
				DocumentBuilder db = dbf.newDocumentBuilder();
				LocationInfo = db.parse(file);
			}

		} catch (java.io.IOException e) {
			getLogger().warn("geoip.allowPlayback.config: File "+ConfigFile+" doesn't exist.");
			LocationInfo = null;
		} catch (Exception e) {
			getLogger().warn("geoip.allowPlayback.config: Problem parsing "+ConfigFile+" file.");
			LocationInfo = null;
		}

		// log last modified time for updates
		LocationInfoLastModified = lastModified;

		logDebug("Checking stream: " + streamName + " / IP: " + IPAddress);

		if (LocationInfo != null) {
			// we have a DOM structure, go trough locations.
			NodeList locations = LocationInfo.getDocumentElement().getElementsByTagName("Location");
			for (int childNum = 0; childNum < locations.getLength(); childNum++) {
				Element child = (Element)locations.item(childNum);
				String locPath = child.getAttributes().getNamedItem("path").getNodeValue();
				String locRestrict = child.getAttributes().getNamedItem("restrict").getNodeValue();

				// default: path starts with location name
				boolean validPath = streamName.startsWith(locPath);

				// regex paths
				if (child.getAttributes().getNamedItem("type") != null && child.getAttributes().getNamedItem("type").getNodeValue().equals("regex")) {
					// cache compiled regex
					if (!regex_pool.containsKey(locPath)) {
						logDebug("Compiling regex pattern: '"+locPath+"'");
						regex_pool.put(locPath, Pattern.compile(locPath));
					}
					// match regex
					validPath = regex_pool.get(locPath).matcher(streamName).find();
				}

				if (validPath) {
					logDebug("Location found: " + locPath + " restricted='" + locRestrict + "'");

					allowPlayback = false;

					// get exceptions to base restriction
					NodeList exceptions = child.getElementsByTagName("Except");
					for (int exceptNum = 0; exceptNum < exceptions.getLength(); exceptNum++) {
						Element child2 = (Element)exceptions.item(exceptNum);
						String exceptType = child2.getAttributes().getNamedItem("type").getNodeValue();
						String exceptValue = child2.getFirstChild().getNodeValue();

						logDebug("    Except type: " + exceptType + " value='" + exceptValue + "'");

						// validate ip
						if (exceptType.equals("ip")) {
							try {
								if (netmask_lookup.ValidateIP(IPAddress, exceptValue)) {
									logDebug("    > Validated IP ("+exceptValue+")");
									allowPlayback = true;
									break;
								}
							} catch (Exception e) {
								getLogger().error(e.toString());
							}
						}

						// validate country
						if (exceptType.equals("country")) {
							if (geoip_lookup.ValidateCountry(IPAddress, exceptValue)) {
								logDebug("    > Validated country ("+exceptValue+")");
								allowPlayback = true;
								break;
							}
						}
					}
					// reverse restrictions
					if (locRestrict.equals("none")) {
						allowPlayback = !allowPlayback;
					}
					// Some valuable info for the debug console
					if (allowPlayback) {
						logDebug("GEO1 NOT Restricting playback.");
					} else {
						logDebug("GEO1 RESTRICTING playback.");
					}
					return allowPlayback;
				}
			}
		}

		// Restrict to default country configured in Application.xml
		String DefaultRestrictCountry = ServerSideParameters.getPropertyStr("GeolocationDefaultRestrictCountry");
		if (!allowPlayback && geoip_lookup.ValidateCountry(IPAddress, DefaultRestrictCountry)) {
			allowPlayback = !allowPlayback;
		}

		// Some valuable info for the debug console
		if (allowPlayback) {
			logDebug("GEO2 NOT Restricting playback.");
		} else {
			logDebug("GEO2 RESTRICTING playback.");
		}

		return allowPlayback;
	}

	/** Playback method, overriden */
	public void play(IClient client, RequestFunction function, AMFDataList params)
	{
		String streamName = getParamString(params, PARAM1);
		String[] streamNameSplit = streamName.split(":");
		String ClientIP = client.getIp();

		// Extremists.flv vs. mp4:Extremists.mp4
		String realStreamName = streamNameSplit.length==1 ? streamNameSplit[0] : streamNameSplit[1];
		if (!allowPlayback(realStreamName, ClientIP)) {
			if (streamShutdown) {
				client.setShutdownClient(true);
				return;
			}
			params.set(PARAM1, streamRemap);
			logDebug("play - rewriting stream to "+streamRemap);
		}

		ModuleCore.play(client, function, params);
	}

	/** Debug logging method */
	void logDebug(String str)
	{
		if (debug) {
			getLogger().info("geoip.debug: " + str);
		}
	}

	public void onAppStart(IApplicationInstance appInstance) {
		String fullname = appInstance.getApplication().getName() + "/" + appInstance.getName();
		getLogger().info("geoip.onAppStart: " + fullname);

		// Extract the parameters
		ServerSideParameters = appInstance.getProperties();

		// Set up environment variables
		Map<String,String> pathMap = new HashMap<String,String>();
		pathMap.put("com.wowza.wms.context.VHost", appInstance.getVHost().getName());
		pathMap.put("com.wowza.wms.context.VHostConfigHome", appInstance.getVHost().getHomePath());
		pathMap.put("com.wowza.wms.context.Application", appInstance.getApplication().getName());
		pathMap.put("com.wowza.wms.context.ApplicationInstance", appInstance.getName());

		// Expand config file using environment
		ConfigFile = ServerSideParameters.getPropertyStr("GeolocationConfigFile");
		if (ConfigFile == null) {
			getLogger().error("geoip.onAppStart: Property GeolocationConfigFile is missing.");
		} else {
			getLogger().info("geoip.onAppStart: Property GeolocationConfigFile: " + ConfigFile);
			ConfigFile = SystemUtils.expandEnvironmentVariables(ConfigFile, pathMap);
			getLogger().info("geoip.onAppStart: Property GeolocationConfigFile: " + ConfigFile);
		}

		// Set debug value
		debug = ServerSideParameters.getPropertyBoolean("GeolocationDebug", debug);

		// Configure stream action (shutdown or remap to default location)
		streamShutdown = ServerSideParameters.getPropertyBoolean("GeolocationPlaybackShutdown", true);
		if (!streamShutdown) {
			streamRemap = ServerSideParameters.getPropertyStr("GeolocationPlaybackFile");
			if (streamRemap == null) {
				getLogger().error("geoip.onAppStart: Property GeolocationPlaybackFile is not set, but GeolocationPlaybackShutdown is False!");
				streamShutdown = true;
			}
		}

		// Spawn netmask lookup service
		netmask_lookup = new NetMaskLookupService();

		// Configure our GeoIP lookup service
		String GeoIPDatabase = ServerSideParameters.getPropertyStr("GeoIPDatabase","/usr/share/GeoIP/GeoIP.dat");
		geoip_lookup = new GeoIPLookupService(GeoIPDatabase);
		if (!geoip_lookup.GetStatus()) {
			getLogger().error("geoip.onAppStart: GeoIP LookupService - GeoIPDatabase problem!");
		}
	}

	// code by marzipi and shamrock (taken from wowza forum 1978) - copied from hampei
	public void onHTTPCupertinoStreamingSessionCreate(HTTPStreamerSessionCupertino httpCupertinoStreamingSession)
	{
		String ClientIP = httpCupertinoStreamingSession.getIpAddress();
		String streamName = httpCupertinoStreamingSession.getStreamName();

		String[] streamNameSplit = streamName.split(":");
		String realStreamName = streamNameSplit.length==1 ? streamNameSplit[0] : streamNameSplit[1];
		logDebug("geoip.cupertino: Real stream name "+realStreamName);
		logDebug("geoip.cupertino: IP source "+ClientIP);

		if (!allowPlayback(realStreamName, ClientIP)) {
			httpCupertinoStreamingSession.rejectSession();
		}
	}

	public void onHTTPSmoothStreamingSessionCreate(HTTPStreamerSessionSmoothStreamer httpSmoothStreamingSession)
	{
		String ClientIP = httpSmoothStreamingSession.getIpAddress();
		String streamName = httpSmoothStreamingSession.getStreamName();

		String[] streamNameSplit = streamName.split(":");
		String realStreamName = streamNameSplit.length==1 ? streamNameSplit[0] : streamNameSplit[1];
		logDebug("geoip.smooth: Real stream name "+realStreamName);
		logDebug("geoip.smooth: IP source "+ClientIP);

		if (!allowPlayback(realStreamName, ClientIP)) {
			httpSmoothStreamingSession.rejectSession();
		}
	}
	
	public void onHTTPSanjoseStreamingSessionCreate(HTTPStreamerSessionSanJose httpSanJoseStreamingSession)
	{
		String ClientIP = httpSanJoseStreamingSession.getIpAddress();
		String streamName = httpSanJoseStreamingSession.getStreamName();

		String[] streamNameSplit = streamName.split(":");
		String realStreamName = streamNameSplit.length==1 ? streamNameSplit[0] : streamNameSplit[1];
		logDebug("geoip.sanjose: Real stream name "+realStreamName);
		logDebug("geoip.sanjose: IP source "+ClientIP);

		if (!allowPlayback(realStreamName, ClientIP)) {
			httpSanJoseStreamingSession.rejectSession();
		}
	}
	
	public void onRTPSessionCreate(RTPSession rtpSession)
	{
		String ClientIP = rtpSession.getIp();
		String streamName = rtpSession.getUri();

		String[] streamNameSplit = streamName.split(":");
		String realStreamName = streamNameSplit.length==1 ? streamNameSplit[0] : streamNameSplit[1];
		logDebug("geoip.rtp: Real stream name "+realStreamName);
		logDebug("geoip.rtp: IP source "+ClientIP);

		if (!allowPlayback(realStreamName, ClientIP)) {
			rtpSession.rejectSession();
		}
	}
}
