package code.dennis.kugelmann.lpevents;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.provider.Settings;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.Toast;

import com.firebase.client.AuthData;
import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.ValueEventListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        com.google.android.gms.location.LocationListener {

    private static final int REQUEST_LOCATION = 2;
    public static final int INTERVAL = 10000; //Deafult 300000
    public static final int FASTEST_INTERVAL = 10000;
    private GoogleMap mMap;
    private Firebase myFirebaseRef;
    private Firebase userRef;
    private HashMap<String, String> locations = new HashMap<>();
    private String uid, provider;
    private ArrayList<Marker> marker = new ArrayList<>();
    private boolean tracking;
    private LocationManager locationManager;
    private MyLocationListener myListener;
    private Criteria criteria;
    private LocationRequest locationRequest;
    private GoogleApiClient mGoogleApiClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        tracking = true;
        uid = getDeviceId(this);

        buildGoogleApiClient();
        setUpFirebase();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        MenuItem item = menu.findItem(R.id.trackMovesItem);
        item = item.setActionView(R.layout.switch_layout);
        RelativeLayout layout = (RelativeLayout) item.getActionView();
        Switch aSwitch = (Switch) layout.getChildAt(0);
        aSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked && !tracking) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                                Toast.makeText(MapsActivity.this,
                                        "Location permission is required for using with the app.",
                                        Toast.LENGTH_SHORT).show();
                            }
                            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION);
                        }
                    }
                    //TODO Enable LocationRequest
                    startLocationUpdates();
                    boolean connected = mGoogleApiClient.isConnected();
                    Toast.makeText(MapsActivity.this, "Tracking enabled", Toast.LENGTH_SHORT).show();
                    tracking = true;
                } else if (!isChecked && tracking) {
                    //TODO Disable LocationRequest
                    boolean connected = mGoogleApiClient.isConnected();
                    stopLocationUpdates();
                    Toast.makeText(MapsActivity.this, "Tracking disabled", Toast.LENGTH_SHORT).show();
                    tracking = false;
                }
            }
        });
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_LOCATION) {
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(MapsActivity.this, "Permission was not granted, so App was closed", Toast.LENGTH_SHORT).show();
                System.runFinalization();
                System.exit(0);
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void setUpLocationManager() {
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_COARSE);
        criteria.setCostAllowed(false);

        provider = locationManager.getBestProvider(criteria, false);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    Toast.makeText(MapsActivity.this,
                            "Location permission is requiered for using with the app.",
                            Toast.LENGTH_SHORT).show();
                }
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION);
            }
        }

        final Location location = locationManager.getLastKnownLocation(provider);
        myListener = new MyLocationListener();

        if (location != null) {
            myListener.onLocationChanged(location);
        } else {
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);
        }

        locationManager.requestLocationUpdates(provider, 10000, 5, myListener);
        tracking = true;
    }

    private void createLocationRequest() {
        locationRequest = new LocationRequest();
        locationRequest.setInterval(INTERVAL);
        locationRequest.setFastestInterval(FASTEST_INTERVAL);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    private void setUpFirebase() {
        Firebase.setAndroidContext(getBaseContext());
        myFirebaseRef = new Firebase("https://lp-events.firebaseio.com");

        Firebase.AuthResultHandler authResultHandler = new Firebase.AuthResultHandler() {
            @Override
            public void onAuthenticated(AuthData authData) {
                userRef = myFirebaseRef.child(uid);
            }

            @Override
            public void onAuthenticationError(FirebaseError firebaseError) {
            }
        };

        myFirebaseRef.authAnonymously(authResultHandler);

        myFirebaseRef.addValueEventListener(new ValueEventListener() {

            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if (dataSnapshot.getValue() != null) {
                    HashMap map = (HashMap) dataSnapshot.getValue();

                    for (Object o : map.entrySet()) {
                        Map.Entry entry = (Map.Entry) o;
                        locations.put((String) entry.getKey(), (String) entry.getValue());
                    }
                    updateMap();
                }
            }


            @Override
            public void onCancelled(FirebaseError firebaseError) {
            }
        });
    }

    private void updateMap() {
        Iterator iterator = locations.entrySet().iterator();
        removeMarker();

        while (iterator.hasNext()) {
            Map.Entry entry = (Map.Entry) iterator.next();
            String loc = (String) entry.getValue();
            String[] latLng = loc.split("/");
            double lat = Double.valueOf(latLng[0]);
            double lng = Double.valueOf(latLng[1]);
            Marker m = mMap.addMarker(new MarkerOptions()
                    .position(new LatLng(lat, lng))
                    .icon(BitmapDescriptorFactory.defaultMarker()));
            marker.add(m);
        }
    }

    private void removeMarker() {
        for (int i=0; i<marker.size(); i++) {
            marker.get(i).remove();
        }
        marker.clear();
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        updateMap();
    }

    @Override
    public void onConnected(Bundle bundle) {
        if (tracking) {
            startLocationUpdates();
        }
        Toast.makeText(MapsActivity.this, "Connected", Toast.LENGTH_SHORT).show();
    }

    private void startLocationUpdates() {
        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi
                    .requestLocationUpdates(mGoogleApiClient, locationRequest, this);
        }
    }

    private void stopLocationUpdates() {
        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi
                    .removeLocationUpdates(mGoogleApiClient, this);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    private synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
        createLocationRequest();
    }

    @Override
    public void onLocationChanged(Location location) {
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();

        if (userRef != null) {
            userRef.setValue(latitude + "/" + longitude);
        }

        Toast.makeText(MapsActivity.this, "Location! " + latitude + "/" + longitude,
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

    }

    private class MyLocationListener implements LocationListener {

        @Override
        public void onLocationChanged(Location location) {
            // Initialize the location fields
            double latitude = location.getLatitude();
            double longitude = location.getLongitude();

            if (userRef != null) {
                userRef.setValue(latitude + "/" + longitude);
            }

            Toast.makeText(MapsActivity.this, "Location! " + latitude + "/" + longitude,
                    Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }

    }

    public String getDeviceId(Context context) {
        String id = getUniqueID(context);
        if (id == null)
            id = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        return id;
    }

    private String getUniqueID(Context context) {

        String telephonyDeviceId = "NoTelephonyId";
        String androidDeviceId = "NoAndroidId";

        // get telephony id
        try {
            final TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            telephonyDeviceId = tm.getDeviceId();
            if (telephonyDeviceId == null) {
                telephonyDeviceId = "NoTelephonyId";
            }
        } catch (Exception ignored) {
        }

        // get internal android device id
        try {
            androidDeviceId = android.provider.Settings.Secure.getString(context.getContentResolver(),
                    android.provider.Settings.Secure.ANDROID_ID);
            if (androidDeviceId == null) {
                androidDeviceId = "NoAndroidId";
            }
        } catch (Exception ignored) {

        }

        // build up the uuid
        try {

            return getStringIntegerHexBlocks(androidDeviceId.hashCode())
                    + "-"
                    + getStringIntegerHexBlocks(telephonyDeviceId.hashCode());
        } catch (Exception e) {
            return "0000-0000-1111-1111";
        }
    }

    public String getStringIntegerHexBlocks(int value) {
        String result = "";
        String string = Integer.toHexString(value);

        int remain = 8 - string.length();
        char[] chars = new char[remain];
        Arrays.fill(chars, '0');
        string = new String(chars) + string;

        int count = 0;
        for (int i = string.length() - 1; i >= 0; i--) {
            count++;
            result = string.substring(i, i + 1) + result;
            if (count == 4) {
                result = "-" + result;
                count = 0;
            }
        }

        if (result.startsWith("-")) {
            result = result.substring(1, result.length());
        }

        return result;
    }
}
