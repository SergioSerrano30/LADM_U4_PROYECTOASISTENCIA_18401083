package mx.tecnm.ladm_u4_proyectoasistencia_18401083.interfaces

import android.bluetooth.BluetoothSocket

interface OnSocketReceive {
    fun onReceive(blueSocket: BluetoothSocket)
}