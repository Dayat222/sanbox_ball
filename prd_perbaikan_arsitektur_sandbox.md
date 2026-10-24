# Product Requirements Document (PRD): Perbaikan Arsitektur & DevSecOps (v1.1.0)

**Nama Proyek:** Stealth Sandbox Engine (`sanbox_ball`)  
**Status Dokumen:** Rencana Perbaikan Teknis (Remediation Plan)  
**Target Rilis:** v1.1.0-release  
**Platform Target:** Android API 21 $\rightarrow$ API 34 (Compile SDK 35)

---

## 1. Latar Belakang & Tujuan Perbaikan
Berdasarkan hasil audit teknis pada versi `1.0.3`, ditemukan bahwa antarmuka pengguna (UI) dan generator profil telah berfungsi dengan baik, namun lapisan inti kontainer (*container runtime*), pencegatan panggilan sistem (*hooking*), serta konfigurasi *build* masih bersifat pasif (simulasi) dan memiliki celah *thread-safety*.

Dokumen PRD ini mendefinisikan spesifikasi teknis untuk memperbaiki empat area kritis agar aplikasi dapat menjalankan kontainer secara terisolasi, aman dari kondisi *race condition*, dan terkompilasi sesuai standar produksi.

---

## 2. Ruang Lingkup Perbaikan (Remediation Scope)

| ID | Area Perbaikan | Masalah Saat Ini | Solusi Arsitektur Target | Prioritas |
| :--- | :--- | :--- | :--- | :--- |
| **REM-01** | **Container Runtime** | `StubActivity` membuka aplikasi via `Intent` eksternal di OS utama. | Pemuatan APK secara internal di dalam proses `:container` menggunakan refleksi `ActivityThread` dan `LoadedApk`. | Kritis |
| **REM-02** | **Native PLT/GOT Hooking** | `syscall_hook.cpp` hanya memanggil `dlsym` tanpa menimpa tabel simbol memori. | Implementasi *PLT/GOT hooking* untuk mengarahkan `open`, `openat`, `fopen`, `stat`, dan `access` ke fungsi pengalihan VFS. | Kritis |
| **REM-03** | **VFS Thread-Safety** | `sanitizeMapsFile()` dipanggil di luar kunci `std::mutex`, memicu *race condition*. | Enkapsulasi penuh dengan `std::lock_guard<std::mutex>` atau penggunaan deskriptor memori anonim (`memfd_create`). | Tinggi |
| **REM-04** | **Binder Proxy Hooking** | Kelas di `core/hooks/` bersifat statis pasif dan tidak terhubung ke sistem Android. | Implementasi Java `Dynamic Proxy` (`InvocationHandler`) pada `ServiceManager` untuk mencegat transaksi IPC Binder. | Kritis |
| **REM-05** | **NDK Build System** | File `CMakeLists.txt` belum terdefinisi secara eksplisit dalam paket *build*. | Pembuatan konfigurasi `src/main/cpp/CMakeLists.txt` untuk kompilasi `libstealth_engine.so`. | Tinggi |
| **REM-06** | **DevSecOps & CI/CD** | Rilis menggunakan `app-debug.apk`, `isMinifyEnabled = false`, dan `local.properties` bocor. | Kompilasi `assembleRelease` dengan R8/ProGuard aktif, *signing key* via GitHub Secrets, dan pembersihan `.gitignore`. | Tinggi |

---

## 3. Spesifikasi Teknis Perbaikan

### 3.1. Perbaikan Eksekusi Kontainer (`core/container/`)
Saat ini `StubActivity.kt` memanggil `packageManager.getLaunchIntentForPackage(pkg)` yang menyerahkan eksekusi kembali ke OS utama.
* **Kebutuhan Teknis:**
  1. **Context & LoadedApk Replacement:** Saat `StubActivity` dijalankan di proses `:container`, sistem harus membuat instance `Context` khusus yang mengarahkan `getDataDir()`, `getFilesDir()`, dan `getCacheDir()` ke direktori `VirtualFileSystem` (`/sandbox_vfs/data/<pkg>`).
  2. **Instrumentation & Activity Lifecycle:** Mengganti `mInstrumentation` pada `ActivityThread` saat ini agar setiap pemanggilan `execStartActivity` dari aplikasi yang dikloning dibungkus kembali ke dalam `StubActivity` milik kontainer.
  3. **Native Library Loading:** Memastikan direktori `lib/<abi>` dari `base.apk` diekstrak ke `/sandbox_vfs/apps/<pkg>/lib/` dan ditambahkan ke *path* pencarian `DexClassLoader`.

### 3.2. Perbaikan Lapisan Native C++ (`src/main/cpp/`)
#### A. Implementasi PLT/GOT Interception (`syscall_hook.cpp`)
* `dlsym(libc, "open")` hanya mengambil alamat fungsi asli. Untuk mengalihkan panggilan dari *library* yang dimuat di dalam kontainer, sistem harus memindai tabel PLT/GOT dari *shared libraries* (`.so`) yang dimuat di memori proses `:container` dan mengganti alamat fungsi `open`, `openat`, `fopen`, `stat`, `lstat`, dan `access` ke fungsi *wrapper* `hooked_*`.

#### B. Perbaikan Race Condition pada VFS (`vfs_core.cpp`)
* Pada metode `VfsCore::resolvePath(const char* originalPath)`:
  * Pindahkan kunci `std::lock_guard<std::mutex> lock(mutex_);` ke baris paling atas sebelum pemeriksaan `sanitizeMapsFile()`.
  * Gunakan penulisan file atomik (menulis ke file sementara `proc_maps.<tid>` lalu mengganti namanya, atau menggunakan sistem *in-memory file descriptor* `memfd_create("sanitized_maps", MFD_CLOEXEC)`) agar pembacaan `/proc/self/maps` oleh banyak *thread* sekaligus tidak saling menimpa.

#### C. Konfigurasi `CMakeLists.txt`
* Menyediakan file `app/src/main/cpp/CMakeLists.txt` yang mendefinisikan:
  * Target *shared library*: `add_library(stealth_engine SHARED stealth_engine.cpp vfs/vfs_core.cpp hook/syscall_hook.cpp)`
  * Flag visibilitas simbol: `-fvisibility=hidden` dan `-Oz` (optimasi ukuran biner pada mode rilis).
  * Penautan pustaka sistem: `target_link_libraries(stealth_engine android log dl)`.

### 3.3. Perbaikan Lapisan Middleware Framework (`core/hooks/`)
Agar objek `TelephonyHook`, `WifiHook`, `SettingsSecureHook`, dan `PackageManagerHook` benar-benar bekerja saat aplikasi berjalan:
* **Binder IPC Interception:**
  1. Membuat komponen `BinderProxyManager.kt` yang mengganti objek `IInterface` pada *cache* `ServiceManager` menggunakan `java.lang.reflect.Proxy.newProxyInstance`.
  2. **PackageManager (`IPackageManager`):** Mencegat metode `getInstalledPackages`, `getInstalledApplications`, `getPackageInfo`, dan `getInstallerPackageName`, lalu meneruskan hasilnya ke filter yang sudah ada di `PackageManagerHook.kt`.
  3. **Telephony (`ITelephony` & `IPhoneSubInfo`):** Mencegat metode `getDeviceId`, `getImeiForSlot`, dan `getSubscriberIdForSubscriber`, lalu mengembalikan nilai dari `TelephonyHook.kt`.
  4. **Settings Provider (`IContentProvider`):** Mencegat panggilan `CALL_METHOD` untuk `GET_secure` (`android_id`) dan mengembalikan nilai dari `SettingsSecureHook.kt`.
* **Kompatibilitas Android 9+ (`BuildPropsHook.kt`):**
  * Menghapus penggunaan refleksi `Field::class.java.getDeclaredField("modifiers")` yang diblokir oleh *Hidden API Policy* pada Android 9 $\rightarrow$ 14. Cukup gunakan `field.isAccessible = true` langsung pada *field* statis kelas `android.os.Build` atau lakukan modifikasi properti melalui jembatan JNI (`env->SetStaticObjectField`) di `stealth_engine.cpp` karena lapisan JNI memiliki akses lebih leluasa terhadap *field* `static final` dibandingkan refleksi Java.

---

## 4. Spesifikasi Keamanan Build & CI/CD (DevSecOps)

### 4.1. Pengamanan Konfigurasi Gradle (`app/build.gradle.kts`)
1. **Aktifkan Obfuscation (R8/ProGuard) pada Mode Release:**
   * Mengubah `isMinifyEnabled = true` dan menambahkan `isShrinkResources = true` pada blok `buildTypes.release`.
   * Menghapus simbol *debug* dari pustaka C++ dengan menambahkan `ndk { debugSymbolLevel = "NONE" }` pada blok `release`.
2. **Pembersihan File Lokal:**
   * Menghapus `local.properties` dari indeks Git (`git rm --cached local.properties`) dan menambahkannya ke dalam `.gitignore`.

### 4.2. Pembaruan Alur Kerja GitHub Actions (`.github/workflows/build.yml`)
1. Mengganti perintah `./gradlew assembleDebug` menjadi `./gradlew assembleRelease` untuk rilis produksi agar APK tidak memiliki atribut `android:debuggable="true"`.
2. Menambahkan langkah *APK Signing* atau konfigurasi `signingConfigs` di Gradle yang membaca variabel lingkungan dari GitHub Secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`).
3. Memastikan `UpdateManager.kt` mencari aset APK rilis (`*-release.apk`) terlebih dahulu sebelum jatuh kembali (*fallback*) ke aset lainnya.

---

## 5. Kriteria Penerimaan (Acceptance Criteria) & Pengujian

1. **Verifikasi Isolasi Kontainer:**
   * Saat aplikasi target dijalankan melalui `StubActivity`, pemeriksaan `ps -A` atau `ActivityManager.getRunningAppProcesses()` harus menunjukkan bahwa aplikasi berjalan di dalam proses `com.sandbox.ball:container`, bukan sebagai proses mandiri di luar *sandbox*.
2. **Verifikasi Intersepsi Identitas & VFS:**
   * Unit test dan *instrumentation test* (`androidTest`) yang memanggil `Settings.Secure.getString(resolver, ANDROID_ID)` serta membaca `/sys/class/net/wlan0/address` melalui `FileInputStream` di dalam kontainer harus menerima nilai dari `HardwareProfile` yang sedang aktif, bukan nilai perangkat asli.
3. **Verifikasi Thread-Safety VFS:**
   * Pengujian pembacaan `/proc/self/maps` secara paralel oleh 10 *thread* bersamaan selama 5 detik tidak boleh menghasilkan *crash* (`SIGSEGV`/`SIGABRT`) atau *file descriptor leak*.
4. **Verifikasi Build Rilis:**
   * GitHub Actions berhasil menghasilkan APK rilis yang telah di-*obfuscate* oleh R8 dan ditandatangani secara valid tanpa menyertakan bendera `FLAG_DEBUGGABLE`.