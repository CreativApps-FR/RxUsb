package dev.creativapps.rxusb

import android.hardware.usb.UsbManager

/**
 * This class allows you to access the state of USB and communicate with USB accessories, in a reactive
 * way through RxJava libraries.
 *
 * Created : 13/06/2020.
 * @author Creativapps - Damien P.
 */
interface RxUsbManager {

    companion object {

        const val ACTION_USB_PERMISSION = "rx.usb_accessory.USB_PERMISSION"
        const val ACTION_USB_ACCESSORY_DETACHED = UsbManager.ACTION_USB_ACCESSORY_DETACHED
        const val ACTION_USB_ACCESSORY_ATTACHED = UsbManager.ACTION_USB_ACCESSORY_ATTACHED


    }

}