# CSV/Excel Template Guide for USSD Registration App

## 📋 Overview
This app accepts CSV and Excel files with registration data. You can use either **4-column** or **5-column** format.

---

## 📁 Template Files Included

1. **TEMPLATE_4_COLUMNS.csv** - Standard format with full name in one column
2. **TEMPLATE_5_COLUMNS.csv** - Extended format with separate first/last name
3. **TEMPLATE_SEMICOLON.csv** - For Excel compatibility (uses semicolon delimiter)

---

## 📊 Supported Formats

### Format 1: 4 Columns (Recommended)
```
PHONE_NUMBER, PUK_LAST_4, FULL_NAME, CNE
0612345678, 1234, Mohamed Ahmed, AB123456
0698765432, 5678, Fatima Zahra, CD789012
```

**Column Details:**
- **Column 1**: Phone number (10 digits, starts with 06 or 07)
- **Column 2**: Last 4 digits of PUK code
- **Column 3**: Full name (first and last name together)
- **Column 4**: CNE/CIN number

---

### Format 2: 5 Columns
```
PHONE_NUMBER, PUK_LAST_4, CNE, FIRST_NAME, LAST_NAME
0612345678, 1234, AB123456, Mohamed, Ahmed
0698765432, 5678, CD789012, Fatima, Zahra
```

**Column Details:**
- **Column 1**: Phone number (10 digits, starts with 06 or 07)
- **Column 2**: Last 4 digits of PUK code
- **Column 3**: CNE/CIN number
- **Column 4**: First name
- **Column 5**: Last name

---

## 📝 Field Requirements

### 1. Phone Number
- ✅ Must start with `06` or `07`
- ✅ Must be exactly 10 digits
- ✅ Examples: `0612345678`, `0798765432`
- ❌ Invalid: `612345678` (missing 0), `0512345678` (wrong prefix)

### 2. PUK Last 4 Digits
- ✅ Exactly 4 digits
- ✅ Numbers only (0-9)
- ✅ Examples: `1234`, `5678`, `0000`
- ❌ Invalid: `123` (too short), `12345` (too long), `abcd` (not numbers)

### 3. Full Name / First & Last Name
- ✅ Cannot be empty
- ✅ Can contain letters and spaces
- ✅ Examples: `Mohamed Ahmed`, `Fatima Zahra El Alaoui`

### 4. CNE/CIN
- ✅ Cannot be empty
- ✅ Can contain letters and numbers
- ✅ Examples: `AB123456`, `CD789012`, `EF345678`

---

## 🔧 Delimiter Support

The app automatically detects delimiters:
- **Comma (,)** - Most common for CSV files
- **Semicolon (;)** - Common when opening CSV in Excel

Both work perfectly!

---

## 📑 Excel Support

You can also use Excel files (.xlsx or .xls):

1. Create a new Excel workbook
2. Add headers in the first row (optional but recommended)
3. Fill in your data starting from row 2
4. Save as `.xlsx` or `.xls`

**Example Excel Layout:**

| PHONE_NUMBER | PUK_LAST_4 | FULL_NAME      | CNE      |
|--------------|------------|----------------|----------|
| 0612345678   | 1234       | Mohamed Ahmed  | AB123456 |
| 0698765432   | 5678       | Fatima Zahra   | CD789012 |
| 0656789012   | 9012       | Hassan Ibrahim | EF345678 |

---

## 🎯 How to Use

### Step 1: Prepare Your File
1. Download one of the template files
2. Open it in Excel, Google Sheets, or text editor
3. Replace sample data with your actual data
4. Keep the header row (first row) or remove it - both work!
5. Save the file

### Step 2: Load in App
1. Open USSD Registration App
2. Click "Select File" button
3. Choose your CSV or Excel file
4. Wait for parsing confirmation
5. Check for any error messages

### Step 3: Start Processing
1. Click "Start Processing" button
2. App will process each number automatically
3. Monitor progress in the list view
4. Check response messages for each number

---

## ⚠️ Common Errors and Solutions

### Error: "Invalid phone number"
- ✅ **Solution**: Ensure phone starts with 06 or 07 and has 10 digits total

### Error: "PUK last 4 digits must be exactly 4 digits"
- ✅ **Solution**: Check column 2, must be exactly 4 numbers

### Error: "Full name is required"
- ✅ **Solution**: Column 3 (or columns 4+5) cannot be empty

### Error: "CNE is required"
- ✅ **Solution**: CNE/CIN column cannot be empty

### Error: "Expected at least 4 columns"
- ✅ **Solution**: Each row must have at least 4 columns of data

### Error: "Unsupported file format"
- ✅ **Solution**: Use .csv, .xlsx, or .xls files only

---

## 💡 Tips for Best Results

1. **Remove Extra Columns**: Only include the required 4 or 5 columns
2. **No Empty Rows**: Delete any blank rows between data
3. **Consistent Format**: Use the same format for all rows
4. **Test First**: Start with 2-3 rows to test before adding hundreds
5. **UTF-8 Encoding**: Save CSV files as UTF-8 to support Arabic characters
6. **No Special Characters**: Avoid quotes or special characters in phone numbers

---

## 📞 Example Data Sets

### Small Test File (2 records)
```csv
PHONE_NUMBER,PUK_LAST_4,FULL_NAME,CNE
0612345678,1234,Mohamed Ahmed,AB123456
0698765432,5678,Fatima Zahra,CD789012
```

### Large Production File (100+ records)
```csv
PHONE_NUMBER,PUK_LAST_4,FULL_NAME,CNE
0612345678,1234,Mohamed Ahmed,AB123456
0698765432,5678,Fatima Zahra,CD789012
0656789012,9012,Hassan Ibrahim,EF345678
0671234567,3456,Amina Khalil,GH901234
... (add more rows as needed)
```

---

## 🔍 File Format Detection

The app intelligently detects your file format:
1. First checks MIME type from system
2. Then checks file extension (.csv, .xlsx, .xls)
3. Finally tries to parse as CSV if unsure
4. Automatically detects comma or semicolon delimiter

---

## 📱 What Happens After Import

1. App validates each row
2. Shows total records loaded
3. Displays any errors found
4. Stores valid records in database
5. You can start processing immediately

---

## 🎨 CSV Format Examples

### With Header Row:
```csv
PHONE_NUMBER,PUK_LAST_4,FULL_NAME,CNE
0612345678,1234,Mohamed Ahmed,AB123456
```

### Without Header Row:
```csv
0612345678,1234,Mohamed Ahmed,AB123456
0698765432,5678,Fatima Zahra,CD789012
```

Both work! The app auto-detects if first row is a header.

---

## ✅ Quick Checklist Before Import

- [ ] File is .csv, .xlsx, or .xls format
- [ ] All phone numbers start with 06 or 07
- [ ] All phone numbers are 10 digits
- [ ] All PUK codes are exactly 4 digits
- [ ] No empty names or CNE fields
- [ ] No empty rows between data
- [ ] File saved in UTF-8 encoding (for CSV)

---

## 📧 Need Help?

If you encounter issues:
1. Check error messages in the app
2. Verify your data matches template format
3. Test with one of the provided templates first
4. Make sure file has proper extension (.csv, .xlsx, .xls)

---

**Last Updated**: December 21, 2025
**App Version**: 1.0

