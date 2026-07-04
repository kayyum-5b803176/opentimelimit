# Open TimeLimit Copyright <C> 2019 - 2020 Jonas Lochmann
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation version 3 of the License.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program. If not, see <https://www.gnu.org/licenses/>.


# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-dontwarn okio.**

# ServiceLoader support
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Most of volatile fields are updated with AFU and should not be mangled
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# readable stack traces
-keepattributes SourceFile,LineNumberTable

# Curve25519 support
-keepnames class org.whispersystems.curve25519.NativeCurve25519Provider {}
-keepnames class org.whispersystems.curve25519.JavaCurve25519Provider {}
-keepnames class org.whispersystems.curve25519.J2meCurve25519Provider {}
-keepnames class org.whispersystems.curve25519.OpportunisticCurve25519Provider {}
-keep class org.whispersystems.curve25519.NativeCurve25519Provider {}
-keep class org.whispersystems.curve25519.JavaCurve25519Provider {}
-keep class org.whispersystems.curve25519.J2meCurve25519Provider {}
-keep class org.whispersystems.curve25519.OpportunisticCurve25519Provider {}