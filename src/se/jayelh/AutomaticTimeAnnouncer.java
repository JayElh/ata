package se.jayelh;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;
import android.widget.ToggleButton;
//import android.util.Log;

public class AutomaticTimeAnnouncer extends Activity implements
        OnSharedPreferenceChangeListener {
    private static final int MY_DATA_CHECK_CODE = 1;
    private static final int ABOUT_DIALOG = 1;

    private SharedPreferences prefs;

    private TimeAnnouncerService mBoundService;

    private Integer intervallBettwenAnouncment;
    private String language;

    protected boolean mIsBound;
    private boolean saySeconds;
    // TODO: add to string or something
    private static final String KEY_MY_PREFERENCE = "sayTimeIntervallPreference";
    private static final String KEY_SAY_SECONDS = "saySeconds";
    private static final String KEY_MY_PREFERENCE2 = "language_preference";
    private static final String KEY_PREF_24h = "24hFormat";

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.v("AutomaticTimeAnnouncer", "onCreate");

        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // mText = (TextView) findViewById(R.id.textview);
        // mText.append("\nThis is a test\n");

        ToggleButton toggleButton = (ToggleButton) findViewById(R.id.OnOff);

        toggleButton.setOnClickListener(mOnOffListener);

        Intent checkIntent = new Intent();
        checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);

        startActivityForResult(checkIntent, MY_DATA_CHECK_CODE);

        prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        // Set up a listener whenever a key changes
        prefs.registerOnSharedPreferenceChangeListener(this);
        getPrefs();

        startService(new Intent(AutomaticTimeAnnouncer.this,
                TimeAnnouncerService.class));

        if (!mIsBound) {
            // class name because we want a specific service implementation
            // that
            // we know will be running in our own process (and thus won't be
            // supporting component replacement by other applications).
            bindService(new Intent(AutomaticTimeAnnouncer.this,
                    TimeAnnouncerService.class), mConnection,
                    Context.BIND_AUTO_CREATE);
            mIsBound = true;

        }

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        // Log.v("AutomaticTimeAnnouncer", "onDestroy");

        if (mIsBound) {
            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;

            if (!mBoundService.isAnnouncerRunning()) {
                // Log.v("AutomaticTimeAnnouncer", "onDestroy stop service");
                stopService(new Intent(AutomaticTimeAnnouncer.this,
                        TimeAnnouncerService.class));
            }
        }

    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == MY_DATA_CHECK_CODE) {
            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                // success, create the TTS instance
                // mTts = new TextToSpeech(this, this);
            } else {
                // missing data, install it
                Intent installIntent = new Intent();
                installIntent
                        .setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                startActivity(installIntent);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the currently selected menu XML resource.
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.title_only, menu);

        // // Change instructions
        // mInstructionsText.setText(getResources().getString(
        // R.string.menu_from_xml_instructions_go_back));

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

        case R.id.settings:

            Intent settingsActivity = new Intent(getBaseContext(),
                    PreferencesFromXml.class);
            startActivity(settingsActivity);
            return true;

        case R.id.about:

            showDialog(ABOUT_DIALOG);

            return true;

        }

        return false;
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
            String key) {
        Toast.makeText(this, R.string.clock_service_changed, Toast.LENGTH_SHORT)
                .show();
        if (key.equals(KEY_MY_PREFERENCE)) {

        }
        getPrefs();
        sendPrefsToService();
    }

    private void getPrefs() {
        // Get the xml/preferences.xml preferences

        String string = prefs.getString(KEY_MY_PREFERENCE, "10000");
        intervallBettwenAnouncment = Integer.valueOf(string);

        saySeconds = prefs.getBoolean(KEY_SAY_SECONDS, false);

        language = prefs.getString(KEY_MY_PREFERENCE2, "en_US");

        prefs.getBoolean(KEY_PREF_24h, true);

    }

    private void sendPrefsToService() {
        if (mBoundService != null) {
            mBoundService.setPrefs(intervallBettwenAnouncment, saySeconds,
                    language);
        }
    }

    private OnClickListener mOnOffListener = new OnClickListener() {
        public void onClick(View v) {
            // Log.v("AutomaticTimeAnnouncer", "OnOff");
            if (mBoundService.isAnnouncerRunning()) {
                // Log.v("AutomaticTimeAnnouncer", "stop");

                if (mBoundService != null) {
                    mBoundService.stop();
                    Toast.makeText(AutomaticTimeAnnouncer.this,
                            R.string.clock_service_stopped, Toast.LENGTH_SHORT)
                            .show();
                }
            } else {
                // Log.v("AutomaticTimeAnnouncer", "click start! mIsBound: "
                // + mIsBound);
                if (mBoundService != null) {

                    sendPrefsToService();
                    mBoundService.start();
                    Toast.makeText(AutomaticTimeAnnouncer.this,
                            R.string.clock_service_started, Toast.LENGTH_SHORT)
                            .show();

                }
            }
        }

    };

    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            // This is called when the connection with the service has been
            // established, giving us the service object we can use to
            // interact with the service. Because we have bound to a explicit
            // service that we know is running in our own process, we can
            // cast its IBinder to a concrete class and directly access it.
            mBoundService = ((TimeAnnouncerService.LocalBinder) service)
                    .getService();

            ToggleButton toggleButton = (ToggleButton) findViewById(R.id.OnOff);

            if (mBoundService.isAnnouncerRunning()) {
                toggleButton.setChecked(true);
            } else {
                toggleButton.setChecked(false);
            }

        }

        public void onServiceDisconnected(ComponentName className) {
            // This is called when the connection with the service has been
            // unexpectedly disconnected -- that is, its process crashed.
            // Because it is running in our same process, we should never
            // see this happen.
            mBoundService = null;

        }
    };

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
        case ABOUT_DIALOG:
            return new AlertDialog.Builder(AutomaticTimeAnnouncer.this)
                    // .setIcon(R.drawable.ic_dialog_time)
                    .setTitle(R.string.about_title)
                    .setMessage(R.string.about_text)
                    .setPositiveButton(R.string.alert_ok,
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                        int whichButton) {

                                    /* User clicked OK so do some stuff */
                                }
                            }).create();
        }
        return null;
    }
}