package com.awprog.komenkejessuyrysh;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.ndeftools.Message;
import org.ndeftools.Record;
import org.ndeftools.externaltype.AndroidApplicationRecord;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity implements NfcAdapter.OnNdefPushCompleteCallback, NfcAdapter.CreateNdefMessageCallback {
	private TextView mTextViewOut;
	private NfcAdapter nfcAdapter;
	private PendingIntent nfcPendingIntent;
	private Tag tag = null;
	private int cmdIndex = 0;
	private static final byte[] select = {(byte)0x00 ,(byte)0xA4 ,(byte)0x04 ,(byte)0x0C ,(byte)0x06 ,(byte)0xA0 ,(byte)0x00 ,(byte)0x00 ,(byte)0x00 ,(byte)0x69 ,(byte)0x00};
	private static final byte[][] request = {
			// 00 B2 P1 P2 LENGTH
			{(byte)0x00 ,(byte)0xB2 ,(byte)0x01 ,(byte)0x64 ,(byte)0x04},
			{(byte)0x00 ,(byte)0xB2 ,(byte)0x01 ,(byte)0xBC ,(byte)0x16},
			{(byte)0x00 ,(byte)0xB2 ,(byte)0x01 ,(byte)0xC4 ,(byte)0x09},
			{(byte)0x00 ,(byte)0xB2 ,(byte)0x01 ,(byte)0x44 ,(byte)0x0E},
			{(byte)0x00 ,(byte)0xB2 ,(byte)0x01 ,(byte)0xE4 ,(byte)0x30},
			{(byte)0x00 ,(byte)0xB2 ,(byte)0x01 ,(byte)0xEC ,(byte)0x33},
			{(byte)0x00 ,(byte)0xB2 ,(byte)0x02 ,(byte)0xE4 ,(byte)0x30},
			{(byte)0x00 ,(byte)0xB2 ,(byte)0x02 ,(byte)0xEC ,(byte)0x33},
			{(byte)0x00 ,(byte)0xB2 ,(byte)0x03 ,(byte)0xEC ,(byte)0x33},
			{(byte)0x00 ,(byte)0xB2 ,(byte)0x04 ,(byte)0xEC ,(byte)0x33},
			{(byte)0x00 ,(byte)0xB2 ,(byte)0x05 ,(byte)0xEC ,(byte)0x33},
			{(byte)0x00 ,(byte)0xB2 ,(byte)0x03 ,(byte)0xE4 ,(byte)0x30},
			{(byte)0x00 ,(byte)0xB2 ,(byte)0x04 ,(byte)0xE4 ,(byte)0x30},
			{(byte)0x00 ,(byte)0xB2 ,(byte)0x05 ,(byte)0xE4 ,(byte)0x30},
			{(byte)0x00 ,(byte)0xB2 ,(byte)0x06 ,(byte)0xE4 ,(byte)0x30},

			{(byte)0x00 ,(byte)0xB2 ,(byte)0x07 ,(byte)0xE4 ,(byte)0x00},//((byte)0x6A ,(byte)0x83)
			{(byte)0x00 ,(byte)0xB2 ,(byte)0x06 ,(byte)0xEC ,(byte)0x00} //((byte)0x6A ,(byte)0x83)
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		mTextViewOut = (TextView) findViewById(R.id.tv_out);
		((Button)findViewById(R.id.b_send)).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if(tag == null) {
					write("Error : tag=null");
					return;
				}
				
				cmdIndex = (cmdIndex+1)%request.length;
				write("getting IsoDep tool");
				IsoDep nfc = IsoDep.get(tag);
				byte in[];
				
				try {
					write("IsoDep.connect");
					nfc.connect();
					
					// S�lection de l'application moneo
					write("Select Moneo AID");
					write("IsoDep.transceive : "+ MainActivity.ByteArrayToHexString(select));
					in = nfc.transceive(select);
					write("received data : "+MainActivity.ByteArrayToHexString(in));

					if(in[0] != (byte)0x90 || in[1] != (byte)0x00) {
						write("Error : data must be 90 00");
						throw new IOException("Incorrect answer");
					}
					
					byte[] data = request[cmdIndex];
					
					// Envoie de la requ�te sans longueur de r�ponse d�finie
					write("sending request with length=0");
					data[data.length-1] = 0x00; // Longueur = 0
					
					write("IsoDep.transceive : "+ MainActivity.ByteArrayToHexString(data) + " ("+ cmdIndex + ")");
					in = nfc.transceive(data);
					String str = MainActivity.ByteArrayToHexString(in);
					if(in.length == 2 && str.equalsIgnoreCase("6A 83"))
						write("Answer : " + str + " > error 404");
					else if(in.length == 2 && str.equalsIgnoreCase("6A 82"))
						write("Answer : " + str + " > communication error");
					else if(in.length == 2 && str.startsWith("6C")) {
						write("Answer : " + str + " > requiered length should be " + in[1]);
						// Envoie de la requ�te avec la longueur de r�ponse obtenue
						write("sending request with length="+in[1]);
						data [data.length-1] = in[1];
						
						write("IsoDep.transceive : "+ MainActivity.ByteArrayToHexString(data) + " ("+ cmdIndex + ")");
						in = nfc.transceive(data);
						write("Answer : "+MainActivity.ByteArrayToHexString(in));
						
					}
					else
						write("Answer : "+str);
					
					
				}
				catch (IOException e) {
					e.printStackTrace();
					write("IOException : "+e.getMessage());
				}
				finally {
					write("IsoDep.close");
					try {
						nfc.close();
					} catch (IOException e) {
						write("IOException : "+e.getMessage());
						e.printStackTrace();
					}	
				}
			}
		});
		
		write("initializing nfc");
		//initialize NFC
		nfcAdapter = NfcAdapter.getDefaultAdapter(this);
		nfcPendingIntent = PendingIntent.getActivity(this, 0, new Intent(this, this.getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
		
		if(nfcAdapter == null || !nfcAdapter.isEnabled())
			write("fail !");
		
		write("initializing callback");
		nfcAdapter.setNdefPushMessageCallback(this, this);
		nfcAdapter.setOnNdefPushCompleteCallback(this, this);
	}

	public void enableForegroundMode() {
		write("enabling ForegroundMode");
		
		IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
		IntentFilter[] writeTagFilters = new IntentFilter[] {tagDetected};
		nfcAdapter.enableForegroundDispatch(this, nfcPendingIntent, writeTagFilters, null);
	}
	public void diseableForegroundMode() {
		write("disabling ForegroundMode");
		
		nfcAdapter.disableForegroundDispatch(this);
	}

	@Override
	public void onNewIntent(Intent intent) {
		write("intent action : "+intent.getAction());
		if (true/* || NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())*/) {
			write("reading NFC id :");
			write("NFC tag detected !");
			write("tag Id is : " + ByteArrayToHexString(intent.getByteArrayExtra(NfcAdapter.EXTRA_ID)));
			
			tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
			write("getting the Tag Object : "+tag.toString());
			
			Parcelable[] messages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
			if (messages != null){
				write("Found" + messages.length + " NDEF messages");
				
				
				for (int i = 0; i<messages.length; i++){
					try {
						List<Record> records = new Message((NdefMessage)messages[i]);
						
						write("Found " + records.size() + " record in message " + i);
						
						for (int j=0; j < records.size(); j++){
							write("Record #" + j + " is of class " + records.get(j).getClass().getSimpleName());
							
							Record record = records.get(j);
							
							if (record instanceof AndroidApplicationRecord) {
								AndroidApplicationRecord aar = (AndroidApplicationRecord)record;
								write("Package is " + aar.getDomain() + " " + aar.getType());
							}
						}
					} catch (Exception e) {
						write("Problem parsing message");
					}
				}
			}
		}
		
	}
	
	@Override
	protected void onResume() {
		super.onResume();
		if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())
				|| NfcAdapter.ACTION_TAG_DISCOVERED.equals(getIntent().getAction())
				|| NfcAdapter.ACTION_TECH_DISCOVERED.equals(getIntent().getAction())) {
            onNewIntent(getIntent());
        }
		enableForegroundMode();
	}
	
	@Override
	protected void onPause() {
		super.onPause();
		
		diseableForegroundMode();
	}

	private static String ByteArrayToHexString(byte [] inArray) {
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
	@SuppressWarnings("unused")
	private static byte[] HexStringToByteArray(String inHexString) {
	    String hex = "0123456789ABCDEFabcdef";
	    ArrayList<Byte> out = new ArrayList<Byte>();
	    byte b = 0;
	    boolean newbyte = true;
	    
	    for(int j = 0 ; j < inHexString.length() ; ++j) 
	    if(hex.indexOf(inHexString.charAt(j)) != -1)
	    {
	    	if(newbyte) {
	    		b = (byte) (Byte.valueOf(inHexString.substring(j, j+1), 16) << 4);
	    		newbyte = false;
	    	} else {
	    		b |= Byte.valueOf(inHexString.substring(j, j+1), 16);
	    		newbyte = true;
	    		out.add(b);
	    	}
	    }
	    
	    if(!newbyte)
	    	out.add(b);
	    
	    byte outByte[] = new byte[out.size()];
	    for(int j = outByte.length-1; j >= 0; --j)
	    	outByte[j] = out.get(j);
	    
	    return outByte;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	private void write(String msg) {
		mTextViewOut.setText(mTextViewOut.getText()+"\n"+msg);
	}

	@Override
	public NdefMessage createNdefMessage(NfcEvent event) {
		write("creating message");
		byte[] data = {0x00, (byte) 0xA4, 0x04, 0x0C, 0x06, (byte) 0xA0, 0x00, 0x00, 0x00, 0x69, 0x00};
		
		try {
			return new NdefMessage(data);
		} catch (FormatException e) {
			write("fail !");
			e.printStackTrace();
			return null;
		}
	}
	public static NdefRecord createMimeRecord(String mimeType, byte[] payload) {
		byte[] mimeBytes = mimeType.getBytes(Charset.forName("US-ASCII"));
		NdefRecord mimeRecord = new NdefRecord(NdefRecord.TNF_MIME_MEDIA,
				mimeBytes, new byte[0], payload);
		return mimeRecord;
	}

	@Override
	public void onNdefPushComplete(NfcEvent event) {
		write("Ndef push complete with event : " + event.toString() );
	}
}
