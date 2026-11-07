package com.hstudy.app.platform

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
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

    /**
     * 从 NSData(sockaddr) 列表解析 IPv4/IPv6 地址字符串。
     *
     * 实现说明：把 NSData 的字节整体拷进 Kotlin ByteArray 后自行格式化，而不是用
     * inet_ntoa/inet_ntop —— 后者要读取 sockaddr 里的嵌套结构字段（sin_addr / sin6_addr），
     * 其 cinterop 写法在 Windows 上无法编译验证，容易留下不可编译的代码。
     * Darwin 的 sockaddr 布局（sockaddr 前 1 字节 sa_len、次 1 字节 sa_family）：
     * - sockaddr_in ：[len, family, port(2), addr(4)]              → 地址偏移 4，4 字节
     * - sockaddr_in6：[len, family, port(2), flowinfo(4), addr(16)] → 地址偏移 8，16 字节
     * 输出与 Android 侧 `serviceInfo.host?.hostAddress` 同形（点分十进制 / RFC 5952 压缩 IPv6）。
     */
    private fun hostFromAddresses(addresses: List<*>?): String? {
        addresses ?: return null
        for (item in addresses) {
            val data = item as? NSData ?: continue
            val bytes = ByteArray(data.length.toInt())
            if (bytes.isEmpty()) continue
            bytes.usePinned { pinned -> data.getBytes(pinned.addressOf(0), data.length.toULong()) }
            val family = bytes[FAMILY_INDEX].toInt() and 0xFF
            val host = when (family) {
                AF_INET -> ipv4Text(bytes)
                AF_INET6 -> ipv6Text(bytes)
                else -> null
            }
            if (!host.isNullOrEmpty()) return host
        }
        return null
    }

    private fun ipv4Text(b: ByteArray): String? {
        if (b.size < IPV4_ADDR_OFFSET + 4) return null
        return (0 until 4).joinToString(".") { b[IPV4_ADDR_OFFSET + it].toInt() and 0xFF }
    }

    private fun ipv6Text(b: ByteArray): String? {
        if (b.size < IPV6_ADDR_OFFSET + 16) return null
        val groups = ArrayList<Int>(8)
        for (i in 0 until 8) {
            val hi = b[IPV6_ADDR_OFFSET + i * 2].toInt() and 0xFF
            val lo = b[IPV6_ADDR_OFFSET + i * 2 + 1].toInt() and 0xFF
            groups.add((hi shl 8) or lo)
        }
        // RFC 5952：最长的一段连续 0（≥2 组）压缩为 "::"，其余小写且不带前导 0
        var bestStart = -1
        var bestLen = 0
        var start = -1
        for (i in 0 until 8) {
            if (groups[i] == 0) {
                if (start < 0) start = i
                val len = i - start + 1
                if (len > bestLen) {
                    bestLen = len
                    bestStart = start
                }
            } else {
                start = -1
            }
        }
        if (bestLen < 2) return groups.joinToString(":") { it.toString(16) }
        val head = groups.take(bestStart).joinToString(":") { it.toString(16) }
        val tail = groups.drop(bestStart + bestLen).joinToString(":") { it.toString(16) }
        return "$head::$tail"
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

        /** Darwin sockaddr：[0]=sa_len，[1]=sa_family */
        private const val FAMILY_INDEX = 1
        private const val IPV4_ADDR_OFFSET = 4
        private const val IPV6_ADDR_OFFSET = 8
    }
}