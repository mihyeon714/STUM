package ssm.bluetooth;
 
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity  {
	// Debugging
	private static final String TAG = "Main";
    private static final boolean D = true;
    // Key names received from the BluetoothService Handler
    public static final String DEVICE_NAME = "device_name";
	public static final String TOAST = "toast";
	// Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
	// Intent request code
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
	private static final int REQUEST_ENABLE_BT = 3;
    // Layout Views
    private ListView mConversationView;
    private TextView mStatus_view;
    private EditText mOutEditText;
    private Button mSendButton;
    // String buffer for outgoing messages
    private StringBuffer mOutStringBuffer;
    // Array adapter for the conversation thread
    private ArrayAdapter<String> mConversationArrayAdapter = null;
    // Local Bluetooth adapter    
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for the BluetoothService
    private BluetoothService mBtService = null;
    // Name of the connected device
    private String mConnectedDeviceName = null;
    
    
    @Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if(D) Log.e(TAG, "+++ ON CREATE +++");
		// Set up the window layout
		setContentView(R.layout.main);
		
		//**** 폰에 Bluetooth가 있는가? 당연히 있을 것이나 Bluetooth가 고장 났을 수도 있음. ****
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            //**** 폰에 Bluetooth가 없으면 더 할일이 없다 . ****
        	Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
    }
    @Override
    public void onStart() {
        super.onStart();
        if(D) Log.e(TAG, "++ ON START ++");
        //**** Bluetooth가 꺼져 있으면 켜달라고 시스템에 요청 ****
        //**** 이 경우  setupService()는 ActivityResult 에서 불려진다.
        if (!mBluetoothAdapter.isEnabled()) {
        	if(D) Log.d(TAG, "Bluetooth ON Request");
        	Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        // Otherwise, setup the chat session
        } else {
            if (mBtService == null) setupService();
        }
    }
    @Override
    public void onRestart() {
        super.onRestart();
        if(D) Log.e(TAG, "++ ON RESTART ++");
    }
    @Override
    public synchronized void onResume() {
        super.onResume();
        if(D) Log.e(TAG, "++ ON RESUME ++");
        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mBtService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mBtService.getState() == BluetoothService.STATE_NONE) {
              // Start the Bluetooth chat services
              mBtService.start();
            }
        }
    }
    @Override
    public synchronized void onPause() {
        super.onPause();
        if(D) Log.e(TAG, "++ ON PAUSE ++");
    }
    @Override
    public void onStop() {
        super.onStop();
        if(D) Log.e(TAG, "++ ON STOP ++");
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        if(D) Log.e(TAG, "++ ON DESTROY ++");
        if (mBtService != null) mBtService.stop();
    }
    
    private void setupService() {	
    	if(D) Log.d(TAG, "setupChat()");
    	// Initialize the array adapter for the conversation thread
        mConversationArrayAdapter = new ArrayAdapter<String>(this, R.layout.device_name);
        mConversationView = (ListView) findViewById(R.id.message_view);
        mConversationView.setAdapter(mConversationArrayAdapter);
        mConversationView.setDivider(null);
        
		mStatus_view = (TextView) findViewById(R.id.status_view);
		mStatus_view.setText("Start Now!");

		// Initialize the compose field with a listener for the return key
        mOutEditText = (EditText) findViewById(R.id.edit_text_out);
        mOutEditText.setOnEditorActionListener(mWriteListener);
        // Initialize the send button with a listener that for click events
        mSendButton = (Button) findViewById(R.id.button_send);
        mSendButton.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                TextView view = (TextView) findViewById(R.id.edit_text_out);
                String message = view.getText().toString() + Character.toString((char)0x0d)+ Character.toString((char)0x0a);
                sendMessage(message);
            }
        });
        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");
	
		// Initialize the BluetoothService to perform bluetooth connections
		if(mBtService == null) {
			mBtService = new BluetoothService(this, mHandler);
		}
    }
    //========================== Device 로 메세지를 보내다.==========================
    // The action listener for the EditText widget, to listen for the return key
    private TextView.OnEditorActionListener mWriteListener =
        new TextView.OnEditorActionListener() {
        public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
            // If the action is a key-up event on the return key, send the message
            if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
                String message = view.getText().toString();
                sendMessage(message);
            }
            if(D) Log.i(TAG, "END onEditorAction");
            return true;
        }
    };
    /**
     * Sends a message.
     * @param message  A string of text to send.
     */
    private void sendMessage(String message) {
        // Check that we're actually connected before trying anything
        if (mBtService.getState() != BluetoothService.STATE_CONNECTED) {
        	Toast.makeText(getApplicationContext(), R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }
        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mBtService.write(send);

            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
            mOutEditText.setText(mOutStringBuffer);
        }
    }
    //========================== Options Menu ==========================    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Intent serverIntent = null;
        switch (item.getItemId()) {
        case R.id.secure_connect_scan:
            // Launch the DeviceListActivity to see devices and do scan
            serverIntent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
            return true;
        case R.id.insecure_connect_scan:
            // Launch the DeviceListActivity to see devices and do scan
            serverIntent = new Intent(this, DeviceListActivity.class);
            startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_INSECURE);
            return true;
        case R.id.discoverable:
            // Ensure this device is discoverable by others
            ensureDiscoverable();
            return true;
        }
        return false;
    }
    /*
           다른 디바이스에서 폰을 스캔할 경우. 즉 폰 끼리 연결하는 경우. 여기서는 아직 쓸 일은 없다.
    */
    private void ensureDiscoverable() {
    	if(D) Log.d(TAG, "ensure discoverable");
    	if (mBluetoothAdapter.getScanMode() !=
    		BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
    		Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
    		discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
    		startActivity(discoverableIntent);
    	}
    }
    //========================== 'BluetoothService' 부터의 Message ==========================
	private final Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
            switch (msg.what) {
            	case MESSAGE_STATE_CHANGE:
                    if(D) Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
                    switch (msg.arg1) {
                    	case BluetoothService.STATE_CONNECTED:
                            setStatus(getString(R.string.status_connected_to, mConnectedDeviceName));
                    		mConversationArrayAdapter.clear();
                    		break;
                    	case BluetoothService.STATE_CONNECTING:
                    		setStatus(getString(R.string.status_connecting));
                    		break;
                    	case BluetoothService.STATE_LISTEN:
                    	case BluetoothService.STATE_NONE:
                    		setStatus(getString(R.string.status_not_connected));
                    		break;
                    }
            		break;
            	case MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String(writeBuf);
                    mConversationArrayAdapter.add("Tx:" + writeMessage);
                    break;
                case MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    //mConversationArrayAdapter.add(mConnectedDeviceName+":  " + readMessage);
                    mConversationArrayAdapter.add("Rx:" + readMessage);
                    break;
                case MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                    Toast.makeText(getApplicationContext(), "Connected to " + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    break;
            	case MESSAGE_TOAST:
                    Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),Toast.LENGTH_LONG).show();
                    break;
            }		
		}
	};
    private final void setStatus(String status) {
        mStatus_view.setText(status);
    }
	//========================== Intent Request Return ==========================
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(D) Log.d(TAG, "onActivityResult " + requestCode + "," + resultCode);
        switch (requestCode) {
        	case REQUEST_CONNECT_DEVICE_SECURE:
            // When DeviceListActivity returns with a device to connect
        		if (resultCode == Activity.RESULT_OK) {
        			connectDevice(data, true);
        		}
        		break;
        	case REQUEST_CONNECT_DEVICE_INSECURE:
            // When DeviceListActivity returns with a device to connect
        		if (resultCode == Activity.RESULT_OK) {
        			connectDevice(data, false);
        		}
        		break;
        	case REQUEST_ENABLE_BT:
        	// When the request to enable Bluetooth returns
        		if (resultCode == Activity.RESULT_OK) {
                // Bluetooth is now enabled, so set up a chat session
        			setupService();
        		} else {
                    // User did not enable Bluetooth or an error occurred
        			if(D) Log.e(TAG, "BT not enabled");
                    Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                    finish();
        		}
        		break;
        }
	}
    private void connectDevice(Intent data, boolean secure) {
        // Get the device MAC address
        String address = data.getExtras().getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mBtService.connect(device, secure);
    }
}
