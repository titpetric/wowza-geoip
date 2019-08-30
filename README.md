# Wowza-GeoIP2

Forked from https://github.com/titpetric/wowza-geoip

This Wowza plugin is suitable for GeoIP2.
Tested with Java 7 and Wowza Streaming Engine 3.6.3

Warning: This plugin is NOT tested with Wowza Streaming Engine 4

# Description

This plugin will check the user's IP and block if the user is located in the restricted location.

You can block the streaming based on their:
- IP address
- Country
- City

Please refer to `conf/example-locationinfo.xml` for example.

# How to Compile

Make sure you compile this package in your Wowza Streaming Engine server.
 
1. Run the script to download dependencies lib
    ```
    cd scripts
    ./get_maxmind_geoip_api
    ``` 
2. Compile using ANT
    ```$bash
    cd git-dir
    ant jar
    ```
3. Voila!

# How to use

1. Copy Maxmind GeoIP2 into `/usr/local/WowzaMediaServer/`
2. Restart you wowza 
3. Write your `locationinfo.xml` file for your application
4. Voila!
