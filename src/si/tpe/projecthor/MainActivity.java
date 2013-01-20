package si.tpe.projecthor;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.Spinner;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Button;
import android.widget.NumberPicker;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.view.View;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothSocket;
import java.util.Set;
import java.util.UUID;
import java.lang.Thread;
import java.io.IOException;
import java.io.OutputStream;

public class MainActivity extends Activity implements OnItemSelectedListener {

	// Déclarations de variables

	// Constantes

	private static final int SUCCEEDED = 1, FAILED = 0;
	private static final String DEVICE_NAME = "ArchYvon-0"; // Le nom du périphérique bluetooth
	private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // Sert à identifier l'application lors de la connexion bluetooth

	// Bluetooth

	private BluetoothAdapter bluetoothAdapter;
	private ConnectThread connectThread;
	private ConnectedThread connectedThread;
	private boolean connexionStarted = false;

	// GUI

	private TextView connexionState;
	private Spinner difficultySpinner;
	private NumberPicker playerNumberPicker;
	private EditText playerScoreEditText;
	private Button playedButton;

	private long difficultyID;
	private int playerNumber;
	private int playerScore;



	// Méthode appelée au lancement de l'application

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.connecting);

    	connexionState = (TextView)findViewById(R.id.connexionState);
		playedButton = (Button)findViewById(R.id.playedButton);
    	
		// On récupère l'accès au bluetooth
		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if(!bluetoothAdapter.isEnabled()) { // On l'active si ce n'est pas déjà fait
			bluetoothAdapter.enable();
			while(!bluetoothAdapter.isEnabled()) { }
		}

		Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices(); // On récupère la liste des périphériques bluetooth
		for(BluetoothDevice device : pairedDevices) { // On cherche s'il y en a un qui correspond à celui qu'on cherche (DEVICE_NAME)
			if(device.getName().equals(DEVICE_NAME)) {
				connexionState.setText("Connexion en cours..."); // On affiche la tentative de connexion
				connectThread = new ConnectThread(device); // On créé un thread de connexion
				connectThread.start(); // On le lance
			}
		}
	}



	// Initialisation du difficultySpinner

	public void loadDifficultySpinner() {
		difficultySpinner = (Spinner)findViewById(R.id.difficultySpinner);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.difficultyArray, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		difficultySpinner.setAdapter(adapter);
		difficultySpinner.setOnItemSelectedListener(this);
	}

	// Initialisation du playerNumberPicker

	public void loadPlayerNumberPicker() {
		playerNumberPicker = (NumberPicker)findViewById(R.id.playerNumberPicker);
		playerNumberPicker.setValue(1);
		playerNumberPicker.setMinValue(1);
		playerNumberPicker.setMaxValue(4);
	}

	// Gestion de la difficulté sélectionnée

	public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
		difficultyID = id;
		//Toast.makeText(this, String.valueOf(difficultyID), Toast.LENGTH_SHORT).show();
	}

	public void onNothingSelected(AdapterView<?> parent) {
			
	}



	// Récupère la réussite ou l'échec de la connexion bluetooth

	private Handler connexionHandler = new Handler() { // Récupère la réussite ou l'échec de la connexion bluetooth
		@Override
		public void handleMessage(Message msg) {
			if(msg.what == SUCCEEDED) { // Si ça réussit
				setContentView(R.layout.main); // On change d'interface
				loadDifficultySpinner();
				loadPlayerNumberPicker();
				connexionStarted = true; // On indique le début de la connexion
			}
			if(msg.what == FAILED) { // Si ça échoue
				connexionState.setText("Connexion échouée"); // On l'indique
			}
		}
	};

	// Callback du bouton launchButton

	public void launchGame(View view) {
		connectedThread.write(String.valueOf(difficultyID)); // On envoie le niveau de difficulté
		playerNumber = playerNumberPicker.getValue();
		setContentView(R.layout.player_round);
		playerScoreEditText = (EditText)findViewById(R.id.playerScoreEditText);
	}

	// Callback du bouton playedButton

	public void played(View view) {
		connectedThread.write("compute"); // On envoie l'ordre de calculer puis préparer la trajectoire du projectile
		playerScore += Integer.parseInt(playerScoreEditText.getText().toString());
		setContentView(R.layout.robot_loading);
	}



	// Thread de connexion
	
	private class ConnectThread extends Thread { // Thread de connexion
		private final BluetoothSocket mmSocket;
		private final BluetoothDevice mmDevice;

		public ConnectThread(BluetoothDevice device) {
			BluetoothSocket tmp = null;
			mmDevice = device;

			try {
				tmp = mmDevice.createRfcommSocketToServiceRecord(MY_UUID); // On crée le socket bluetooth
			} catch (IOException e) { }

			mmSocket = tmp;
		}

		public void run() { // Au lancement du thread
			try {
				mmSocket.connect(); // On lance la connexion
			} catch (IOException connectException) { // En cas d'erreur
				try {
					mmSocket.close(); // On ferme le socket
				} catch (IOException closeException) { }
				connexionHandler.sendMessage(connexionHandler.obtainMessage(FAILED)); // On envoie le message d'erreur	
				return;
			}
			connectedThread = new ConnectedThread(mmSocket); // On crée un thread de contrôle avec le socket bluetooth
			connectedThread.start(); // On le lance
			connexionHandler.sendMessage(connexionHandler.obtainMessage(SUCCEEDED)); // On envoie le message de réussite
		}

		public void cancel() { // À la fermeture de thread
			try {
				mmSocket.close(); // On ferme le socket
			} catch (IOException e) { }
		}
	}

	// Thread de gestion de la connexion

	private class ConnectedThread extends Thread { // Thread de contrôle
		private final BluetoothSocket mmSocket;
		private final OutputStream mmOutStream;

		public ConnectedThread(BluetoothSocket socket) {
			mmSocket = socket;
			OutputStream tmpOut = null;

			try {
				tmpOut = socket.getOutputStream(); // Ouvre un flux sur le socket passé en paramètre
			} catch (IOException e) { }

			mmOutStream = tmpOut;
		}

		public void run() { }

		public void write(String message) { // Sert à envoyer une commande brute, sans traitement
			try {
				mmOutStream.write(message.getBytes());
			} catch (IOException e) { }
		}

		public void cancel() { // À la fin du thread
			try {
				mmSocket.close(); // On ferme le socket
			} catch (IOException e) { }
		}
	}
}
