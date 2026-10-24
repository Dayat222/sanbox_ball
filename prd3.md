# Product Requirements Document (PRD): Perbaikan Komprehensif Arsitektur, Stabilitas Native, & Isolasi Kontainer (v1.2.0)

**Nama Proyek:** Stealth Sandbox Engine (`sanbox_ball`)  
**Repositori Target:** `github.com/Dayat222/sanbox_ball`  
**Status Dokumen:** Spesifikasi Teknis Lengkap (Full Remediation Specification)  
**Versi Basis Audit:** `v1.1.0-release`  
**Target Rilis:** `v1.2.0-release`  
**Target Platform:** Android 5.0 (API 21) $\rightarrow$ Android 14 (API 34), Compile SDK 35

---

## 1. Ringkasan Eksekutif & Latar Belakang Masalah
Pada iterasi `v1.1.0-release`, proyek **Stealth Sandbox Engine** telah berhasil mengimplementasikan struktur dasar *PLT/GOT hooking*, sanitasi berkas `/proc/self/maps` berbasis ID *thread* (`gettid()`), pemalsuan properti `android.os.Build` melalui JNI, serta *pipeline* kompilasi rilis di GitHub Actions.

Namun, hasil audit kode secara menyeluruh terhadap `v1.1.0-release` menemukan **6 masalah teknis kritis** yang menyebabkan aplikasi berisiko gagal dikompilasi (*build failure*), mengalami *crash* saat dijalankan (`UnsatisfiedLinkError` dan `SIGSEGV`), serta gagal mengisolasi aplikasi target di dalam proses `:container`. Dokumen PRD lengkap ini merinci spesifikasi perbaikan tingkat kode untuk setiap komponen agar siap digunakan secara penuh.

---

## 2. Daftar Matriks Masalah & Target Perbaikan (Remediation Matrix)

| Kode ID | Komponen / Berkas Terkait | Masalah Teknis pada v1.1.0 | Solusi Arsitektur pada v1.2.0 | Prioritas |
| :--- | :--- | :--- | :--- | :--- |
| **FIX-01** | `app/src/main/cpp/CMakeLists.txt` | Berkas `CMakeLists.txt` belum tersedia di dalam repositori sedangkan `build.gradle.kts` memintanya. | Membuat `CMakeLists.txt` lengkap untuk mengkompilasi `stealth_engine.cpp`, `vfs_core.cpp`, dan `syscall_hook.cpp`. | **Blocker (P0)** |
| **FIX-02** | `stealth_engine.h` & `stealth_engine.cpp` | Fungsi JNI `nativeApplyPltHooks` dan `nativeSetBuildProp` berada di luar blok `extern "C"`, memicu *C++ Name Mangling* dan `UnsatisfiedLinkError`. | Mendeklarasikan seluruh fungsi JNI di dalam `extern "C"` pada `stealth_engine.h` dan `stealth_engine.cpp`. | **Blocker (P0)** |
| **FIX-03** | `app/src/main/cpp/hook/syscall_hook.cpp` | Pemanggilan `mprotect(..., PROT_READ)` setelah menimpa `.got.plt` membuat segmen `.data` di halaman memori yang sama kehilangan akses tulis (`SIGSEGV`). | Mengubah izin penguncian kembali menjadi `PROT_READ | PROT_WRITE` serta menyempurnakan resolusi pointer `DT_STRTAB`/`DT_SYMTAB`. | **Kritis (P0)** |
| **FIX-04** | `StubActivity.kt`, `ContainerInstrumentation.kt`, `ContainerContext.kt` | Tombol *Launch* masih memanggil `startActivity(launchIntent)` standar ke OS utama dan belum memuat `Activity` dari `DexClassLoader` kontainer. | Menginjeksi `ContainerContext` ke `Activity` target dan membajak siklus hidup peluncuran di `ContainerInstrumentation` agar berjalan di dalam proses `:container`. | **Kritis (P0)** |
| **FIX-05** | `BinderProxyManager.kt`, `SettingsSecureHook.kt`, `WifiHook.kt` | Intersepsi `IPackageManager` belum menyaring daftar paket, serta layanan `wifi` (`IWifiManager`) dan `Settings.Secure` belum terhubung ke *proxy*. | Melengkapi *handler* `IPackageManager`, menambahkan *Binder Proxy* untuk layanan `wifi`, dan memasang intersepsi `Settings.Secure` (`sNameValueCache`). | **Tinggi (P1)** |
| **FIX-06** | `vfs_core.cpp`, `.gitignore`, `local.properties` | File sementara `proc_maps.<tid>` menumpuk di penyimpanan, dan `local.properties` lokal ikut terunggah ke repositori. | Menambahkan pembersihan berkas `proc_maps.*` saat inisialisasi VFS dan menghapus `local.properties` dari indeks Git. | **Menengah (P2)** |

---

## 3. Spesifikasi Teknis Perbaikan Lapisan Native C++ (NDK)

### 3.1. Pembuatan Konfigurasi Build Native (`FIX-01`)
* **Lokasi Berkas:** `app/src/main/cpp/CMakeLists.txt`
* **Spesifikasi:**
  * Menggunakan `cmake_minimum_required(VERSION 3.22.1)`.
  * Mendefinisikan pustaka bersama (*shared library*) bernama `stealth_engine`.
  * Mendaftarkan tiga berkas sumber utama:
    1. `stealth_engine.cpp`
    2. `vfs/vfs_core.cpp`
    3. `hook/syscall_hook.cpp`
  * Mengatur direktori *header* (`target_include_directories`) untuk `.`, `vfs`, `hook`, dan `utils`.
  * Menambahkan *compiler flags* `-fvisibility=hidden`, `-Wall`, `-Wextra`, serta `-Oz` untuk meminimalkan ukuran biner dan menyembunyikan simbol internal dari pemindaian tabel simbol.
  * Menautkan pustaka sistem Android: `android`, `log`, dan `dl`.

### 3.2. Perbaikan Deklarasi Simbol JNI & C++ Name Mangling (`FIX-02`)
* **Lokasi Berkas:** `app/src/main/cpp/stealth_engine.h` & `app/src/main/cpp/stealth_engine.cpp`
* **Analisis Akar Masalah:**
  Pada `v1.1.0`, fungsi `Java_com_sandbox_ball_core_nativebridge_NativeBridge_nativeApplyPltHooks` dan `Java_com_sandbox_ball_core_nativebridge_NativeBridge_nativeSetBuildProp` ditulis di bagian bawah `stealth_engine.cpp` tanpa deklarasi di dalam blok `extern "C"` pada `stealth_engine.h`. Kompiler Clang C++ akan mengubah nama simbol fungsi tersebut menjadi format *mangled* (contoh: `_Z69Java_com_sandbox...`), sehingga mesin virtual ART Android gagal menemukannya saat dipanggil dari `NativeBridge.kt`.
* **Spesifikasi Perbaikan:**
  1. Tambahkan deklarasi prototipe kedua fungsi tersebut di dalam blok `extern "C" { ... }` pada `stealth_engine.h`:
     ```cpp
     JNIEXPORT jboolean JNICALL
     Java_com_sandbox_ball_core_nativebridge_NativeBridge_nativeApplyPltHooks(
             JNIEnv* env, jobject thiz);

     JNIEXPORT jboolean JNICALL
     Java_com_sandbox_ball_core_nativebridge_NativeBridge_nativeSetBuildProp(
             JNIEnv* env, jobject thiz, jstring fieldName, jstring fieldValue);
     ```
  2. Bungkus seluruh implementasi fungsi JNI di dalam `stealth_engine.cpp` dengan blok `extern "C" { ... }` untuk menjamin tidak ada fungsi JNI yang terlewat di masa mendatang.

### 3.3. Perbaikan Proteksi Memori PLT/GOT Hooking (`FIX-03`)
* **Lokasi Berkas:** `app/src/main/cpp/hook/syscall_hook.cpp`
* **Analisis Akar Masalah:**
  1. Setelah mengganti nilai `*got_entry = target.hook_func;`, kode memanggil `mprotect(page_start, page_size, PROT_READ);`. Jika halaman memori (4096 byte) tempat `.got.plt` berada juga memuat variabel global yang dapat ditulis (`.data` atau `.bss`), pencabutan izin `PROT_WRITE` akan memicu `SIGSEGV` (*Segmentation Fault*) seketika saat pustaka menulis ke variabel globalnya.
  2. Perhitungan alamat tabel simbol pada beberapa arsitektur Android (khususnya Android API 21–23 vs API 24+) dapat memiliki `d->d_un.d_ptr` yang sudah berupa alamat absolut atau masih berupa *offset* relatif terhadap `info->dlpi_addr`.
* **Spesifikasi Perbaikan:**
  1. Setelah memodifikasi `*got_entry`, biarkan halaman memori tetap memiliki izin `PROT_READ | PROT_WRITE` atau panggil `__builtin___clear_cache` untuk menyinkronkan *instruction/data cache* CPU:
     ```cpp
     if (mprotect(page_start, page_size, PROT_READ | PROT_WRITE) == 0) {
         if (target.orig_func_ptr && !*(target.orig_func_ptr)) {
             *(target.orig_func_ptr) = *got_entry;
         }
         *got_entry = target.hook_func;
         __builtin___clear_cache(
             reinterpret_cast<char*>(got_entry),
             reinterpret_cast<char*>(got_entry) + sizeof(void*)
         );
     }
     ```
  2. Tambahkan juga intersepsi untuk simbol fungsi varian 64-bit di Android (`open64`, `openat64`, `fopen64`, `stat64`, `lstat64`) yang diarahkan ke *handler* yang sama, karena NDK modern sering memetakan panggilan file I/O ke varian 64-bit secara otomatis.

### 3.4. Pembersihan Berkas Sementara VFS (`FIX-06`)
* **Lokasi Berkas:** `app/src/main/cpp/vfs/vfs_core.cpp`
* **Spesifikasi Perbaikan:**
  * Pada `VfsCore::init()`, bersihkan seluruh berkas `proc_maps.<tid>` sisa sesi sebelumnya di dalam direktori `fake_sys` menggunakan `opendir`/`readdir`/`unlink` agar penyimpanan tidak penuh setelah penggunaan berulang.
  * Pada `VfsCore::shouldRedirect()`, pindahkan `std::lock_guard<std::mutex> lock(mutex_);` ke baris paling atas sebelum membaca `mapsFilterEnabled_` agar pembacaan status konsisten di semua *thread*.

---

## 4. Spesifikasi Teknis Perbaikan Container Runtime (`FIX-04`)

### 4.1. Penyempurnaan Injeksi Konteks & Pemuatan Activity (`StubActivity.kt` & `ContainerInstrumentation.kt`)
* **Analisis Akar Masalah:**
  Pada `v1.1.0`, ketika tombol `btn_container_launch` ditekan di `StubActivity.kt`, kode memanggil:
  ```kotlin
  val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
  startActivity(launchIntent)
  ```
  Karena `ContainerInstrumentation.execStartActivity` hanya meneruskan `intent` tersebut ke `base` tanpa memodifikasi komponen target, sistem operasi Android membuka aplikasi asli di luar kontainer.
* **Spesifikasi Perbaikan:**
  1. **Resolusi Activity Utama dari APK Kontainer (`StubActivity.kt`):**
     * Gunakan `packageManager.getPackageArchiveInfo(apkPath, PackageManager.GET_ACTIVITIES)` untuk membaca daftar `ActivityInfo` langsung dari berkas `base.apk` yang ada di dalam kontainer (`/sandbox_vfs/apps/<pkg>/base.apk`).
     * Ambil nama kelas `Activity` utama (atau dari `getLaunchIntentForPackage(pkg)?.component?.className`).
  2. **Peluncuran Internal di Proses `:container`:**
     * Buat kelas `ContainerHostActivity` (atau gunakan mode *dynamic view hosting* di dalam `StubActivity`) yang berjalan di proses `:container`.
     * Di dalam `ContainerInstrumentation.kt`:
       * Simpan referensi ke `ContainerContext`, `DexClassLoader`, dan nama paket target yang sedang aktif.
       * Saat `execStartActivity` menangkap `Intent` yang menuju ke paket target (`intent.component?.packageName == activeTargetPkg`), simpan nama kelas `Activity` asli ke dalam `intent.putExtra("_stealth_real_activity", realClassName)` dan arahkan komponen `Intent` ke `StubActivity` / `ContainerHostActivity` agar tetap berada di dalam proses `:container`.
       * Override metode `newActivity(cl: ClassLoader?, className: String?, intent: Intent?)`: jika `intent` memiliki atribut `_stealth_real_activity`, muat kelas `Activity` tersebut menggunakan `containerClassLoader.loadClass(realActivityName).newInstance() as Activity`.
       * Override metode `callActivityOnCreate(activity: Activity?, icicle: Bundle?)`: sebelum memanggil `super.callActivityOnCreate`, suntikkan `ContainerContext` sebagai `mBase` dari `activity` menggunakan refleksi pada kelas `ContextWrapper` (`mBaseField.set(activity, containerContext)`), serta sesuaikan `mResources` pada `ContextThemeWrapper` agar sumber daya visual (`Resources` & `AssetManager`) milik aplikasi target termuat dengan benar.

---

## 5. Spesifikasi Teknis Perbaikan Lapisan Middleware & Binder IPC (`FIX-05`)

### 5.1. Penyempurnaan `PackageManagerInvocationHandler` (`BinderProxyManager.kt`)
* **Analisis Akar Masalah:**
  Saat ini `PackageManagerInvocationHandler` baru mencegat `getInstallerPackageName` dan `getPackageInfo`. Metode `getInstalledPackages`, `getInstalledApplications`, dan `queryIntentActivities` belum disaring menggunakan fungsi yang sudah dibuat di `PackageManagerHook.kt`.
* **Spesifikasi Perbaikan:**
  Tambahkan penanganan hasil kembalian (*return value filtering*) pada `PackageManagerInvocationHandler.invoke()`:
  * Untuk `getInstalledPackages`: jika hasil kembalian dari `invokeReal` berupa `ParceledListSlice` (standar internal Android AIDL), ambil daftar di dalamnya melalui refleksi metode `getList()`, saring menggunakan `PackageManagerHook.filterPackageInfoList()`, lalu bungkus kembali ke dalam konstruktor `ParceledListSlice(filteredList)`. Jika berupa `List<PackageInfo>`, saring langsung.
  * Untuk `getInstalledApplications`: lakukan hal serupa menggunakan `PackageManagerHook.filterApplicationInfoList()`.
  * Untuk `queryIntentActivities`: saring hasil menggunakan `PackageManagerHook.filterResolveInfoList()`.
  * Untuk `getInstallSourceInfo` (Android 11 / API 30+): selain `getInstallerPackageName`, Android 11 ke atas menggunakan `getInstallSourceInfo`. Pastikan metode ini juga mengembalikan objek `InstallSourceInfo` dengan *installing/initiating package name* bernilai `"com.android.vending"`.

### 5.2. Penambahan Intersepsi Layanan Wi-Fi (`BinderProxyManager.kt` & `WifiHook.kt`)
* **Spesifikasi Perbaikan:**
  * Di dalam `BinderProxyManager.hookServiceManager()`, tambahkan penyadapan untuk layanan `"wifi"`:
    ```kotlin
    hookServiceBinder(smClass, sCache, "wifi") { realBinder ->
        createWifiProxyBinder(realBinder, profile)
    }
    ```
  * Buat `createWifiInterfaceProxy` untuk antarmuka `android.net.wifi.IWifiManager`:
    * Saat metode `getConnectionInfo` dipanggil, panggil objek asli terlebih dahulu untuk mendapatkan instance `WifiInfo`, lalu gunakan refleksi pada `WifiInfo` untuk mengubah `mMacAddress` menjadi `WifiHook.getMacAddress()`, `mBSSID` menjadi `WifiHook.getBSSID()`, dan `mIpAddress` menjadi `WifiHook.getIpAddressInt()`.
    * Saat metode `getFactoryMacAddresses` dipanggil, kembalikan `arrayOf(WifiHook.getMacAddress())`.

### 5.3. Penambahan Intersepsi `Settings.Secure` (`SettingsSecureHook.kt`)
* **Analisis Akar Masalah:**
  Metode `SettingsSecureHook.intercept()` belum pernah dipanggil oleh sistem ketika aplikasi target menjalankan `Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)`.
* **Spesifikasi Perbaikan:**
  * Tambahkan metode `installInMemoryHook(profile: HardwareProfile)` di dalam `SettingsSecureHook.kt`.
  * Gunakan refleksi pada kelas `android.provider.Settings$Secure`:
    1. Ambil *field* statis `sNameValueCache` pada kelas `Settings$Secure`.
    2. Di dalam objek `NameValueCache` tersebut, akses *field* `mValues` (berupa `HashMap<String, String>` atau `ArrayMap<String, String>`).Jika masih `null`, inisialisasi dengan peta baru, lalu masukkan pasangan kunci-nilai:
       * `Settings.Secure.ANDROID_ID` $\rightarrow$ `profile.androidId`
       * `"bluetooth_name"` $\rightarrow$ `"${profile.brand.uppercase()} ${profile.model}"`
       * `"device_name"` $\rightarrow$ `profile.model`
    3. Panggil `SettingsSecureHook.installInMemoryHook(profile)` setiap kali `SettingsSecureHook.setProfile(profile)` dijalankan, sehingga setiap pembacaan `Settings.Secure.getString()` langsung mengambil nilai dari *in-memory cache* milik `Settings.Secure` tanpa perlu melakukan kueri IPC ke `SettingsProvider` OS utama.

---

## 6. Spesifikasi DevSecOps, Pembersihan Repositori, & Pengujian (`FIX-06`)

### 6.1. Pembersihan Berkas Konfigurasi Lokal (`.gitignore`)
* **Masalah:** Berkas `local.properties` yang memuat `sdk.dir=C\:\\Users\\DAY01\\android-sdk` masih terdapat di dalam struktur proyek.
* **Spesifikasi Perbaikan:**
  1. Hapus berkas `local.properties` dari repositori.
  2. Buat atau perbarui berkas `.gitignore` di direktori *root* proyek agar mencakup:
     ```gitignore
     *.iml
     .gradle
     /local.properties
     /.idea/
     .DS_Store
     /build
     /captures
     .externalNativeBuild
     .cxx
     app/build/
     ```

### 6.2. Optimasi Konfigurasi NDK & Gradle (`app/build.gradle.kts`)
* Pada blok `buildTypes.release`, tambahkan konfigurasi pengupasan simbol *debug* native agar pustaka `libstealth_engine.so` di dalam `app-release.apk` bersih dari metadata fungsi lokal:
  ```kotlin
  ndk {
      debugSymbolLevel = "NONE"
  }
  ```

### 6.3. Penambahan Unit Test Verifikasi (`app/src/test/`)
* Perbarui `BinderProxyManagerTest.kt` dan tambahkan pengujian untuk memastikan:
  1. `SettingsSecureHook.intercept(Settings.Secure.ANDROID_ID, "real_id")` selalu mengembalikan `androidId` milik `HardwareProfile` aktif.
  2. `WifiHook.getMacAddress()`, `WifiHook.getBSSID()`, dan `WifiHook.getSSID()` mengembalikan nilai sesuai profil yang dipasang.
  3. `PackageManagerHook.filterPackageInfoList()` secara akurat membuang paket terlarang (`com.sandbox.ball`, Magisk, Xposed) dan mempertahankan paket aplikasi target.

---

## 7. Daftar Periksa Kriteria Penerimaan (Acceptance Criteria Checklist)

Sebelum merilis tag versi `v1.2.0`, seluruh kriteria berikut wajib terpenuhi:
- [ ] **AC-01 (Build Native):** Berkas `app/src/main/cpp/CMakeLists.txt` tersedia di repositori dan `./gradlew assembleRelease assembleDebug` berhasil menyelesaikan kompilasi C++ tanpa *error*.
- [ ] **AC-02 (JNI Linkage):** Seluruh fungsi di `NativeBridge.kt` (`nativeInitEngine`, `nativeApplyPltHooks`, `nativeSetBuildProp`, dll.) memiliki padanan `extern "C"` di `stealth_engine.h` dan `stealth_engine.cpp` serta tidak melempar `UnsatisfiedLinkError`.
- [ ] **AC-03 (Memory Stability):** Pemanggilan `nativeApplyPltHooks()` menggunakan `mprotect(..., PROT_READ | PROT_WRITE)` dan tidak memicu `SIGSEGV` pada saat aplikasi berjalan.
- [ ] **AC-04 (Active Hooks):** `SettingsSecureHook` menyuntikkan nilai `android_id` ke `sNameValueCache`, serta `BinderProxyManager` memfilter `getInstalledPackages`, `getInstalledApplications`, `queryIntentActivities`, dan memalsukan informasi `IWifiManager`.
- [ ] **AC-05 (Container Isolation):** Peluncuran aplikasi dari `StubActivity` menggunakan `ContainerInstrumentation` dan `ContainerContext` di dalam proses `:container`.
- [ ] **AC-06 (Clean Repo):** Berkas `local.properties` tidak lagi ada di dalam pohon direktori Git.