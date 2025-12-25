# Offline Browser App

 This Android application allows users to browse HTML pages, RSS feeds, and Mastodon feeds offline. It also features an offline weather viewer and integration with "The Bindery" for downloading ZIM archives.

 ## Features

 *   **Offline Browsing**: Download and view HTML pages, RSS feeds, and Mastodon feeds.
 *   **Offline Weather**: View weather forecasts for multiple locations, cached for up to 10 days.
 *   **The Bindery Integration**: Browse and download ZIM files from a local or remote "The Bindery" instance.
 *   **Settings**: Configure WiFi-only downloads, allowed SSIDs, and sync intervals.

 ## Usage Instructions

 ### 1. Adding Feeds

 *   **RSS Feed**: Enter the RSS feed URL in the main input field and click "Add RSS Feed".
 *   **Mastodon Feed**: Enter the Mastodon RSS URL (e.g., `https://mastodon.social/@user.rss`) and click "Add Mastodon Feed".
 *   **HTML Page**: Enter a website URL and click "Add HTML Page". This will download the page for offline viewing.

 ### 2. Weather Module

 *   Click the "Weather" button on the main screen.
 *   **Add Location**:
     *   **Manual**: Enter the Location Name, Latitude, and Longitude, then click "Add Location".
     *   **Automatic**: Click "Use Current Location" to automatically detect and add your current location (requires location permissions).
 *   The weather data is cached for 10 days, allowing you to view the forecast even when offline.

 ### 3. The Bindery (ZIM Files)

 *   Click the "The Bindery (ZIM)" button on the main screen.
 *   **Secure Connection**:
     *   If your Bindery instance uses a self-signed certificate, click "Scan QR Code".
     *   Scan the QR code provided by the server (containing IP, Port, and Certificate Fingerprint).
     *   The app will verify the server's identity and add it to the trusted list.
 *   Enter the URL (e.g., `https://192.168.1.5:8080/`) or it will be auto-filled after scanning.
 *   Click "Connect" to load the list of available modules.
 *   Browse the list and click "Download ZIM" on any module to download the corresponding `.zim` file to your device's app-specific storage (`Android/data/com.example.offlinebrowser/files/`).

 ### 4. Settings

 *   Click the "Settings" button on the main screen.
 *   **WiFi Only**: Toggle to restrict downloads to WiFi connections only.
 *   **Allowed SSIDs**: Enter a comma-separated list of WiFi SSIDs to restrict downloads to specific networks.
 *   **Sync Interval**: Set how often the app should check for feed updates in the background.

 ## Permissions

 *   **Internet**: Required to download feeds and weather data.
 *   **Location**: Required to detect your current location for the weather module.
 *   **Notifications**: Required to show download progress and background sync status.

 ## Development

 *   Built with Android SDK 34 (min SDK 24).
 *   Uses Room for local database storage.
 *   Uses Retrofit for network requests.
 *   Uses WorkManager for background tasks.
