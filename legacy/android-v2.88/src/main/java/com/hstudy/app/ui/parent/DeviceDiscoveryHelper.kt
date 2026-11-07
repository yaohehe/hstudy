package com.hstudy.app.ui.parent

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build

/**
 * 设备发现辅助 - 局域网 NSD 发现 + 蓝牙扫描
 * 注意：仅实现设备发现与配对确认，不含真实数据同步（那是后端服务的职责）
 */
object DeviceDiscoveryHelper {

    const val SERVICE_TYPE = "_hstudy._tcp."

    /** 检查蓝牙是否可用 */
    fun isBluetoothAvailable(context: Context): Boolean {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        return manager?.adapter != null
    }

    /** 获取已绑定的蓝牙设备列表（需要 BLUETOOTH_CONNECT 权限） */
    fun getBondedBluetoothDevices(context: Context): List<String> {
        return try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter: BluetoothAdapter = manager?.adapter ?: return emptyList()
            @Suppress("MissingPermission")
            adapter.bondedDevices?.map { it.name ?: it.address } ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    /**
     * 启动 NSD 局域网发现，发现到的设备名通过 onDeviceFound 回调
     * @return 用于停止发现的监听器
     */
    fun startNsdDiscovery(
        context: Context,
        onDeviceFound: (String) -> Unit,
        onError: (String) -> Unit
    ): NsdManager.DiscoveryListener? {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
            ?: return null
        // NSD 回调运行在 binder 后台线程，必须切回主线程才能安全更新 UI 状态
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                try {
                    if (serviceInfo.serviceType == SERVICE_TYPE) {
                        val name = serviceInfo.serviceName
                        mainHandler.post { onDeviceFound(name) }
                    }
                } catch (_: Exception) {
                }
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                mainHandler.post { onError("启动发现失败 (code=$errorCode)") }
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        return try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            listener
        } catch (e: Exception) {
            mainHandler.post { onError("发现服务异常: ${e.message}") }
            null
        }
    }

    /** 停止 NSD 发现 */
    fun stopNsdDiscovery(context: Context, listener: NsdManager.DiscoveryListener?) {
        if (listener == null) return
        try {
            val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
            nsdManager?.stopServiceDiscovery(listener)
        } catch (_: Exception) {
        }
    }

    /** 注册本机为 NSD 服务（让其他设备能发现自己），返回注册名 */
    fun registerNsdService(context: Context, deviceName: String, port: Int = 8888): String {
        val nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
            ?: return deviceName
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = deviceName
            serviceType = SERVICE_TYPE
            setPort(port)
        }
        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD,
                object : NsdManager.RegistrationListener {
                    override fun onServiceRegistered(info: NsdServiceInfo) {}
                    override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
                    override fun onServiceUnregistered(info: NsdServiceInfo) {}
                    override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
                })
        } catch (_: Exception) {
        }
        return deviceName
    }

    /** 需要申请的蓝牙权限（按系统版本）：仅读取已绑定设备，无需扫描/定位权限 */
    fun bluetoothPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+：访问已绑定设备需 BLUETOOTH_CONNECT 运行时权限
            arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            // Android 11 及以下：BLUETOOTH 为安装时自动授予的普通权限，无需运行时申请
            emptyArray()
        }
    }
}
