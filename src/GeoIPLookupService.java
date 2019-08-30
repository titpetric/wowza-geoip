/*

Tit Petric, Monotek d.o.o., (cc) 2010, tit.petric@monotek.net
http://creativecommons.org/licenses/by-sa/3.0/

Updated by: William Hetherington, NetroMedia, will@netromedia.com

*/

package com.monotek.wms.module; 

import com.maxmind.geoip2.*;
import com.maxmind.db.*;
import java.io.File;
import java.net.InetAddress;
import com.maxmind.geoip2.model.*;
import com.maxmind.geoip2.record.*;
import com.maxmind.db.*;

/** Takes care of GeoIP database lookups for our purposes */
class GeoIPLookupService
{
	/** Static GeoIP lookup object and service status */
	private boolean myServiceStatus;
	private DatabaseReader reader;

	/** Constructor */
	public GeoIPLookupService(String GeoIPDatabase)
	{
		myServiceStatus = true;
		File database;
		try {
			database = new File(GeoIPDatabase);

			// Using CHMCache, lookup performance is significantly
			// improved at the cost of a small (~2MB) memory overhead.
			reader = new DatabaseReader.Builder(database).withCache(new CHMCache()).build();
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
        String IPCountryName = "";
        try {
            InetAddress ipAddress = InetAddress.getByName(IPAddress);
            CityResponse response = reader.city(ipAddress);

            Country country = response.getCountry();
            IPCountryName = country.getIsoCode();
		} catch (Exception e) {
			return false;
		}
		return IPCountryName.equals(CountryName);
	}

	public String getLocation(String IPAddress) {
        String IPCountryName = "";
        String IPCityName = "";
        try {
            InetAddress ipAddress = InetAddress.getByName(IPAddress);
            CityResponse response = reader.city(ipAddress);

            Country country = response.getCountry();
            City city = response.getCity();
            IPCountryName = country.getIsoCode();
            IPCityName = city.getName();
        } catch (Exception e) {
            return "Could not locate";
        }
        return "Country: " + IPCountryName + " City: " + IPCityName;

    }

	/** Validate a city */
	public boolean ValidateCity(String IPAddress, String CountryName, String CityName)
	{
		String IPCountryName = "";
		String IPCityName = "";
		try {

			InetAddress ipAddress = InetAddress.getByName(IPAddress);
			CityResponse response = reader.city(ipAddress);

			Country country = response.getCountry();
			City city = response.getCity();
			IPCountryName = country.getIsoCode();
			IPCityName = city.getName();
		} catch (Exception e) {
			return false;
		}
		return IPCountryName.equals(CountryName) && IPCityName.equals(CityName);
	}
}
