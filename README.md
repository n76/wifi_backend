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

Using Android Studio, select Build->"Generate Signed APK..."

Setup on phone
==============
In the NLP Controller app (interface for µg UnifiedNlp) select the "Personal WiFi Backend".

Settings
--------
-	Required Accuracy: Sets the maximum error that a GPS location report can have for the sampler to trigger the collection of WiFi Access Point (AP) data. For example, if set to 10m then all GPS locations with accuracy greater than 10m will be ignored.
-	Sample Distance: Sets the minimum distance change in a GPS location for the Android OS to give the sampler a new location. For example if set to 20m, then only GPS positions more than 20m apart will be make.
-	Sample Interval: Sets the minimum time between GPS location reports from the Android OS. Smaller values may improve AP range detection but will cause higher processing loads.
-	Mimimum AP Range: Sets the minimum range (accuracy) value back end will report for an AP. This value should be set to the usual coverage radius of a WiFi AP. For current models this is about 100m.
-	Moved Threshold: If a new GPS location sample for an AP is too far from our old estimate we assume the AP has been moved. This value sets the distance that will trigger the moved AP logic.
-	Move Guard: Once an AP has been detected as moved we block its location from being used until we are sure it is stable. Stable is defined as having received a number of GPS location updates for the AP that are plausible. This value sets the number of samples required to clear the "moved" indication.

Collecting WiFi AP Data
-----------------------
To conserve power the collection process does not actually turn on the GPS. If some other app turns on the app, for example a map or navigation app, then the backend will monitor the location and collect WiFi data.

Libraries Used
--------------
-	[UnifiedNlpApi](https://github.com/microg/android_packages_apps_UnifiedNlp)

Other IP used
=============
Icon created with the [Android Asset Studio](https://romannurik.github.io/AndroidAssetStudio/icons-launcher.html#foreground.type=clipart&foreground.space.trim=1&foreground.space.pad=0.15&foreground.clipart=res%2Fclipart%2Ficons%2Fdevice_signal_wifi_3_bar.svg&foreColor=fff%2C0&crop=0&backgroundShape=circle&backColor=4caf50%2C100&effects=none) (Creative Commons Attribution 3.0 Unported License).

Notification icon created with the [Android Asset Studio](https://romannurik.github.io/AndroidAssetStudio/icons-notification.html#source.type=clipart&source.space.trim=1&source.space.pad=0&source.clipart=res%2Fclipart%2Ficons%2Fcommunication_location_off.svg&name=ic_stat_no_location) (Creative Commons Attribution 3.0 Unported License).

Changes
=======

|Version|Date|Comment|
|:-------|:----:|:-------|
0.1.0| |Initial version by n76
0.6.0| |Configurable settings for data collection and use. Some improvements in performance
0.6.1| |Fixup Android Studio/Gradle build environment
0.17.0|21Aug2015|Increase location uncertainty if no position found.
0.9.9|16Jan2016|Thanks to @UnknownUntilNow, new UI, refactored code, import and export of WiFi AP location information, support for Marshmallow
1.0.0|6Jan2016|Thanks to @pejakm, update Serbian translation

License
=======

    Copyright (C) 2014, 2015 Tod Fitch

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
