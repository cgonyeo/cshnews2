package edu.rit.csh.cshnews2;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
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
public class CshNewsService extends Service {
    int returnStatus = 0;
    String apiKey = "0c0f86ead3223876";
    public Semaphore updateLock = new Semaphore(1, true);
    Semaphore unreadListLock = new Semaphore(1, true);
    boolean isStarted = false;
    NewsgroupActivity mActivity = null;
    BroadcastReceiver updateReceiver;
    PendingIntent pendingIntent;
    AlarmManager alarmManager;

    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("Hi", "Starting service...");
        FileStuff.init();
        NetworkStuff.init(apiKey);
        if(!isStarted)
        {
            isStarted = true;
            updateReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    new PostFetcher(CshNewsService.this, apiKey).start();
                }
            };
            registerReceiver(updateReceiver, new IntentFilter("edu.rit.csh.cshnews2"));
            pendingIntent = PendingIntent.getBroadcast(this, 0, new Intent("edu.rit.csh.cshnews2"), 0);
            alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);

            alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, 0,
                    1000 * 60 * 2, pendingIntent);
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
        new PostFetcher(this, apiKey).start();
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

    //Starting at currentNewsgroup, iterates through the newsgroups looking for
    //threads with unread posts in them. Returns the first one it finds, and
    //returns null if none found.
    public JSONObject getNextUnread(String currentNewsgroup)
    {
        try{
            JSONArray unreadList = FileStuff.readJSONArray("unread");

            for(int i = 0; i < unreadList.length(); i++)
            {
                if(!unreadList.isNull(i) && unreadList.getJSONObject(i).getString("newsgroup").equals(currentNewsgroup))
                    return FileStuff.readJSONObject(unreadList.getJSONObject(i).getString("newsgroup") +
                            "/threadmetadata/" +
                            unreadList.getJSONObject(i).getInt("thread_parent"));
            }
            for(int i = 0; i < unreadList.length(); i++)
            {
                if(!unreadList.isNull(i))
                    return FileStuff.readJSONObject(
                            unreadList.getJSONObject(i).getString("newsgroup") +
                            "/threadmetadata/" +
                            unreadList.getJSONObject(i).getInt("thread_parent"));
            }
        } catch (JSONException e) {
            Log.e("Hi", "JSONException on getNextUnread");
        }
        return null;
    }

    //Returns true if threadMetadata contains an unread post
    public boolean threadHasUnread(JSONObject threadMetadata, String selectedNewsgroup)
    {
        try {
            boolean hasUnread = false;
            JSONArray children = threadMetadata.getJSONArray("children");
            for(int i= 0; i < children.length(); i++)
                hasUnread |= threadHasUnread(children.getJSONObject(i), selectedNewsgroup);
            String unread_class = getPost(selectedNewsgroup, threadMetadata.getJSONObject("post")
                    .getString("number")).getJSONObject("post").getString("unread_class");
            hasUnread |= !unread_class.equals("null");
            return hasUnread;
        } catch (JSONException e) {
            Log.e("Hi", "Error parsing json for threadHasUnread");
            Log.e("Hi", "Error " + e.toString());
        }
        return false;
    }

    public void changeReadStatusOfPost(final String newsgroup, final String postNum, final boolean newValue)
    {
        new Thread(){
            public void run()
            {
                try {
                    removeFromUnreadList(newsgroup, Integer.parseInt(postNum));
                    ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
                    params.add(new BasicNameValuePair("number",postNum));
                    params.add(new BasicNameValuePair("newsgroup",newsgroup));
                    if(!newValue)
                        params.add(new BasicNameValuePair("mark_unread","hi"));
                    NetworkStuff.makePutRequest("mark_read", params);

                    if(returnStatus == 200)
                    {
                        JSONObject post = FileStuff.readJSONObject(newsgroup + "/" + postNum);
                        post.getJSONObject("post").put("unread_class", (newValue ? "null" : "manual"));
                        FileStuff.writeJSONObject(newsgroup + "/" + postNum, post);
                    }
                } catch (JSONException e) {
                    Log.e("Hi", "Error parsing JSON for changeReadStatusOfPost");
                    Log.e("Hi", "Error " + e.toString());
                }
            }
        }.start();
    }

    public void addToUnreadList(String newsgroup, int threadNum, int postnum)
    {
        try {
            unreadListLock.acquire();

            JSONArray unreadList = FileStuff.readJSONArray("unread");

            JSONObject newEntry = new JSONObject();
            newEntry.put("newsgroup", newsgroup);
            newEntry.put("number", postnum);
            newEntry.put("thread_parent", threadNum);

            unreadList.put(newEntry);

            FileStuff.writeJSONArray("unread", unreadList);

            unreadListLock.release();
        } catch (InterruptedException e) {
            Log.e("Hi", "InterruptedException error on addToUnreadList");
        } catch (JSONException e) {
            Log.e("Hi", "Error parsing json for addToUnreadList");
            Log.e("Hi", "Error " + e.toString());
        }
    }

    public void removeFromUnreadList(String newsgroup, int postnum)
    {
        try {
            unreadListLock.acquire();

            JSONArray unreadList = FileStuff.readJSONArray("unread");
            JSONArray newUnreadList = new JSONArray();

            for(int i = unreadList.length() - 1; i >= 0; i--)
            {
                if(!unreadList.isNull(i))
                {
                    JSONObject temp = unreadList.getJSONObject(i);
                    if(!temp.getString("newsgroup").equals(newsgroup) || temp.getInt("number") != postnum)
                    {
                        newUnreadList.put(temp);
                    }
                }
            }

            FileStuff.writeJSONArray("unread", newUnreadList);

            unreadListLock.release();
        } catch (InterruptedException e) {
            Log.e("Hi", "InterruptedException error on removeFromUnreadList");
        } catch (JSONException e) {
            Log.e("Hi", "Error parsing json for removeFromUnreadList");
            Log.e("Hi", "Error " + e.toString());
        }
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
