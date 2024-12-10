package com.gps2usb;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDeviceConnection;
import android.location.Location;
import android.os.Bundle;
import android.util.Log; // Import for logging
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.OnSuccessListener;

import android.hardware.usb.UsbManager;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private boolean locationUpdatesEnabled = false;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final int WRITE_WAIT_MILLIS = 100;
    private static final String TAG = "MainActivity"; // Tag for logging
    private TextView coordinatesTextView;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private TextView logTextView, GPSstatus;
    private final StringBuilder serialBuffer = new StringBuilder();
    // Serial listener
    private final SerialInputOutputManager.Listener usbListener = new SerialInputOutputManager.Listener() {
        @Override
        public void onNewData(byte[] data) {
            // Accumulate received bytes into the buffer
            for (byte b : data) {
                serialBuffer.append((char) b);
            }

            // Check if the buffer contains a newline character, indicating end of message
            if (serialBuffer.toString().contains("\n")) {
                // Extract the complete message and process it
                String completeMessage = serialBuffer.toString().trim();

                // Display the message as a Toast and handle it
                runOnUiThread(() -> {
//                    Toast.makeText(MainActivity.this, "Received from Serial: " + completeMessage, Toast.LENGTH_SHORT).show();
                    handleSerialInput(completeMessage);
                });

                // Clear the buffer for the next message
                serialBuffer.setLength(0);
            }
        }

        @Override
        public void onRunError(Exception e) {
            runOnUiThread(() -> {
                appendLog("Error in serial communication: " + e.getMessage());
//                Toast.makeText(MainActivity.this, "USB connection error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });

            // Restart the connection if an error occurs
            closePort();
            setupUsbSerial(); // Reinitialize the serial connection
        }
    };

    private UsbSerialPort port;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        coordinatesTextView = findViewById(R.id.coordinatesTextView);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        GPSstatus = findViewById(R.id.StatusGPS);
        logTextView = findViewById(R.id.logTextView);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    coordinatesTextView.setText("Unable to get location");
                    return;
                }
                for (Location location : locationResult.getLocations()) {
                    updateLocationDisplay(location);
                }
            }
        };

        // Request location permission and start location updates immediately
        requestLocationPermission();
        locationUpdatesEnabled = true; // Enable location updates by default
        startLocationUpdates();

        // Setup USB serial communication
        setupUsbSerial();
        appendLog("Started sending location updates by default.");
    }



    private void requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
//        } else {
//            startLocationUpdates();
//        }
        }
    }

    private void startLocationUpdates() {
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 0)
                .setMinUpdateIntervalMillis(0)
                .setWaitForAccurateLocation(false)
                .setDurationMillis(Long.MAX_VALUE) // Request updates indefinitely
                .build();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);

            fusedLocationClient.getLastLocation().addOnSuccessListener(new OnSuccessListener<Location>() {
                @Override
                public void onSuccess(Location location) {
                    if (location != null) {
                        updateLocationDisplay(location);
                        GPSstatus.setText("Status : Signal Available");
                    } else {
                        coordinatesTextView.setText("Waiting for location...");
                        GPSstatus.setText("Status : No Signal");
                    }
                }
            });
        }
    }

    private void setupUsbSerial() {
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        if (availableDrivers.isEmpty()) {
//            Toast.makeText(this, "No USB device found", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "No USB device found");
            return;
        }

        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
        if (connection == null) {
//            Toast.makeText(this, "Permission required for USB device", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "USB permission required");
            return;
        }

        port = driver.getPorts().get(0);
        try {
            port.open(connection);
            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            Toast.makeText(this, "USB Serial port opened successfully", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "USB Serial port opened successfully");

            // Start the USB serial I/O manager with the listener
            SerialInputOutputManager usbIoManager = new SerialInputOutputManager(port, usbListener);
            Executors.newSingleThreadExecutor().execute(usbIoManager);
            Toast.makeText(this, "SerialInputOutputManager started", Toast.LENGTH_SHORT).show();

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error opening port", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Error opening USB serial port", e);
            closePort();
        }
    }
    private void handleSerialInput(String input) {
        runOnUiThread(() -> {
            appendLog("Received from Serial: " + input);

            if (input.equalsIgnoreCase("stop")) {
                if (locationUpdatesEnabled) {
                    locationUpdatesEnabled = false;
                    appendLog("Received stop command, disabling location updates");
                }
            }
        });
    }

    private void updateLocationDisplay(Location location) {
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        String locationText = "Latitude: " + latitude + "\nLongitude: " + longitude;
        coordinatesTextView.setText(locationText);

        // Log GPS coordinates
        Log.d(TAG, "GPS Coordinates: " + locationText);

        if (port != null) {
            // Add a delimiter, e.g., a newline character, at the end of the message
            String message = "Lat: " + latitude + ", Lon: " + longitude + "\n";
            try {
                // Send the message as a byte array
                port.write(message.getBytes(StandardCharsets.UTF_8), WRITE_WAIT_MILLIS);
                Log.d(TAG, "Sent GPS data over USB serial: " + message);
//                Toast.makeText(this, "Sent GPS data over USB serial: " + message, Toast.LENGTH_SHORT).show();

                // Append the log
                appendLog("Location updated: " + locationText);
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Error writing to port", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Error writing GPS data to USB port", e);
            }
        } else {
            Log.d("port", "Port not found");
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            } else {
                Toast.makeText(this, "Location permission is required", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void closePort() {
        try {
            if (port != null) {
                port.close();
                port = null;
            }
            Log.d(TAG, "USB Serial port closed");
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "Error closing USB port", e);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        closePort();
    }
    private void appendLog(String message) {
        String timeStamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        String logMessage = timeStamp + " - " + message + "\n";

        // Append message to logTextView
        logTextView.append(logMessage);

        // Scroll to the bottom of the ScrollView
        ScrollView scrollView = findViewById(R.id.LogGPS);
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }
}