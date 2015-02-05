package com.awprog.komenkejessuyrysh;

import java.io.IOException;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Message;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

public class KKJR extends Activity {
	private TextView mTVMoney;
	private NfcAdapter nfcAdapter;
	private PendingIntent nfcPendingIntent;
	private Tag tag = null;
	private Toast toast;

	private static final byte[] select = {(byte)0x00 ,(byte)0xA4 ,(byte)0x04 ,(byte)0x0C ,(byte)0x06 ,(byte)0xA0 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x69 ,(byte)0x00};
	private static final byte[] request = {(byte)0x00 ,(byte)0xB2 ,(byte)0x01 ,(byte)0xC4 ,(byte)0x09};
	
	MoneoComm loader = null;
	
	private Handler handler = new Handler(new Handler.Callback() {
		@Override
		public boolean handleMessage(Message msg) {
			mTVMoney.setText((String)msg.obj);
			return true;
		}
	});
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.kkjr);
		log("onCreate -----------------------------------------");
		mTVMoney = (TextView) findViewById(R.id.tv_money);
		write("");
		
		//initialize NFC
		nfcAdapter = NfcAdapter.getDefaultAdapter(this);
		if(nfcAdapter == null || !nfcAdapter.isEnabled()) {
			sendToast("Cannot find NFC Adapter");
			finish();
		}

		nfcPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, this.getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
		
	}

	public void enableForegroundMode() {
		IntentFilter[] writeTagFilters = new IntentFilter[] {
				new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
				new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
		};
		nfcAdapter.enableForegroundDispatch(this, nfcPendingIntent, writeTagFilters, null);
	}
	public void diseableForegroundMode() {
		nfcAdapter.disableForegroundDispatch(this);
	}

	@Override
	public void onNewIntent(Intent intent) {
		if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(intent.getAction())
				|| NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
			tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
			
			//sendToast("Carte détectée");
			log("New Intent : tag discovered");
			
			if(loader != null) {
				loader.cancel(true);
				log("cancel AsyncTask");
			}
			
			log("execute AsyncTask");
			(loader = new MoneoComm()).execute(tag);
		}
	}
	boolean onResumeFirstTime = true;
	@Override
	protected void onResume() {
		super.onResume();
		
		if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(getIntent().getAction()) && onResumeFirstTime)
            onNewIntent(getIntent());
		
		enableForegroundMode();
		onResumeFirstTime = false;
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		diseableForegroundMode();
	}

	private static String ByteArrayToHexString(byte [] inArray) {
		if(inArray == null)
			return null;
	    int i, in;
	    String [] hex = {"0","1","2","3","4","5","6","7","8","9","A","B","C","D","E","F"};
	    String out= "";
	    
	    for(int j = 0 ; j < inArray.length ; ++j) 
	    {
	        in = (int) inArray[j] & 0xff;
	        i = (in >> 4) & 0x0f;
	        out += hex[i];
	        i = in & 0x0f;
	        out += hex[i];
	        if(j != inArray.length-1) out += ' ';
	    }
	    return out;
	}
	
	// Ecrit le solde contenu dans la chaîne de caractère en code hexa
	private void write(String msgByteArray) {
		String txt = "";

		if(msgByteArray != null && msgByteArray.length() != 0) {
			int i = 0;
			for(; i < 4; i++)
				if(msgByteArray.charAt(i) != '0' && msgByteArray.charAt(i) != ' ')
					break;
			for(; i < 8; i++)
				if(msgByteArray.charAt(i) != ' ')
					txt += msgByteArray.charAt(i);
				else if(i == 5)
					txt += '.';
			
			txt += "€";
		}
		
		Message msg = new Message();
		msg.obj = txt;
		handler.sendMessage(msg); //mTVMoney.setText(txt + "€");
	}
	Handler handlerToast = new Handler(new Callback() {
		@Override
		public boolean handleMessage(Message msg) {
			//if(toast == null) {
				toast = Toast.makeText(KKJR.this, (String)msg.obj, Toast.LENGTH_SHORT);
			/*}
			else {
				toast.cancel();
				toast.setText((String)msg.obj);
			}*/
			toast.show();
			return true;
		}
	});
	private void sendToast(String s) {
		Message msg = new Message(); msg.obj = s;
		handlerToast.sendMessage(msg);
	}
	
	// Retourne la réponse de la carte à la requête de REQUEST, null en cas d'erreur
	private byte[] getBalanceWithNFC(Tag tag, MoneoComm async) {
		if(async.isCancelled()) { log("cancelled", async); return null; }
		
		if(tag == null) {
			write("");
			sendToast("Carte absente");
			return null;
		}

		log("get tag from isodep", async);
		IsoDep nfc = IsoDep.get(tag);
		if(nfc == null) {
			write("");
			sendToast("Tag NFC incompatible");
			return null;
		}

		log("timeout="+nfc.getTimeout()+" maxlength="+nfc.getMaxTransceiveLength(), async);
		
		if(async.isCancelled()) { log("cancelled", async); return null; }
		
		byte in[] = null;

		log("connect", async);
		// Etablit la connection
		try {
			nfc.connect();
		} catch (IOException e) {
			write("");
			sendToast("Carte absente");
			log("err connect | "+e.getMessage());
			return null;
		};
		
		nfc.setTimeout(3000);

		if(async.isCancelled()) { log("cancelled", async); return null; }
		
		if(!nfc.isConnected())
		{
			write("");
			sendToast("Erreur de connection");
			return null;
		}
		

		if(async.isCancelled()) { log("cancelled", async); return null; }
		
		// Communications
		try {
			log("transceive [select]", async);
			// Sélection de l'application moneo (10 tentatives)
			in = transceive(nfc, select);
			log("success="+(in!=null), async);
			
			if(async.isCancelled()) { log("cancelled", async); return null; }
			
			// Gestion de exception pour la commande SELECT
			if(in == null || in[0] != (byte)0x90 || in[1] != (byte)0x00)
				throw new Exception("Error command SELECT");
			
			byte[] data = request;
			
			// Requête sans longueur de réponse définie
			data[data.length-1] = 0x00; // Longueur = 0
			boolean end = false;
			
			do {
				log("transceive [request]", async);
				// Envoie de la commande
				in = transceive(nfc, data);
				
				if(async.isCancelled()) { log("cancelled"); return null; }
				
				if(in == null)
					throw new Exception("Error command TRANSCEIVE");

				log("byte array", async);
				String str = KKJR.ByteArrayToHexString(in);
				
				// <=> erreur 404
				if(in.length == 2 && str.equalsIgnoreCase("6A 83"))
					throw new Exception("Error : code is 6A 83");
				// mauvaise commande (ou commande SELECT non effectué)
				else if(in.length == 2 && str.equalsIgnoreCase("6A 82"))
					throw new Exception("Error : code is 6A 82");
				// Envoie de la requête avec la longueur de réponse obtenue
				else if(in.length == 2 && str.startsWith("6C"))
					data[data.length-1] = in[1];
				else
					end = true;
			} while (!end);

			log("end try", async);
		}
		catch (IOException e) {
			log("ErrCom : "+e.getMessage());
			if(e.getMessage() == null)
				sendToast("Carte absente");
			else
				sendToast("Erreur de communication");
			in = null;
		}
		catch (Exception e) {
			log("ErrCom : "+e.getMessage());
			sendToast("Erreur de communication");
			in = null;
		}
		finally {
			log("close", async);
			try {
				nfc.close();
			} catch (IOException e) { }	
		}

		if(async.isCancelled()) { log("cancelled", async); return null; }
		return in;
	}
	
	// <attemptsMax> tentatives d'envoie des données
	private byte[] transceive(IsoDep nfc, byte[] data) {
		byte[] in = null;
		try {
			log("attempt");
			in = nfc.transceive(data);
		} catch (IOException e) {
			log("error transceive | "+e.getMessage());
			in = null;
		}	
		
		return in;
	}
	int count = 0;
	class MoneoComm extends AsyncTask<Tag, Void, byte[]> {
		int taskId = count++;
		@Override
		protected byte[] doInBackground(Tag... params) {
			log("getBalance inBackground", this);
			return getBalanceWithNFC(params[0], this);
		}
		
		@Override
		protected void onPostExecute(byte[] result) {
			write(ByteArrayToHexString(result));
		}
		@Override
		protected void onCancelled(byte[] result) {
			log("onCancelled", this);
			write("");//ByteArrayToHexString(result));
		}
		
	}
	public static void log(String a) {
		Log.i("###", "-- " + a + " #"+Thread.currentThread().getStackTrace()[3].getLineNumber());
	}
	public static void log(String a, MoneoComm m) {
		Log.i("###",  "@"+m.taskId+" " + a + " #"+Thread.currentThread().getStackTrace()[3].getLineNumber());
	}
}
