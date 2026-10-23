# Stealth Sandbox Engine (`sanbox_ball`)

![Android](https://img.shields.io/badge/Platform-Android_API_21_--_34-00FFA3?style=flat&logo=android)
![Target](https://img.shields.io/badge/Compile_SDK-35-00E5FF?style=flat)
![Build](https://img.shields.io/badge/CI%2FCD-GitHub_Actions-blue?style=flat&logo=githubactions)
![License](https://img.shields.io/badge/License-Proprietary-EF4444?style=flat)

> **Repositori:** `github.com/Dayat222/sanbox_ball`  
> **Status:** Versi 1.0.0-beta  
> **Target:** Android 5.0 (API 21) hingga Android 14 (API 34/35)

---

## 1. Ringkasan Eksekutif & Latar Belakang

Sistem *anti-cheat* dan pelacak integritas perangkat modern pada aplikasi/game telah beralih dari deteksi modifikasi memori ke pemblokiran permanen pengenal fisik perangkat (**Hardware ID Ban** seperti IMEI, Android ID, MAC Address, dan Serial). 

**Stealth Sandbox Engine** dirancang sebagai lingkungan virtual terisolasi tanpa butuh akses root (*rootless container*) yang bertindak sebagai tameng. Sistem ini mencegat setiap permintaan data identitas perangkat, menyajikan data palsu yang realistis, serta menyembunyikan status virtualisasinya dari pemindai anti-cheat dan modul pendeteksi sandbox.

---

## 2. Fitur Utama (Core Features)

| Fitur | Deskripsi Fungsional & Evasion | Prioritas |
| :--- | :--- | :--- |
| **Kloning Aplikasi** | Menyalin APK target dari OS utama ke dalam struktur kontainer virtual terisolasi (`/sandbox_vfs/apps/<pkg>/base.apk` dan data terpisah di `/sandbox_vfs/data/<pkg>`) tanpa memerlukan root. | **Tinggi** |
| **Hardware ID Spoofer** | Menghasilkan parameter perangkat tiruan realistis: IMEI (15 digit tervalidasi algoritma Luhn), IMSI, Android ID (16 hex), MAC Address, Nomor Seri, Model, Brand, Manufaktur, dan Fingerprint. | **Tinggi** |
| **1-Click Reset** | Tombol cepat untuk menghapus cache/data kontainer dan merotasi profil Hardware ID secara instan ke identitas baru saat terjadi banned akun atau deteksi. | **Tinggi** |
| **App Visibility Hiding** | Mencegat query `PackageManager` (`getInstalledPackages`, `getInstalledApplications`, `queryIntentActivities`) sehingga aplikasi target hanya melihat sistem standar dan dirinya sendiri. Menyembunyikan Magisk, Xposed, Lucky Patcher, dan paket sandbox itu sendiri (`com.sandbox.ball`). | **Tinggi** |
| **Mock Network Data** | Menyembunyikan parameter koneksi Wi-Fi asli (SSID, BSSID, Fake IP Address, Gateway, DNS). | **Menengah** |

---

## 3. Arsitektur Anti-Detection & Native Evasion

### A. System Call Interception (C++ Native Hooker)
Intersepsi fungsi libc pada level low-level native untuk memotong akses langsung yang mencoba membaca informasi kernel/OS:
* `open` & `openat`: Meredireksi pembacaan file sistem sensitif.
* `access`, `stat`, `lstat`: Memastikan status file virtual valid.
* `fopen`: Meredireksi panggilan stream tingkat tinggi.

### B. Virtual File System (VFS) & Mount Namespace
* `/proc/cpuinfo` $\rightarrow$ Mengembalikan data prosesor standar (Qualcomm SM8550 Snapdragon 8 Gen 2 / Gen 3).
* `/proc/version` $\rightarrow$ Mengembalikan kernel release Android standar.
* `/sys/class/net/wlan0/address` $\rightarrow$ Mengembalikan alamat MAC tiruan.
* `/proc/self/maps` $\rightarrow$ **Sanitizer Engine** otomatis menyaring dan menghapus baris memori yang memuat library sandbox (`libstealth_engine.so`), package name (`com.sandbox.ball`), frida, dan xposed.
* Mount namespace mengarahkan query `/data/data/<pkg>` dan `/data/user/0/<pkg>` langsung ke kontainer terisolasi.

### C. Anti-Debugger Bypass (`ptrace`)
* Modul native menyisipkan hook pada fungsi libc `ptrace`.
* Permintaan anti-cheat seperti `ptrace(PTRACE_TRACEME, 0, 1, 0)` dan `ptrace(PTRACE_ATTACH, ...)` akan selalu dicegat dan mengembalikan nilai sukses palsu (`0`).
* Mencegah crash paksa atau suicide loop aplikasi target yang memeriksa debugger.
* File `/proc/self/status` dipalsukan dengan `TracerPid: 0`.

### D. Dynamic Package Renaming
* Menyamarkan nama paket internal dan menyaring referensi `com.sandbox.ball` agar scanner anti-cheat tidak dapat menemukan tanda-tanda aplikasi sandbox di memori maupun daftar proses.

---

## 4. Desain Antarmuka & UX (Pure Dark Theme)

Sesuai spesifikasi PRD Section 6:
* **Tema Gelap Murni:** Menggunakan palet `#0B0E14` (Deep Dark), `#151B23` (Card), dengan aksen neon cyan `#00E5FF` dan emerald `#00FFA3`.
* **Navigasi Ringkas:** Indikator perisai keamanan (*Shield Status Badge: 5/5 SHIELDS ACTIVE*), kartu profil aktif dengan daftar berpoin yang rapi, dan tombol aksi terpusat.
* **Notifikasi Pop-up Bagian Bawah:** Menggunakan *Toast Notification Pop-Up* khusus di bagian bawah layar tanpa dialog box yang mengganggu saat mereset identitas atau mengkloning aplikasi.

---

## 5. Struktur Repositori & Modul

```
sanbox_ball/
├── .github/
│   └── workflows/
│       └── build.yml               # GitHub Actions CI/CD otomatis kompilasi APK
├── app/
│   ├── build.gradle.kts            # Konfigurasi NDK 26.1, CMake 3.22, SDK 35
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── cpp/
│       │   │   ├── CMakeLists.txt  # Konfigurasi C++ 17 native engine
│       │   │   ├── stealth_engine.cpp / .h  (JNI bridge)
│       │   │   ├── vfs/
│       │   │   │   ├── vfs_core.cpp / .h    (VFS Redirection & Maps Filter)
│       │   │   ├── hook/
│       │   │   │   ├── syscall_hook.cpp / .h (Libc & PTRACE Interceptor)
│       │   │   └── utils/log.h
│       │   ├── java/com/sandbox/ball/
│       │   │   ├── StealthApplication.kt
│       │   │   ├── core/
│       │   │   │   ├── nativebridge/NativeBridge.kt
│       │   │   │   ├── spoofer/ (HardwareProfile, ProfileGenerator, ProfileManager)
│       │   │   │   ├── hooks/   (Telephony, SettingsSecure, Wifi, PackageManager, Build)
│       │   │   │   └── container/ (AppContainer, VirtualFileSystem, StubActivity)
│       │   │   ├── model/ (ClonedAppInfo, InstalledAppInfo, EvasionStatus)
│       │   │   └── ui/    (MainActivity, AppClonerActivity, ProfileDetailActivity, StealthToast)
│       │   └── res/   (Layouts, Themes, Colors, Vectors)
│       └── test/      (Unit Tests: Luhn IMEI, Android ID, App Visibility Hiding)
├── prd.md             # Dokumen Persyaratan Produk
└── README.md          # Dokumentasi Lengkap
```

---

## 6. Cara Kompilasi (Build Guide)

### Prasyarat
1. Java Development Kit (JDK) 17 atau 21.
2. Android SDK (Platform 35, Build-Tools 35.0.0).
3. Android NDK (versi `26.1.10909125`).
4. CMake (versi `3.22.1`).

### Menjalankan Unit Test
```bash
./gradlew test
```

### Kompilasi Debug APK
```bash
./gradlew assembleDebug
```
Output APK: `app/build/outputs/apk/debug/app-debug.apk`

### Kompilasi Release APK
```bash
./gradlew assembleRelease
```
Output APK: `app/build/outputs/apk/release/app-release-unsigned.apk`
