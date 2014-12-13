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

Used libraries
--------------
-	[UnifiedNlpApi](https://github.com/microg/android_packages_apps_UnifiedNlp)
-	[libwlocate](http://sourceforge.net/projects/libwlocate/) (included)


Changes
-------

0.1.0 - Initial version by n76

License
-------

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
