package com.example.testmaps

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.testmaps.databinding.ActivityMapsBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import com.parse.ParseObject
import com.parse.ParseQuery
import kotlin.math.floor


class MapsActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMapLongClickListener {

    private var map: GoogleMap? = null
    private lateinit var binding: ActivityMapsBinding

    // Camera position
    private var cameraPosition: CameraPosition? = null

    // The entry point to the Places API.
    private lateinit var placesClient: PlacesClient

    // The entry point to the Fused Location Provider.
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    // A default location (Sydney, Australia) and default zoom to use when location permission is
    // not granted.
    private val defaultLocation = LatLng(-33.8523341, 151.2106085)
    private var locationPermissionGranted = false

    // User's region code used for finding nearby markers
    private var regionCode: Number = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Construct a PlacesClient
        Places.initialize(applicationContext, BuildConfig.MAPS_API_KEY)
        placesClient = Places.createClient(this)

        // Construct a FusedLocationProviderClient.
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
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
    override fun onMapReady(googleMap: GoogleMap) {
        this.map = googleMap

        map!!.uiSettings.isZoomControlsEnabled = true
        map!!.uiSettings.isZoomGesturesEnabled = true

        // Prompt the user for permission.
        getLocationPermission()

        // Turn on the My Location layer and the related control on the map.
        updateLocationUI()

        // Get the current location of the device and set the position of the map.
        getDeviceLocation()

        // Listen for long click events for adding markers
        map?.setOnMapLongClickListener(this)
    }

    /**
     * Gets all saved markers from database and adds them to the map.
     */
    private fun loadSavedMarkers() {
        val query = ParseQuery.getQuery<ParseObject>("Location")
        query.whereEqualTo("region", regionCode)
        Log.d("loadSavedMarker", "regionCode = $regionCode")
        query.findInBackground { locations, e ->
            if (e == null) {
                for (location in locations) {
                    // Add marker
                    val latlng = LatLng(location.getDouble("latitude"), location.getDouble("longitude"))
                    val locationTitle = location.getString("title") ?: ""
                    val locationDescription = location.getString("description") ?: ""
                    displayMarker(latlng, locationTitle, locationDescription)
                }
            } else {
                Log.d("getLocation", "Error: $e")
            }
         }
    }

    /**
     * Gets the current location of the device, and positions the map's camera.
     */
    @SuppressLint("MissingPermission")
    private fun getDeviceLocation() {
        /*
         * Get the best and most recent location of the device, which may be null in rare
         * cases when a location is not available.
         */
        try {
            if (locationPermissionGranted) {
                val locationResult = fusedLocationProviderClient.lastLocation
                locationResult.addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // Set the map's camera position to the current location of the device.
                        val currentLocation = task.result
                        regionCode = floor((currentLocation.latitude+90)/10)*36 + floor(((currentLocation.longitude)+180)/10)
                        Log.d("findRegionCode", "region code is $regionCode")
                        if (currentLocation != null) {
                            map?.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                LatLng(currentLocation.latitude,
                                    currentLocation.longitude), DEFAULT_ZOOM.toFloat()))

                            // Load in marker locations from database
                            loadSavedMarkers()
                        }
                    } else {
                        Log.d(TAG, "Current location is null. Using defaults.")
                        Log.e(TAG, "Exception: %s", task.exception)
                        map?.moveCamera(CameraUpdateFactory
                            .newLatLngZoom(defaultLocation, DEFAULT_ZOOM.toFloat()))
                        map?.uiSettings?.isMyLocationButtonEnabled = false
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e("Exception: %s", e.message, e)
        }
    }

    /**
     * Prompts the user for permission to use the device location.
     */
    private fun getLocationPermission() {
        /*
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback,
         * onRequestPermissionsResult.
         */
        if (ContextCompat.checkSelfPermission(this.applicationContext,
                Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            locationPermissionGranted = true
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION)
        }
    }

    /**
     * Handles the result of the request for location permissions.
     */
    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>,
                                            grantResults: IntArray) {
        locationPermissionGranted = false
        when (requestCode) {
            PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION -> {

                // If request is cancelled, the result arrays are empty.
                if (grantResults.isNotEmpty() &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    locationPermissionGranted = true
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
        updateLocationUI()
    }

    /**
     * Updates the map's UI settings based on whether the user has granted location permission.
     */
    @SuppressLint("MissingPermission")
    private fun updateLocationUI() {
        if (map == null) {
            return
        }
        try {
            if (locationPermissionGranted) {
                map?.isMyLocationEnabled = true
                map?.uiSettings?.isMyLocationButtonEnabled = true
            } else {
                map?.isMyLocationEnabled = false
                map?.uiSettings?.isMyLocationButtonEnabled = false
                getLocationPermission()
            }
        } catch (e: SecurityException) {
            Log.e("Exception: %s", e.message, e)
        }
    }

    override fun onMapLongClick(p0: LatLng) {
        showAlertDialogForPoint(p0)
    }

    /**
     * Display alert that adds marker to map.
     */
    private fun showAlertDialogForPoint(point: LatLng) {
        // inflate message_item.xml view
        val messageView: View =
            LayoutInflater.from(this).inflate(R.layout.message_item, null)
        // Create alert dialog builder
        val alertDialogBuilder: AlertDialog.Builder = AlertDialog.Builder(this)
        // set message_item.xml to AlertDialog builder
        alertDialogBuilder.setView(messageView)

        // Create alert dialog
        val alertDialog: AlertDialog = alertDialogBuilder.create()

        // Configure dialog button (OK)
        alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, "OK"
        ) { _, _ ->
            // Extract content from alert dialog
            val title = (alertDialog.findViewById(R.id.etTitle) as EditText).text.toString()
            val snippet = (alertDialog.findViewById(R.id.etSnippet) as EditText).text.toString()
            addMarker(point, title, snippet)
        }

        // Configure dialog button (Cancel)
        alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel",
            DialogInterface.OnClickListener { dialog, id -> dialog.cancel() })

        // Display the dialog
        alertDialog.show()
    }

    private fun addMarker(point: LatLng, title: String, snippet: String) {
        displayMarker(point, title, snippet)
        val regionC = floor((point.latitude+90)/10)*36 + floor(((point.longitude)+180)/10)
        // Add marker to the database
        val location = ParseObject("Location")
        location.put("title", title)
        location.put("description", snippet)
        location.put("latitude", point.latitude)
        location.put("longitude", point.longitude)
        location.put("region", regionC)
        location.saveInBackground()
    }

    private fun displayMarker(point: LatLng, title:String, snippet: String) {
        // Define color of marker icon
        val defaultMarker =
            BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)

        // Creates and adds marker to the map
        map!!.addMarker(
            MarkerOptions()
                .position(point)
                .title(title)
                .snippet(snippet)
                .icon(defaultMarker)
        )
    }

    companion object {
        private val TAG = MapsActivity::class.java.simpleName
        private const val DEFAULT_ZOOM = 15
        private const val PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1

        // Keys for storing activity state.
        private const val KEY_CAMERA_POSITION = "camera_position"
        private const val KEY_LOCATION = "location"
    }
}