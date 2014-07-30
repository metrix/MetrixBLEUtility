/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.metrix.ble;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service {
    private final static String TAG = BluetoothLeService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;
    
    private Queue<BleAction> ble_action_queue;
    private boolean ready_for_ble_action;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            "com.mvmnt.hex02.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.mvmnt.hex02.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.mvmnt.hex02.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.mvmnt.hex02.ACTION_DATA_AVAILABLE";
    public final static String ACTION_DATA_WRITE_SUCCEEDED =
            "com.mvmnt.hex02.ACTION_DATA_WRITE_SUCCEEDED";
    public final static String ACTION_DATA_WRITE_FAILED =
            "com.mvmnt.hex02.ACTION_DATA_WRITE_FAILED";
//    public final static String EXTRA_DATA =
//            "com.mvmnt.hex02.EXTRA_DATA";
    public final static String EXTRA_DATA_STRING =
            "com.mvmnt.rise01.EXTRA_DATA_STRING";
    public final static String EXTRA_DATA_BYTE =
            "com.mvmnt.rise01.EXTRA_DATA_BYTE";
    public final static String CHARA_UUID =
    		"com.mvmnt.hex02.CHARA_UUID";

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                mConnectionState = STATE_CONNECTED;
                broadcastUpdate(intentAction);
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
            
            ready_for_ble_action = true;
            BleActionWorker();
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            
            ready_for_ble_action = true;
            BleActionWorker();
        }

    
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
            	broadcastUpdate(ACTION_DATA_WRITE_SUCCEEDED, characteristic);
            } else {
            	broadcastUpdate(ACTION_DATA_WRITE_FAILED, characteristic);            	
            }
            
            ready_for_ble_action = true;
            BleActionWorker();
        }
        
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor desc, int i) {
            Log.d(TAG, "on descriptor write");
        	ready_for_ble_action = true;
            BleActionWorker();        	
        }
        
      };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);
        intent.putExtra(CHARA_UUID, characteristic.getUuid().toString());

     // For all other profiles, writes the data formatted in HEX.
        final byte[] data = characteristic.getValue();
        if (data != null && data.length > 0) {

            final StringBuilder stringBuilder = new StringBuilder(data.length);

            for(byte byteChar : data) 
            {
                stringBuilder.append(String.format("%02X ", byteChar));
            }
        	// add extra as string
            intent.putExtra(EXTRA_DATA_STRING, stringBuilder.toString());
            
        	// add extra as value
        	intent.putExtra(EXTRA_DATA_BYTE, data);

        }
        sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }
        
        ble_action_queue = new LinkedList<BleAction>();
        ready_for_ble_action = true;
        
        return true;
    }

    /**
     * Connects to the GATT server hosted on the Bluetooth LE device.
     *
     * @param address The device address of the destination device.
     *
     * @return Return true if the connection is initiated successfully. The connection result
     *         is reported asynchronously through the
     *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     *         callback.
     */
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }

        // Previously connected device.  Try to reconnect.
        if (mBluetoothDeviceAddress != null && address.equals(mBluetoothDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
                mConnectionState = STATE_CONNECTING;
                return true;
            } else {
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }
        // We want to directly connect to the device, so we are setting the autoConnect
        // parameter to false.
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        Log.d(TAG, "Trying to create a new connection.");
        mBluetoothDeviceAddress = address;
        mConnectionState = STATE_CONNECTING;
        return true;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }
    
    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
    	// add new BLE action to queue
    	ble_action_queue.add(new BleAction(characteristic, BleAction.ACTION_READ));
    	
    	// ask the BLE service to take care of the queue
    	BleActionWorker();
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
    	// add new BLE action to queue
    	byte[] value = {0};
    	if (enabled) {
    		value[0] = 1;
    	} else {
    		Log.d(TAG, "want to disable notification");
    	}
    	ble_action_queue.add(new BleAction(characteristic, BleAction.ACTION_SET_NOTIFY, value));
    	ble_action_queue.add(new BleAction(characteristic, BleAction.ACTION_WRITE_DESCRIPTOR, value));
    	
    	// ask the BLE service to take care of the queue
    	BleActionWorker();
    }
    
    /**
     * Write value to gatt characteristic
     * 
     * @param characteristic Characteristic to write to
     * @param value Value to write to characteristic
     */
    public void writeCharacteristic(BluetoothGattCharacteristic characteristic, byte[] value) {
    	Log.i(TAG, "now adding write to queue");
    	// add new BLE action to queue
    	ble_action_queue.add(new BleAction(characteristic, BleAction.ACTION_WRITE, value));
    	
    	// ask the BLE service to take care of the queue
    	BleActionWorker();
    }
    
    public void BleActionWorker() {
    	if (ble_action_queue == null) {
        	Log.e(TAG, "no action queue");
    	} else if (ready_for_ble_action) {
    		// TODO: add a timer that resets ready_for_ble_actoin and calls this method after 3s
    		// TODO: reset timer in this branch
    		
    		//Log.i(TAG, "ready for BLE action");
    		    		
    		BleAction current_action = ble_action_queue.poll();
    		
    		if (current_action != null) {
        		ready_for_ble_action = false;

//    			Log.d(TAG, "current_action.action: " + current_action.action);
//    			Log.d(TAG, "current_action.data: " + String.valueOf(current_action.data));
//    			Log.d(TAG, "current_action.characteristic: " + String.valueOf(current_action.ble_chara));
    			if (current_action.action.equals(BleAction.ACTION_READ)) {
    				readCharacteristicWorker(current_action.ble_chara);
    			} else if (current_action.action.equals(BleAction.ACTION_SET_NOTIFY)) {
    				setCharacteristicNotificationWorker(current_action.ble_chara, current_action.data[0] != 0);
    			} else if (current_action.action.equals(BleAction.ACTION_WRITE)) {
    				Log.i(TAG, "writing to BLE characteristic");
    				writeCharacteristicWorker(current_action.ble_chara, current_action.data);
    			} else if (current_action.action.equals(BleAction.ACTION_WRITE_DESCRIPTOR)) {
    				writeCharacteristicDescriptorWorker(current_action.ble_chara, current_action.data[0] != 0);
    			} else {
    				Log.e(TAG, "error executing ble action: no such action as " + current_action.action);
    			}
	
    		} 
    	}
    }
    

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristicWorker(BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.readCharacteristic(characteristic);
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotificationWorker(BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
		if (mBluetoothAdapter == null || mBluetoothGatt == null) {
			Log.w(TAG, "BluetoothAdapter not initialized");
			return;
		}
		
		boolean val = mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
		Log.d(TAG, "enabling notifications succeeded: " + String.valueOf(val));

        ready_for_ble_action = true;
        BleActionWorker();
    }
    
    public void writeCharacteristicDescriptorWorker(BluetoothGattCharacteristic characteristic, boolean  enabled) {
		BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
		UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
		
		if (descriptor != null) {
			if (enabled) {
				descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
			} else {
				descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);			
			}
			mBluetoothGatt.writeDescriptor(descriptor);
		} else {
			Log.e(TAG, "couldn't update descriptor");
	        ready_for_ble_action = true;
	        BleActionWorker();
		}
    }
    
    /**
     * Write value to gatt characteristic
     * 
     * @param characteristic Characteristic to write to
     * @param value Value to write to characteristic
     */
    public void writeCharacteristicWorker(BluetoothGattCharacteristic characteristic, byte[] value) {
    	if (characteristic == null) {
	    	Log.d(TAG, "Characteristic null");
	    	return;
    	}
    	characteristic.setValue(value);
    	boolean status = mBluetoothGatt.writeCharacteristic(characteristic);
//    	Log.i(TAG, "Write Status: " + status);
    }
    
    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }
    
    private class BleAction {
    	public BluetoothGattCharacteristic ble_chara;
        public String action;
        public byte[] data;

        public final static String ACTION_WRITE =
                "BluetoothLeService.BleActivity.ACTION_WRITE";
        public final static String ACTION_READ =
        		 "BluetoothLeService.BleActivity.ACTION_READ";
        public final static String ACTION_SET_NOTIFY =
        		 "BluetoothLeService.BleActivity.ACTION_SET_NOTIFY";
        public final static String ACTION_WRITE_DESCRIPTOR = 
        		"BluetoothLeService.BleActivity.ACTION_WRITE_DESCRIPTOR";
        
        public BleAction(BluetoothGattCharacteristic chara, String act, byte[] value) {
        	ble_chara = chara;
        	action = act;
        	data = value;
        }
        
        public BleAction(BluetoothGattCharacteristic chara, String act) {
        	ble_chara = chara;
        	action = act;
        	data = null;
        }
    
    }
}
