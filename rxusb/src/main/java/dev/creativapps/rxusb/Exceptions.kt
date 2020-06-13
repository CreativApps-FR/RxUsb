package dev.creativapps.rxusb

import android.hardware.usb.UsbAccessory

/* RxUsb related exceptions that can be thrown while using the library */

class AccessoryPermissionDeniedException(usbAccessory: UsbAccessory?) :
    Exception("Permission denied for USB accessory : $usbAccessory")