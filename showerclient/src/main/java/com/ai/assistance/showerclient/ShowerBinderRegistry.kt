package com.ai.assistance.showerclient

import android.os.IBinder
import com.ai.assistance.shower.IShowerService

/**
 * Global registry for the active Shower Binder service.
 *
 * Host apps should update this from their broadcast receiver when the
 * Shower server process publishes a new Binder.
 */
object ShowerBinderRegistry {

    private const val TAG = "ShowerBinderRegistry"

    @Volatile
    private var service: IShowerService? = null

    fun setService(newService: IShowerService?) {
        service = newService
        val alive = newService?.asBinder()?.isBinderAlive == true
        ShowerLog.d(TAG, "setService: service=$newService alive=$alive")
    }

    fun getService(): IShowerService? = service

    fun hasAliveService(): Boolean {
        val binder: IBinder? = service?.asBinder()
        val alive = binder?.isBinderAlive == true
        ShowerLog.d(TAG, "hasAliveService: binder=$binder alive=$alive")
        return alive
    }
}
