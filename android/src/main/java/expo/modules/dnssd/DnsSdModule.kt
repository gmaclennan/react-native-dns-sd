package expo.modules.dnssd

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import java.net.Inet4Address
import java.net.Inet6Address

class DnsSdModule : Module() {
  companion object {
    private const val TAG = "DnsSdModule"
  }

  private var nsdManager: NsdManager? = null
  private var discoveryListener: NsdManager.DiscoveryListener? = null
  private var registrationListener: NsdManager.RegistrationListener? = null
  private var registeredServiceName: String? = null
  private var isDiscovering = false
  private var currentServiceType: String? = null

  private val context: Context
    get() = appContext.reactContext ?: throw IllegalStateException("React context is not available")

  private fun getNsdManager(): NsdManager {
    if (nsdManager == null) {
      nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }
    return nsdManager!!
  }

  override fun definition() = ModuleDefinition {
    Name("DnsSd")

    Events(
      "onServiceFound",
      "onServiceLost",
      "onError",
      "onDiscoveryStarted",
      "onDiscoveryStopped",
      "onServiceRegistered",
      "onServiceUnregistered"
    )

    Function("startDiscovery") { type: String ->
      startDiscovery(type)
    }

    Function("stopDiscovery") {
      stopDiscovery()
    }

    Function("registerService") { name: String, type: String, port: Int, txt: Map<String, String> ->
      registerService(name, type, port, txt)
    }

    Function("unregisterService") {
      unregisterService()
    }

    OnDestroy {
      teardown()
    }
  }

  private fun startDiscovery(type: String) {
    if (isDiscovering) {
      stopDiscovery()
    }

    currentServiceType = type

    discoveryListener = object : NsdManager.DiscoveryListener {
      override fun onDiscoveryStarted(serviceType: String) {
        Log.d(TAG, "Discovery started for $serviceType")
        isDiscovering = true
        sendEvent("onDiscoveryStarted", mapOf("type" to serviceType))
      }

      override fun onServiceFound(serviceInfo: NsdServiceInfo) {
        Log.d(TAG, "Service found: ${serviceInfo.serviceName}")
        resolveService(serviceInfo)
      }

      override fun onServiceLost(serviceInfo: NsdServiceInfo) {
        Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
        sendEvent("onServiceLost", mapOf(
          "name" to serviceInfo.serviceName,
          "type" to serviceInfo.serviceType
        ))
      }

      override fun onDiscoveryStopped(serviceType: String) {
        Log.d(TAG, "Discovery stopped for $serviceType")
        isDiscovering = false
        sendEvent("onDiscoveryStopped", mapOf("type" to serviceType))
      }

      override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
        Log.e(TAG, "Start discovery failed: error code $errorCode")
        isDiscovering = false
        sendEvent("onError", mapOf(
          "error" to "Start discovery failed with error code $errorCode",
          "domain" to serviceType
        ))
      }

      override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
        Log.e(TAG, "Stop discovery failed: error code $errorCode")
        sendEvent("onError", mapOf(
          "error" to "Stop discovery failed with error code $errorCode",
          "domain" to serviceType
        ))
      }
    }

    try {
      getNsdManager().discoverServices(type, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to start discovery", e)
      sendEvent("onError", mapOf("error" to (e.message ?: "Failed to start discovery")))
    }
  }

  private fun resolveService(serviceInfo: NsdServiceInfo) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      // API 34+: use resolveService with Executor
      getNsdManager().resolveService(serviceInfo, { it.run() }, object : NsdManager.ServiceInfoCallback {
        override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
          Log.e(TAG, "Service info callback registration failed: $errorCode")
        }

        override fun onServiceUpdated(info: NsdServiceInfo) {
          Log.d(TAG, "Service resolved (API 34+): ${info.serviceName}")
          emitResolvedService(info)
        }

        override fun onServiceLost() {
          // handled by discovery listener
        }

        override fun onServiceInfoCallbackUnregistered() {}
      })
    } else {
      // Legacy resolve
      @Suppress("DEPRECATION")
      getNsdManager().resolveService(serviceInfo, object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
          if (errorCode == NsdManager.FAILURE_ALREADY_ACTIVE) {
            // Retry after a short delay - NSD can only resolve one service at a time on older APIs
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
              resolveService(serviceInfo)
            }, 100)
          } else {
            Log.e(TAG, "Resolve failed for ${serviceInfo.serviceName}: error code $errorCode")
            sendEvent("onError", mapOf(
              "error" to "Resolve failed for ${serviceInfo.serviceName} with error code $errorCode"
            ))
          }
        }

        override fun onServiceResolved(info: NsdServiceInfo) {
          Log.d(TAG, "Service resolved: ${info.serviceName} at ${info.host}:${info.port}")
          emitResolvedService(info)
        }
      })
    }
  }

  private fun emitResolvedService(info: NsdServiceInfo) {
    val addresses = mutableListOf<String>()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      info.hostAddresses.forEach { addr ->
        addresses.add(addr.hostAddress ?: "")
      }
    } else {
      @Suppress("DEPRECATION")
      info.host?.hostAddress?.let { addresses.add(it) }
    }

    val txtRecord = mutableMapOf<String, String>()
    info.attributes?.forEach { (key, value) ->
      txtRecord[key] = value?.let { String(it, Charsets.UTF_8) } ?: ""
    }

    val fullName = "${info.serviceName}.${info.serviceType}local."

    sendEvent("onServiceFound", mapOf(
      "name" to info.serviceName,
      "fullName" to fullName,
      "host" to (addresses.firstOrNull { isIPv4(it) } ?: addresses.firstOrNull() ?: ""),
      "port" to info.port,
      "txt" to txtRecord,
      "addresses" to addresses
    ))
  }

  private fun isIPv4(address: String): Boolean {
    return address.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))
  }

  private fun stopDiscovery() {
    if (!isDiscovering || discoveryListener == null) return
    try {
      getNsdManager().stopServiceDiscovery(discoveryListener)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to stop discovery", e)
    }
    discoveryListener = null
    isDiscovering = false
  }

  private fun registerService(name: String, type: String, port: Int, txt: Map<String, String>) {
    val serviceInfo = NsdServiceInfo().apply {
      serviceName = name
      serviceType = type
      setPort(port)
      txt.forEach { (key, value) ->
        setAttribute(key, value)
      }
    }

    registrationListener = object : NsdManager.RegistrationListener {
      override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
        Log.e(TAG, "Registration failed: error code $errorCode")
        sendEvent("onError", mapOf(
          "error" to "Registration failed with error code $errorCode"
        ))
      }

      override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
        Log.e(TAG, "Unregistration failed: error code $errorCode")
        sendEvent("onError", mapOf(
          "error" to "Unregistration failed with error code $errorCode"
        ))
      }

      override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
        // The service name may have been changed by the system to avoid conflicts
        registeredServiceName = serviceInfo.serviceName
        Log.d(TAG, "Service registered: ${serviceInfo.serviceName}")

        sendEvent("onServiceRegistered", mapOf(
          "name" to serviceInfo.serviceName,
          "fullName" to "${serviceInfo.serviceName}.${serviceInfo.serviceType}local.",
          "host" to "",
          "port" to port,
          "txt" to txt,
          "addresses" to emptyList<String>()
        ))
      }

      override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
        Log.d(TAG, "Service unregistered: ${serviceInfo.serviceName}")
        sendEvent("onServiceUnregistered", mapOf(
          "name" to serviceInfo.serviceName,
          "type" to serviceInfo.serviceType
        ))
        registeredServiceName = null
      }
    }

    try {
      getNsdManager().registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to register service", e)
      sendEvent("onError", mapOf("error" to (e.message ?: "Failed to register service")))
    }
  }

  private fun unregisterService() {
    if (registrationListener == null) return
    try {
      getNsdManager().unregisterService(registrationListener)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to unregister service", e)
    }
    registrationListener = null
  }

  private fun teardown() {
    stopDiscovery()
    unregisterService()
    nsdManager = null
  }
}
