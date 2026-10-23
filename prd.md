# Product Requirements Document (PRD): Stealth Sandbox Engine

**Status Dokumen:** Draft / Perencanaan
**Repositori:** `github.com/Dayat222/sanbox_ball`
**Target Platform:** Android (API 21 - API 34)

---

## 1. Latar Belakang & Ringkasan Eksekutif
Sistem *anti-cheat* modern pada aplikasi dan game telah bergeser dari sekadar mendeteksi modifikasi memori menjadi pelacakan identitas perangkat keras (Hardware ID). Ketika pelanggaran terdeteksi, server akan memblokir secara permanen pengenal fisik perangkat (IMEI, Android ID, MAC Address). 

**Stealth Sandbox Engine** dirancang sebagai lingkungan virtual terisolasi yang berfungsi sebagai tameng. Sistem ini mencegat setiap permintaan data identitas perangkat, memberikan data palsu, dan menyembunyikan status virtualisasinya.

## 2. Tujuan & Matriks Keberhasilan

### Tujuan
* **Isolasi Hardware:** Melindungi identitas asli perangkat dari pembacaan pihak ketiga.
* **Bypass Anti-Cheat:** Menghindari deteksi lingkungan virtual.
* **Efisiensi Manajemen:** Memberikan kontrol instan bagi pengguna untuk mereset identitas virtual dalam satu ketukan saat akun terblokir.

### Matriks Keberhasilan
* **0% Device Ban Rate:** Perangkat fisik utama tidak pernah masuk daftar hitam.
* **Crash-Free Rate > 95%:** Aplikasi target dapat dimuat di dalam sandbox tanpa force close.
* **Evasion Success:** Lolos dari pemeriksaan PackageManager dan deteksi ptrace.

---

## 3. Spesifikasi Fungsional (Core Features)

| Fitur | Deskripsi Fungsional | Prioritas |
| :--- | :--- | :--- |
| **Kloning Aplikasi** | Menyalin APK dari OS utama ke dalam kontainer tanpa root. | Tinggi |
| **Hardware ID Spoofer** | Menghasilkan parameter perangkat palsu (IMEI, IMSI, Android ID, MAC Address). | Tinggi |
| **1-Click Reset** | Tombol cepat menghapus cache dan merotasi Hardware ID ke profil baru. | Tinggi |
| **App Visibility Hiding** | Memblokir akses aplikasi target saat meminta daftar aplikasi yang terinstal. | Tinggi |
| **Mock Network Data** | Menyembunyikan parameter koneksi asli (SSID, BSSID Wi-Fi). | Menengah |

---

## 4. Persyaratan Non-Fungsional (Anti-Detection & Evasion)

* **System Call Interception:** Menerapkan intersepsi di level native (C/C++) menggunakan inline hooking. VFS (Virtual File System) akan mengarahkan pembacaan file sistem sensitif ke file teks palsu.
* **Mount Namespace & Direktori:** Menyembunyikan path instalasi asli agar terlihat seperti direktori `/data/app/` standar.
* **Anti-Debugger Bypass:** Menyisipkan hook pada fungsi `ptrace` agar selalu mengembalikan nilai sukses palsu (`0`).
* **Dynamic Package Renaming:** Menyamarkan nama paket (`com.sandbox.ball`) agar tidak terdeteksi scanner.

---

## 5. Arsitektur Teknis
1. **UI / Host App (Java/Kotlin):** Antarmuka pengguna untuk mengatur aplikasi yang dikloning.
2. **Java Framework Middleware:** Mengelola isolasi Android Framework (mengganti Context, mencegat TelephonyManager).
3. **Native VFS & Hooker (C++ / JNI):** Komponen keamanan low-level yang memotong instruksi langsung ke Kernel/OS.

---

## 6. Desain Antarmuka & UX
* **Tema & Tata Letak:** *Dark theme* murni dengan navigasi *mobile* yang *compact* agar ringan dijalankan. 
* **Struktur Visual:** Daftar menu profil dan status aplikasi dibuat berpoin serta terstruktur rapi tanpa ornamen grafis yang berlebihan.
* **Notifikasi:** Menggunakan *toast notification pop-up* di bagian bawah layar saat profil identitas berhasil direset atau aplikasi selesai dikloning, sehingga tidak ada *dialog box* yang menghalangi layar.

---

## 7. Fase Pengembangan (Milestones)
* **Fase 1: Inisialisasi & CI/CD** (Setup repositori dan GitHub Actions untuk kompilasi APK otomatis).
* **Fase 2: Virtualization Core** (Membangun kontainer dasar dan implementasi Java API Hooking).
* **Fase 3: Native Evasion** (Penulisan library C++ untuk JNI hooking dan implementasi VFS).
* **Fase 4: Antarmuka & Rilis** (Penyelesaian UI *dark theme* dan rilis APK versi 1.0.0-beta).