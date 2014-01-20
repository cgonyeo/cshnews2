package edu.rit.csh.cshnews2;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.Semaphore;


/**
 * Created by derek on 12/23/13.
 */
public class CshNewsService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener {
    int returnStatus = 0;
    String apiKey = null;
    public Semaphore updateLock = new Semaphore(1, true);
    Semaphore unreadListLock = new Semaphore(1, true);
    boolean isStarted = false;
    NewsgroupActivity mActivity = null;
    BroadcastReceiver updateReceiver;
    PendingIntent pendingIntent;
    AlarmManager alarmManager;
    SharedPreferences pm = null;
    boolean updateWanted = false;
    int updateInterval = 5;

    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("Hi", "Starting service...");
        if(pm == null)
        {
            pm = PreferenceManager.getDefaultSharedPreferences(this);
        }
        if(apiKey == null)
        {
            apiKey = pm.getString("apiKeyInput", "");
            updateWanted = pm.getBoolean("shouldRunUpdate", updateWanted);
            String intervalTemp = pm.getString("updateInterval", "");
            if(intervalTemp != "" && Integer.parseInt(intervalTemp) > 0)
                updateInterval = Integer.parseInt(intervalTemp);
        }
        FileStuff.init();
        NetworkStuff.init(apiKey, this);
        if(!isStarted)
        {
            isStarted = true;
            updateReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    new NewsUpdater(CshNewsService.this, apiKey).start();
                }
            };
            registerReceiver(updateReceiver, new IntentFilter("edu.rit.csh.cshnews2"));
            pendingIntent = PendingIntent.getBroadcast(this, 0, new Intent("edu.rit.csh.cshnews2"), 0);
            alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);

            if(updateWanted)
                alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, 0,
                        1000 * 60 * updateInterval, pendingIntent);
        }
        return Service.START_NOT_STICKY;
    }

    @Override
    public void onDestroy()
    {
        alarmManager.cancel(pendingIntent);
        unregisterReceiver(updateReceiver);
        super.onDestroy();
    }

    public void update()
    {
        new NewsUpdater(this, apiKey).start();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        pm = sharedPreferences;

        String newApiKey = pm.getString("apiKeyInput", "");
        if(!newApiKey.equals(apiKey))
        {
            apiKey = newApiKey;
            update();
        }

        String intervalTemp = pm.getString("updateInterval", "");
        if(intervalTemp != "" && Integer.parseInt(intervalTemp) > 0)
            updateInterval = Integer.parseInt(intervalTemp);

        boolean newUpdateWanted = pm.getBoolean("shouldRunUpdate", updateWanted);
        if(newUpdateWanted && !updateWanted)
        {
            updateWanted = newUpdateWanted;
            alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, 0,
                    1000 * 60 * updateInterval, pendingIntent);
        }
        if(!newUpdateWanted && updateWanted)
        {
            updateWanted = newUpdateWanted;
            alarmManager.cancel(pendingIntent);
        }
    }

    public class JSONComparator implements Comparator<JSONObject> {

        @Override
        public int compare(JSONObject lhs, JSONObject rhs) {
            try {
                return lhs.getJSONObject("post").getString("date")
                        .compareTo(rhs.getJSONObject("post").getString("date"));
            } catch (JSONException e) {
                Log.e("Hi", "Error parsing json for jsoncomparator");
                Log.e("Hi", "Error " + e.toString());
            }
            return 0;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d("Hi", "Service bound to");
        return mBinder;
    }

    public void registerUI(NewsgroupActivity mActivity)
    {
        this.mActivity = mActivity;
    }

    public void deregisterUI()
    {
        this.mActivity = null;
    }

    public JSONArray getNewsgroups()
    {
        try {
            JSONObject newsgroupsObject = FileStuff.readJSONObject("newsgroups");
            if(newsgroupsObject != null)
                return newsgroupsObject.getJSONArray("newsgroups");
            else
                return null;
        } catch (JSONException e) {
            Log.e("Hi", "Error parsing json for getnewsgroups");
        }
        return null;
    }

    public JSONArray getRecentActivity()
    {
        try {
            JSONObject recentActivity =  FileStuff.readJSONObject("recentactivity");
            if(recentActivity != null)
                return recentActivity.getJSONArray("activity");
            else
                return null;
        } catch (JSONException e) {
            Log.e("Hi", "Error parsing json for getRecentActivity");
        }
        return null;
    }

    public JSONObject getThreadMetadata(String newsgroup, String threadNum)
    {
        return FileStuff.readJSONObject(newsgroup + "/threadmetadata/" + threadNum);
    }

    public JSONObject getPost(String newsgroup, String postNum)
    {
        return FileStuff.readJSONObject(newsgroup + "/" + postNum);
    }

    public JSONArray getThreadsForNewsgroup(String newsgroup)
    {
        if(FileStuff.fileExists(newsgroup + "/threadmetadata"))
        {
            JSONComparator compare = new JSONComparator();

            ArrayList<JSONObject> threads = new ArrayList<JSONObject>();
            File[] threaddatas = FileStuff.getFilesInFolder(newsgroup + "/threadmetadata");
            for(File f : threaddatas)
            {
                threads.add(FileStuff.readJSONObject(newsgroup + "/threadmetadata/" + f.getName()));
            }
            Collections.sort(threads, compare);
            Collections.reverse(threads);
            return new JSONArray(threads);
        }
        return null;

    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        CshNewsService getService() {
            // Return this instance of LocalService so clients can call public methods
            return CshNewsService.this;
        }
    }
}
