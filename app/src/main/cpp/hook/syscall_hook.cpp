#include "syscall_hook.h"
#include "../vfs/vfs_core.h"
#include "../utils/log.h"

#include <dlfcn.h>
#include <link.h>
#include <elf.h>
#include <sys/mman.h>
#include <unistd.h>
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

static orig_open_t   g_orig_open   = nullptr;
static orig_openat_t g_orig_openat = nullptr;
static orig_fopen_t  g_orig_fopen  = nullptr;
static orig_access_t g_orig_access = nullptr;
static orig_stat_t   g_orig_stat   = nullptr;
static orig_lstat_t  g_orig_lstat  = nullptr;
static orig_ptrace_t g_orig_ptrace = nullptr;

static int g_antidebug_enabled = 1;
static int g_hooks_installed = 0;

void stealth_set_antidebug_enabled(int enabled) {
    g_antidebug_enabled = enabled;
    ALOGI("Anti-debugger bypass set to: %d", enabled);
}

int stealth_is_antidebug_enabled() {
    return g_antidebug_enabled;
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
    if (g_antidebug_enabled) {
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
        {"openat", reinterpret_cast<void*>(hooked_openat), reinterpret_cast<void**>(&g_orig_openat)},
        {"fopen", reinterpret_cast<void*>(hooked_fopen), reinterpret_cast<void**>(&g_orig_fopen)},
        {"access", reinterpret_cast<void*>(hooked_access), reinterpret_cast<void**>(&g_orig_access)},
        {"stat", reinterpret_cast<void*>(hooked_stat), reinterpret_cast<void**>(&g_orig_stat)},
        {"lstat", reinterpret_cast<void*>(hooked_lstat), reinterpret_cast<void**>(&g_orig_lstat)},
        {"ptrace", reinterpret_cast<void*>(hooked_ptrace), reinterpret_cast<void**>(&g_orig_ptrace)}
    };
}

static int plt_hook_callback(struct dl_phdr_info* info, size_t /* size */, void* data) {
    if (!info || !info->dlpi_name || !data) return 0;
    const char* lib_name = info->dlpi_name;

    // Skip self and libc/libdl to prevent circular hooks
    if (strstr(lib_name, "libstealth_engine.so") != nullptr ||
        strstr(lib_name, "libc.so") != nullptr ||
        strstr(lib_name, "libdl.so") != nullptr) {
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
                        mprotect(page_start, page_size, PROT_READ | PROT_WRITE);
                        if (target.orig_func_ptr && !*(target.orig_func_ptr)) {
                            *(target.orig_func_ptr) = *got_entry;
                        }
                        *got_entry = target.hook_func;
                        mprotect(page_start, page_size, PROT_READ);
                        ALOGI("PLT Hooked %s in %s", name, lib_name);
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
                        mprotect(page_start, page_size, PROT_READ | PROT_WRITE);
                        if (target.orig_func_ptr && !*(target.orig_func_ptr)) {
                            *(target.orig_func_ptr) = *got_entry;
                        }
                        *got_entry = target.hook_func;
                        mprotect(page_start, page_size, PROT_READ);
                        ALOGI("PLT Hooked %s in %s", name, lib_name);
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
    if (g_hooks_installed) {
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
    }

    // Apply PLT/GOT memory table redirection
    stealth_apply_plt_hooks();

    g_hooks_installed = 1;
    ALOGI("Stealth Hook Engine: Libc interception hooks installed.");
    return 0;
}

int stealth_uninstall_hooks() {
    g_hooks_installed = 0;
    ALOGI("Stealth Hook Engine: Libc interception hooks uninstalled.");
    return 0;
}
