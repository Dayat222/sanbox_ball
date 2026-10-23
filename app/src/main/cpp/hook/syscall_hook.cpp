#include "syscall_hook.h"
#include "../vfs/vfs_core.h"
#include "../utils/log.h"

#include <dlfcn.h>
#include <cstdarg>
#include <cerrno>
#include <cstring>
#include <string>

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

int stealth_install_hooks() {
    if (g_hooks_installed) return 0;

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

    g_hooks_installed = 1;
    ALOGI("Stealth Hook Engine: Libc interception hooks installed.");
    return 0;
}

int stealth_uninstall_hooks() {
    g_hooks_installed = 0;
    ALOGI("Stealth Hook Engine: Libc interception hooks uninstalled.");
    return 0;
}
