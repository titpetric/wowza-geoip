/*

Tit Petrič, Monotek d.o.o., (cc) 2010, tit.petric@monotek.net
http://creativecommons.org/licenses/by-sa/3.0/

*/

package com.monotek.wms.module;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.File;

import java.util.*;

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

public class GeoIP extends ModuleBase
{
	public static WMSProperties ServerSideParameters;
	private static long LocationInfoLastModified = 0;
	private static Document LocationInfo;

	private String ConfigFile;
	private boolean debug = false;

	private static GeoIPLookupService geoip_lookup;
	private static NetMaskLookupService netmask_lookup;

	public void onStreamCreate(IMediaStream stream)
	{
		stream.addClientListener(new StreamListener());
	}

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

				if (streamName.length() > locPath.length() && streamName.startsWith(locPath)) {
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
							if (IPAddress.equals(exceptValue)) {
								logDebug("    > Validated IP ("+exceptValue+")");
								allowPlayback = true;
								break;
							}
						}

						// validate netmask
						if (exceptType.equals("netmask")) {
							try {
								if (netmask_lookup.ValidateIP(IPAddress, exceptValue)) {
									logDebug("    > Validated netmask ("+exceptValue+")");
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

	void logDebug(String str)
	{
		if (debug) {
			getLogger().info("geoip.debug: " + str);
		}
	}

	/** Glue for each client stream */
	class StreamListener implements IMediaStreamActionNotify
	{
		public void onPlay(IMediaStream stream, String streamName, double playStart, double playLen, int playReset)
		{
			IClient ClientTMP = stream.getClient();
			String ClientIP = ClientTMP.getIp();

			if (!allowPlayback(streamName, ClientIP)) {
				// @todo: figure out how to redirect stream or notify player of geoip/access restrictions
				ClientTMP.setShutdownClient(true);
			}
		}

		public void onStop(IMediaStream stream) { }
		public void onSeek(IMediaStream stream, double seek) { }
		public void onPause(IMediaStream stream,boolean sure,double where) { }
		public void onUnPublish(IMediaStream stream,String a,boolean b,boolean c) { }
		public void onPublish(IMediaStream stream,String a,boolean b,boolean c) { }
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

		// Spawn netmask lookup service
		netmask_lookup = new NetMaskLookupService();

		// Configure our GeoIP lookup service
		String GeoIPDatabase = ServerSideParameters.getPropertyStr("GeoIPDatabase","/usr/share/GeoIP/GeoIP.dat");
		geoip_lookup = new GeoIPLookupService(GeoIPDatabase);
		if (!geoip_lookup.GetStatus()) {
			getLogger().error("geoip.onAppStart: GeoIP LookupService - GeoIPDatabase problem!");
		}
	}
}