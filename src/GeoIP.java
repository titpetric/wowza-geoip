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

import com.maxmind.geoip.*;

public class GeoIP extends ModuleBase
{
	public static WMSProperties ServerSideParameters;
	private static long LocationInfoLastModified = 0;
	private static Document LocationInfo;

	public void onStreamCreate(IMediaStream stream)
	{
		stream.addClientListener(new StreamListener());
	}

	public boolean allowPlayback(String streamName, String IPAddress)
	{
		// default playback restrictions
		boolean allowPlayback = true;
		String DefaultRestrictCountry = ServerSideParameters.getPropertyStr("GeolocationDefaultRestrictCountry");
		if (ServerSideParameters.getPropertyInt("GeolocationDefaultRestrict",0)==1) {
			allowPlayback = false;
		}

		// resolve country by ip
		String CountryCode = "--";
		try {
			LookupService cl = new LookupService("/usr/share/GeoIP/GeoIP.dat", LookupService.GEOIP_MEMORY_CACHE);
			CountryCode = cl.getCountry(IPAddress).getCode();
			getLogger().info("GEO Country: "+IPAddress+" => "+CountryCode);
		} catch(Exception e) {
			getLogger().info("GeoIP database problem");
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

		LocationInfoLastModified = lastModified;

		getLogger().info("GEO: Checking stream name: " + streamName);

		if (LocationInfo != null) {
			// we have a DOM structure, go trough locations.
			NodeList locations = LocationInfo.getDocumentElement().getElementsByTagName("Location");
			for (int childNum = 0; childNum < locations.getLength(); childNum++) {
				Element child = (Element)locations.item(childNum);
				String locPath = child.getAttributes().getNamedItem("path").getNodeValue();
				String locRestrict = child.getAttributes().getNamedItem("restrict").getNodeValue();
				// find current location of streamed file
				if (streamName.length() > locPath.length() && streamName.startsWith(locPath)) {
					getLogger().info("GEO Location found: " + locPath + " restricted='" + locRestrict + "'");
					allowPlayback = locRestrict.equals("none");

					// get exceptions to base restriction
					NodeList exceptions = child.getElementsByTagName("Except");
					for (int exceptNum = 0; exceptNum < exceptions.getLength(); exceptNum++) {
						Element child2 = (Element)exceptions.item(exceptNum);
						String exceptCountry = child2.getAttributes().getNamedItem("country").getNodeValue();
						if (CountryCode.equals(exceptCountry)) {
							// invert base restriction
							getLogger().info("GEO Except "+exceptCountry+" / is matched!");
							allowPlayback = !allowPlayback;
							break;
						} else {
							getLogger().info("GEO Except "+exceptCountry+" / not matched");
						}
					}
					if (allowPlayback) {
						getLogger().info("GEO NOT Restricting playback.");
					} else {
						getLogger().info("GEO RESTRICTING playback.");
					}
					return allowPlayback;
				}
			}
		}
		if (!allowPlayback && CountryCode.startsWith(DefaultRestrictCountry)) {
			allowPlayback = !allowPlayback;
		}
		if (allowPlayback) {
			getLogger().info("GEO2 NOT Restricting playback.");
		} else {
			getLogger().info("GEO2 RESTRICTING playback.");
		}
		return allowPlayback;
	}

	class StreamListener implements IMediaStreamActionNotify
	{
		public void onPlay(IMediaStream stream, String streamName, double playStart, double playLen, int playReset)
		{
			IClient ClientTMP = stream.getClient();
			String ClientIP = ClientTMP.getIp();

			if (!allowPlayback(streamName, ClientIP)) {
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
	}
}