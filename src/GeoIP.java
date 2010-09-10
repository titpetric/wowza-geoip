/*

Tit Petrič, Monotek d.o.o., (cc) 2010, tit.petric@monotek.net
http://creativecommons.org/licenses/by-sa/3.0/

*/

package com.monotek.wms.module;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.File;

import com.wowza.wms.module.*;
import com.wowza.wms.application.*;
import com.wowza.wms.stream.*;
import com.wowza.wms.client.*;

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
		String ConfigFile = ServerSideParameters.getPropertyStr("GeolocationConfigFile");
		File file = new File(ConfigFile);
		long lastModified = 0;
		try {
			// reload if modified since last load
			lastModified = file.lastModified();
			if (lastModified != LocationInfoLastModified) {
				getLogger().info("GEO: Reading "+ConfigFile+".");
				LocationInfo = null;
				DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
				DocumentBuilder db = dbf.newDocumentBuilder();
				LocationInfo = db.parse(file);
			}

		} catch (java.io.IOException e) {
			getLogger().info("GEO: File "+ConfigFile+" doesn't exist.");
			LocationInfo = null;
		} catch (Exception e) {
			getLogger().info("GEO: Problem parsing "+ConfigFile+" file.");
			LocationInfo = null;
		}

		// log last modified time for updates
		LocationInfoLastModified = lastModified;

		getLogger().info("GEO: Checking stream name: " + streamName);

		if (LocationInfo != null) {
			// we have a DOM structure, go trough locations.
			NodeList locations = LocationInfo.getDocumentElement().getElementsByTagName("Location");
			for (int childNum = 0; childNum < locations.getLength(); childNum++) {
				Element child = (Element)locations.item(childNum);
				String locPath = child.getAttributes().getNamedItem("path").getNodeValue();
				String locRestrict = child.getAttributes().getNamedItem("restrict").getNodeValue();

				if (streamName.length() > locPath.length() && streamName.startsWith(locPath)) {
					getLogger().info("GEO Location found: " + locPath + " restricted='" + locRestrict + "'");

					allowPlayback = false;

					// get exceptions to base restriction
					NodeList exceptions = child.getElementsByTagName("Except");
					for (int exceptNum = 0; exceptNum < exceptions.getLength(); exceptNum++) {
						Element child2 = (Element)exceptions.item(exceptNum);
						String exceptType = child2.getAttributes().getNamedItem("type").getNodeValue();
						String exceptValue = child2.getFirstChild().getNodeValue();

						getLogger().info("    Except type: " + exceptType + " value='" + exceptValue + "'");

						// validate ip
						if (exceptType.equals("ip")) {
							if (IPAddress.equals(exceptValue)) {
								getLogger().info("    > Validated IP ("+exceptValue+")");
								allowPlayback = true;
								break;
							}
						}

						// validate netmask
						if (exceptType.equals("netmask")) {
							try {
								if (netmask_lookup.ValidateIP(IPAddress, exceptValue)) {
									getLogger().info("    > Validated netmask ("+exceptValue+")");
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
								getLogger().info("    > Validated country ("+exceptValue+")");
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
						getLogger().info("GEO1 NOT Restricting playback.");
					} else {
						getLogger().info("GEO1 RESTRICTING playback.");
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
			getLogger().info("GEO2 NOT Restricting playback.");
		} else {
			getLogger().info("GEO2 RESTRICTING playback.");
		}

		return allowPlayback;
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
		getLogger().info("onAppStart: " + fullname);

		// Extract the parameters and save them to global variable
		ServerSideParameters = appInstance.getProperties();

		// Spawn netmask lookup service
		netmask_lookup = new NetMaskLookupService();

		// Configure our GeoIP lookup service
		String GeoIPDatabase = ServerSideParameters.getPropertyStr("GeoIPDatabase","/usr/share/GeoIP/GeoIP.dat");
		geoip_lookup = new GeoIPLookupService(GeoIPDatabase);
		if (!geoip_lookup.GetStatus()) {
			getLogger().error("GeoIP LookupService - GeoIPDatabase problem!");
		}
	}
}