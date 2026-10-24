# Product Requirements Document (PRD): Hardening Keamanan, Mitigasi Sandbox Escape, & DevSecOps (v1.4.0)

**Nama Proyek:** Stealth Sandbox Engine (`sanbox_ball`)  
**Repositori Target:** `github.com/Dayat222/sanbox_ball`  
**Status Dokumen:** Spesifikasi Remediasi Keamanan (Security Hardening Specification)  
**Versi Basis Audit:** `v1.3.0-release`  
**Target Rilis:** `v1.4.0-release`  
**Target Platform:** Android 5.0 (API 21) $\rightarrow$ Android 14 (API 34), Compile SDK 35

---

## 1. Ringkasan Eksekutif & Latar Belakang Audit
Berdasarkan audit keamanan menyeluruh terhadap kode sumber **Stealth Sandbox Engine (`v1.3.0-release`)**, ditemukan **7 celah keamanan** yang mencakup kategori Kritis, Tinggi, Menengah, hingga Rendah. Celah keamanan tersebut berpotensi dieksploitasi oleh aplikasi berbahaya yang dikloning untuk keluar dari isolasi kontainer (*Sandbox Escape*), menimpa berkas internal aplikasi *host* (*Zip Slip*), melakukan serangan *Man-in-the-Middle* (MitM) pada mekanisme pembaruan aplikasi (*In-App Updater*), serta memicu *Undefined Behavior* akibat *Data Race* pada lapisan C++.

Dokumen PRD ini menetapkan spesifikasi teknis tingkat kode untuk menutup seluruh celah keamanan tersebut agar arsitektur kontainer memenuhi standar keamanan produksi.

---

## 2. Matriks Kerentanan & Target Remediasi (Security Remediation Matrix)

| Kode ID | Komponen / Berkas Terkait | Celah Keamanan pada v1.3.0 | Solusi Remediasi pada v1.4.0 | Tingkat Risiko |
| :--- | :--- | :--- | :--- | :--- |
| **SEC-01** | `NativeLibExtractor.kt` | Kerentanan *Zip Slip* (*Arbitrary File Overwrite*) saat mengekstrak pustaka `.so` dari APK tanpa validasi jalur kanonik. | Validasi ketat `outFile.canonicalPath` terhadap `targetLibDir.canonicalPath` sebelum ekstraksi. | **Kritis (P0)** |
| **SEC-02** | `app/src/main/cpp/vfs/vfs_core.cpp` | *Path Traversal* (`..`) keluar dari VFS, pencocokan prefix tanpa batas direktori (`/`), dan intersepsi `/maps` yang salah sasaran. | Normalisasi jalur (`realpath`/resolusi segmen `..`), validasi batas direktori `/`, dan pembatasan filter `/maps` khusus pada `/proc/`. | **Kritis (P0)** |
| **SEC-03** | `app/src/main/cpp/hook/syscall_hook.cpp` | Halaman memori `.got` dibiarkan `PROT_WRITE` secara permanen (melemahkan RELRO & memicu deteksi *anti-cheat*). | Membaca izin asli halaman memori dan mengembalikan proteksi memori (`PROT_READ`) setelah penulisan pointer GOT selesai. | **Tinggi (P0)** |
| **SEC-04** | `UpdateManager.kt` & `AppUpdateInfo.kt` | *In-App Update* tanpa verifikasi hash SHA-256 & tanda tangan sertifikat, celah *HTTP redirect downgrade*, serta penyimpanan di *external storage*. | Penambahan validasi skema `https://` pada *redirect*, penyimpanan di `context.cacheDir`, serta verifikasi SHA-256 dan sertifikat APK. | **Tinggi (P0)** |
| **SEC-05** | `vfs_core.h`, `vfs_core.cpp`, `syscall_hook.cpp` | *Data Race* (C++17 *Undefined Behavior*) pada variabel status boolean/integer yang diakses lintas *thread* tanpa sinkronisasi atomik. | Mengubah `enabled_`, `mapsFilterEnabled_`, `g_antidebug_enabled`, dan `g_hooks_installed` menjadi `std::atomic`. | **Menengah (P1)** |
| **SEC-06** | `app/build.gradle.kts` & `.github/workflows/build.yml` | APK `release` ditandatangani dengan kunci `debug` (`CN=Android Debug`) dan `app-debug.apk` ikut dipublikasikan ke GitHub Release. | Konfigurasi `signingConfigs.release` berbasis *Environment Variables* / *GitHub Secrets* dan penghapusan `app-debug.apk` dari rilis publik. | **Menengah (P1)** |
| **SEC-07** | `AndroidManifest.xml` & `res/xml/file_paths.xml` | Atribut `android:allowBackup="true"` mengekspos data kontainer via ADB, dan `file_paths.xml` mengekspos seluruh direktori root (`.`). | Mengubah `android:allowBackup="false"` dan membatasi `file_paths.xml` hanya pada sub-direktori `updates/`. | **Rendah (P2)** |

---

## 3. Spesifikasi Teknis Perbaikan Lapisan Kotlin/Java

### 3.1. Mitigasi Kerentanan *Zip Slip* pada Ekstraktor Native (`SEC-01`)
* **Lokasi Berkas:** `app/src/main/java/com/sandbox/ball/core/container/NativeLibExtractor.kt`
* **Analisis Masalah:**
  Saat mengekstrak berkas `.so` dari APK yang dikloning, kode mengambil `val soName = entry.name.substring(prefix.length)` lalu langsung membuka `FileOutputStream(File(targetLibDir, soName))`. APK yang dimodifikasi dengan nama entri `lib/arm64-v8a/../../../files/exploit.so` dapat menimpa berkas di luar direktori target.
* **Spesifikasi Perbaikan:**
  1. Ambil jalur kanonik dari direktori tujuan terlebih dahulu: `val canonicalDestDir = targetLibDir.canonicalPath + File.separator`.
  2. Tolak setiap nama entri yang mengandung karakter pemisah direktori terlarang atau yang jalur kanoniknya keluar dari `canonicalDestDir`:
     ```kotlin
     val soName = entry.name.substring(prefix.length)
     if (soName.contains("/") || soName.contains("\\") || soName.contains("..")) {
         Log.w(TAG, "Blocked suspicious zip entry name: ${entry.name}")
         continue
     }

     val outFile = File(targetLibDir, soName)
     val canonicalOutPath = outFile.canonicalPath
     if (!canonicalOutPath.startsWith(canonicalDestDir)) {
         throw SecurityException("Zip Slip attack detected on entry: ${entry.name}")
     }
     ```

### 3.2. Pengamanan *In-App Updater* & Verifikasi Integritas APK (`SEC-04`)
* **Lokasi Berkas:** `app/src/main/java/com/sandbox/ball/core/updater/UpdateManager.kt` & `AppUpdateInfo.kt`
* **Analisis Masalah:**
  1. `UpdateManager.downloadApk()` mengikuti *redirect* HTTP (`301/302/307/308`) tanpa memeriksa apakah URL tujuan (`Location`) diturunkan ke protokol tidak terenkripsi (`http://`) atau diarahkan ke domain berbahaya.
  2. Berkas APK disimpan di `context.getExternalFilesDir(null)` yang lebih rentan terhadap modifikasi pihak ketiga dibandingkan ruang penyimpanan internal aplikasi.
  3. Tidak ada verifikasi *checksum* SHA-256 maupun pemeriksaan kesesuaian tanda tangan kriptografi (*APK Signature Verification*) sebelum memanggil `installApk()`.
* **Spesifikasi Perbaikan:**
  1. **Validasi Protokol & Domain pada Redirect:**
     * Buat fungsi pembantu `isTrustedUpdateUrl(url: String): Boolean` yang memastikan URL menggunakan skema `https://` dan berasal dari domain resmi GitHub (`github.com` atau `objects.githubusercontent.com`).
     * Sebelum mengikuti header `Location`, panggil `isTrustedUpdateUrl(newUrl)`. Jika tidak valid, batalkan pengunduhan dan lempar `SecurityException`.
  2. **Isolasi Penyimpanan Unduhan:**
     * Ubah lokasi penyimpanan berkas unduhan dari `context.getExternalFilesDir(null)` menjadi direktori internal `File(context.cacheDir, "updates")`.
  3. **Verifikasi SHA-256 & Sertifikat Penandatangan APK:**
     * Tambahkan properti `val sha256Checksum: String = ""` pada `AppUpdateInfo.kt` (diekstrak dari catatan rilis GitHub jika tersedia, misalnya format `SHA256: <64-hex>`).
     * Buat metode `verifyDownloadedApk(context: Context, apkFile: File, expectedSha256: String): Boolean` di `UpdateManager.kt`:
       * Jika `expectedSha256` tidak kosong, hitung digest `SHA-256` dari `apkFile` dan bandingkan secara *constant-time* (`MessageDigest.isEqual`).
       * Gunakan `PackageManager.getPackageArchiveInfo()` dengan flag `GET_SIGNING_CERTIFICATES` (API 28+) atau `GET_SIGNATURES` (API 21–27) untuk memverifikasi bahwa nama paket di dalam APK yang diunduh adalah `context.packageName` dan sertifikat penandatangannya identik dengan sertifikat aplikasi yang sedang terinstal.
       * Jika verifikasi gagal, hapus `apkFile` seketika dan batalkan proses instalasi.

---

## 4. Spesifikasi Teknis Perbaikan Lapisan Native C++ (NDK)

### 4.1. Sanitasi *Path Traversal* & Perbaikan Pencocokan VFS (`SEC-02`)
* **Lokasi Berkas:** `app/src/main/cpp/vfs/vfs_core.cpp` & `app/src/main/cpp/vfs/vfs_core.h`
* **Analisis Masalah:**
  1. Pemanggilan `open("/data/data/<targetPkg>/../../../data/com.sandbox.ball/shared_prefs/...")` lolos dari pemeriksaan prefix dan memungkinkan aplikasi di dalam kontainer mengakses direktori milik *host* `com.sandbox.ball`.
  2. Pemeriksaan `pathStr.find("/maps") != std::string::npos` terlalu luas karena mencegat semua berkas aplikasi yang kebetulan memiliki kata `/maps` di dalamnya.
  3. Pencocokan prefix `pathStr.rfind(prefix, 0) == 0` tidak memeriksa karakter pembatas `/`, sehingga `/data/data/com.app` dapat mencocokkan `/data/data/com.app_other`.
* **Spesifikasi Perbaikan:**
  1. **Fungsi Normalisasi Jalur (`normalizeLexicalPath`):**
     Tambahkan fungsi di `vfs_core.cpp` yang memecah jalur berdasarkan `/` dan menyelesaikan setiap segmen `.` serta `..` secara leksikal sebelum pencocokan dilakukan:
     ```cpp
     static std::string normalizeLexicalPath(const std::string& rawPath) {
         if (rawPath.empty()) return "";
         bool isAbsolute = (rawPath[0] == '/');
         std::vector<std::string> parts;
         std::stringstream ss(rawPath);
         std::string item;
         while (std::getline(ss, item, '/')) {
             if (item.empty() || item == ".") continue;
             if (item == "..") {
                 if (!parts.empty()) parts.pop_back();
             } else {
                 parts.push_back(item);
             }
         }
         std::string result = isAbsolute ? "/" : "";
         for (size_t i = 0; i < parts.size(); ++i) {
             result += parts[i];
             if (i + 1 < parts.size()) result += "/";
         }
         return result;
     }
     ```
  2. **Proteksi Direktori Host Sandbox:**
     Setelah jalur dinormalisasi, apabila jalur mengarah ke direktori internal `com.sandbox.ball` di luar `virtualRootDir_` (misalnya `/data/data/com.sandbox.ball/shared_prefs` atau `/data/user/0/com.sandbox.ball/shared_prefs`), blokir akses dengan mengarahkannya ke `/dev/null` atau mengembalikan jalur tidak valid agar aplikasi target tidak dapat mencuri konfigurasi *host*.
  3. **Perbaikan Intersepsi `/proc/.../maps`:**
     Buat fungsi spesifik `isProcMapsPath(const std::string& path)`:
     ```cpp
     static bool isProcMapsPath(const std::string& path) {
         if (path == "/proc/self/maps" || path == "/proc/thread-self/maps") return true;
         if (path.rfind("/proc/", 0) == 0 && path.length() > 11) {
             return path.compare(path.length() - 5, 5, "/maps") == 0;
         }
         return false;
     }
     ```
  4. **Validasi Batas Direktori pada Prefix Mapping:**
     Pastikan karakter setelah prefix adalah `/` atau akhir dari string:
     ```cpp
     if (normPath.rfind(prefix, 0) == 0 &&
         (normPath.length() == prefix.length() || normPath[prefix.length()] == '/')) {
         std::string sub = normPath.substr(prefix.length());
         return pair.second + sub;
     }
     ```

### 4.2. Pemulihan Proteksi Memori GOT / RELRO (`SEC-03`)
* **Lokasi Berkas:** `app/src/main/cpp/hook/syscall_hook.cpp`
* **Analisis Masalah:**
  Pada `plt_hook_callback`, halaman memori `.got` diubah menggunakan `mprotect(page_start, page_size, PROT_READ | PROT_WRITE)` namun tidak dikembalikan ke izin semula. Apabila pustaka dikompilasi dengan *Full RELRO* (`GNU_RELRO`), membiarkan halaman GOT berstatus `RW` membuka celah *GOT Overwrite* dan memicu deteksi *anti-cheat* yang memindai adanya izin `w` pada segmen konstanta/RELRO di `/proc/self/maps`.
* **Spesifikasi Perbaikan:**
  1. Periksa apakah alamat `got_entry` berada di dalam segmen `PT_GNU_RELRO` milik objek ELF terkait (`phdr[i].p_type == PT_GNU_RELRO`).
  2. Jika `got_entry` berada di dalam rentang `PT_GNU_RELRO`, maka setelah nilai `*got_entry = target.hook_func` ditulis dan `__builtin___clear_cache` dipanggil, kembalikan izin halaman memori tersebut menjadi `PROT_READ`:
     ```cpp
     if (is_relro_page) {
         mprotect(page_start, page_size, PROT_READ);
     }
     ```
  3. Jika tidak berada di dalam `PT_GNU_RELRO` (berbagi halaman dengan `.data`), biarkan `PROT_READ | PROT_WRITE` agar tidak memicu `SIGSEGV`.

### 4.3. Perbaikan *Data Race* dengan `std::atomic` (`SEC-05`)
* **Lokasi Berkas:** `app/src/main/cpp/vfs/vfs_core.h`, `app/src/main/cpp/vfs/vfs_core.cpp`, dan `app/src/main/cpp/hook/syscall_hook.cpp`
* **Spesifikasi Perbaikan:**
  1. Di `vfs_core.h`:
     * Tambahkan `#include <atomic>`.
     * Ubah `bool enabled_ = false;` menjadi `std::atomic<bool> enabled_{false};`.
     * Ubah `bool mapsFilterEnabled_ = true;` menjadi `std::atomic<bool> mapsFilterEnabled_{true};`.
     * Gunakan `enabled_.load(std::memory_order_acquire)` pada pembacaan dan `enabled_.store(enabled, std::memory_order_release)` pada penulisan.
  2. Di `syscall_hook.cpp`:
     * Tambahkan `#include <atomic>`.
     * Ubah `static int g_antidebug_enabled = 1;` menjadi `static std::atomic<int> g_antidebug_enabled{1};`.
     * Ubah `static int g_hooks_installed = 0;` menjadi `static std::atomic<int> g_hooks_installed{0};`.

---

## 5. Spesifikasi Pengamanan Manifest, Build, & DevSecOps

### 5.1. Konfigurasi Penandatanganan Rilis & GitHub Actions (`SEC-06`)
* **Lokasi Berkas:** `app/build.gradle.kts` & `.github/workflows/build.yml`
* **Spesifikasi Perbaikan:**
  1. Di `app/build.gradle.kts`, tambahkan blok `signingConfigs` yang membaca konfigurasi *Keystore* produksi dari variabel lingkungan (`KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`), dan gunakan *fallback* ke `debug` hanya jika variabel lingkungan tidak tersedia pada kompilasi lokal:
     ```kotlin
     signingConfigs {
         create("release") {
             val ksPath = System.getenv("KEYSTORE_PATH")
             if (!ksPath.isNullOrEmpty() && file(ksPath).exists()) {
                 storeFile = file(ksPath)
                 storePassword = System.getenv("KEYSTORE_PASSWORD")
                 keyAlias = System.getenv("KEY_ALIAS")
                 keyPassword = System.getenv("KEY_PASSWORD")
             }
         }
     }
     ```
  2. Pada blok `buildTypes.release`, arahkan `signingConfig` ke `signingConfigs.getByName("release")` apabila `storeFile` tersedia.
  3. Di `.github/workflows/build.yml`, pada langkah `Create GitHub Release`, hapus `app/build/outputs/apk/debug/app-debug.apk` dari properti `files:` agar rilis publik hanya mendistribusikan `app-release.apk` yang telah di-*obfuscate* dan tidak memiliki flag *debuggable*.

### 5.2. Hardening `AndroidManifest.xml` & `file_paths.xml` (`SEC-07`)
* **Lokasi Berkas:** `app/src/main/AndroidManifest.xml` & `app/src/main/res/xml/file_paths.xml`
* **Spesifikasi Perbaikan:**
  1. Di `app/src/main/AndroidManifest.xml`, ubah atribut `<application>` dari `android:allowBackup="true"` menjadi:
     ```xml
     android:allowBackup="false"
     android:fullBackupContent="false"
     ```
  2. Di `app/src/main/res/xml/file_paths.xml`, hapus eksposur direktori root (`path="."`) dan batasi hanya ke sub-direktori `updates/`:
     ```xml
     <?xml version="1.0" encoding="utf-8"?>
     <paths xmlns:android="http://schemas.android.com/apk/res/android">
         <cache-path name="updates_cache" path="updates/" />
         <files-path name="updates_files" path="updates/" />
     </paths>
     ```

---

## 6. Rencana Pengujian Keamanan (Security Verification Plan)

Tambahkan pengujian unit baru pada `app/src/test/java/com/sandbox/ball/SecurityHardeningTest.kt` yang memverifikasi:
1. **Uji Anti-Zip Slip:** Mensimulasikan entri zip dengan nama `lib/arm64-v8a/../../shared_prefs/hacked.xml` dan memastikan fungsi validasi menolak jalur tersebut dengan `SecurityException`.
2. **Uji Validasi URL Pembaruan:** Memastikan URL dengan protokol `http://` atau domain di luar GitHub ditolak oleh validator URL pada `UpdateManager`.
3. **Uji Verifikasi Checksum SHA-256:** Memastikan fungsi pencocokan hash SHA-256 menolak berkas APK jika *checksum* berbeda dari nilai yang diharapkan.

---

## 7. Daftar Periksa Kriteria Penerimaan (Acceptance Criteria Checklist)

Sebelum merilis tag versi `v1.4.0-release`, seluruh kriteria berikut wajib terpenuhi:
- [ ] **AC-01 (Anti-Zip Slip):** `NativeLibExtractor.kt` memvalidasi `canonicalPath` dan menolak entri zip yang mengandung `..` atau karakter pemisah direktori.
- [ ] **AC-02 (VFS Path Normalization):** `VfsCore::resolvePath()` menormalisasi segmen `..`, memeriksa batas direktori `/`, melindungi direktori `com.sandbox.ball`, dan hanya menerapkan filter `/maps` pada jalur `/proc/`.
- [ ] **AC-03 (RELRO Memory Protection):** `syscall_hook.cpp` mengembalikan izin `PROT_READ` pada halaman memori yang termasuk dalam segmen `PT_GNU_RELRO` setelah modifikasi GOT selesai.
- [ ] **AC-04 (Secure Updater):** `UpdateManager.kt` menyimpan APK di `context.cacheDir`, memvalidasi `https://` saat *redirect*, serta memverifikasi hash SHA-256 dan sertifikat paket sebelum instalasi.
- [ ] **AC-05 (Thread-Safe Atomics):** Seluruh variabel status lintas *thread* di `vfs_core.h` dan `syscall_hook.cpp` menggunakan `std::atomic`.
- [ ] **AC-06 (Manifest & Provider Hardening):** `AndroidManifest.xml` menggunakan `android:allowBackup="false"` dan `file_paths.xml` hanya mengekspos folder `updates/`.
- [ ] **AC-07 (Release Pipeline):** GitHub Release hanya mendistribusikan `app-release.apk` dan mendukung konfigurasi *Keystore* produksi.