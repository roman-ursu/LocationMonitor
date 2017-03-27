package com.romio.locationtest;

import android.app.Application;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.crashlytics.android.Crashlytics;
import com.firebase.jobdispatcher.Constraint;
import com.firebase.jobdispatcher.FirebaseJobDispatcher;
import com.firebase.jobdispatcher.GooglePlayDriver;
import com.firebase.jobdispatcher.Job;
import com.firebase.jobdispatcher.Lifetime;
import com.firebase.jobdispatcher.RetryStrategy;
import com.firebase.jobdispatcher.Trigger;
import com.google.android.gms.common.api.GoogleApiClient;
import com.j256.ormlite.android.apptools.OpenHelperManager;
import com.romio.locationtest.data.db.DBHelper;
import com.romio.locationtest.data.db.DBManager;
import com.romio.locationtest.data.db.DataBaseHelper;
import com.romio.locationtest.data.repository.AreasManager;
import com.romio.locationtest.data.repository.AreasManagerImpl;
import com.romio.locationtest.data.repository.MockTrackingManager;
import com.romio.locationtest.data.repository.TrackingManager;
import com.romio.locationtest.geofence.GeofenceManager;
import com.romio.locationtest.geofence.GeofenceManagerImpl;
import com.romio.locationtest.service.UploadTrackingDataJobService;
import com.romio.locationtest.tracking.LocationManager;
import com.romio.locationtest.tracking.LocationManagerImpl;
import com.romio.locationtest.utils.NetworkManager;
import com.romio.locationtest.utils.NetworkManagerImpl;

import io.fabric.sdk.android.Fabric;

/**
 * Created by roman on 1/9/17
 */

public class LocationMonitorApp extends Application implements DBHelper {

    public static final String TAG = LocationMonitorApp.class.getSimpleName();

    private static final String LOCATION_DATA_UPLOAD = "com.romio.locationtest.location_data_upload";
    private static final String JOB_TAG = LocationMonitorApp.class.getSimpleName();

    private AreasManager areasManager;
    private TrackingManager trackingManager;
    private NetworkManager networkManager;
    private DataBaseHelper databaseHelper;
    private LocationManager locationManager;
    private volatile GoogleApiClient googleApiClient;
    private volatile GeofenceManager geofenceManager;

    @Override
    public void onCreate() {
        super.onCreate();
        Fabric.with(this, new Crashlytics());

        initUploadDataScheduler();
    }

    public AreasManager getAreasManager() {
        if (areasManager == null) {
            areasManager = new AreasManagerImpl(this, this, getNetworkManager());
        }

        return areasManager;
    }

    public TrackingManager getTrackingManager() {
        if (trackingManager == null) {
//            trackingManager = new TrackingManagerImpl(this, networkManager, this);
            trackingManager = new MockTrackingManager(this); // TODO: 3/24/17 TESTING
        }

        return trackingManager;
    }

    public LocationManager getLocationManager() {
        if (locationManager == null) {
            locationManager = new LocationManagerImpl(this);
        }

        return locationManager;
    }

    public GeofenceManager getGeofenceManager() {
        if (geofenceManager == null) {
            geofenceManager = new GeofenceManagerImpl(this, getLocationManager());
        }

        return geofenceManager;
    }

    public DBHelper getDBHelper() {
        return this;
    }

    @Override
    public DBManager getDbManager() {
        if (databaseHelper == null) {
            databaseHelper = OpenHelperManager.getHelper(this, DataBaseHelper.class);
        }

        return databaseHelper;
    }

    @Override
    public void release() {
        if (databaseHelper != null) {
            OpenHelperManager.releaseHelper();
            databaseHelper = null;
        }
    }

    public GoogleApiClient getGoogleApiClient() {
        return googleApiClient;
    }

    public void setGoogleApiClient(GoogleApiClient googleApiClient) {
        this.googleApiClient = googleApiClient;
    }

    private NetworkManager getNetworkManager() {
        if (networkManager == null) {
            networkManager = new NetworkManagerImpl(this);
        }

        return networkManager;
    }

    /**
     * data upload section
     */
    public void initUploadDataScheduler() {
        if (!isDataUploadServiceSet()) {
            FirebaseJobDispatcher dispatcher = new FirebaseJobDispatcher(new GooglePlayDriver(this));
            Job myJob = dispatcher.newJobBuilder()
                    .setService(UploadTrackingDataJobService.class)
                    .setTag(JOB_TAG)
                    .setRecurring(true)
                    .setLifetime(Lifetime.FOREVER)
                    .setTrigger(Trigger.executionWindow(60, 180))
                    .setReplaceCurrent(false)
                    .setRetryStrategy(RetryStrategy.DEFAULT_LINEAR)
                    .setConstraints(Constraint.ON_ANY_NETWORK)
                    .build();

            dispatcher.mustSchedule(myJob);

            setDataUploadServiceState(true);
        }
    }

    private boolean isDataUploadServiceSet() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        return preferences.getBoolean(LOCATION_DATA_UPLOAD, false);
    }

    private void setDataUploadServiceState(boolean isSet) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        preferences.edit().putBoolean(LOCATION_DATA_UPLOAD, isSet).apply();
    }
}
