NOTICE
======
The author of this backend is now primarily focused on the [Déjà Vu backend](/n76/DejaVu). Bug fixes and pull requests will be accepted for this backend but it will not be as well supported as Déjà Vu going forward.

Local WiFi Backend
==================
[UnifiedNlp](https://github.com/microg/android_packages_apps_UnifiedNlp) backend that uses locally acquired WiFi AP data to resolve user location.

This backend consists of two parts sharing a common database. One part passively monitors the GPS. If the GPS has acquired and has a good position accuracy, then the WiFi APs detected by the phone are stored.

The other part is the actual location provider which uses the database to estimate location when the GPS is not available. The use of stored WiFi AP can decrease the GPS time to first fix and allows apps to get an immediate approximate location.

This backend uses no network data. All data acquired by the phone stays on the phone and no queries are made to a centralized WiFi AP location provider.

[![Get it on F-Droid](get_it_on_f-droid.png?raw=true)](https://f-droid.org/repository/browse/?fdid=org.fitchfamily.android.wifi_backend)

Requirement for building
========================

1. Building requires Android SDK with API 19 or higher.

Requirements on phone
=====================
1. This is a plug-in for [µg UnifiedNlp](http://forum.xda-developers.com/android/apps-games/app-g-unifiednlp-floss-wi-fi-cell-tower-t2991544) which can be [installed from f-droid](https://f-droid.org/repository/browse/?fdfilter=unified&fdpage=1&page_id=0). The [µg GmsCore](http://forum.xda-developers.com/android/apps-games/app-microg-gmscore-floss-play-services-t3217616) can also use this backend.

How to build and install
========================

Using Android Studio, select Build->"Generate Signed APK..."

Setup on phone
==============
In the NLP Controller app (interface for µg UnifiedNlp) select the "WiFi Location Service". If using GmsCore, then the little gear at microG Settings->UnifiedNlp Settings->Configure location backends->WiFi Location Service is used.

Advanced Settings
--------
-	Required Accuracy: Sets the maximum error that a GPS location report can have for the sampler to trigger the collection of WiFi Access Point (AP) data. For example, if set to 10m then all GPS locations with accuracy worse (greater) than 10m will be ignored.
-	Sample Distance: Sets the minimum distance change in a GPS location for the Android OS to give the sampler a new location. For example if set to 20m, then only GPS positions more than 20m apart will be used.
-	Sample Interval: Sets the minimum time between GPS location reports from the Android OS. Smaller values may improve AP range detection but will cause higher processing loads.
-	GPS Valid Time: How long a position report from the GPS is considered good. WiFi APs detected during this time will use the most recent valid GPS location when updating the database.
-	Minimum AP Range: Sets the minimum range (accuracy) value back end will report for an AP. This value should be set to the usual coverage radius of a WiFi AP. For current model APs this is about 100m.
-	Moved Threshold: If a new GPS location sample for an AP is too far from our old estimate we assume the AP has been moved. This value sets the distance that will trigger the moved AP logic.
-	Move Guard: Once an AP has been detected as moved we block its location from being used until we are sure it is stable. Stable is defined as having received a number of GPS location updates for the AP that are plausible. This value sets the number of samples required to clear the "moved" indication.

Collecting WiFi AP Data
-----------------------
To conserve power the collection process does not actually turn on the GPS. If some other app turns on the app, for example a map or navigation app, then the backend will monitor the location and collect WiFi data.

What is stored in the database
------------------------------
For each WiFi AP the [bssid](https://en.wikipedia.org/wiki/Service_set_(802.11_network)#Basic_service_set_identification_.28BSSID.29) and, if set, the [ssid](https://en.wikipedia.org/wiki/Service_set_(802.11_network)#Service_set_identification_.28SSID.29) are stored along with up to three sets of latitude/longitude samples. There is also a "moved" indicator set if it appears the AP may have moved.

The ssid is stored for display only and is irrelevant to actual function of this software.

The algorithm attempts to determine the outer edge of the coverage area a WiFi AP by saving the three samples that give the largest reasonable circle within which the AP is detected. The [logic behind this was to reduce identifiable "bread crumb" trails](http://retiredtechie.fitchfamily.org/2014/12/13/bread-crumbs/) for data collected by stumblers and may not be ideal.

Export and Import of WiFi (WLAN) Access Point (AP) data
-------------------------------------------------------
-	On export each sample for each AP is written as a separate record with up to three records per AP. Excluded from this are points for any AP which has been detected as moved or moving.
-	On import each record is treated the same as a data point from the internal background sampling server. That is the data is merged into the database with each position compared against the ones already in the database to see if using it would provide a better AP position estimate.
-	It is possible to share export files: Position sample data from a file someone else exported will be merged on import and you will end up with a database containing the "best" set of location sample points from all imports as well as those collected locally.
-	The location lookup process requires a minimum of three samples for a AP before it will use that AP. So imports that have a single best position will be entered in the database but will not be used for position estimation until at least two more position samples are available. If you are importing from another project you may wish to pre-process their data to create three points around the best guess location and then import the three estimated points rather than the center location.
-	This backend does not support the import or export of data to anyplace other than local storage on the phone. If you wish to back up the data or share it, you will need to do that through other means.

Clearing the database
---------------------
To clear or reset database, touch the "Reset Database" in the backend setting.

Libraries Used
--------------
-	A full list of libraries used is listed in the "External Libraries" area of this app's settings.

Other IP used
=============
Icon created with the [Android Asset Studio](https://romannurik.github.io/AndroidAssetStudio/icons-launcher.html#foreground.type=clipart&foreground.space.trim=1&foreground.space.pad=0.15&foreground.clipart=res%2Fclipart%2Ficons%2Fdevice_signal_wifi_3_bar.svg&foreColor=fff%2C0&crop=0&backgroundShape=circle&backColor=4caf50%2C100&effects=none) (Creative Commons Attribution 3.0 Unported License).

Notification icon created with the [Android Asset Studio](https://romannurik.github.io/AndroidAssetStudio/icons-notification.html#source.type=clipart&source.space.trim=1&source.space.pad=0&source.clipart=res%2Fclipart%2Ficons%2Fcommunication_location_off.svg&name=ic_stat_no_location) (Creative Commons Attribution 3.0 Unported License).

Changes
=======
[History is now a separate file](CHANGELOG.md)

License
=======

Copyright (C) 2014, 2015, 2016 Tod Fitch

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
