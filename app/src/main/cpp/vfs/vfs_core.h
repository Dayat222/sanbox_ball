#ifndef STEALTH_VFS_CORE_H
#define STEALTH_VFS_CORE_H

#include <string>
#include <unordered_map>
#include <mutex>
#include <vector>
#include <atomic>

namespace stealth {
namespace vfs {

class VfsCore {
public:
    static VfsCore& getInstance();

    void init(const std::string& virtualRootDir, const std::string& targetPkg,
              const std::string& fakeMac, const std::string& fakeSerial);

    void addPathMapping(const std::string& virtualPath, const std::string& realPath);
    void addPrefixMapping(const std::string& virtualPrefix, const std::string& realPrefix);

    std::string resolvePath(const char* originalPath);
    bool shouldRedirect(const char* originalPath) const;

    void generateSpoofedFiles();
    std::string sanitizeMapsFile();
    std::string getSpoofedStatusFile();

    bool isEnabled() const { return enabled_.load(std::memory_order_acquire); }
    void setEnabled(bool enabled) { enabled_.store(enabled, std::memory_order_release); }

    bool isMapsFilterEnabled() const { return mapsFilterEnabled_.load(std::memory_order_acquire); }
    void setMapsFilterEnabled(bool enabled) { mapsFilterEnabled_.store(enabled, std::memory_order_release); }

    std::string getVirtualRootDir() const { return virtualRootDir_; }
    std::string getTargetPackage() const { return targetPkg_; }

private:
    VfsCore() = default;
    ~VfsCore() = default;
    VfsCore(const VfsCore&) = delete;
    VfsCore& operator=(const VfsCore&) = delete;

    // Atomic flags to prevent data races across multiple threads (SEC-05)
    std::atomic<bool> enabled_{false};
    std::atomic<bool> mapsFilterEnabled_{true};

    std::string virtualRootDir_;
    std::string targetPkg_;
    std::string fakeMac_;
    std::string fakeSerial_;

    mutable std::mutex mutex_;
    std::unordered_map<std::string, std::string> exactMappings_;
    std::vector<std::pair<std::string, std::string>> prefixMappings_;

    std::string fakeCpuInfoPath_;
    std::string fakeVersionPath_;
    std::string fakeMacPath_;
    std::string fakeStatusPath_;
    std::string sanitizedMapsPath_;
};

} // namespace vfs
} // namespace stealth

#endif // STEALTH_VFS_CORE_H
