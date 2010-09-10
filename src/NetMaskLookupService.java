/*

Tit Petrič, Monotek d.o.o., (cc) 2010, tit.petric@monotek.net
http://creativecommons.org/licenses/by-sa/3.0/

*/

package com.monotek.wms.module;

import java.net.InetAddress;
import java.net.UnknownHostException;

import java.util.StringTokenizer;
import java.util.Vector;

/** This class checks that an IP exists in a specified subnet specified via netmask.

Supported netmask formats are:

  1.1.1.1/255.255.255.255
  1.1.1.1/32 (CIDR-style)

@todo: IPv6 support

*/
class NetMaskLookupService
{
	/** Validate that IPAddress exists in NetMask address space */
	public boolean ValidateIP(String IPAddress, String NetMask) throws UnknownHostException, Exception
	{
		int ip, network, netmask;

		// convert IP to int
		if (!validateInetAddress(IPAddress)) {
			return false;
		}
		ip = toInt(InetAddress.getByName(IPAddress));

		// split network/netmask
		Vector<Object> nm = new Vector<Object>();
		StringTokenizer nmt = new StringTokenizer(NetMask,"/");
		while (nmt.hasMoreTokens()) {
			nm.add(nmt.nextToken());
		}

		// network to int
		if (!validateInetAddress(nm.get(0).toString())) {
			return false;
		}
		network = toInt(InetAddress.getByName(nm.get(0).toString()));

		// generate netmask int from cidr/network notations
		if (nm.get(1).toString().length() < 3) {
			int cidr = Integer.parseInt( nm.get(1).toString() );
			if (!validateCIDR(cidr, NetMask)) {
				return false;
			}
			netmask = 0x80000000 >> (cidr - 1); // 1st bit is sticky
		} else {
			if (!validateInetAddress(nm.get(1).toString())) {
				return false;
			}
			// if we get 255.127.1.0 it's considered like 255.255.0.0 ... add netmask validation?
			netmask = 0x80000000 >> (Integer.bitCount( toInt(InetAddress.getByName(nm.get(1).toString())) ) - 1);
		}
		return ((ip & netmask) == network);
	}

	/** Check cidr value in bounds */
	private boolean validateCIDR(int cidr, String NetMask) throws Exception
	{
		if (cidr<0 || cidr>32) {
			throw new Exception("CIDR value out of bounds [0..32]: "+NetMask);
		}
		return true;
	}

	/** Validate the IP address doesn't have any out of bound values */
	private boolean validateInetAddress(String IPAddress) throws Exception
	{
		int i = 0;
		StringTokenizer tokens = new StringTokenizer(IPAddress, ".");
		while (tokens.hasMoreTokens()) {
			String token = tokens.nextToken().toString();
			if (!Integer.toString((Integer.parseInt(token)&0xff)).equals(token)) {
				throw new Exception("Can't validate IP Address: "+IPAddress);
			}
			i++;
		}
		if (i>4) {
			throw new Exception("IP Address has more than 4 parts: "+IPAddress);
		}
		return true;
	}

	/** Convert InetAddress to int */
	private int toInt(InetAddress inetAddress)
	{
		byte[] address = inetAddress.getAddress();
		int net = 0;
		for (int i=0; i<address.length; i++) {
			net = (net<<8) | (address[i] & 0xff);
		}
		return net;
	}
}
