/*

Tit Petric, Monotek d.o.o., (cc) 2010, tit.petric@monotek.net
http://creativecommons.org/licenses/by-sa/3.0/

Updated by: William Hetherington, NetroMedia, will@netromedia.com

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
	public boolean ValidateCountry(String IPAddress, String CountryName)
	{
		String IPCountryName = "--";
		try {
			//IPCountryCode = myLookupService.getCountry(IPAddress).getCode();
			Location myLocation = myLookupService.getLocation(IPAddress);
			IPCountryName = myLocation.countryCode;
		} catch (Exception e) {
			return false;
		}
		return IPCountryName.equals(CountryName);
	}
	
	/** Validate Country & Region code */
	public boolean ValidateRegion(String IPAddress, String CountryName, String RegionName)
	{
		String IPCountryName = "";
		String IPRegionName = "";
		try {
			Location myLocation = myLookupService.getLocation(IPAddress);
			IPCountryName = myLocation.countryCode;
			IPRegionName = myLocation.region;
		} catch (Exception e) {
			return false;
		}
		return IPCountryName.equals(CountryName) && IPRegionName.equals(RegionName);
	}
	
	/** Validate a city */
	public boolean ValidateCity(String IPAddress, String CountryName, String RegionName, String CityName)
	{
		String IPCountryName = "";
		String IPRegionName = "";
		String IPCityName = "";
		try {
			Location myLocation = myLookupService.getLocation(IPAddress);
			IPCountryName = myLocation.countryCode;
			IPRegionName = myLocation.region;
			IPCityName = myLocation.city;
		} catch (Exception e) {
			return false;
		}
		return IPCountryName.equals(CountryName) && IPRegionName.equals(RegionName) && IPCityName.equals(CityName);
	}
}
