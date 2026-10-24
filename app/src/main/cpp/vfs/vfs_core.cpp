#include "vfs_core.h"
#include "../utils/log.h"

#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>
#include <fcntl.h>
#include <fstream>
#include <sstream>
#include <cstring>

namespace stealth {
namespace vfs {

static void ensureDirectory(const std::string& path) {
    size_t pos = 0;
    while ((pos = path.find('/', pos + 1)) != std::string::npos) {
        std::string sub = path.substr(0, pos);
        if (!sub.empty()) {
            mkdir(sub.c_str(), 0755);
        }
    }
    mkdir(path.c_str(), 0755);
}

VfsCore& VfsCore::getInstance() {
    static VfsCore instance;
    return instance;
}

void VfsCore::init(const std::string& virtualRootDir, const std::string& targetPkg,
                   const std::string& fakeMac, const std::string& fakeSerial) {
    std::lock_guard<std::mutex> lock(mutex_);
    virtualRootDir_ = virtualRootDir;
    targetPkg_ = targetPkg;
    fakeMac_ = fakeMac.empty() ? "02:00:00:00:00:00" : fakeMac;
    fakeSerial_ = fakeSerial.empty() ? "RF8M1234XYZ" : fakeSerial;

    ensureDirectory(virtualRootDir_);
    std::string fakeDir = virtualRootDir_ + "/fake_sys";
    ensureDirectory(fakeDir);

    fakeCpuInfoPath_ = fakeDir + "/cpuinfo";
    fakeVersionPath_ = fakeDir + "/version";
    fakeMacPath_ = fakeDir + "/mac_address";
    fakeStatusPath_ = fakeDir + "/proc_status";
    sanitizedMapsPath_ = fakeDir + "/proc_maps";

    generateSpoofedFiles();

    // Map sensitive proc and sys paths
    exactMappings_["/proc/cpuinfo"] = fakeCpuInfoPath_;
    exactMappings_["/proc/version"] = fakeVersionPath_;
    exactMappings_["/sys/class/net/wlan0/address"] = fakeMacPath_;
    exactMappings_["/sys/class/net/eth0/address"] = fakeMacPath_;
    exactMappings_["/proc/self/status"] = fakeStatusPath_;

    // Mount namespace & directory mapping:
    // When app requests /data/data/<pkg> or /data/user/0/<pkg>, redirect to container isolated path
    if (!targetPkg_.empty()) {
        std::string virtualDataDir = virtualRootDir_ + "/data/" + targetPkg_;
        ensureDirectory(virtualDataDir);
        prefixMappings_.push_back({"/data/data/" + targetPkg_, virtualDataDir});
        prefixMappings_.push_back({"/data/user/0/" + targetPkg_, virtualDataDir});
    }

    enabled_ = true;
    ALOGI("VfsCore initialized successfully for package: %s, root: %s",
          targetPkg_.c_str(), virtualRootDir_.c_str());
}

void VfsCore::addPathMapping(const std::string& virtualPath, const std::string& realPath) {
    std::lock_guard<std::mutex> lock(mutex_);
    exactMappings_[virtualPath] = realPath;
}

void VfsCore::addPrefixMapping(const std::string& virtualPrefix, const std::string& realPrefix) {
    std::lock_guard<std::mutex> lock(mutex_);
    prefixMappings_.push_back({virtualPrefix, realPrefix});
}

std::string VfsCore::resolvePath(const char* originalPath) {
    if (!originalPath || !enabled_) {
        return originalPath ? std::string(originalPath) : "";
    }

    std::lock_guard<std::mutex> lock(mutex_);
    std::string pathStr(originalPath);

    // Special interception for /proc/self/maps or /proc/<pid>/maps
    if (mapsFilterEnabled_ && (pathStr == "/proc/self/maps" || pathStr.find("/maps") != std::string::npos)) {
        std::string sanitized = sanitizeMapsFile();
        if (!sanitized.empty()) {
            return sanitized;
        }
    }

    // Check exact matches
    auto it = exactMappings_.find(pathStr);
    if (it != exactMappings_.end()) {
        return it->second;
    }

    // Check prefix matches
    for (const auto& pair : prefixMappings_) {
        const std::string& prefix = pair.first;
        if (pathStr.rfind(prefix, 0) == 0) {
            std::string sub = pathStr.substr(prefix.length());
            return pair.second + sub;
        }
    }

    return pathStr;
}

bool VfsCore::shouldRedirect(const char* originalPath) const {
    if (!originalPath || !enabled_) return false;
    std::string pathStr(originalPath);

    if (mapsFilterEnabled_ && (pathStr == "/proc/self/maps" || pathStr.find("/maps") != std::string::npos)) {
        return true;
    }

    std::lock_guard<std::mutex> lock(mutex_);
    if (exactMappings_.find(pathStr) != exactMappings_.end()) {
        return true;
    }

    for (const auto& pair : prefixMappings_) {
        if (pathStr.rfind(pair.first, 0) == 0) {
            return true;
        }
    }
    return false;
}

void VfsCore::generateSpoofedFiles() {
    // Generate fake /proc/cpuinfo
    std::ofstream cpuFile(fakeCpuInfoPath_);
    if (cpuFile.is_open()) {
        cpuFile << "Processor\t: AArch64 Processor rev 1 (aarch64)\n"
                << "processor\t: 0\n"
                << "BogoMIPS\t: 38.40\n"
                << "Features\t: fp asimd evtstrm aes pmull sha1 sha2 crc32 atomics fphp\n"
                << "CPU implementer\t: 0x51\n"
                << "CPU architecture: 8\n"
                << "CPU variant\t: 0x2\n"
                << "CPU part\t: 0x805\n"
                << "CPU revision\t: 14\n\n"
                << "Hardware\t: Qualcomm Technologies, Inc SM8550\n";
        cpuFile.close();
    }

    // Generate fake /proc/version
    std::ofstream verFile(fakeVersionPath_);
    if (verFile.is_open()) {
        verFile << "Linux version 5.15.123-android14-9-g8a3b4c (build-user@build-host) "
                << "(Android (10087015, based on r487747c) clang version 17.0.2) "
                << "#1 SMP PREEMPT Wed Jan 15 12:00:00 UTC 2026\n";
        verFile.close();
    }

    // Generate fake MAC address
    std::ofstream macFile(fakeMacPath_);
    if (macFile.is_open()) {
        macFile << fakeMac_ << "\n";
        macFile.close();
    }

    // Generate fake /proc/self/status with TracerPid: 0
    std::ofstream statusFile(fakeStatusPath_);
    if (statusFile.is_open()) {
        statusFile << "Name:\t" << (targetPkg_.empty() ? "app_process" : targetPkg_) << "\n"
                   << "Umask:\t0077\n"
                   << "State:\tS (sleeping)\n"
                   << "Tgid:\t1000\n"
                   << "Ngid:\t0\n"
                   << "Pid:\t1000\n"
                   << "PPid:\t500\n"
                   << "TracerPid:\t0\n"
                   << "Uid:\t10000\t10000\t10000\t10000\n"
                   << "Gid:\t10000\t10000\t10000\t10000\n";
        statusFile.close();
    }
}

std::string VfsCore::sanitizeMapsFile() {
    std::ifstream src("/proc/self/maps");
    if (!src.is_open()) {
        return "";
    }

    // Thread-safe atomic output per thread to prevent concurrent race conditions
    std::string tidPath = sanitizedMapsPath_ + "." + std::to_string(gettid());
    std::ofstream dst(tidPath, std::ios::trunc);
    if (!dst.is_open()) {
        src.close();
        return "";
    }

    std::string line;
    while (std::getline(src, line)) {
        // Strip references to sandbox engine, hooking libs, xposed, frida
        if (line.find("libstealth_engine.so") != std::string::npos ||
            line.find("com.sandbox.ball") != std::string::npos ||
            line.find("frida") != std::string::npos ||
            line.find("xposed") != std::string::npos ||
            line.find("substrate") != std::string::npos ||
            line.find("dexrepair") != std::string::npos) {
            continue; // Skip sensitive line
        }
        dst << line << "\n";
    }

    src.close();
    dst.close();
    return tidPath;
}

std::string VfsCore::getSpoofedStatusFile() {
    return fakeStatusPath_;
}

} // namespace vfs
} // namespace stealth
