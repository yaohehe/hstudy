package com.hstudy.app.platform

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.ArrayDeque

/**
 * Android 设备发现：NSD 局域网发现（_hstudy._tcp.，与原 DeviceDiscoveryHelper 一致）
 * + 已绑定蓝牙设备名兜底。发现后 resolve 出 host/port，回调切回主线程。
 */
class AndroidDeviceDiscovery(private val context: Context) : DeviceDiscovery {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var onFound: ((DiscoveredDevice) -> Unit)? = null

    // 旧版 NsdManager 同一时刻只允许一个 resolve，用队列串行处理
    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private val foundNames = mutableSetOf<String>()

    override fun startDiscovery(onFound: (DiscoveredDevice) -> Unit) {
        stopDiscovery()
        this.onFound = onFound
        foundNames.clear()

        emitBondedBluetoothDevices(onFound)

        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                try {
                    if (serviceInfo.serviceType == SERVICE_TYPE && foundNames.add(serviceInfo.serviceName)) {
                        synchronized(resolveQueue) { resolveQueue.addLast(serviceInfo) }
                        resolveNext(nsdManager)
                    }
                } catch (_: Exception) {
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                foundNames.remove(serviceInfo.serviceName)
            }

            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            discoveryListener = listener
        } catch (_: Exception) {
        }
    }

    override fun stopDiscovery() {
        val listener = discoveryListener
        discoveryListener = null
        onFound = null
        synchronized(resolveQueue) { resolveQueue.clear() }
        if (listener != null) {
            try {
                val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
                nsdManager?.stopServiceDiscovery(listener)
            } catch (_: Exception) {
            }
        }
    }

    private fun resolveNext(nsdManager: NsdManager) {
        synchronized(resolveQueue) {
            if (resolving) return
            val next = resolveQueue.pollFirst() ?: return
            resolving = true
            try {
                nsdManager.resolveService(next, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        finishResolve(nsdManager)
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        try {
                            val host = serviceInfo.host?.hostAddress ?: ""
                            val device = DiscoveredDevice(
                                name = serviceInfo.serviceName,
                                address = host,
                                port = serviceInfo.port
                            )
                            mainHandler.post { onFound?.invoke(device) }
                        } catch (_: Exception) {
                        }
                        finishResolve(nsdManager)
                    }
                })
            } catch (_: Exception) {
                resolving = false
            }
        }
    }

    private fun finishResolve(nsdManager: NsdManager) {
        synchronized(resolveQueue) { resolving = false }
        resolveNext(nsdManager)
    }

    /** 已绑定蓝牙设备兜底（与原 getBondedBluetoothDevices 一致；port=-1 表示蓝牙通道） */
    private fun emitBondedBluetoothDevices(onFound: (DiscoveredDevice) -> Unit) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return
            val adapter = manager.adapter ?: return
            @Suppress("MissingPermission")
            val bonded = adapter.bondedDevices ?: return
            bonded.forEach { device ->
                @Suppress("MissingPermission")
                val name = device.name ?: device.address
                mainHandler.post { onFound(DiscoveredDevice(name, device.address, -1)) }
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    companion object {
        const val SERVICE_TYPE = "_hstudy._tcp."
    }
}