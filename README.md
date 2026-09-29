# Smart Solar Microgrid Trading System — Android Client

The native Android client for the **Smart Solar Microgrid Trading System**, built for the
**SE4040 Enterprise Application Development** assignment. It lets solar-panel owners (*prosumers*)
book time slots at microgrid stations to trade energy, and lets station staff (*grid operators*)
check those bookings in by QR code and manage their station's slots.

The app is a **thin client**: it displays data and sends user actions to a C# Web API (backed by
MongoDB) that owns every business rule. The mobile code contains no pricing, eligibility or
booking logic; if the server says no, the app shows the server's message.

---

## Table of contents

1. [Features](#1-features)
2. [Screens](#2-screens)
3. [Assignment requirements coverage](#3-assignment-requirements-coverage)
4. [Tech stack](#4-tech-stack)
5. [Architecture](#5-architecture)
6. [Project structure](#6-project-structure)
7. [Backend API contract](#7-backend-api-contract)
8. [Offline caching](#8-offline-caching)
9. [Getting started](#9-getting-started)
10. [Testing](#10-testing)
11. [Coding conventions and workflow](#11-coding-conventions-and-workflow)
12. [Security and privacy notes](#12-security-and-privacy-notes)
13. [Troubleshooting](#13-troubleshooting)

---

## 1. Features

The app serves two roles. A third role, **Backoffice**, is web-only: if a Backoffice user logs in
on mobile, the app tells them to use the web app and signs them out.

### Prosumer (solar homeowner)

- **Register and log in.** Self-registration creates a *pending* account that cannot be used until
  Backoffice approves it. Login is by email and password (JWT).
- **Dashboard.** Counts of pending, upcoming-approved, completed and cancelled reservations, plus
  the next approved reservation.
- **Book a slot.** Pick a station, pick an available slot (next 7 days), confirm, and see a
  summary of what the server created.
- **My Bookings.** A tab per status (Pending / Approved / Completed / Cancelled / All) and a detail
  screen for each reservation.
- **Change a booking.** Move a *Pending* reservation to another slot at the same station; cancel a
  *Pending* or *Approved* one with an optional reason. Every result is shown on the same summary
  screen used for booking.
- **QR code.** For an *Approved* reservation, show a scannable QR code to present at the station.
- **Nearby stations.** A Google Map of stations around the user's location, with a details sheet
  and a shortcut into slot selection.
- **Profile.** Edit name, email, contact number, address and panel capacity (the NIC is read-only),
  change password, and request account deactivation.
- **Works offline for reading.** Dashboard, bookings, stations and profile fall back to the last
  data fetched, clearly labelled as such (see [Offline caching](#8-offline-caching)).

### Grid operator (station manager)

- **Dashboard.** Today's pending / approved / completed counts, the approved-future count, and a
  preview of upcoming approved reservations for their own station.
- **Pending approvals.** A read-only queue. Approving happens in the web app; there is no approve
  action on mobile.
- **Completed history.** Paginated list of completed reservations at their station.
- **QR check-in.** Scan a prosumer's QR code, review the reservation it belongs to, then confirm to
  complete the charging session.
- **Slot management.** Edit the start time, end time or capacity of their station's upcoming
  unbooked slots.

Operator screens always show live data and are never cached.

---

## 2. Screens

The app has 16 Activities (Activity per screen, plus dialogs and bottom sheets).

| Screen | Class (package `ui.*`) | Role | Purpose |
|---|---|---|---|
| Login | `auth.LoginActivity` | all | Launcher. Skips straight to the right home if a valid session exists. |
| Register | `auth.RegisterActivity` | prosumer | Self-registration form. |
| Prosumer home | `prosumer.ProsumerHomeActivity` | prosumer | Dashboard and navigation hub. |
| Station picker | `booking.StationPickerActivity` | prosumer | Booking step 1: choose a station. |
| Slot picker | `booking.SlotPickerActivity` | prosumer | Booking step 2 (also used when moving a booking). |
| Confirm booking | `booking.ConfirmBookingActivity` | prosumer | Booking step 3 (also confirms a move). |
| Booking summary | `booking.BookingSummaryActivity` | prosumer | Reusable result screen for create / update / cancel. |
| My bookings | `booking.MyBookingsActivity` | prosumer | Tabbed list of reservations. |
| Booking detail | `booking.BookingDetailActivity` | prosumer | One reservation, with Update / Cancel / Show QR as allowed. |
| QR display | `booking.QrDisplayActivity` | prosumer | Renders the reservation's QR code. |
| Nearby stations | `maps.NearbyStationsActivity` | prosumer | Map, location permission, station details sheet. |
| Profile | `profile.ProfileActivity` | prosumer | View/edit profile, change password, request deactivation. |
| Operator home | `operator.OperatorHomeActivity` | operator | Dashboard and navigation hub. |
| Pending approvals | `operator.PendingApprovalsActivity` | operator | Read-only approval queue. |
| Completed history | `operator.CompletedHistoryActivity` | operator | Paginated history. |
| QR scanner | `operator.QrScannerActivity` | operator | Camera scan, verify, complete. |
| Slot management | `operator.SlotManagementActivity` | operator | List and edit slots. |

---

## 3. Assignment requirements coverage

| Requirement | How it is met |
|---|---|
| Native Android, no cross-platform framework | Kotlin, Android Views with ViewBinding, Material 3. |
| Client only consumes the API; no business logic on the client | All rules enforced server-side; the app shows server messages verbatim. Client-side checks are limited to input shape. |
| No hardcoded business data | Stations, slots, reservations, counts and profiles all come from the API. |
| Local persistence with SQLite via Room | Room database caches prosumer read data for offline display. |
| Google Maps for a nearby-stations screen | `NearbyStationsActivity` (Maps SDK + fused location). |
| ZXing for QR generation and scanning | Generation in `QrDisplayActivity`, scanning in `QrScannerActivity`. |
| MVVM | Activity → ViewModel → Repository → Retrofit / Room. |
| Git with conventional commits and feature branches | See [workflow](#11-coding-conventions-and-workflow). |

---

## 4. Tech stack

| Area | Choice | Version |
|---|---|---|
| Language | Kotlin | 2.0.21 |
| Build | Android Gradle Plugin / Gradle | 8.11.2 / 8.13 |
| Java / JVM target | JDK 17 | 17 |
| Android levels | minSdk / targetSdk / compileSdk | 26 / 34 / 34 |
| UI | Android Views + ViewBinding, Material Components (Material 3 theme), ConstraintLayout | Material 1.10.0, ConstraintLayout 2.1.4 |
| Architecture | ViewModel + LiveData (`lifecycle`) | 2.8.3 |
| Async | Kotlin Coroutines | 1.8.1 |
| Networking | Retrofit + Gson converter, OkHttp + logging interceptor | 2.11.0, 4.12.0 |
| JSON | Gson | 2.11.0 |
| Local database | Room (with KSP) | 2.6.1, KSP 2.0.21-1.0.28 |
| Maps and location | Google Play Services Maps, Location | 18.2.0, 21.2.0 |
| QR | ZXing Android Embedded (+ ZXing core) | 4.3.0 (core 3.5.2) |
| Unit testing | JUnit 4, MockK, kotlinx-coroutines-test, androidx arch core-testing | 4.13.2, 1.13.12, 1.8.1, 2.2.0 |

---

## 5. Architecture

The app follows **MVVM** with a repository layer. There is no dependency-injection framework:
repositories are created with a `Context`, and ViewModels create their own repository by default
(constructor parameters allow tests to substitute fakes).

---

## 9. Getting started

### Pointing the app at the API

The API address is compiled into `BuildConfig.API_BASE_URL` from the Gradle property
`API_BASE_URL` (see `app/build.gradle.kts`); `Constants.BASE_URL` reads it. You never edit Kotlin
to change it.

| Where the app runs | `API_BASE_URL` | What to do |
|---|---|---|
| Android emulator | `http://10.0.2.2:5151/` (the default) | Nothing. `10.0.2.2` is the emulator's alias for the PC's `localhost`. |
| Physical phone | `http://<PC LAN IP>:5151/`, e.g. `http://192.168.1.5:5151/` | Set the property (below), sync Gradle, reinstall. |

Set the property in one of these places. Each one overrides the ones below it:

1. The command line: `gradlew installDebug -PAPI_BASE_URL=http://192.168.1.5:5151/`
2. **Your user-level** `~/.gradle/gradle.properties` (on Windows,
   `C:\Users\<you>\.gradle\gradle.properties`), as `API_BASE_URL=http://192.168.1.5:5151/`.
   This is recommended because your IP never ends up in git.
3. The project's `gradle.properties`, which holds a commented-out example. Don't commit your IP.

The value is compiled into the APK. After changing it, run **Sync Project with Gradle Files** and
reinstall the app. A missing trailing `/` is added for you. A value that doesn't start with
`http://` or `https://` fails the build with a clear message, rather than crashing the app on its
first request.

**Running on a physical phone.** Check these before the demo:

1. Put the phone and the PC on the same Wi-Fi network.
2. Find the PC's LAN IP with `ipconfig`: it is the *IPv4 Address* of the Wi-Fi adapter.
3. Make the API listen on every interface, not just `localhost`. For example, run
   `dotnet run --urls http://0.0.0.0:5151`.
4. Allow inbound TCP port 5151 in Windows Defender Firewall.
5. Open `http://<PC LAN IP>:5151/api/health` in the phone's browser. If it doesn't answer there,
   the app can't reach it either.

Plain `http://` works because the manifest allows cleartext traffic
(`android:usesCleartextTraffic="true"`). That setting is for development only.

### Google Maps API key

The Nearby Stations map needs a **Maps SDK for Android** key. The key lives only in
`local.properties`, which is gitignored. `app/build.gradle.kts` reads it and injects it into the
manifest through `manifestPlaceholders`. **Never put a real key in `AndroidManifest.xml`,
`local.properties.example`, or anything else that is committed.**

1. Copy `local.properties.example` to `local.properties` in the project root. If Android Studio
   already created `local.properties` (with `sdk.dir`), just add the line from step 5 to it.
2. In the [Google Cloud Console](https://console.cloud.google.com/), create or select a project.
   Google requires a billing account on the project for Maps; check Google's current Maps pricing.
3. Go to **APIs & Services → Library** and enable **Maps SDK for Android**.
4. Go to **APIs & Services → Credentials → Create credentials → API key**. Restrict the key:
   - *Application restrictions*: **Android apps**, with package name `com.example.smartmicrogrid`
     and the SHA-1 of your debug keystore. Get the SHA-1 by running `gradlew signingReport`, or
     with `keytool -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android`.
     Each teammate's debug keystore has a different SHA-1, so add every one that needs the map.
   - *API restrictions*: **Maps SDK for Android** only.
5. Put the key in `local.properties`:

   ```properties
   MAPS_API_KEY=AIza...your-key...
   ```

6. Sync Gradle and reinstall the app. New keys and restriction changes can take a few minutes to
   start working.

**Without a key,** the build prints a `MAPS_API_KEY is not set` warning and uses the placeholder
`MAPS_API_KEY_NOT_SET`. The app runs and the map screen still requests nearby stations, but the
map itself shows no tiles, and logcat reports a Maps *authorization failure*. The emulator also needs
a system image with **Google APIs** or **Google Play**, or neither the map nor location works.
