#include "syscall_hook.h"
#include "../vfs/vfs_core.h"
#include "../utils/log.h"

#include <dlfcn.h>
#include <link.h>
#include <elf.h>
#include <sys/mman.h>
#include <unistd.h>
#include <atomic>
#include <cstdarg>
#include <cerrno>
#include <cstring>
#include <string>
#include <vector>

using stealth::vfs::VfsCore;

// Function pointer types
typedef int (*orig_open_t)(const char*, int, ...);
typedef int (*orig_openat_t)(int, const char*, int, ...);
typedef FILE* (*orig_fopen_t)(const char*, const char*);
typedef int (*orig_access_t)(const char*, int);
typedef int (*orig_stat_t)(const char*, struct stat*);
typedef int (*orig_lstat_t)(const char*, struct stat*);
typedef long (*orig_ptrace_t)(int, pid_t, void*, void*);
typedef int (*orig_system_property_get_t)(const char*, char*);

static orig_open_t   g_orig_open   = nullptr;
static orig_openat_t g_orig_openat = nullptr;
static orig_fopen_t  g_orig_fopen  = nullptr;
static orig_access_t g_orig_access = nullptr;
static orig_stat_t   g_orig_stat   = nullptr;
static orig_lstat_t  g_orig_lstat  = nullptr;
static orig_ptrace_t g_orig_ptrace = nullptr;
static orig_system_property_get_t g_orig_system_property_get = nullptr;

static std::mutex g_sysprop_mutex;
static std::string g_prop_model;
static std::string g_prop_manufacturer;
static std::string g_prop_brand;
static std::string g_prop_device;
static std::string g_prop_board;
static std::string g_prop_fingerprint;
static std::string g_prop_serial;

void stealth_set_system_properties(
    const char* model,
    const char* manufacturer,
    const char* brand,
    const char* device,
    const char* board,
    const char* fingerprint,
    const char* serial
) {
    std::lock_guard<std::mutex> lock(g_sysprop_mutex);
    g_prop_model = model ? model : "";
    g_prop_manufacturer = manufacturer ? manufacturer : "";
    g_prop_brand = brand ? brand : "";
    g_prop_device = device ? device : "";
    g_prop_board = board ? board : "";
    g_prop_fingerprint = fingerprint ? fingerprint : "";
    g_prop_serial = serial ? serial : "";
    ALOGI("Stealth Engine: Native system properties spoofed for model: %s", g_prop_model.c_str());
}

int hooked_system_property_get(const char* name, char* value) {
    if (!name || !value) return 0;

    {
        std::lock_guard<std::mutex> lock(g_sysprop_mutex);
        if (!g_prop_model.empty()) {
            if (strcmp(name, "ro.product.model") == 0 ||
                strcmp(name, "ro.product.vendor.model") == 0 ||
                strcmp(name, "ro.product.odm.model") == 0) {
                strncpy(value, g_prop_model.c_str(), 92);
                value[91] = '\0';
                return static_cast<int>(strlen(value));
            }
            if (strcmp(name, "ro.product.manufacturer") == 0 ||
                strcmp(name, "ro.product.vendor.manufacturer") == 0) {
                strncpy(value, g_prop_manufacturer.c_str(), 92);
                value[91] = '\0';
                return static_cast<int>(strlen(value));
            }
            if (strcmp(name, "ro.product.brand") == 0 ||
                strcmp(name, "ro.product.vendor.brand") == 0) {
                strncpy(value, g_prop_brand.c_str(), 92);
                value[91] = '\0';
                return static_cast<int>(strlen(value));
            }
            if (strcmp(name, "ro.product.device") == 0 ||
                strcmp(name, "ro.product.vendor.device") == 0) {
                strncpy(value, g_prop_device.c_str(), 92);
                value[91] = '\0';
                return static_cast<int>(strlen(value));
            }
            if (strcmp(name, "ro.product.board") == 0 ||
                strcmp(name, "ro.board.platform") == 0) {
                strncpy(value, g_prop_board.c_str(), 92);
                value[91] = '\0';
                return static_cast<int>(strlen(value));
            }
            if (strcmp(name, "ro.build.fingerprint") == 0 ||
                strcmp(name, "ro.vendor.build.fingerprint") == 0) {
                strncpy(value, g_prop_fingerprint.c_str(), 92);
                value[91] = '\0';
                return static_cast<int>(strlen(value));
            }
            if (strcmp(name, "ro.serialno") == 0 ||
                strcmp(name, "ro.boot.serialno") == 0) {
                strncpy(value, g_prop_serial.c_str(), 92);
                value[91] = '\0';
                return static_cast<int>(strlen(value));
            }
        }
    }

    if (!g_orig_system_property_get) {
        g_orig_system_property_get = reinterpret_cast<orig_system_property_get_t>(
            dlsym(RTLD_NEXT, "__system_property_get")
        );
    }
    if (g_orig_system_property_get) {
        return g_orig_system_property_get(name, value);
    }
    return 0;
}

static std::atomic<int> g_antidebug_enabled{1};
static std::atomic<int> g_hooks_installed{0};

void stealth_set_antidebug_enabled(int enabled) {
    g_antidebug_enabled.store(enabled, std::memory_order_release);
    ALOGI("Anti-debugger bypass set to: %d", enabled);
}

int stealth_is_antidebug_enabled() {
    return g_antidebug_enabled.load(std::memory_order_acquire);
}

int hooked_open(const char* pathname, int flags, ...) {
    mode_t mode = 0;
    if (flags & O_CREAT) {
        va_list args;
        va_start(args, flags);
        mode = static_cast<mode_t>(va_arg(args, int));
        va_end(args);
    }

    if (!pathname) {
        errno = EFAULT;
        return -1;
    }

    std::string resolved = VfsCore::getInstance().resolvePath(pathname);
    if (resolved != pathname) {
        ALOGD("open() redirected: %s -> %s", pathname, resolved.c_str());
    }

    if (!g_orig_open) {
        g_orig_open = reinterpret_cast<orig_open_t>(dlsym(RTLD_NEXT, "open"));
    }

    if (g_orig_open) {
        return (flags & O_CREAT) ? g_orig_open(resolved.c_str(), flags, mode)
                                 : g_orig_open(resolved.c_str(), flags);
    }
    return -1;
}

int hooked_openat(int dirfd, const char* pathname, int flags, ...) {
    mode_t mode = 0;
    if (flags & O_CREAT) {
        va_list args;
        va_start(args, flags);
        mode = static_cast<mode_t>(va_arg(args, int));
        va_end(args);
    }

    if (!pathname) {
        errno = EFAULT;
        return -1;
    }

    std::string resolved = VfsCore::getInstance().resolvePath(pathname);
    if (resolved != pathname) {
        ALOGD("openat() redirected: %s -> %s", pathname, resolved.c_str());
    }

    if (!g_orig_openat) {
        g_orig_openat = reinterpret_cast<orig_openat_t>(dlsym(RTLD_NEXT, "openat"));
    }

    if (g_orig_openat) {
        return (flags & O_CREAT) ? g_orig_openat(dirfd, resolved.c_str(), flags, mode)
                                 : g_orig_openat(dirfd, resolved.c_str(), flags);
    }
    return -1;
}

FILE* hooked_fopen(const char* filename, const char* mode) {
    if (!filename) {
        errno = EFAULT;
        return nullptr;
    }

    std::string resolved = VfsCore::getInstance().resolvePath(filename);
    if (resolved != filename) {
        ALOGD("fopen() redirected: %s -> %s", filename, resolved.c_str());
    }

    if (!g_orig_fopen) {
        g_orig_fopen = reinterpret_cast<orig_fopen_t>(dlsym(RTLD_NEXT, "fopen"));
    }

    if (g_orig_fopen) {
        return g_orig_fopen(resolved.c_str(), mode);
    }
    return nullptr;
}

int hooked_access(const char* pathname, int mode) {
    if (!pathname) {
        errno = EFAULT;
        return -1;
    }

    std::string resolved = VfsCore::getInstance().resolvePath(pathname);
    if (!g_orig_access) {
        g_orig_access = reinterpret_cast<orig_access_t>(dlsym(RTLD_NEXT, "access"));
    }

    if (g_orig_access) {
        return g_orig_access(resolved.c_str(), mode);
    }
    return -1;
}

int hooked_stat(const char* pathname, struct stat* statbuf) {
    if (!pathname) {
        errno = EFAULT;
        return -1;
    }

    std::string resolved = VfsCore::getInstance().resolvePath(pathname);
    if (!g_orig_stat) {
        g_orig_stat = reinterpret_cast<orig_stat_t>(dlsym(RTLD_NEXT, "stat"));
    }

    if (g_orig_stat) {
        return g_orig_stat(resolved.c_str(), statbuf);
    }
    return -1;
}

int hooked_lstat(const char* pathname, struct stat* statbuf) {
    if (!pathname) {
        errno = EFAULT;
        return -1;
    }

    std::string resolved = VfsCore::getInstance().resolvePath(pathname);
    if (!g_orig_lstat) {
        g_orig_lstat = reinterpret_cast<orig_lstat_t>(dlsym(RTLD_NEXT, "lstat"));
    }

    if (g_orig_lstat) {
        return g_orig_lstat(resolved.c_str(), statbuf);
    }
    return -1;
}

long hooked_ptrace(int request, pid_t pid, void* addr, void* data) {
    // 0 = PTRACE_TRACEME, 16 = PTRACE_ATTACH
    if (g_antidebug_enabled.load(std::memory_order_acquire)) {
        if (request == 0 /* PTRACE_TRACEME */ || request == 16 /* PTRACE_ATTACH */) {
            ALOGI("Stealth Engine: ptrace(%d, %d) intercepted! Returning fake success (0)", request, pid);
            return 0; // Fake success
        }
    }

    if (!g_orig_ptrace) {
        g_orig_ptrace = reinterpret_cast<orig_ptrace_t>(dlsym(RTLD_NEXT, "ptrace"));
    }

    if (g_orig_ptrace) {
        return g_orig_ptrace(request, pid, addr, data);
    }
    return 0;
}

#ifdef __LP64__
  #define STEALTH_ELF_R_SYM(i) ELF64_R_SYM(i)
#else
  #define STEALTH_ELF_R_SYM(i) ELF32_R_SYM(i)
#endif

struct PltHookTarget {
    const char* symbol_name;
    void* hook_func;
    void** orig_func_ptr;
};

static std::vector<PltHookTarget> get_hook_targets() {
    return {
        {"open", reinterpret_cast<void*>(hooked_open), reinterpret_cast<void**>(&g_orig_open)},
        {"open64", reinterpret_cast<void*>(hooked_open), reinterpret_cast<void**>(&g_orig_open)},
        {"openat", reinterpret_cast<void*>(hooked_openat), reinterpret_cast<void**>(&g_orig_openat)},
        {"openat64", reinterpret_cast<void*>(hooked_openat), reinterpret_cast<void**>(&g_orig_openat)},
        {"fopen", reinterpret_cast<void*>(hooked_fopen), reinterpret_cast<void**>(&g_orig_fopen)},
        {"fopen64", reinterpret_cast<void*>(hooked_fopen), reinterpret_cast<void**>(&g_orig_fopen)},
        {"access", reinterpret_cast<void*>(hooked_access), reinterpret_cast<void**>(&g_orig_access)},
        {"stat", reinterpret_cast<void*>(hooked_stat), reinterpret_cast<void**>(&g_orig_stat)},
        {"stat64", reinterpret_cast<void*>(hooked_stat), reinterpret_cast<void**>(&g_orig_stat)},
        {"lstat", reinterpret_cast<void*>(hooked_lstat), reinterpret_cast<void**>(&g_orig_lstat)},
        {"lstat64", reinterpret_cast<void*>(hooked_lstat), reinterpret_cast<void**>(&g_orig_lstat)},
        {"ptrace", reinterpret_cast<void*>(hooked_ptrace), reinterpret_cast<void**>(&g_orig_ptrace)},
        {"__system_property_get", reinterpret_cast<void*>(hooked_system_property_get), reinterpret_cast<void**>(&g_orig_system_property_get)}
    };
}

static bool is_page_strictly_relro(uintptr_t page_start, size_t page_size, ElfW(Addr) base, const ElfW(Phdr)* phdr, ElfW(Half) phnum) {
    uintptr_t page_end = page_start + page_size;
    for (ElfW(Half) i = 0; i < phnum; ++i) {
        if (phdr[i].p_type == PT_GNU_RELRO) {
            uintptr_t relro_start = base + phdr[i].p_vaddr;
            uintptr_t relro_end = relro_start + phdr[i].p_memsz;
            // The entire page must be strictly within the PT_GNU_RELRO boundaries.
            // If the page extends beyond relro_end, it overlaps with writable .data / .bss
            // and locking it to PROT_READ causes SIGSEGV when that data is written!
            if (page_start >= relro_start && page_end <= relro_end) {
                return true;
            }
        }
    }
    return false;
}

static int plt_hook_callback(struct dl_phdr_info* info, size_t /* size */, void* data) {
    if (!info || !info->dlpi_name || !data) return 0;
    const char* lib_name = info->dlpi_name;

    // Skip self, libc, libdl, and core Android system/ART runtime libraries.
    // Hooking core ART/system libraries causes internal VM deadlocks or fatal aborts!
    if (strstr(lib_name, "libstealth_engine.so") != nullptr ||
        strstr(lib_name, "libc.so") != nullptr ||
        strstr(lib_name, "libdl.so") != nullptr ||
        strstr(lib_name, "libm.so") != nullptr ||
        strstr(lib_name, "libart.so") != nullptr ||
        strstr(lib_name, "libart-compiler.so") != nullptr ||
        strstr(lib_name, "libopenjdk.so") != nullptr ||
        strstr(lib_name, "libopenjdkjvm.so") != nullptr ||
        strstr(lib_name, "libandroid_runtime.so") != nullptr ||
        strstr(lib_name, "liblog.so") != nullptr ||
        strstr(lib_name, "libbinder.so") != nullptr ||
        strstr(lib_name, "libutils.so") != nullptr) {
        return 0;
    }

    ElfW(Addr) base = info->dlpi_addr;
    const ElfW(Phdr)* phdr = info->dlpi_phdr;
    ElfW(Half) phnum = info->dlpi_phnum;

    // Find PT_DYNAMIC
    const ElfW(Dyn)* dynamic = nullptr;
    for (ElfW(Half) i = 0; i < phnum; ++i) {
        if (phdr[i].p_type == PT_DYNAMIC) {
            dynamic = reinterpret_cast<const ElfW(Dyn)*>(base + phdr[i].p_vaddr);
            break;
        }
    }
    if (!dynamic) return 0;

    const char* strtab = nullptr;
    const ElfW(Sym)* symtab = nullptr;
    const void* jmprel = nullptr;
    size_t plt_rel_sz = 0;
    ElfW(Word) plt_rel_type = 0;

    for (const ElfW(Dyn)* d = dynamic; d->d_tag != DT_NULL; ++d) {
        switch (d->d_tag) {
            case DT_STRTAB: {
                ElfW(Addr) ptr = d->d_un.d_ptr;
                strtab = reinterpret_cast<const char*>(ptr < base ? base + ptr : ptr);
                break;
            }
            case DT_SYMTAB: {
                ElfW(Addr) ptr = d->d_un.d_ptr;
                symtab = reinterpret_cast<const ElfW(Sym)*>(ptr < base ? base + ptr : ptr);
                break;
            }
            case DT_JMPREL: {
                ElfW(Addr) ptr = d->d_un.d_ptr;
                jmprel = reinterpret_cast<const void*>(ptr < base ? base + ptr : ptr);
                break;
            }
            case DT_PLTRELSZ:
                plt_rel_sz = d->d_un.d_val;
                break;
            case DT_PLTREL:
                plt_rel_type = d->d_un.d_val;
                break;
            default:
                break;
        }
    }

    if (!strtab || !symtab || !jmprel || plt_rel_sz == 0) {
        return 0;
    }

    long page_size = sysconf(_SC_PAGESIZE);
    if (page_size <= 0) page_size = 4096;

    auto* targets = reinterpret_cast<const std::vector<PltHookTarget>*>(data);

    if (plt_rel_type == DT_RELA) {
        const auto* rela = reinterpret_cast<const ElfW(Rela)*>(jmprel);
        size_t count = plt_rel_sz / sizeof(ElfW(Rela));
        for (size_t i = 0; i < count; ++i) {
            size_t sym_idx = STEALTH_ELF_R_SYM(rela[i].r_info);
            const char* name = strtab + symtab[sym_idx].st_name;
            for (const auto& target : *targets) {
                if (strcmp(name, target.symbol_name) == 0) {
                    void** got_entry = reinterpret_cast<void**>(base + rela[i].r_offset);
                    if (*got_entry != target.hook_func) {
                        void* page_start = reinterpret_cast<void*>(
                            reinterpret_cast<uintptr_t>(got_entry) & ~(page_size - 1));
                        if (mprotect(page_start, page_size, PROT_READ | PROT_WRITE) == 0) {
                            if (target.orig_func_ptr && !*(target.orig_func_ptr)) {
                                *(target.orig_func_ptr) = *got_entry;
                            }
                            *got_entry = target.hook_func;
                            __builtin___clear_cache(
                                reinterpret_cast<char*>(got_entry),
                                reinterpret_cast<char*>(got_entry) + sizeof(void*)
                            );
                            ALOGI("PLT Hooked %s in %s (GOT: %p)", name, lib_name, got_entry);

                            // SEC-03: Restore PROT_READ strictly for pages fully inside PT_GNU_RELRO
                            if (is_page_strictly_relro(reinterpret_cast<uintptr_t>(page_start), page_size, base, phdr, phnum)) {
                                mprotect(page_start, page_size, PROT_READ);
                            }
                        }
                    }
                    break;
                }
            }
        }
    } else if (plt_rel_type == DT_REL) {
        const auto* rel = reinterpret_cast<const ElfW(Rel)*>(jmprel);
        size_t count = plt_rel_sz / sizeof(ElfW(Rel));
        for (size_t i = 0; i < count; ++i) {
            size_t sym_idx = STEALTH_ELF_R_SYM(rel[i].r_info);
            const char* name = strtab + symtab[sym_idx].st_name;
            for (const auto& target : *targets) {
                if (strcmp(name, target.symbol_name) == 0) {
                    void** got_entry = reinterpret_cast<void**>(base + rel[i].r_offset);
                    if (*got_entry != target.hook_func) {
                        void* page_start = reinterpret_cast<void*>(
                            reinterpret_cast<uintptr_t>(got_entry) & ~(page_size - 1));
                        if (mprotect(page_start, page_size, PROT_READ | PROT_WRITE) == 0) {
                            if (target.orig_func_ptr && !*(target.orig_func_ptr)) {
                                *(target.orig_func_ptr) = *got_entry;
                            }
                            *got_entry = target.hook_func;
                            __builtin___clear_cache(
                                reinterpret_cast<char*>(got_entry),
                                reinterpret_cast<char*>(got_entry) + sizeof(void*)
                            );
                            ALOGI("PLT Hooked %s in %s (GOT: %p)", name, lib_name, got_entry);

                            // SEC-03: Restore PROT_READ strictly for pages fully inside PT_GNU_RELRO
                            if (is_page_strictly_relro(reinterpret_cast<uintptr_t>(page_start), page_size, base, phdr, phnum)) {
                                mprotect(page_start, page_size, PROT_READ);
                            }
                        }
                    }
                    break;
                }
            }
        }
    }

    return 0;
}

int stealth_apply_plt_hooks() {
    auto targets = get_hook_targets();
    dl_iterate_phdr(plt_hook_callback, &targets);
    ALOGI("Stealth Hook Engine: PLT/GOT scan and interception completed.");
    return 0;
}

int stealth_install_hooks() {
    if (g_hooks_installed.load(std::memory_order_acquire)) {
        stealth_apply_plt_hooks();
        return 0;
    }

    void* libc = dlopen("libc.so", RTLD_NOW);
    if (libc) {
        g_orig_open   = reinterpret_cast<orig_open_t>(dlsym(libc, "open"));
        g_orig_openat = reinterpret_cast<orig_openat_t>(dlsym(libc, "openat"));
        g_orig_fopen  = reinterpret_cast<orig_fopen_t>(dlsym(libc, "fopen"));
        g_orig_access = reinterpret_cast<orig_access_t>(dlsym(libc, "access"));
        g_orig_stat   = reinterpret_cast<orig_stat_t>(dlsym(libc, "stat"));
        g_orig_lstat  = reinterpret_cast<orig_lstat_t>(dlsym(libc, "lstat"));
        g_orig_ptrace = reinterpret_cast<orig_ptrace_t>(dlsym(libc, "ptrace"));
        g_orig_system_property_get = reinterpret_cast<orig_system_property_get_t>(dlsym(libc, "__system_property_get"));
    }

    // Apply PLT/GOT memory table redirection
    stealth_apply_plt_hooks();

    g_hooks_installed.store(1, std::memory_order_release);
    ALOGI("Stealth Hook Engine: Libc interception hooks installed.");
    return 0;
}

int stealth_uninstall_hooks() {
    g_hooks_installed.store(0, std::memory_order_release);
    ALOGI("Stealth Hook Engine: Libc interception hooks uninstalled.");
    return 0;
}
