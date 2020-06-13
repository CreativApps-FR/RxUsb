package dev.creativapps.rxusb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbAccessory
import android.hardware.usb.UsbManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.subjects.PublishSubject

/**
 * This class allows you to access the state of USB and communicate with USB accessories, in a reactive
 * way through RxJava capabilities.
 *
 * Created : 13/06/2020.
 * @author CreativApps - Damien P.
 */
interface RxUsbManager {

    companion object {

        const val ACTION_USB_PERMISSION = "rx.usb_accessory.USB_PERMISSION"
        const val ACTION_USB_ACCESSORY_DETACHED = UsbManager.ACTION_USB_ACCESSORY_DETACHED
        const val ACTION_USB_ACCESSORY_ATTACHED = UsbManager.ACTION_USB_ACCESSORY_ATTACHED

        /**
         * Create a new instance of a [RxUsbManager] which is [Lifecycle] aware
         * of the linked Activity/Fragment.
         */
        fun createManager(context: Context, lifecycle: Lifecycle): RxUsbManager {
            val manager = RxUsbManagerImpl(context, lifecycle)
            lifecycle.addObserver(manager)

            return manager
        }

    }

    /**
     * Emits data when an [UsbAccessory] is attached to the devices using AOA protocol.
     * This is a hot [Observable], it won't re-emit previously attached accessories ; to know
     * if some [UsbAccessory] are already connected, use [scanAttachedAccessories] instead.
     *
     * You must ALWAYS use [hasPermission] before using the [UsbAccessory] in any way.
     */
    fun listenAttachedAccessoryEvents(): Observable<UsbAccessory>

    /**
     * Emits data when an [UsbAccessory] is detached from the devices using AOA protocol.
     * This is a hot [Observable] as well.
     */
    fun listenDetachedAccessoryEvents(): Observable<UsbAccessory>

    /**
     * Emits data when [UsbAccessory] has been granted by user. Data are emitted only if
     * caller used [requestPermission] => result flow.
     *
     * Can throw :
     * - [IllegalStateException] if no accessory returned
     * - [AccessoryPermissionDeniedException] is permission is denied by user
     */
    fun listenGrantedAccessoryEvents(): Observable<UsbAccessory>

    /**
     * Scan the USB connections to see if any [UsbAccessory] is already attached
     * to the Android device.
     * Provides an empty [List] if no accessories where founds.
     */
    fun scanAttachedAccessories(): Single<List<UsbAccessory>>

    /**
     * Tell us if the [UsbAccessory] can be used as it is.
     */
    fun hasPermission(usbAccessory: UsbAccessory): Boolean

    /**
     * Request permission, through a [PendingIntent] / [BroadcastReceiver] mechanism,
     * to use the given [UsbAccessory] ; state of the permission is provided through
     * [listenGrantedAccessoryEvents].
     * Emits [Completable.complete] when the question is asked.
     *
     * If the user has not already granted permission, trigger and AlertDialog to ask.
     */
    fun requestPermission(usbAccessory: UsbAccessory): Completable

}

/**
 * Actual implementation of [RxUsbManager] which is [LifecycleObserver] to start/stop listening
 * to context.
 */
private class RxUsbManagerImpl(private val context: Context, lifecycle: Lifecycle) :
    RxUsbManager, LifecycleObserver {

    // region PROPERTIES

    private val attachedPublisher: PublishSubject<UsbAccessory> = PublishSubject.create()
    private val detachedPublisher: PublishSubject<UsbAccessory> = PublishSubject.create()
    private lateinit var permissionPublisher: PublishSubject<UsbAccessory>

    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val permissionIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(RxUsbManager.ACTION_USB_PERMISSION),
        PendingIntent.FLAG_UPDATE_CURRENT
    )

    private val usbBroadcastReceiver: BroadcastReceiver

    private var isPermissionRequestPending = false

    // endregion

    // region INIT

    init {
        usbBroadcastReceiver = object : BroadcastReceiver() {

            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {

                    // Return from permission request
                    RxUsbManager.ACTION_USB_PERMISSION -> {
                        // Lock the process => avoid double processing
                        synchronized(this) {
                            val accessory =
                                intent.getParcelableExtra<UsbAccessory>(UsbManager.EXTRA_ACCESSORY)
                            val isPermissionGranted = intent.getBooleanExtra(
                                UsbManager.EXTRA_PERMISSION_GRANTED,
                                false
                            )

                            if (isPermissionGranted) {
                                if (accessory != null) {
                                    permissionPublisher.onNext(accessory)
                                } else {
                                    permissionPublisher.onError(
                                        IllegalStateException("Provided UsbAccessory is null")
                                    )
                                }
                            } else {
                                permissionPublisher.onError(
                                    AccessoryPermissionDeniedException(accessory)
                                )
                            }

                            isPermissionRequestPending = false
                        }
                    }

                    // USB cable is plugged in
                    RxUsbManager.ACTION_USB_ACCESSORY_ATTACHED -> {
                        synchronized(this) {
                            val accessory =
                                intent.getParcelableExtra<UsbAccessory>(UsbManager.EXTRA_ACCESSORY)
                            if (accessory != null) {
                                attachedPublisher.onNext(accessory)
                            }
                        }
                    }

                    // USB cable is plugged out
                    RxUsbManager.ACTION_USB_ACCESSORY_DETACHED -> {
                        synchronized(this) {
                            val accessory =
                                intent.getParcelableExtra<UsbAccessory>(UsbManager.EXTRA_ACCESSORY)
                            if (accessory != null) {
                                detachedPublisher.onNext(accessory)
                            }
                        }
                    }

                }
            }
        }

        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            startListening()
        }
    }

    // endregion

    // region INTERFACE IMPLEMENTATION

    override fun listenAttachedAccessoryEvents(): Observable<UsbAccessory> {
        return attachedPublisher
    }

    override fun listenDetachedAccessoryEvents(): Observable<UsbAccessory> {
        return detachedPublisher
    }

    override fun listenGrantedAccessoryEvents(): Observable<UsbAccessory> {
        return permissionPublisher
    }

    override fun scanAttachedAccessories(): Single<List<UsbAccessory>> {
        return Single.create { emitter ->
            val accessories = usbManager.accessoryList
            if (accessories != null) {
                val list = accessories.toList().distinct()
                emitter.onSuccess(list)
            } else {
                emitter.onSuccess(emptyList())
            }
        }
    }

    override fun hasPermission(usbAccessory: UsbAccessory): Boolean {
        return usbManager.hasPermission(usbAccessory)
    }

    override fun requestPermission(usbAccessory: UsbAccessory): Completable {
        if (isPermissionRequestPending) {
            return Completable.complete()
        } else {
            // Avoid terminated state if onError/Exception is triggered
            permissionPublisher = PublishSubject.create()

            return Completable.create { emitter ->
                usbManager.requestPermission(usbAccessory, permissionIntent)
                isPermissionRequestPending = true
                emitter.onComplete()
            }
        }
    }

    // endregion

    // region METHODS

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    private fun startListening() {

        // Listen USB events through context
        val filter = IntentFilter()
        filter.addAction(RxUsbManager.ACTION_USB_PERMISSION)
        filter.addAction(RxUsbManager.ACTION_USB_ACCESSORY_ATTACHED)
        filter.addAction(RxUsbManager.ACTION_USB_ACCESSORY_DETACHED)

        context.registerReceiver(usbBroadcastReceiver, filter)

    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    private fun stopListening() {
        context.unregisterReceiver(usbBroadcastReceiver)
    }

    // endregion
}