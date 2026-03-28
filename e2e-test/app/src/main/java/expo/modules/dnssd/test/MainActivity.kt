package expo.modules.dnssd.test

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket

/**
 * Main activity that acts as an NSD service advertiser and TCP echo server.
 * Launched on emulators 2 and 3 to advertise services that emulator 1 discovers.
 *
 * Pass service name via intent extra:
 *   am start -n expo.modules.dnssd.test/.MainActivity --es service_name "MyService"
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "DnsSdE2ETest"
        private const val SERVICE_TYPE = "_dnssdtest._tcp."
    }

    private var nsdManager: NsdManager? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var serverSocket: ServerSocket? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val serviceName = intent.getStringExtra("service_name")
            ?: "DnsSdTest-${android.os.Build.SERIAL}"

        val statusText = findViewById<TextView>(R.id.statusText)
        val serviceNameText = findViewById<TextView>(R.id.serviceNameText)

        statusText.text = "Starting service: $serviceName"
        serviceNameText.text = "Service: $serviceName"

        // Start TCP echo server
        serverSocket = ServerSocket(0)
        val port = serverSocket!!.localPort
        Log.i(TAG, "Echo server started on port $port")

        Thread(Runnable {
            while (!serverSocket!!.isClosed) {
                try {
                    val socket = serverSocket!!.accept()
                    Thread(Runnable {
                        try {
                            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                            val writer = PrintWriter(socket.getOutputStream(), true)
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                writer.println("ECHO:$line")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Echo error", e)
                        } finally {
                            socket.close()
                        }
                    }, "echo-handler").apply { isDaemon = true }.start()
                } catch (e: Exception) {
                    if (!serverSocket!!.isClosed) {
                        Log.e(TAG, "Accept error", e)
                    }
                }
            }
        }, "echo-server").apply { isDaemon = true }.start()

        // Register NSD service
        nsdManager = getSystemService(NSD_SERVICE) as NsdManager
        val serviceInfo = NsdServiceInfo().apply {
            this.serviceName = serviceName
            serviceType = SERVICE_TYPE
            setPort(port)
            setAttribute("emulator", serviceName)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onRegistrationFailed(si: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Registration failed: $errorCode")
                runOnUiThread { statusText.text = "Registration failed: $errorCode" }
            }

            override fun onUnregistrationFailed(si: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Unregistration failed: $errorCode")
            }

            override fun onServiceRegistered(si: NsdServiceInfo) {
                Log.i(TAG, "Service registered: ${si.serviceName}")
                runOnUiThread {
                    statusText.text = "Registered: ${si.serviceName} on port $port"
                    serviceNameText.text = "Service: ${si.serviceName}"
                }
            }

            override fun onServiceUnregistered(si: NsdServiceInfo) {
                Log.i(TAG, "Service unregistered: ${si.serviceName}")
            }
        }

        nsdManager!!.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            registrationListener?.let { nsdManager?.unregisterService(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister", e)
        }
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close server", e)
        }
    }
}
