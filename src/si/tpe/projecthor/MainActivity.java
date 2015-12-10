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
import java.io.InputStream;
import java.io.OutputStream;

public class MainActivity extends Activity implements OnItemSelectedListener {

	// Déclarations de variables

	// Constantes

	private static final int SUCCEEDED = 1, FAILED = 0, MESSAGE_READ = 2;
	private static final String DEVICE_NAME = "Prjecthr01"; // Le nom du périphérique bluetooth
	private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); // Sert à identifier l'application lors de la connexion bluetooth

	// Bluetooth

	private BluetoothAdapter bluetoothAdapter;
	private ConnectThread connectThread;
	private ConnectedThread connectedThread;

	// GUI

	private TextView connexionState;
	private TextView totalScoreTextView;
	private TextView remainingShotsTextView;
	private TextView winLoseTextView;
	private TextView finalScoreTextView;
	private Spinner difficultySpinner;
	private NumberPicker shotsNumberPicker;
	private NumberPicker playerScoreNumberPicker;
	private Button reconnectButton;
	private Button fireButton;

	private long difficultyID;
	private int playerScore;
	private int robotScore;
	private int remainingShots;



	// Méthode appelée au lancement de l'application

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.connecting);
		
		connexionState = (TextView)findViewById(R.id.connexionState);
		reconnectButton = (Button)findViewById(R.id.reconnectButton);
    	
		// On récupère l'accès au bluetooth
		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if(!bluetoothAdapter.isEnabled()) { // On l'active si ce n'est pas déjà fait
			bluetoothAdapter.enable();
			while(!bluetoothAdapter.isEnabled()) { }
			Toast.makeText(this, "Bluetooth activé", Toast.LENGTH_SHORT).show();
		}

		launchConnexion();
	}

	// On quitte lors de l'appui sur le bouton système "Précédent"

	public void onBackPressed() {
		exit();
	}
	
	public void launchConnexion() {
		Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices(); // On récupère la liste des périphériques bluetooth
		for(BluetoothDevice device : pairedDevices) { // On cherche s'il y en a un qui correspond à celui qu'on cherche (DEVICE_NAME)
			if(device.getName().equals(DEVICE_NAME)) {
				connexionState.setText("Connexion en cours..."); // On affiche la tentative de connexion
				connectThread = new ConnectThread(device); // On créé un thread de connexion
				connectThread.start(); // On le lance
			}
		}
	}



	// Afficher la vue main

	public void displayMain() {
		setContentView(R.layout.main);

		shotsNumberPicker = (NumberPicker)findViewById(R.id.shotsNumberPicker);
		shotsNumberPicker.setValue(3);
		shotsNumberPicker.setMinValue(1);
		shotsNumberPicker.setMaxValue(100);

		difficultySpinner = (Spinner)findViewById(R.id.difficultySpinner);
		ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.difficultyArray, android.R.layout.simple_spinner_item);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		difficultySpinner.setAdapter(adapter);
		difficultySpinner.setOnItemSelectedListener(this);
	}

	// Afficher la vue player_round

	public void displayPlayerRound() {
		setContentView(R.layout.player_round);

		playerScoreNumberPicker = (NumberPicker)findViewById(R.id.playerScoreNumberPicker);
		playerScoreNumberPicker.setValue(0);
		playerScoreNumberPicker.setMinValue(0);
		playerScoreNumberPicker.setMaxValue(9);

		refreshScore();
	}

	// Rafraîchir le score total et le nombre de tirs restants

	public void refreshScore() {
		remainingShotsTextView = (TextView)findViewById(R.id.remainingShotsTextView);
		remainingShotsTextView.setText("Tirs restants : " + String.valueOf(remainingShots));
		totalScoreTextView = (TextView)findViewById(R.id.totalScoreTextView);
		totalScoreTextView.setText("Score total : " + String.valueOf(playerScore) + " - " + String.valueOf(robotScore));
	}

	// Gestion de la difficulté sélectionnée

	public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
		difficultyID = id;
	}

	public void onNothingSelected(AdapterView<?> parent) { }



	// Permet de gérer la connexion

	private Handler connexionHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch(msg.what) {
			case SUCCEEDED : // Si ça réussit
				displayMain();
				break;
			case FAILED : // Si ça échoue
				connexionState.setText("Connexion échouée"); // On l'indique
				reconnectButton.setVisibility(View.VISIBLE);
				break;
			case MESSAGE_READ : // Si un message est reçu
				byte[] buffer = (byte[])msg.obj;
				String messageRead = new String(buffer, 0, msg.arg1);
				if(messageRead.contains("r")) { // Si le robot est prêt à tirer
					setContentView(R.layout.robot_round); // On change d'interface
					fireButton = (Button)findViewById(R.id.fireButton);
					refreshScore();
				}
				else { // Sinon le message est le score effectué par le robot
					robotScore += Integer.parseInt(messageRead);
					refreshScore();
					if(remainingShots > 0) {
						displayPlayerRound();
					}
					else { // Si la partie est finie
						connectedThread.write("e");
						setContentView(R.layout.game_over);

						winLoseTextView = (TextView)findViewById(R.id.winLoseTextView);
						if(playerScore == 0) {
							winLoseTextView.setText("Essayez au moins de toucher la cible...");
						}
						else if(playerScore == 27) {
							winLoseTextView.setText("Épique !");
						}
						else if(playerScore > robotScore) {
							winLoseTextView.setText("Bien joué, vous avez gagné !");
						}
						else if(playerScore < robotScore) {
							winLoseTextView.setText("Dommage, vous avez perdu.");
						}
						else {
							winLoseTextView.setText("Égalité : vous êtes tenace !");
						}

						finalScoreTextView = (TextView)findViewById(R.id.finalScoreTextView);
						finalScoreTextView.setText(totalScoreTextView.getText());
					}
				}
				break;
			}
		}
	};

	// Callback du bouton reconnectButton

	public void reconnect(View view) {
		reconnectButton.setVisibility(View.GONE);
		connexionState.setText("Connexion en cours...");
		launchConnexion();
	}

	// Callback du bouton launchButton

	public void launchGame(View view) {
		connectedThread.write(String.valueOf(difficultyID)); // On envoie le niveau de difficulté
		remainingShots = shotsNumberPicker.getValue();
		displayPlayerRound();
	}

	// Callback du bouton playedButton

	public void played(View view) {
		remainingShots--;
		playerScore += playerScoreNumberPicker.getValue();

		setContentView(R.layout.robot_loading);
		refreshScore();

		connectedThread.write("c"); // On envoie l'ordre de calculer puis préparer la trajectoire du projectile
	}

	// Callback du bouton fireButton

	public void fire(View view) {
		connectedThread.write("f"); // On envoie l'ordre de tirer
		fireButton.setEnabled(false);
	}

	// Callback du bouton replayButton

	public void replay(View view) {
		displayMain();
		remainingShots = 3;
		playerScore = 0;
		robotScore = 0;
	}

	// Callback du bouton quitButton

	public void quit(View view) {
		exit();
	}

	// Coupe la connexion et quitte l'application

	public void exit()
	{
		if(bluetoothAdapter.getState() == BluetoothAdapter.STATE_ON)
		{
			connectThread.cancel();
			connectedThread.cancel();
		}
		finish();
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
		private final InputStream mmInStream;
		private final OutputStream mmOutStream;

		public ConnectedThread(BluetoothSocket socket) {
			mmSocket = socket;
			InputStream tmpIn = null;
			OutputStream tmpOut = null;

			try {
				tmpIn = socket.getInputStream(); // Ouvre un flux entrant sur le socket passé en paramètre
				tmpOut = socket.getOutputStream(); // Ouvre un flux sortant sur le socket passé en paramètre
			} catch (IOException e) { }

			mmInStream = tmpIn;
			mmOutStream = tmpOut;
		}

		public void run() {
			byte[] buffer = new byte[1024];  // buffer store for the stream
			int bytes; // bytes returned from read()
		 
			while (true) {
				try {
					bytes = mmInStream.read(buffer);
					connexionHandler.sendMessage(connexionHandler.obtainMessage(MESSAGE_READ, bytes, -1, buffer));
				} catch (IOException e) {
					break;
				}
			}
		}

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
