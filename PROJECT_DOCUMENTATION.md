# USSD Registration Automation Android App

## 📱 Project Overview
This is a **private Android application (APK)** designed to automate a **USSD-based phone number registration process** for a telecom system (Orange-like).

The app processes **thousands of phone numbers per day** by:
- Reading data from a CSV/Excel file
- Executing USSD codes
- Automatically filling system prompts (Name & CNE)
- Logging results locally

⚠️ This app is for **internal use only** and will NOT be published on Google Play.

---

## 🧠 Business Logic

USSD format:
```
#555*1*{phone_number}*1*{puk_last_4}#
```

Example: `#555*1*0622777777*1*1234#`

After dialing:
1. System asks for **Full Name**
2. Then asks for **CNE**
3. Registration is confirmed

---

## 📁 Input File Format

CSV or Excel columns:

| PHONE_NUMBER | DDDD | FULL_NAME | CNE |
|--------------|------|-----------|-----|
| 0622777777 | 1234 | AHMED EL ALAMI | JC123456 |

- **PHONE_NUMBER**: Moroccan phone number (10 digits, starts with 06 or 07)
- **DDDD**: Last 4 digits of PUK code
- **FULL_NAME**: Full name of the person
- **CNE**: National ID number

---

## ⚙️ Technologies Used

- **Android Native**
- **Kotlin**
- **Android Studio**
- **Room Database** - Local data persistence
- **Accessibility Service** - Auto-fill USSD prompts
- **TelephonyManager** - USSD execution
- **Apache POI** - Excel file parsing
- **OpenCSV** - CSV file parsing
- **Coroutines** - Asynchronous operations

---

## 🏗️ Project Structure

```
app/
├── src/main/
│   ├── java/com/orange/ussd/registration/
│   │   ├── data/
│   │   │   ├── model/
│   │   │   │   └── RegistrationRecord.kt
│   │   │   ├── dao/
│   │   │   │   └── RegistrationDao.kt
│   │   │   └── database/
│   │   │       ├── AppDatabase.kt
│   │   │       └── Converters.kt
│   │   ├── service/
│   │   │   ├── USSDProcessingService.kt
│   │   │   └── USSDAccessibilityService.kt
│   │   ├── ui/
│   │   │   ├── MainActivity.kt
│   │   │   └── RegistrationAdapter.kt
│   │   └── utils/
│   │       └── FileParser.kt
│   ├── res/
│   │   ├── layout/
│   │   ├── values/
│   │   └── xml/
│   └── AndroidManifest.xml
└── build.gradle.kts
```

---

## 🚀 How to Build

### Prerequisites
- Android Studio (latest version)
- JDK 17 or higher
- Android SDK (API 26+)

### Build Steps

1. **Open the project in Android Studio**
   ```bash
   cd c:\Users\hi\Desktop\orange
   ```
   Then open Android Studio and select "Open an Existing Project"

2. **Sync Gradle**
   - Wait for Gradle to sync all dependencies
   - If there are sync errors, click "Sync Now"

3. **Build APK**
   - Build → Build Bundle(s) / APK(s) → Build APK(s)
   - Or use command line:
     ```bash
     gradlew assembleRelease
     ```

4. **Install on Device**
   - The APK will be located at: `app/build/outputs/apk/release/app-release.apk`
   - Transfer to device and install

---

## 📲 How to Use

### 1. Initial Setup

1. **Install the APK** on your Android device
2. **Grant Permissions**:
   - Phone Call Permission
   - Phone State Permission
   - File Access Permission
   - Notification Permission

3. **Enable Accessibility Service**:
   - Settings → Accessibility → USSD Registration
   - Toggle ON the service

### 2. Prepare Your Data File

Create a CSV or Excel file with this format:
```csv
PHONE_NUMBER,DDDD,FULL_NAME,CNE
0622777777,1234,AHMED EL ALAMI,JC123456
0623888888,5678,FATIMA ZAHRA,JC789012
```

### 3. Process Registrations

1. **Open the app**
2. **Click "Select CSV/Excel File"** and choose your data file
3. **Review the loaded records** in the list
4. **Click "Start Processing"** to begin automation
5. **Monitor progress** in real-time
   - Status updates appear at the top
   - Statistics show: Total, Pending, In Progress, Completed, Failed

### 4. How It Works

For each record:
1. App dials USSD code: `#555*1*{phone}*1*{puk}#`
2. System prompts for "Full Name"
3. Accessibility service auto-fills the name
4. System prompts for "CNE"
5. Accessibility service auto-fills the CNE
6. Registration completes
7. Status updates to "COMPLETED"
8. Next record processes automatically

---

## ⚠️ Important Notes

### Permissions
- **CALL_PHONE**: Required to execute USSD codes
- **READ_PHONE_STATE**: Required for telephony operations
- **Accessibility Service**: Required to auto-fill USSD prompts
- **File Access**: Required to read CSV/Excel files

### Limitations
- Device must have active SIM card
- USSD code format must match: `#555*1*{phone}*1*{puk}#`
- Phone numbers must be Moroccan format (10 digits, 06/07 prefix)
- One registration at a time (sequential processing)

### Error Handling
- Invalid file format → Error dialog shown
- Missing permissions → Permission request dialog
- USSD execution failure → Record marked as FAILED
- Timeout (30 seconds) → Record marked as FAILED

---

## 🔧 Troubleshooting

### Accessibility Service Not Working
1. Go to Settings → Accessibility
2. Find "USSD Registration"
3. Toggle OFF, then ON again
4. Grant all requested permissions

### USSD Not Executing
1. Verify phone call permission is granted
2. Check SIM card is active
3. Ensure device can make calls

### File Not Loading
1. Use CSV or Excel (.xlsx, .xls) format only
2. Ensure columns are in correct order
3. Check file is not corrupted

### Registration Stuck
1. Check accessibility service is enabled
2. Verify USSD dialogs are appearing
3. Stop and restart processing

---

## 📊 Database Schema

### RegistrationRecord Table
| Column | Type | Description |
|--------|------|-------------|
| id | Long | Primary key (auto-increment) |
| phoneNumber | String | Phone number to register |
| pukLastFour | String | Last 4 digits of PUK |
| fullName | String | Full name |
| cne | String | CNE/National ID |
| status | Enum | PENDING, IN_PROGRESS, COMPLETED, FAILED |
| errorMessage | String? | Error details if failed |
| timestamp | Long | Creation timestamp |
| ussdExecuted | Boolean | USSD code executed |
| nameFilled | Boolean | Name field filled |
| cneFilled | Boolean | CNE field filled |
| completed | Boolean | Registration completed |

---

## 🎨 UI Features

- **File Selection**: Choose CSV/Excel files
- **Progress Tracking**: Real-time status updates
- **Statistics**: Total, Pending, Completed, Failed counts
- **Record List**: Scrollable list of all registrations
- **Color-Coded Status**: 
  - Green = Completed
  - Red = Failed
  - Blue = In Progress
  - Gray = Pending

---

## 🔒 Security & Privacy

- All data stored locally on device
- No internet connection required
- No data sent to external servers
- Database can be cleared at any time

---

## 🛠️ Development

### Adding New Features
1. Model changes: Update `RegistrationRecord.kt`
2. Database changes: Update `AppDatabase.kt` version
3. UI changes: Modify layout files in `res/layout/`
4. Business logic: Update service files

### Testing
- Test on real Android device (emulator may not support USSD)
- Test with small batch first (5-10 records)
- Verify accessibility service permissions
- Check all file formats (CSV, XLSX, XLS)

---

## 📝 License

This is a private, internal-use application. Not for public distribution.

---

## 👤 Contact

For issues or questions about this application, contact the development team.
