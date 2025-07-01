package com.sulisea.arc1960cordinates

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.math.roundToInt
import org.locationtech.proj4j.*

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun LocationScreen(
    fusedLocationClient: FusedLocationProviderClient,
    locationPermissionRequest: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    context: Context
) {
    var location by remember { mutableStateOf<Location?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val hasLocationPermission = remember {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestLocation() {
        if (!hasLocationPermission) {
            locationPermissionRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }

        isLoading = true
        errorMessage = null

        val priority = Priority.PRIORITY_HIGH_ACCURACY
        val cancellationToken = CancellationTokenSource()

        try {
            fusedLocationClient.getCurrentLocation(priority, cancellationToken.token)
                .addOnSuccessListener { currentLocation ->
                    location = currentLocation
                    isLoading = false
                }
                .addOnFailureListener { exception ->
                    errorMessage = "Error getting location: ${exception.message}"
                    isLoading = false
                }
        } catch (e: SecurityException) {
            errorMessage = "Location permission not granted"
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "GPS Coordinates Locator",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = "Location",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (isLoading) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Fetching location...")
        } else if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else if (location != null) {
            val lat = location!!.latitude
            val lng = location!!.longitude
            val alt = location!!.altitude

            // Get accuracy information
            val hasAccuracy = location!!.hasAccuracy()
            val accuracy = if (hasAccuracy) "±${location!!.accuracy.toInt()} m" else "Unknown"
            val hasAltitude = location!!.hasAltitude()
            val verticalAccuracy = if (location!!.hasVerticalAccuracy()) "±${location!!.verticalAccuracyMeters.toInt()} m" else "Unknown"
            
            // Display WGS84 coordinates and UTM zone with accuracy
            val utmZone = calculateUTMZone(lat, lng)
            CoordinateItem(
                "Latitude (WGS84)", 
                "${lat.toFormattedString()}°",
                "Horizontal Accuracy: $accuracy"
            )
            CoordinateItem(
                "Longitude (WGS84)", 
                "${lng.toFormattedString()}°",
                "Horizontal Accuracy: $accuracy"
            )
            CoordinateItem(
                "Altitude", 
                "${alt.roundToInt()} m",
                "Vertical Accuracy: $verticalAccuracy"
            )
            CoordinateItem(
                "UTM Zone", 
                utmZone,
                "Calculated from WGS84 coordinates"
            )

            // Convert to Arc 1960 (UTM Zone 37S)
            // Convert to Arc 1960 (UTM Zone 37S)
            val arc1960Coords = convertToArc1960(lat, lng)
            if (arc1960Coords != null) {
                // Estimate accuracy in Arc 1960 (assuming similar to WGS84 for this projection)
                val arc1960Accuracy = if (hasAccuracy) "±${(location!!.accuracy * 1.5).toInt()} m" else "Unknown"
                
                CoordinateItem(
                    "Easting (Arc 1960 / UTM ${utmZone})", 
                    "${arc1960Coords.first.toFormattedString(2)} m",
                    "Estimated Accuracy: $arc1960Accuracy"
                )
                CoordinateItem(
                    "Northing (Arc 1960 / UTM ${utmZone})", 
                    "${arc1960Coords.second.toFormattedString(2)} m",
                    "Estimated Accuracy: $arc1960Accuracy"
                )
            }

        } else {
            Text(
                text = "No location data available.\nTap the button below to get your current location.",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { requestLocation() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 100.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
            Text("Get Current Location")
        }

    }

}

@Composable
fun CoordinateItem(label: String, value: String, accuracyInfo: String? = null) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        accuracyInfo?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Divider()
    }
}

private fun Double.toFormattedString(digits: Int = 6): String {
    return "%.${digits}f".format(this)
}

/**
 * Calculates the UTM zone number (1-60) for given coordinates
 * @param latitude Latitude in decimal degrees
 * @param longitude Longitude in decimal degrees
 * @return String representing the UTM zone (e.g., "36N", "37S")
 */
private fun calculateUTMZone(latitude: Double, longitude: Double): String {
    // UTM zone number (1-60)
    val zoneNumber = ((longitude + 180) / 6).toInt() + 1
    
    // Determine hemisphere (N/S)
    val hemisphere = if (latitude >= 0) "N" else "S"
    
    return "$zoneNumber$hemisphere"
}

/**
 * Converts WGS84 coordinates to Arc 1960 / UTM zone 37S
 * @param latitude Latitude in decimal degrees (WGS84)
 * @param longitude Longitude in decimal degrees (WGS84)
 * @return Pair of (easting, northing) in meters or null if conversion fails
 */
private fun convertToArc1960(latitude: Double, longitude: Double): Pair<Double, Double>? {
    return try {
        val crsFactory = CRSFactory()
        
        // Define coordinate reference systems
        val wgs84 = crsFactory.createFromName("EPSG:4326")  // WGS84
        val arc1960 = crsFactory.createFromParameters(
            "Arc 1960 / UTM zone 37S",
            "+proj=utm +zone=37 +south +ellps=clrk80 +towgs84=-169.5,-19.4,-99.4,0,0,0,0 +units=m +no_defs"
        )
        
        // Create coordinate transformation
        val transform = CoordinateTransformFactory().createTransform(wgs84, arc1960)
        
        // Create source point (WGS84)
        val srcPoint = ProjCoordinate(longitude, latitude)
        val dstPoint = ProjCoordinate()
        
        // Perform the transformation
        transform.transform(srcPoint, dstPoint)
        
        // Return easting (x) and northing (y)
        Pair(dstPoint.x, dstPoint.y)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}