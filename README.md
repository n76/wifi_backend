Local WiFi Backend
==================
[UnifiedNlp](https://github.com/microg/android_packages_apps_UnifiedNlp) backend that uses locally acquired WiFi AP data to resolve user location.

This backend consists of two parts sharing a common database. One part passively monitors the GPS. If the GPS has acquired and has a good position accuracy, then the WiFi APs detected by the phone are stored.

The other part is the actual location provider which uses the database to estimate location when the GPS is not available or has not yet gotten its first fix. The use of stored WiFi AP can dramatically decrease the GPS time to first fix.

This backend performs no network data. All data acquired by the phone stays on the phone and no queries are made to a centralized AP location provider.

Requirement for building
========================

1. Building requires Android SDK with API 19 or higher.


Requirements on phone
=====================
1. This is a plug in for µg UnifiedNlp which can be installed from f-droid.

How to build and install
========================

1. ant debug
2. adb install bin/wifi_backend-debug.apk

Setup on phone
==============
In the NLP Controller app (interface for µg UnifiedNlp) select the "Personal WiFi Backend".

Settings
--------
-	Required Accuracy: Sets the maximum error that a GPS location report can have for the sampler to trigger the collection of WiFi Access Point (AP) data.
-	Sample Distance: Sets the minimum distance change in a GPS location for the Android OS to give the sampler a new location.
-	Sample Interval: Sets the minimum time between GPS location reports from the Android OS. Smaller values may improve AP range detection but will cause higher processing loads.
-	Mimimun AP Range: Sets the minimum range (accuracy) value back end will report for an AP.
-	Moved Threshold: If a new GPS location sample for an AP is too far from our old estimate we assume the AP has been moved. This value sets the distance that will trigger the moved AP logic.
-	Move Guard: Once an AP has been detected as moved we block its location from being used until we are sure it is stable. Stable is defined as having received a number of GPS location updates for the AP that are plausible. This value sets the number of samples required to clear the "moved" indication.

Libraries Used
--------------
-	[UnifiedNlpApi](https://github.com/microg/android_packages_apps_UnifiedNlp)

Other IP used
=============
Public domain icon from https://en.wikipedia.org/wiki/File:Wireless-icon.png

Changes
=======

-	0.1.0 - Initial version by n76
-	0.6.0 - Configurable settings for data collection and use. Some improvements in performance

License
=======

    Copyright (C) 2014 Tod Fitch

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
