package com.example.tanbeehwatch;

//import libraries
import android.app.Activity;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class MainActivity extends Activity implements SensorEventListener {
    //variables
    private DatabaseReference databaseRef;
    //access the sensors from it
    private SensorManager sensorManager;
    private Sensor accelerometer, heartRateSensor;
    //variables for monitoring
    private boolean waitingForReset = false;
    private boolean highAccelerationDetected = false;
    private boolean fallDetected = false;
    //cceleration bounded
    private static final float HIGH_ACCELERATION_THRESHOLD = 25f;
    private static final float STABLE_LOWER_BOUND = 9.0f;
    private static final float STABLE_UPPER_BOUND = 10.0f;
    //time for make action
    private static final long STABLE_TIME_REQUIRED = 10000; // لما الشخص يسقط ويثبت 10 ثواني بعدها يطلع الانذار
    private static final long RESET_FALL_TIME = 15000; //لما الشخص يتحرك ويرجه طبيعي بعدها نحسب 15 ثانية
    //save time of high acceleration + time of stable situation
    private long highAccelerationTime = 0;
    private long stableStartTime = 0;
    //delay and cancel detection
    private Handler handler = new Handler();
    private Runnable resetFallRunnable;

    private TextView heartRateText, statusText;
    //method for initialize variables and user interface
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        heartRateText = findViewById(R.id.heartRateText);
        statusText = findViewById(R.id.statusText);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
        }

        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }

        if (heartRateSensor != null) {
            sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_NORMAL);
        }
        //connected with firebase
        databaseRef = FirebaseDatabase.getInstance().getReference("Users/qasim/vitals");
    }//end onCreate method
    //method for save change in data from sensor
    @Override
    public void onSensorChanged(SensorEvent event) {
        int sensorType = event.sensor.getType();
        //calculate acceleration data
        if (sensorType == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            float acceleration = (float) Math.sqrt(x * x + y * y + z * z);
            long currentTime = System.currentTimeMillis();
            //لو التسارع اتخطى الحد الاعلى + ما كان في حالة سقوط + ما كان في وقت اعادة التعيين
            if (acceleration > HIGH_ACCELERATION_THRESHOLD && !fallDetected && !waitingForReset) {
                highAccelerationDetected = true;
                highAccelerationTime = currentTime;
                stableStartTime = 0;
                Log.d("FallCheck", "High acceleration detected");
            }//for the first fall
            //لو التسارع اتخطى الحد الاعلى + ما كان في حالة سقوط + ما كان في وقت اعادة التعيين
            if (highAccelerationDetected && !fallDetected && !waitingForReset) {
                //كان التسارع مساوي لتسارع الجاذبية الارضية = الشخص سقط وثبت بالارض بدون حركة
                if (acceleration >= STABLE_LOWER_BOUND && acceleration <= STABLE_UPPER_BOUND) {
                    if (stableStartTime == 0) {
                        stableStartTime = currentTime;
                    } else if (currentTime - stableStartTime >= STABLE_TIME_REQUIRED) {
                        fallDetected = true;
                        waitingForReset = true;
                        statusText.setText("Fall detected!🚨");
                        Log.d("FallDetector", "Fall approved!");
                        databaseRef.child("fallDetected").setValue(true);
                        if (resetFallRunnable != null) handler.removeCallbacks(resetFallRunnable);
                        resetFallRunnable = () -> {
                            fallDetected = false;
                            waitingForReset = false;
                            highAccelerationDetected = false;
                            stableStartTime = 0;
                            statusText.setText("Normal situation");
                            databaseRef.child("fallDetected").setValue(false);
                            Log.d("FallDetector", "Fellow back to move normally");
                        };}} else {
                    stableStartTime = 0;}
            }//for fall approve
            //الغاء حالة السقوط بعد ما الشخص يتحرك
            if (fallDetected && acceleration > STABLE_UPPER_BOUND && resetFallRunnable != null) {
                handler.postDelayed(resetFallRunnable, RESET_FALL_TIME);
            }//cancel fall
            // save acceleration in firebase
            databaseRef.child("Accelerometer").setValue((double) Math.round(acceleration * 100) / 100);
        }//end acceleration detect

        if (sensorType == Sensor.TYPE_HEART_RATE) {
            float heartRate = event.values[0];
            heartRateText.setText("Heart Rate: " + heartRate + " bpm");
            Log.d("HeartRateMonitor", "Heart Rate: " + heartRate);
            databaseRef.child("heartRate").setValue(heartRate);
        }//end heart rate detect
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        //empty
    }//end onAccuracyChanged

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(this);
    }//end onDestroy
}//end MainActivity class
