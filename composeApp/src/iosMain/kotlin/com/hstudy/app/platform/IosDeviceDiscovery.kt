@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.hstudy.app.platform

import platform.Foundation.NSDefaultRunLoopMode
import platform.Foundation.NSNetService
import platform.Foundation.NSNetServiceBrowser
import platform.Foundation.NSNetServiceBrowserDelegateProtocol
import platform.Foundation.NSNetServiceDelegateProtocol
import platform.Foundation.NSRunLoop
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

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
            val device = DiscoveredDevice(service.name, hostOf(service), service.port.convert())
            dispatch_async(dispatch_get_main_queue()) { onFound(device) }
        }
        resolveDelegate = resolve

        val delegate = BrowserDelegate { service ->
            resolving.add(service)
            service.delegate = resolve
            service.resolveWithTimeout(5.0)
        }
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
     * 主机名取 Bonjour 名称（如 "iPhone-8e21.local."）：iOS 上 getaddrinfo 会经 mDNSResponder
     * 解析 .local，因此 TCP 连接可用，语义与 Android 侧 host?.hostAddress 一致。
     * 不解析 sockaddr 原始字节：cinterop 未暴露 -[NSData getBytes:length:]，
     * 而 NSNetServiceBrowser 的 didFind/didRemove 两个回调在 Kotlin 侧签名冲突，只能实现其一。
     */
    private fun hostOf(service: NSNetService): String =
        service.hostName?.takeIf { it.isNotBlank() } ?: service.name

    private class BrowserDelegate(
        private val onFoundService: (NSNetService) -> Unit
    ) : NSObject(), NSNetServiceBrowserDelegateProtocol {

        // 只实现 didFindService：协议里 didFindService/didRemoveService 映射到同一个 Kotlin 方法名，
        // 同时重写会触发 "Conflicting overloads"；服务下线由下次 startDiscovery 前清空列表兜底。
        override fun netServiceBrowser(
            browser: NSNetServiceBrowser,
            didFindService: NSNetService,
            moreComing: Boolean
        ) {
            onFoundService(didFindService)
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
