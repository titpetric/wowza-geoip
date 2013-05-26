Getting started
===============

Requirements
------------

To compile the GeoIP plugin for Wowza, you need to provide the following
software:

  - Java run time environment (JRE)
  - Java development kit (JDK)
  - Ant (Java "make")
  - GeoIP library by Maxmind
  - Source for the Maxmind GeoIP Java API

To install most of the required packages do this for Ubuntu/Debian:

  # apt-get install ant openjdk-6-jdk libgeoip1 libgeoip-dev

This should install the required packages and dependencies for you.


Building the project
--------------------

Just run "ant" in the base folder of the project, where build.xml is located.

The file fetches external GeoIP dependencies (GeoIP Java API 1.2.8).

This will build a "geoip.jar" file in the lib/ folder.


Installation
------------

You must copy this file into your Wowza installation lib folder, usually
located under /usr/lib/WowzaMediaServer/lib. You can then use the geoip
plugin in accordance with the documentation.

Thanks
------

Thanks go to the following people:

Gorazd Zagar
William Hetherington
Richard Lanham
