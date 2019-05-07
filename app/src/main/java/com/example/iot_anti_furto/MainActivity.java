package com.example.iot_anti_furto;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.ColorDrawable;
import android.hardware.SensorEvent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    public static final int REQUEST_ENABLE_BT=1;
    ListView lv_paired_devices;
    Button btn_enviar_msg;
    Set<BluetoothDevice> set_pairedDevices;
    ArrayAdapter adapter_paired_devices;
    BluetoothAdapter bluetoothAdapter;
    public static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    public static final int MESSAGE_READ=0;
    public static final int MESSAGE_WRITE=1;
    public static final int CONNECTING=2;
    public static final int CONNECTED=3;
    public static final int NO_SOCKET_FOUND=4;


    public BluetoothSocket getSocket() {
        return socket;
    }

    public void setSocket(BluetoothSocket socket) {
        this.socket = socket;
    }

    private BluetoothSocket socket = null;


    String bluetooth_message="00";

    public static String MENSAGEM_ALERTA = "ALERTA";

    @SuppressLint("HandlerLeak")
    Handler mHandler=new Handler() {

        @Override
        public void handleMessage(Message msg_type) {
            super.handleMessage(msg_type);

            switch (msg_type.what){
                case MESSAGE_READ:

                    byte[] readbuf=(byte[])msg_type.obj;
                    String string_recieved = new String(readbuf);
                    Toast.makeText(getApplicationContext(),"Lendo mensagem recebida... " + string_recieved,Toast.LENGTH_SHORT).show();

                    //TODO do some task based on recieved string
                    if (MENSAGEM_ALERTA.equals(string_recieved)) {

                        Toast.makeText(getApplicationContext(),"ALERTA!",Toast.LENGTH_SHORT).show();

                        //TODO - Criar algum efeito visual para tocar junto com a sirene
                        //Toca o som de sirene
                        MediaPlayer mp = MediaPlayer.create(getApplicationContext(), R.raw.police_siren);
                        mp.start();

                        //Alterar cor para #C00808
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {

                                //Remover a lista de devices
                                LinearLayout linearLayoutMain = findViewById(R.id.main_linear_view);
                                final AnimationDrawable drawable = new AnimationDrawable();
                                final Handler handler = new Handler();

                                drawable.addFrame(new ColorDrawable(Color.RED), 400);
                                drawable.addFrame(new ColorDrawable(Color.BLUE), 400);
                                drawable.setOneShot(false);
                                linearLayoutMain.setBackground(drawable);
                                handler.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        drawable.start();
                                    }
                                }, 100);
                            }
                        });
                    }

                    break;
                case MESSAGE_WRITE:

                    if(msg_type.obj!=null){
                        ConnectedThread connectedThread=new ConnectedThread((BluetoothSocket)msg_type.obj);
                        connectedThread.write(bluetooth_message.getBytes());

                    }
                    break;

                case CONNECTED:
                    Toast.makeText(getApplicationContext(),"Dispositivo conectado.",Toast.LENGTH_SHORT).show();
                    break;

                case CONNECTING:
                    Toast.makeText(getApplicationContext(),"Conectando ao dispositivo...",Toast.LENGTH_SHORT).show();
                    break;

                case NO_SOCKET_FOUND:
                    Toast.makeText(getApplicationContext(),"Nenhum socket encontrado.",Toast.LENGTH_SHORT).show();
                    break;
            }
        }
    };



    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initialize_layout();
        initialize_bluetooth();
        start_accepting_connection();
        initialize_clicks();

        DetectorMovimento.getInstance(MainActivity.this.getApplicationContext()).addListener(new DetectorMovimento.Listener() {

            @Override
            public void onMotionDetected(SensorEvent event, float acceleration) {
                if (acceleration > 0.5) {
                    Toast.makeText(getApplicationContext(),"Movimento Detectado. Alertando dispositivo vigilante!",Toast.LENGTH_SHORT).show();
                    MainActivity.this.bluetooth_message = MainActivity.MENSAGEM_ALERTA;
                    mHandler.obtainMessage(MESSAGE_WRITE, MainActivity.this.getSocket()).sendToTarget();
                }
            }
        });
    }

    public void start_accepting_connection() {
        //call this on button click as suited by you

        AcceptThread acceptThread = new AcceptThread();
        acceptThread.start();
        Toast.makeText(getApplicationContext(),"Dispositivo também pronto para ser vigiado...",Toast.LENGTH_SHORT).show();
    }
    public void initialize_clicks() {

        lv_paired_devices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id)
            {
                Object[] objects = set_pairedDevices.toArray();
                BluetoothDevice device = (BluetoothDevice) objects[position];

                ConnectThread connectThread = new ConnectThread(device);
                connectThread.start();

                Toast.makeText(getApplicationContext(),"Dispositivo escolhido = "+device.getName(),Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void initialize_layout() {
        lv_paired_devices = (ListView)findViewById(R.id.lv_paired_devices);
        adapter_paired_devices = new ArrayAdapter(getApplicationContext(),R.layout.support_simple_spinner_dropdown_item);
        lv_paired_devices.setAdapter(adapter_paired_devices);
    }

    public void initialize_bluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            // Device doesn't support Bluetooth
            Toast.makeText(getApplicationContext(),"Seu dispositivo não suporta a tecnologia Bluetooth.",Toast.LENGTH_SHORT).show();
            finish();
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        else {
            set_pairedDevices = bluetoothAdapter.getBondedDevices();

            if (set_pairedDevices.size() > 0) {

                for (BluetoothDevice device : set_pairedDevices) {
                    String deviceName = device.getName();
                    String deviceHardwareAddress = device.getAddress(); // MAC address

                    adapter_paired_devices.add(device.getName() + "\n" + device.getAddress());
                }
            }
        }
    }


    public class AcceptThread extends Thread {
        private final BluetoothServerSocket serverSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord("NAME",MY_UUID);
            } catch (IOException e) { }
            serverSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket = null;

            // Keep listening until exception occurs or a socket is returned
            while (true) {
                try {
                    socket = serverSocket.accept();
                } catch (IOException e) {
                    break;
                }

                // If a connection was accepted
                if (socket != null) {
                    MainActivity.this.setSocket(socket);
                    // Do work to manage the connection (in a separate thread)
                    mHandler.obtainMessage(CONNECTED).sendToTarget();
                    ConnectedThread connectedThread = new ConnectedThread(socket);
                    connectedThread.start();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            //Remover a lista de devices
                            LinearLayout linearLayout = (LinearLayout)lv_paired_devices.getParent();
                            linearLayout.removeAllViews();

                            //Adicionar mensagem de modo vigiado
                            TextView valueTV = new TextView(MainActivity.this);
                            valueTV.setText("Modo Vigilante Ativado");
                            valueTV.setTextSize(50);
                            valueTV.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                            valueTV.setLayoutParams(
                                    new LinearLayout.LayoutParams(
                                            LinearLayout.LayoutParams.MATCH_PARENT,
                                            LinearLayout.LayoutParams.WRAP_CONTENT)
                            );

                            linearLayout.addView(valueTV);
                        }
                    });

                }
            }
        }
    }


    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket,
            // because mmSocket is final
            BluetoothSocket tmp = null;
            mmDevice = device;

            // Get a BluetoothSocket to connect with the given BluetoothDevice
            try {
                // MY_UUID is the app's UUID string, also used by the server code
                tmp = device.createRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) { }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it will slow down the connection
            bluetoothAdapter.cancelDiscovery();

            try {
                // Connect the device through the socket. This will block
                // until it succeeds or throws an exception
                mHandler.obtainMessage(CONNECTING).sendToTarget();

                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and get out
                    Toast.makeText(getApplicationContext(),"Não foi possível conectar ao dispositivo.",Toast.LENGTH_SHORT).show();
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                }
                return;
            }

            // Do work to manage the connection (in a separate thread)
            MainActivity.this.setSocket(mmSocket);
            DetectorMovimento.getInstance(MainActivity.this.getApplicationContext()).start();

            runOnUiThread(new Runnable() {
              @Override
              public void run() {

                                  //Remover a lista de devices
                LinearLayout linearLayout = (LinearLayout)lv_paired_devices.getParent();
                linearLayout.removeAllViews();

                //Adicionar mensagem de modo vigiado
                TextView valueTV = new TextView(MainActivity.this);
                valueTV.setText("Modo Anti-Furto Ativado.");
                valueTV.setTextSize(50);
                valueTV.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                valueTV.setLayoutParams(
                    new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT)
                    );

                linearLayout.addView(valueTV);
              }
            });

        }

        /** Will cancel an in-progress connection, and close the socket */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }
    private class ConnectedThread extends Thread {

        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams, using temp objects because
            // member streams are final
            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) { }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[6];  // buffer store for the stream
            int bytes; // bytes returned from read()

            // Keep listening to the InputStream until an exception occurs
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = mmInStream.read(buffer);
                    // Send the obtained bytes to the UI activity
                    mHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer).sendToTarget();

                } catch (IOException e) {
                    break;
                }
            }
        }

        /* Call this from the main activity to send data to the remote device */
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);
            } catch (IOException e) {
                Toast.makeText(getApplicationContext(),"Ocorreu um erro na tentativa de enviar os dados. Verifique os logs para mais detalhes. " +
                        e.getMessage(),Toast.LENGTH_LONG).show();
            }
        }

        /* Call this from the main activity to shutdown the connection */
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) { }
        }
    }
}