package edu.rit.csh.cshnews2;



import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.EditTextPreference;
import android.preference.PreferenceFragment;
import android.text.InputType;

/**
 * A simple {@link android.support.v4.app.Fragment} subclass.
 *
 */
public class SettingsFragment extends PreferenceFragment {
    CshNewsService mService;
    boolean mBound = false;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);

        EditTextPreference etp = (EditTextPreference)findPreference("updateInterval");
        etp.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
        EditTextPreference etp1 = (EditTextPreference)findPreference("apiKeyInput");
        etp1.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        Intent intent = new Intent(getActivity(), CshNewsService.class);
        getActivity().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if(!mBound)
        {
            Intent intent = new Intent(getActivity(), CshNewsService.class);
            getActivity().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    public void onPause()
    {
        super.onPause();
        getPreferenceScreen().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(mService);
    }

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            CshNewsService.LocalBinder binder = (CshNewsService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
            getPreferenceScreen().getSharedPreferences()
                    .registerOnSharedPreferenceChangeListener(mService);

        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };
}