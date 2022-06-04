package mx.tecnm.ladm_u4_proyectoasistencia_18401083

import android.Manifest
import android.R
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.LocationManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Message
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.google.firebase.firestore.FirebaseFirestore
import mx.tecnm.ladm_u4_proyectoasistencia_18401083.Sockets.BServerSocket
import mx.tecnm.ladm_u4_proyectoasistencia_18401083.Util.BluetoothStateCustom
import mx.tecnm.ladm_u4_proyectoasistencia_18401083.databinding.ActivityMainBinding
import mx.tecnm.ladm_u4_proyectoasistencia_18401083.interfaces.OnHandlerMsg
import mx.tecnm.ladm_u4_proyectoasistencia_18401083.interfaces.OnSocketReceive
import java.io.*
import java.lang.Exception
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity(){
    lateinit var binding: ActivityMainBinding

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothDevices: MutableList<BluetoothDevice>
    private lateinit var arrayAdapter: ArrayAdapter<String>
    private lateinit var listDevicesNamed:MutableList<String>

    private val uuid: UUID = UUID.fromString("213154a9-aa1c-4ffb-b53a-ae02bd2b079a")

    private var sendReceiveMsg: SendReceiveMsg?=null

    private lateinit var locationManager: LocationManager

    private val REQUEST_ENABLE_BLUETOOTH = 111

    val baseRemota = FirebaseFirestore.getInstance()
    var listaId = ArrayList<String>()
    var listaDatos = ArrayList<String>()
    lateinit var fechaHoy:String
    lateinit var collectionHora:String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        fechaHoy  = ""
        val cal = GregorianCalendar.getInstance()
        fechaHoy = cal.get(Calendar.DAY_OF_MONTH).toString() +"-"+
        cal.get(Calendar.MONTH).toString() +"-"+
        cal.get(Calendar.YEAR)
        var tiempo = ""
        if (cal.get(Calendar.AM_PM).equals(1)) tiempo = "PM"
        else if (cal.get(Calendar.AM_PM).equals(0)) tiempo = "AM"
        var hora = cal.get(Calendar.HOUR)
        collectionHora = "${hora} ${tiempo} a ${hora+1} ${tiempo}"
        baseRemota.collection("ListaAlumnos").document(fechaHoy).collection(collectionHora)
            .addSnapshotListener { query, error ->

                if (error !=null){
                    //Si hubo error
                    AlertDialog.Builder(this)
                        .setMessage(error.message)
                        .show()
                    return@addSnapshotListener
                }


                listaId.clear()
                listaDatos.clear()

                for (documento in query!!){
                    var cadena = "${documento.getString("noControl")},${documento.getString("hora")}"
                    listaDatos.add(cadena)
                    listaId.add(documento.id.toString())
                }
                binding.lvAsistencias.adapter = ArrayAdapter<String>(this,
                    R.layout.simple_list_item_1, listaDatos)
            }
        binding.btnArchivo.setOnClickListener {
            var infoLista = "noControl,hora\n"

            (0..listaDatos.size-1).forEach {
                var separador = listaDatos.get(it).split(",")
                infoLista+=separador.get(0)+"," //noControl
                infoLista+=separador.get(1)+"\n" //hora
            }


            guardarArchivo(infoLista)
        }
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        listDevicesNamed = mutableListOf()
        bluetoothDevices = mutableListOf()

        arrayAdapter = ArrayAdapter(applicationContext,android.R.layout.simple_list_item_1,listDevicesNamed)
        if (!bluetoothAdapter.isEnabled){
            startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),REQUEST_ENABLE_BLUETOOTH)
        }

        binding.apply {

            btnListen.setOnClickListener {
                val bServerSocket = BServerSocket(bluetoothAdapter,uuid,object:OnHandlerMsg{
                    override fun onMsgGet(msg: Message) {
                        handler.sendMessage(msg)
                    }
                }, object:OnSocketReceive{
                    override fun onReceive(blueSocket: BluetoothSocket) {
                        sendReceiveMsg = SendReceiveMsg(blueSocket)
                        sendReceiveMsg!!.start()
                    }
                })
                bServerSocket.start()
            }
        }
    }



    private fun guardarArchivo(cadena:String) {
        var filename = "listaAlumnos_${fechaHoy}_${collectionHora}.cvs"
        val archivo = OutputStreamWriter(this.openFileOutput(filename,MODE_PRIVATE))
        try {

            archivo.write(cadena)
            archivo.flush()
            archivo.close()
            val sendIntent = Intent(Intent.ACTION_SEND)
            val file = File(this.getFilesDir(), "listaAlumnos_${fechaHoy}_${collectionHora}.cvs")
            sendIntent.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(this,
                "mx.tecnm.ladm_u4_proyectoasistencia_18401083.provider",file))
            sendIntent.type = "text/csv"
            startActivity(Intent.createChooser(sendIntent, "SHARE"))
        }catch (e:Exception){
            AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage(e.message).show()
        }
}


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode==REQUEST_ENABLE_BLUETOOTH){
            if (resultCode== RESULT_OK){
                Toast.makeText(applicationContext,"Bluetooth is enabled", Toast.LENGTH_SHORT).show()
            }else {
                Toast.makeText(applicationContext,"Bluetooth is cancelled", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private var handler: Handler = Handler { msg ->
        when (msg.what) {
            BluetoothStateCustom.STATE_LISTENING.state -> {
                binding.tvStatus.text = "Status: Escuchando"
            }
            BluetoothStateCustom.STATE_CONNECTED.state -> {
                binding.tvStatus.text = "Status: Conectado!"
                binding.tvStatus.setTextColor(Color.rgb(38, 135, 64))
            }
            BluetoothStateCustom.STATE_CONNECTING.state -> {
                binding.tvStatus.text = "Status: Conectando..."
                binding.tvStatus.setTextColor(Color.rgb(38, 77, 135))
            }
            BluetoothStateCustom.STATE_CONNECTION_FAILED.state -> {
                binding.tvStatus.text = "Status: Falló la conexión :("
                binding.tvStatus.setTextColor(Color.rgb(158, 30, 2))
            }
            BluetoothStateCustom.STATE_MESSAGE_RECEIVED.state -> {
                val bytesRec:ByteArray = msg.obj as ByteArray
                val str = String(bytesRec,0,msg.arg1)
                //Toast.makeText(applicationContext,"Bytes:"+msg.arg1,Toast.LENGTH_SHORT).show()
                binding.tvMessage.text = str
                agregarAsistencia(str)
            }

        }
        true
    }

    private fun agregarAsistencia(dato: String) {
        val info = dato.split("\n")
        val datos = hashMapOf(
            "noControl" to info.get(0),
            "hora" to info.get(1)
        )
        baseRemota.collection("ListaAlumnos").document(fechaHoy).collection(collectionHora)
            .add(datos)
            .addOnSuccessListener {
                //Si se pudo
                Toast.makeText(this,"Insertado correctamente en BD", Toast.LENGTH_LONG)
                    .show()
            }
            .addOnFailureListener {
                AlertDialog.Builder(this)
                    .setMessage(it.message)
                    .show()
            }
    }

    inner class SendReceiveMsg(var bluetoothSocket: BluetoothSocket):Thread(){
        private var inputStream: DataInputStream?
        private var outputStream: DataOutputStream?

        init {
            var tempIS: DataInputStream?=null
            var tempOS: DataOutputStream?=null
            try {
                tempIS = DataInputStream(bluetoothSocket.inputStream)
                tempOS = DataOutputStream(bluetoothSocket.outputStream)
            }catch (e: IOException){
                e.printStackTrace()
            }
            inputStream = tempIS
            outputStream = tempOS
        }

        override fun run() {
            var l = 0;
            if (!bluetoothSocket.isConnected)
                bluetoothSocket.close()
            else{
                inputStream?.let {
                    l = it.readInt()
                }
                if (l==1){
                    val buffer = ByteArray(1024)
                    var bytes = 0
                    inputStream?.let { iS ->
                        while (true){
                            //-1 = because not used param arg2
                            try {
                                bytes = iS.read(buffer)
                                handler.obtainMessage(BluetoothStateCustom.STATE_MESSAGE_RECEIVED.state,bytes,-1,buffer).sendToTarget()
                            }catch (e: Exception){
                                break
                            }
                        }

                    }
                }
            }
        }

    }
}