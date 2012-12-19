package se.jayelh;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimerTask;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Toast;
//import java.text.DateFormat;

// Need the following import to get access to the app resources, since this
// class is in a sub-package.
//import com.example.android.apis.R;

/**
 * This is an example of implementing an application service that runs locally
 * in the same process as the application. The {@link LocalServiceController}
 * and {@link LocalServiceBinding} classes show how to interact with the
 * service.
 * 
 * <p>
 * Notice the use of the {@link NotificationManager} when interesting things
 * happen in the service. This is generally how background services should
 * interact with the user, rather than doing something more disruptive such as
 * calling startActivity().
 */
public class TimeAnnouncerService extends Service implements
        TextToSpeech.OnInitListener {

    private NotificationManager mNM;
    private TextToSpeech tts;
    private RefreshHandler sayTimeHandler = new RefreshHandler();
    // This is the object that receives interactions from clients. See
    // RemoteService for a more complete example.
    private final IBinder mBinder = new LocalBinder();
    private int intervallBettwenAnouncment = 1;
    // 10s,20s,30s, 1m, | 5m, 10m, 20m, 30m, 1h
    private static int timeMarks[][] = {
            { 0, 10000, 20000, 30000, 40000, 50000 },
            { 0, 20000, 40000 },
            { 0, 30000 },
            { 0 },
            { 0, 300000, 600000, 900000, 1200000, 1500000, 1800000, 2100000,
                    2400000, 2700000, 3000000, 3300000 },
            { 0, 600000, 1200000, 1800000, 2400000, 3000000 },
            { 0, 1200000, 2400000 }, { 0, 1800000 }, { 0 }

    };

    private PowerManager.WakeLock wl;
    private boolean started;
    private boolean stoped;
    private boolean saySeconds;

    /**
     * Class for clients to access. Because we know this service always runs in
     * the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        TimeAnnouncerService getService() {
            return TimeAnnouncerService.this;
        }
    }

    @Override
    public void onCreate() {
        Calendar rightNow = Calendar.getInstance();
        rightNow.get(Calendar.MILLISECOND);

        started = false;
        stoped = false;

        Log.v("TimeAnnouncerService",
                "onCreate\nTime: " + rightNow.get(Calendar.SECOND)
                        + rightNow.get(Calendar.MILLISECOND));
        Log.v("TimeAnnouncerService", "\nstarted: " + started + " stoped: "
                + stoped);
        mNM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        
//        startForeground(id, notification)
        // // Display a notification about us starting. We put an icon in the
        // // status bar.
        // showNotification();
        tts = new TextToSpeech(this, this);

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "My Tag");
        wl.acquire();
    }

    @Override
    public void onDestroy() {
        Log.v("TimeAnnouncerService", "onDestroy");
        // // Cancel the persistent notification.
         mNM.cancel(R.string.clock_service_started);

        wl.release();
        tts.shutdown();

        // Tell the user we stopped.
        Toast.makeText(this, R.string.clock_service_stopped, Toast.LENGTH_SHORT)
                .show();
    }

    @Override
    public IBinder onBind(Intent intent) {
        Calendar rightNow = Calendar.getInstance();
        rightNow.get(Calendar.MILLISECOND);

        Log.v("onBind",
                "\nTime: " + rightNow.get(Calendar.SECOND)
                        + rightNow.get(Calendar.MILLISECOND));

        return mBinder;
    }


    public void onInit(int status) {

    }

    /**
     * Show a notification while this service is running.
     * @return 
     */
    private Notification createNotification() {
        CharSequence text = getText(R.string.clock_service_started);
        
        // Set the icon, scrolling text and timestamp
        Notification notification = new Notification(R.drawable.stat_sample,
                text, System.currentTimeMillis());
        
        // The PendingIntent to launch our activity if the user selects this
        // notification
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, AutomaticTimeAnnouncer.class), 0);
        
        // Set the info for the views that show in the notification panel.
        notification.setLatestEventInfo(this,
                getText(R.string.clock_service_label), text, contentIntent);
        
        return notification;
    }
    
    private void timeCheck() {

        sayTime();
    }

    private int timeToSleep() {
        Calendar rightNow = Calendar.getInstance();
        int result = 1000;
        boolean timeFound = false;

        int nowInMille;
        if (intervallBettwenAnouncment < 4) {
            nowInMille = rightNow.get(Calendar.SECOND) * 1000
                    + rightNow.get(Calendar.MILLISECOND);
            Log.v("timeCheck", "\nTime now: " + nowInMille);
        } else {
            nowInMille = rightNow.get(Calendar.MINUTE) * 1000 * 60
                    + rightNow.get(Calendar.SECOND) * 1000
                    + rightNow.get(Calendar.MILLISECOND);
        }

        Log.v("timeToSleep", "\ninterval:" + intervallBettwenAnouncment);

        for (int i = 1; i < timeMarks[intervallBettwenAnouncment].length; i++) {
            if (nowInMille <= timeMarks[intervallBettwenAnouncment][i]) {
                Log.v("timeCheck", "\nnextTime: "
                        + timeMarks[intervallBettwenAnouncment][i] + " i: " + i);

                timeFound = true;
                result = (timeMarks[intervallBettwenAnouncment][i] - nowInMille);
                break;
            }
        }
        if (!timeFound) {
            Log.v("timeCheck", "\nnextTime: "
                    + timeMarks[intervallBettwenAnouncment][0]);
            if (intervallBettwenAnouncment < 4) {
                result = 60000 - nowInMille;
            } else {
                result = 3600000 - nowInMille;
            }
        }

        Log.v("timeToSleep", "\nresult: " + result);
        return result;
    }

    private void sayTime() {
        Calendar rightNow = Calendar.getInstance();
        rightNow.get(Calendar.MILLISECOND);

        Log.v("sayTime", "\nStart Time: " + rightNow.get(Calendar.SECOND)
                + rightNow.get(Calendar.MILLISECOND));
        StringBuffer time = new StringBuffer();

        time.append(rightNow.get(Calendar.HOUR_OF_DAY));
        time.append(", ");
        time.append(rightNow.get(Calendar.MINUTE));
        if ((intervallBettwenAnouncment < 3) || saySeconds) {
            time.append(", and ");
            time.append(rightNow.get(Calendar.SECOND));
        }
        tts.speak(time.toString(), TextToSpeech.QUEUE_FLUSH, null);

        sayTimeHandler.sleep(timeToSleep());

        rightNow = Calendar.getInstance();
        rightNow.get(Calendar.MILLISECOND);

        Log.v("sayTime", "\nEnd Time: " + rightNow.get(Calendar.SECOND)
                + rightNow.get(Calendar.MILLISECOND));
    }

    public void start() {
        Log.v("TimeAnnouncerService", "start! started: " + started
                + " stoped: " + stoped);
        if (!started) {
            started = true;
            stoped = false;
            // Display a notification about us starting. We put an icon in the
            // status bar.
            Notification notification = createNotification();
            
            //Start the service in the foreground, otherwise it might be killed!
            startForeground(R.string.clock_service_started, notification);


            Calendar rightNow = Calendar.getInstance();
            rightNow.get(Calendar.MILLISECOND);

            Log.v("TimeAnnouncerService",
                    "\nTime: " + rightNow.get(Calendar.SECOND)
                            + rightNow.get(Calendar.MILLISECOND));
            sayTime();
        }
    }

    public void setPrefs(int sleepIntervall, boolean saySeconds, String language) {
        Calendar rightNow = Calendar.getInstance();
        rightNow.get(Calendar.MILLISECOND);
        Log.v("TimeAnnouncerService",
                "setPrefs\nTime: " + rightNow.get(Calendar.SECOND)
                        + rightNow.get(Calendar.MILLISECOND));
        Log.v("TimeAnnouncerService", "setPrefs sleep: " + sleepIntervall
                + "  lang: " + language);
        intervallBettwenAnouncment = sleepIntervall;
        if (intervallBettwenAnouncment < 0 || intervallBettwenAnouncment > 8) {
            intervallBettwenAnouncment = 1;
        }
        if (tts.isLanguageAvailable(new Locale(language)) >= TextToSpeech.LANG_AVAILABLE) {
            tts.setLanguage(new Locale(language));
        }

        this.saySeconds = saySeconds;

        this.restart();

    }

    public void stop() {
        Log.v("TimeAnnouncerService", "start! started: " + started
                + " stoped: " + stoped);
        sayTimeHandler.cancel();
        
        stopForeground(true);
        // Cancel the persistent notification.
        
//        mNM.cancel(R.string.clock_service_started);
        // tts.shutdown();
        started = false;
        stoped = true;
    }

    public void restart() {
        Log.v("TimeAnnouncerService", "restart!");
        sayTimeHandler.cancel();
        // Cancel the persistent notification.
        mNM.cancel(R.string.clock_service_started);

        sayTimeHandler.sleep(timeToSleep());
    }

    public synchronized boolean isAnnouncerRunning() {
        return started;
    }

    class SayTime extends TimerTask {
        public void run() {
            
        }
    }
    
    class RefreshHandler extends Handler {
        
        @Override
        public void handleMessage(Message msg) {
            TimeAnnouncerService.this.timeCheck();
        }
        
        public void sleep(long delayMillis) {
            
            this.removeMessages(0);
            
            sendMessageDelayed(obtainMessage(0), delayMillis);
            
        }
        
        public void cancel() {
            this.removeMessages(0);
        }
    }
}