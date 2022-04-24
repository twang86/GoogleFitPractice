package test.google.fit;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.widget.TextViewCompat;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessOptions;
import com.google.android.gms.fitness.data.DataSource;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.data.Value;
import com.google.android.gms.fitness.request.DataSourcesRequest;
import com.google.android.gms.fitness.request.OnDataPointListener;
import com.google.android.gms.fitness.request.SensorRequest;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.material.snackbar.Snackbar;

import java.util.concurrent.TimeUnit;

import test.google.fit.logger.Log;
import test.google.fit.logger.LogView;
import test.google.fit.logger.LogWrapper;
import test.google.fit.logger.MessageOnlyLogFilter;

public class MainActivity extends AppCompatActivity {
    private enum FitActionRequestCode {
        FIND_DATA_SOURCES
    }

    public static final String TAG = "GoogleFitTest";

    private final FitnessOptions fitnessOptions = FitnessOptions.builder().addDataType(DataType.TYPE_STEP_COUNT_DELTA).build();
    private final boolean runningQOrLater = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q;
    private OnDataPointListener dataPointListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initializeLogging();
        checkPermissionsAndRun(FitActionRequestCode.FIND_DATA_SOURCES);
    }

    private void checkPermissionsAndRun(FitActionRequestCode fitActionRequestCode) {
        if (permissionApproved()) {
            fitSignIn(fitActionRequestCode);
        } else {
            requestRuntimePermissions(fitActionRequestCode);
        }
    }

    private void requestRuntimePermissions(FitActionRequestCode requestCode) {
        boolean shouldProvideRationale =
                ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACTIVITY_RECOGNITION);

        // Provide an additional rationale to the user. This would happen if the user denied the
        // request previously, but didn't check the "Don't ask again" checkbox.
        if (shouldProvideRationale) {
            Log.i(TAG, "Displaying permission rationale to provide additional context.");
            Snackbar.make(
                    findViewById(R.id.main_activity_view),
                    R.string.permission_rationale,
                    Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.ok, v -> ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACTIVITY_RECOGNITION}, requestCode.ordinal())).show();
        } else {
            Log.i(TAG, "Requesting permission");
            // Request permission. It's possible this can be auto answered if device policy
            // sets the permission in a given state or the user denied the permission
            // previously and checked "Never ask again".
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACTIVITY_RECOGNITION},
                    requestCode.ordinal());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length == 0)
            Log.i(TAG, "User interaction was cancelled.");
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Permission granted, signing into fitness");
            FitActionRequestCode fitActionRequestCode = FitActionRequestCode.values()[requestCode];
                fitSignIn(fitActionRequestCode);
        }
        else {
            Snackbar.make(
                    findViewById(R.id.main_activity_view),
                    R.string.permission_denied_explanation,
                    Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.settings, v -> {
                        Intent intent = new Intent();
                        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.fromParts("package",
                                BuildConfig.APPLICATION_ID, null);
                        intent.setData(uri);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    }).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK)
        {
            FitActionRequestCode request = FitActionRequestCode.values()[requestCode];
            performActionForRequestCode(request);
        }
        else
        {
            oAuthErrorMsg(requestCode, resultCode);
        }
    }

    private void oAuthErrorMsg(int requestCode, int resultCode) {
        String message = "error, resultCode " + requestCode + " requestCode " + requestCode;
        Log.e(TAG, message);
    }

    private void fitSignIn(FitActionRequestCode fitActionRequestCode) {
        if (oAuthPermissionsApproved()) {
            performActionForRequestCode(fitActionRequestCode);
        } else {
            GoogleSignIn.requestPermissions(
                    this,
                    fitActionRequestCode.ordinal(),
                    getGoogleAccount(), fitnessOptions);
        }
    }

    private void performActionForRequestCode(FitActionRequestCode requestCode) {
        switch (requestCode) {
            case FIND_DATA_SOURCES:
                findFitnessDataSources();
                break;
        }
    }

    private void findFitnessDataSources() { // [START find_data_sources]
        // Note: Fitness.SensorsApi.findDataSources() requires the ACCESS_FINE_LOCATION permission.
        Fitness.getSensorsClient(this, getGoogleAccount())
                .findDataSources(
                        new DataSourcesRequest.Builder()
                                .setDataTypes(DataType.TYPE_STEP_COUNT_DELTA)
                                .setDataSourceTypes(DataSource.TYPE_DERIVED)
                                .build())
                .addOnSuccessListener(dataSources -> {
                    for (DataSource dataSource : dataSources) {
                        Log.i(TAG, "Data source found: " + dataSource);
                        Log.i(TAG, "Data Source type: " + dataSource.getDataType());
                        // Let's register a listener to receive Activity data!
                        if (dataSource.getDataType().equals(DataType.TYPE_STEP_COUNT_DELTA)) {
                            Log.i(TAG, "Data source for LOCATION_SAMPLE found!  Registering.");
                            registerFitnessDataListener(dataSource, DataType.TYPE_STEP_COUNT_DELTA);
                        }
                        else
                        {
                            Log.e(TAG, "Wrong data type, looking for " + DataType.TYPE_STEP_COUNT_DELTA + " got " + dataSource.getDataType());
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "failed", e));
    }

    private void registerFitnessDataListener(DataSource dataSource, DataType dataType) {
        dataPointListener = dataPoint -> {
            for (Field field : dataPoint.getDataType().getFields()) {
                Value value = dataPoint.getValue(field);
                Log.i(TAG, "Detected DataPoint field: " + field.getName());
                Log.i(TAG, "Detected DataPoint value: " + value);
            }
        };

        Fitness.getSensorsClient(this, getGoogleAccount())
                .add(new SensorRequest.Builder()
                        .setDataSource(dataSource)
                        .setDataType(dataType)
                        .setSamplingRate(1, TimeUnit.SECONDS)
                        .build(), dataPointListener).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Log.i(TAG, "Listener registered!");
            } else {
                Log.e(TAG, "Listener not registered.", task.getException());
            }
        });
    }

    private void unregisterFitnessDataListener() {
        if (dataPointListener == null) {
            // This code only activates one listener at a time.  If there's no listener, there's
            // nothing to unregister.
            return;
        }
        // [START unregister_data_listener]
        // Waiting isn't actually necessary as the unregister call will complete regardless,
        // even if called from within onStop, but a callback can still be added in order to
        // inspect the results.
        Fitness.getSensorsClient(this, getGoogleAccount())
                .remove(dataPointListener)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && task.getResult()) {
                        Log.i(TAG, "Listener was removed!");
                    } else {
                        Log.i(TAG, "Listener was not removed.");
                    }
                });
    }

    private GoogleSignInAccount getGoogleAccount() {
        return GoogleSignIn.getAccountForExtension(this, fitnessOptions);
    }

    private boolean oAuthPermissionsApproved() {
        return GoogleSignIn.hasPermissions(getGoogleAccount(), fitnessOptions);
    }


    private boolean permissionApproved() {
        boolean approved = true;

        if (runningQOrLater) {
            approved = PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACTIVITY_RECOGNITION);
        }
        return approved;
    }

    /**
     * Initializes a custom log class that outputs both to in-app targets and logcat.
     */
    private void initializeLogging() { // Wraps Android's native log framework.
        LogWrapper logWrapper = new LogWrapper();
        // Using Log, front-end to the logging chain, emulates android.util.log method signatures.
        Log.setLogNode(logWrapper);
        // Filter strips out everything except the message text.
        MessageOnlyLogFilter msgFilter = new MessageOnlyLogFilter();
        logWrapper.setNext(msgFilter);
        // On screen logging via a customized TextView.
        LogView infoView = findViewById(R.id.logView);
        TextViewCompat.setTextAppearance(infoView, R.style.Log);
        infoView.setBackgroundColor(Color.WHITE);
        msgFilter.setNext(infoView);
        Log.i(TAG, "Ready");
    }
}