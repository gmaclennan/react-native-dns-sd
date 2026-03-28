package expo.modules.dnssd.test

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class NsdDiscoveryTest {

    companion object {
        private const val TAG = "NsdDiscoveryTest"
        private const val SERVICE_TYPE = "_dnssdtest._tcp."
        private const val DISCOVERY_TIMEOUT_SECONDS = 120L
        private const val REGISTRATION_TIMEOUT_SECONDS = 15L
        private const val CONNECT_TIMEOUT_MS = 10_000
    }

    private lateinit var context: Context
    private lateinit var nsdManager: NsdManager
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    @After
    fun tearDown() {
        try {
            discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
        } catch (_: Exception) {}
        try {
            registrationListener?.let { nsdManager.unregisterService(it) }
        } catch (_: Exception) {}
    }

    @Test
    fun nsdRegistrationWorks() {
        val latch = CountDownLatch(1)
        var registeredName: String? = null
        var error: String? = null

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "E2ETestReg"
            serviceType = SERVICE_TYPE
            setPort(12345)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(si: NsdServiceInfo, errorCode: Int) {
                error = "Registration failed with error code $errorCode"
                latch.countDown()
            }
            override fun onUnregistrationFailed(si: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceRegistered(si: NsdServiceInfo) {
                registeredName = si.serviceName
                Log.i(TAG, "Registration succeeded: ${si.serviceName}")
                latch.countDown()
            }
            override fun onServiceUnregistered(si: NsdServiceInfo) {}
        }

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        assertTrue("Registration timed out", latch.await(REGISTRATION_TIMEOUT_SECONDS, TimeUnit.SECONDS))
        assertNull("Registration error: $error", error)
        assertNotNull("Service name should not be null", registeredName)
        Log.i(TAG, "✓ nsdRegistrationWorks passed: $registeredName")
    }

    @Test
    fun nsdDiscoveryStarts() {
        val latch = CountDownLatch(1)
        var error: String? = null

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.i(TAG, "Discovery started for $serviceType")
                latch.countDown()
            }
            override fun onServiceFound(si: NsdServiceInfo) {}
            override fun onServiceLost(si: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                error = "Start discovery failed with error code $errorCode"
                latch.countDown()
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        assertTrue("Discovery start timed out", latch.await(10, TimeUnit.SECONDS))
        assertNull("Discovery start error: $error", error)
        Log.i(TAG, "✓ nsdDiscoveryStarts passed")
    }

    @Test
    fun discoverServicesFromOtherEmulators() {
        // Register our own service so we can filter it out
        val ownServiceName = "E2ETestDiscoverer"
        val ownRegLatch = CountDownLatch(1)

        val ownServiceInfo = NsdServiceInfo().apply {
            serviceName = ownServiceName
            serviceType = SERVICE_TYPE
            setPort(12346)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(si: NsdServiceInfo, errorCode: Int) { ownRegLatch.countDown() }
            override fun onUnregistrationFailed(si: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceRegistered(si: NsdServiceInfo) {
                Log.i(TAG, "Own service registered: ${si.serviceName}")
                ownRegLatch.countDown()
            }
            override fun onServiceUnregistered(si: NsdServiceInfo) {}
        }

        nsdManager.registerService(ownServiceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        assertTrue("Own service registration timed out", ownRegLatch.await(REGISTRATION_TIMEOUT_SECONDS, TimeUnit.SECONDS))

        // Now discover services from other emulators
        val otherServices = CopyOnWriteArrayList<String>()
        val foundTwoLatch = CountDownLatch(2)

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.i(TAG, "Discovery started for cross-emulator test")
            }

            override fun onServiceFound(si: NsdServiceInfo) {
                val name = si.serviceName
                Log.i(TAG, "Found service: $name")
                // Filter out our own service
                if (!name.startsWith(ownServiceName) && !otherServices.contains(name)) {
                    otherServices.add(name)
                    Log.i(TAG, "Found OTHER service: $name (total: ${otherServices.size})")
                    foundTwoLatch.countDown()
                }
            }

            override fun onServiceLost(si: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery start failed: $errorCode")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        val found = foundTwoLatch.await(DISCOVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        Log.i(TAG, "Discovered ${otherServices.size} other services: $otherServices")
        assertTrue(
            "Expected to discover at least 2 services from other emulators, found ${otherServices.size}: $otherServices",
            otherServices.size >= 2
        )
        Log.i(TAG, "✓ discoverServicesFromOtherEmulators passed")
    }

    @Test
    fun connectToDiscoveredService() {
        val resolvedServices = CopyOnWriteArrayList<NsdServiceInfo>()
        val foundLatch = CountDownLatch(1)

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.i(TAG, "Discovery started for connect test")
            }

            override fun onServiceFound(si: NsdServiceInfo) {
                val name = si.serviceName
                Log.i(TAG, "Found service for connect test: $name")
                // Look for services from other emulators (not from our test)
                if (!name.startsWith("E2ETest")) {
                    @Suppress("DEPRECATION")
                    nsdManager.resolveService(si, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                            Log.e(TAG, "Resolve failed for $name: $errorCode")
                        }

                        override fun onServiceResolved(info: NsdServiceInfo) {
                            Log.i(TAG, "Resolved: ${info.serviceName} at ${info.host}:${info.port}")
                            resolvedServices.add(info)
                            foundLatch.countDown()
                        }
                    })
                }
            }

            override fun onServiceLost(si: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery start failed: $errorCode")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        assertTrue("No service found to connect to", foundLatch.await(DISCOVERY_TIMEOUT_SECONDS, TimeUnit.SECONDS))

        val service = resolvedServices.first()
        @Suppress("DEPRECATION")
        val host = service.host
        val port = service.port

        Log.i(TAG, "Connecting to ${host.hostAddress}:$port")

        val socket = Socket()
        socket.connect(java.net.InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
        socket.soTimeout = CONNECT_TIMEOUT_MS

        val writer = PrintWriter(socket.getOutputStream(), true)
        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

        val testMessage = "hello-from-test-runner"
        writer.println(testMessage)
        val response = reader.readLine()

        socket.close()

        assertEquals("ECHO:$testMessage", response)
        Log.i(TAG, "✓ connectToDiscoveredService passed: got '$response'")
    }
}
