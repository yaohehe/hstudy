package com.hstudy.app.platform

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readValue
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDefaultRunLoopMode
import platform.Foundation.NSNetService
import platform.Foundation.NSNetServiceBrowser
import platform.Foundation.NSNetServiceBrowserDelegateProtocol
import platform.Foundation.NSNetServiceDelegateProtocol
import platform.Foundation.NSRunLoop
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.posix.AF_INET
import platform.posix.AF_INET6
import platform.posix.inet_ntoa
import platform.posix.inet_ntop
import platform.posix.sockaddr
import platform.posix.sockaddr_in
import platform.posix.sockaddr_in6

/**
 * iOS 设备发现：NSNetServiceBrowser 浏览 "_hstudy._tcp."（与 Android NsdManager 服务类型一致），
 * resolve 出 host/port 后回调 DiscoveredDevice（切回主线程）。
 * 需要 Info.plist：NSLocalNetworkUsageDescription + NSBonjourServices（已配置）。
 */
class IosDeviceDiscovery : DeviceDiscovery {

    private var browser: NSNetServiceBrowser? = null
    private var browserDelegate: BrowserDelegate? = null
    private var resolveDelegate: ResolveDelegate? = null
    private val resolving = mutableSetOf<NSNetService>()

    override fun startDiscovery(onFound: (DiscoveredDevice) -> Unit) {
        stopDiscovery()

        val resolve = ResolveDelegate { service ->
            resolving.remove(service)
            val host = hostFromAddresses(service.addresses) ?: ""
            val device = DiscoveredDevice(service.name, host, service.port.convert())
            dispatch_async(dispatch_get_main_queue()) { onFound(device) }
        }
        resolveDelegate = resolve

        val delegate = BrowserDelegate(
            onFoundService = { service ->
                resolving.add(service)
                service.delegate = resolve
                service.resolveWithTimeout(5.0)
            },
            onRemovedService = { service ->
                resolving.remove(service)
            }
        )
        browserDelegate = delegate

        val b = NSNetServiceBrowser()
        b.delegate = delegate
        b.scheduleInRunLoop(NSRunLoop.currentRunLoop, NSDefaultRunLoopMode)
        b.searchForServicesOfType(SERVICE_TYPE, "local.")
        browser = b
    }

    override fun stopDiscovery() {
        browser?.stop()
        browser?.delegate = null
        browser = null
        browserDelegate = null
        resolving.forEach {
            it.delegate = null
            it.stop()
        }
        resolving.clear()
        resolveDelegate = null
    }

    /** 从 NSData(sockaddr) 列表解析 IPv4/IPv6 地址字符串 */
    private fun hostFromAddresses(addresses: List<*>?): String? {
        addresses ?: return null
        for (item in addresses) {
            val data = item as? NSData ?: continue
            val bytes = data.bytes ?: continue
            val family = bytes.reinterpret<sockaddr>().pointed.sa_family.toInt()
            if (family == AF_INET) {
                val sin = bytes.reinterpret<sockaddr_in>()
                val text = inet_ntoa(sin.pointed.sin_addr.readValue()) ?: continue
                return text.toKString()
            } else if (family == AF_INET6) {
                val sin6 = bytes.reinterpret<sockaddr_in6>()
                val buf = ByteArray(64)
                val ok = buf.usePinned { pinned ->
                    inet_ntop(AF_INET6, sin6.pointed.sin6_addr.ptr, pinned.addressOf(0), 64u) != null
                }
                if (ok) return buf.toKString()
            }
        }
        return null
    }

    private class BrowserDelegate(
        private val onFoundService: (NSNetService) -> Unit,
        private val onRemovedService: (NSNetService) -> Unit
    ) : NSObject(), NSNetServiceBrowserDelegateProtocol {

        override fun netServiceBrowser(browser: NSNetServiceBrowser, didFindService: NSNetService, moreComing: Boolean) {
            onFoundService(didFindService)
        }

        override fun netServiceBrowser(browser: NSNetServiceBrowser, didRemoveService: NSNetService, moreComing: Boolean) {
            onRemovedService(didRemoveService)
        }
    }

    private class ResolveDelegate(
        private val onResolved: (NSNetService) -> Unit
    ) : NSObject(), NSNetServiceDelegateProtocol {

        override fun netServiceDidResolveAddress(sender: NSNetService) {
            onResolved(sender)
        }
    }

    companion object {
        const val SERVICE_TYPE = "_hstudy._tcp."
    }
}