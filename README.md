# Islamic Widget

A highly customizable, feature-rich Android widget application designed to fulfill the daily needs of Muslims. It provides accurate prayer times, Hijri calendar dates, auto-silent modes, custom Adhan audio, and an inspirational Islamic quotes widget directly on your home screen.

## 🌟 Key Features

### 🕋 Prayer Times & Adhan
* **Accurate Calculation:** Supports multiple calculation methods (Muslim World League, Egyptian, Umm Al-Qura, Karachi, Moonsighting Committee, Dubai, Qatar, Kuwait, Singapore).
* **Automatic Location:** GPS-based location detection to calculate accurate prayer times (Fajr, Dhuhr, Asr, Maghrib, Isha) along with Sunrise and the Last Third of the Night.
* **Adhan Player:** Automatically plays Adhan when prayer time enters.
* **Custom Adhan Audio:** Support for setting custom MP3 files for Regular Adhan and a specific audio file for Fajr Adhan. Includes a built-in volume slider and test button.

### 🔕 Auto-Silent (Do Not Disturb) Mode
* **Smart Mute:** Automatically puts the phone in Silent/DND mode during prayer times.
* **Customizable Durations:** Independently configure how many minutes *before* and *after* each prayer (and Jum'ah/Friday prayers) the phone should remain silent.

### 📅 Advanced Hijri Calendar & Reminders
* **Custom Formatting:** Fully customizable Gregorian and Hijri date formats (e.g., `en-US{EEEE, dd MMMM yyyy}`).
* **Hijri Offset:** Manually adjust the Hijri date by +/- 2 days.
* **Maghrib Date Change:** Option to automatically advance the Hijri date after Maghrib.
* **Sunnah Reminders:** Automatic on-widget reminders for Sunnah practices such as Ayyamul Bidh, Monday-Thursday fasting, Ashura, Arafah, and reading Surah Al-Kahf on Fridays.

### 🎨 Highly Customizable UI
* **Dynamic Widget Scale & Sizes:** Adjust the font size of the clock, date, prayer times, and additional info independently.
* **Color Customization:** Color picker for Widget Text and Background (supports Alpha/Transparency).
* **Border Radius:** Adjustable background corner radius for a perfect look.
* **Theme & Language:** Supports System, Light, and Dark themes. Available in English, Indonesian, and Arabic.
* **Dynamic Launcher Icon:** The app's launcher icon dynamically updates every day to display the current Hijri date.

### 📖 Islamic Quotes Widget
* **Inspirational Quotes:** A secondary widget displaying beautiful Islamic quotes and references.
* **Glassmorphism UI:** Elegant transparent background with customizable Alpha/Transparency levels.
* **Auto & Manual Update:** Set an auto-update interval (in minutes) or refresh the quote manually using the refresh button on the widget.
* **Share Functionality:** Instantly share quotes with friends and family directly from the widget.

## 🛠️ Technical Details
* **Language:** Kotlin
* **Architecture:** Android AppWidgets (RemoteViews), Foreground Services (for Adhan Playback), AlarmManager (for precise background scheduling bypassing Doze mode).
* **Libraries Used:** * `adhan-java` (by batoulapps) for precise astronomical prayer time calculations.
    * Google Play Services Location.
* **Permissions:** Requires Location, Notifications, Alarms & Reminders, Do Not Disturb Access, and Wakelock.

## 🚀 Installation & Build
1. Clone the repository: `git clone https://github.com/cyberzilla/IslamicWidget.git`
2. Open the project in **Android Studio**.
3. Sync Gradle and build the project.
4. Run on an emulator or physical device.

## 👨‍💻 Developer
Developed by [cyberzilla](https://github.com/cyberzilla)

---
*Feel free to contribute, report issues, or suggest new features via GitHub pull requests and issues!*