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

[#555*1*nemuro_sim*1*puk#]
nemuro =06..........
puk= the last 4 numbers from the code puk


After dialing:
1. System asks for **Full Name**
2. Then asks for **CNE**
3. Registration is confirmed

---

## 📁 Input File Format

CSV or Excel columns:

PHONE_NUMBER | DDDD | FULL_NAME | CNE

Example:
0622777777,1234,AHMED EL ALAMI,JC123456

## ⚙️ Technologies Used

- Android Native
- Kotlin
- Android Studio
- Room Database
- Accessibility Service
- TelephonyManager (USSD)

---
