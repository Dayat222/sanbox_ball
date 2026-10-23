#ifndef STEALTH_SYSCALL_HOOK_H
#define STEALTH_SYSCALL_HOOK_H

#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <stdio.h>

#ifdef __cplusplus
extern "C" {
#endif

// Hook initialization
int stealth_install_hooks();
int stealth_uninstall_hooks();

// Anti-debugger bypass status
void stealth_set_antidebug_enabled(int enabled);
int stealth_is_antidebug_enabled();

// Hooked functions declarations
int hooked_open(const char* pathname, int flags, ...);
int hooked_openat(int dirfd, const char* pathname, int flags, ...);
FILE* hooked_fopen(const char* filename, const char* mode);
int hooked_access(const char* pathname, int mode);
int hooked_stat(const char* pathname, struct stat* statbuf);
int hooked_lstat(const char* pathname, struct stat* statbuf);
long hooked_ptrace(int request, pid_t pid, void* addr, void* data);

#ifdef __cplusplus
}
#endif

#endif // STEALTH_SYSCALL_HOOK_H
