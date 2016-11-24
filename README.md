# wowza-geoip

Warning: this plugin is out of date. There's a new commercial version available for Wowza 4.5.0+. Please consult my [Wowza products list](https://scene-si.org/products/wowza/)

# Description

This is a server side module for Wowza Media Server 2.1.0+
allowing you to limig live & vod streams to one or many
geographic locations, based on Maxmind's GeoIP API.

The locations you wish to limit are specified in a
separate config xml file, which is reloaded on each
change, so it doesn't require a Wowza server restart.
