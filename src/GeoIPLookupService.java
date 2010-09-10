/*

Tit Petrič, Monotek d.o.o., (cc) 2010, tit.petric@monotek.net
http://creativecommons.org/licenses/by-sa/3.0/

*/

package com.monotek.wms.module;

import com.maxmind.geoip.*;

/** Takes care of GeoIP database lookups for our purposes */
class GeoIPLookupService
{
	/** Static GeoIP lookup object and service status */
	private LookupService myLookupService;
	private boolean myServiceStatus;

	/** Constructor */
	public GeoIPLookupService(String GeoIPDatabase)
	{
		myServiceStatus = true;
		try {
			myLookupService = new LookupService(GeoIPDatabase, LookupService.GEOIP_MEMORY_CACHE);
		} catch (Exception e) {
			myServiceStatus = false;
		}
	}

	/** Returns status of GeoIP database */
	public boolean GetStatus()
	{
		return myServiceStatus;
	}

	/** Validate Country code against IPAdress's country code */
	public boolean ValidateCountry(String IPAddress, String CountryCode)
	{
		String IPCountryCode = "--";
		try {
			IPCountryCode = myLookupService.getCountry(IPAddress).getCode();
		} catch (Exception e) {
			return false;
		}
		return IPCountryCode.equals(CountryCode);
	}
}
