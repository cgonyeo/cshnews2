package edu.rit.csh.cshnews2;

import android.content.Intent;
import android.os.Environment;
import android.util.Log;
import android.widget.ProgressBar;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;

//Fetches posts. If a newsgroup has no posts yet,
//fetches the five most recent threads and their posts,
//otherwise fetches all posts that have occurred since
//the last known post
class DatabaseBuilder extends Thread
{
    LoginActivity caller;
    String apiKey = "";
    Semaphore postLock = new Semaphore(20, true);
    Semaphore barUpdaterLock = new Semaphore(1, true);
    int numToFetch;
    double barRatio;

    public DatabaseBuilder(LoginActivity caller, String apiKey, int numToFetch)
    {
        this.caller = caller;
        this.apiKey = apiKey;
        this.numToFetch = numToFetch;
        barRatio = 100 / numToFetch;
        FileStuff.init();
    }

    public void run() {
        Log.d("Hi", "Database builder starting");
        //Don't do anything if we can't write to the filesystem, or don't have network
        if(isExternalStorageWritable() && NetworkStuff.isNetworkAvailable(caller))
        {
            Log.d("Hi", "Fetching files...");
            //User file that contains information about the user
            populateUserFile();
            //Unread file that keeps a list of unread posts
            FileStuff.writeJSONArray("unread", new JSONArray());

            try {
                //Fetch newsgroups, save it, and give us a list of newsgroups
                final JSONArray newsgroups = populateNewsgroupsFile().getJSONArray("newsgroups");
                //Get recent activity
                getAndWriteRecentActivity();

                ArrayList<Thread> threads = new ArrayList<Thread>();
                //For every newsgroup, make the folders and start fetching
                for(int i = 0; i < newsgroups.length(); i++)
                {
                    final JSONObject newsgroup = newsgroups.getJSONObject(i);
                    threads.add( new Thread() {
                        public void run() {
                            try {
                                int progress = 0;
                                FileStuff.makeFolder(newsgroup.getString("name"));
                                FileStuff.makeFolder(newsgroup.getString("name") + "/threadmetadata");

                                //We have no posts for the newsgroup yet
                                //Get the json for the newsgroup
                                JSONArray threads = getNumInNewsgroup(newsgroup.getString("name"), numToFetch);
                                if(threads != null)
                                {
                                    ArrayList<Thread> postThreads = new ArrayList<Thread>();
                                    if(caller != null)
                                    {
                                        ProgressBar temp = caller.loadingBars.get(newsgroup.getString("name"));
                                        temp.setIndeterminate(false);
                                        temp.setProgress(0);
                                    }
                                    for(int j = 0; j < threads.length(); j++)
                                    {
                                        String threadNum = threads.getJSONObject(j).getJSONObject("post").getString("number");
                                        writeThreadMetadata(newsgroup.getString("name"), threadNum, threads.getJSONObject(j));
                                        postThreads.add(getAndWritePostObjects(newsgroup.getString("name"), threads.getJSONObject(j), true));
                                    }

                                    for(Thread t : postThreads)
                                    {
                                        t.join();
                                        progress++;
                                    }
                                    if(caller != null)
                                    {
                                        ProgressBar temp = caller.loadingBars.get(newsgroup.getString("name"));
                                        temp.setProgress(100);
                                    }
                                }
                            } catch (JSONException e) {
                                Log.e("Hi", "Error parsing json in an internal thread in DatabaseBuilder");
                                Log.e("Hi", "Error " + e.toString());
                            } catch (InterruptedException e) {
                                Log.e("Hi", "Error joining on threads in DatabaseBuilder");
                            }
                        }
                    });
                }

                for(Thread t : threads)
                        t.start();
                for(Thread t : threads)
                        t.join();

                //Sync unread posts, up to 100
                ArrayList<String[]> unreadPosts = getListOfUnreadPosts();
                JSONArray unreadList = FileStuff.readJSONArray("unread");

                for(String[] s : unreadPosts)
                {
                    if(FileStuff.fileExists(s[0] + "/" + s[1]))
                    {
                        Log.d("Hi", "Adding " + s[0] + " " + s[1] + " " + s[2] + " to unread list");
                        UnreadTools.addToUnreadList(s[0], Integer.parseInt(s[2]), Integer.parseInt(s[1]));
                        JSONObject post = FileStuff.readJSONObject(s[0] + "/" + s[1]);
                        post.getJSONObject("post").put("unread_class", "manual");
                        FileStuff.writeJSONObject(s[0] + "/" + s[1], post);
                    }
                    Log.d("Hi", "Unread: " + s[0] + " " + s[1]);
                }
                Log.d("Hi", "Update complete");
                caller.UpdateFinished();
            } catch (JSONException e) {
                Log.e("Hi", "JSON error with newsgroups");
                Log.e("Hi", "JSON error: " + e.toString());
                for(StackTraceElement ste : e.getStackTrace())
                    Log.e("Hi", ste.toString());
            } catch (NullPointerException e) {
                Log.e("Hi", "NullPointerException, I guess we don't have network");
                Log.e("Hi", "Error: " + e.toString());
            } catch (InterruptedException e) {

                Log.e("Hi", "InterruptedException error with newsgroups");
                Log.e("Hi", "Error: " + e.toString());
            }
        }
    }

    private void getAndWriteRecentActivity()
    {
        try
        {
            JSONObject recentActivity = new JSONObject(NetworkStuff.makeGetRequest("activity"));
            FileStuff.writeJSONObject("recentactivity", recentActivity);
        } catch (JSONException e) {
            Log.e("Hi", "Error parsing json for getAndWriteRecentActivity");
            Log.e("Hi", "Error " + e.toString());
        }
    }

    private ArrayList<String[]> getListOfUnreadPosts()
    {
        try
        {
            ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
            params.add(new BasicNameValuePair("unread","Sales are up!"));
            params.add(new BasicNameValuePair("limit","20"));
            JSONObject reply = new JSONObject(NetworkStuff.makeGetRequest("search", params));

            JSONArray postsOlder = reply.getJSONArray("posts_older");

            ArrayList<String[]> toReturn = new ArrayList<String[]>();
            for(int i = 0; i < postsOlder.length(); i++)
            {
                String newsgroup = postsOlder.getJSONObject(i).getJSONObject("post").getString("newsgroup");
                String postNum = postsOlder.getJSONObject(i).getJSONObject("post").getInt("number") + "";
                String threadNum = "";
                if(FileStuff.fileExists(newsgroup + "/" + postNum))
                {
                    JSONObject post = FileStuff.readJSONObject(newsgroup + "/" + postNum);

                    if(post.getJSONObject("post").has("thread_parent") &&
                            post.getJSONObject("post").getJSONObject("thread_parent")
                                    .getString("newsgroup").equals(newsgroup))
                        threadNum = post.getJSONObject("post").getJSONObject("thread_parent").getInt("number") + "";
                    else
                        threadNum = postNum;

                    String[] holder = { newsgroup, postNum, threadNum };
                    toReturn.add(holder);
                }
            }

            int counter = 0;
            while(reply.getBoolean("more_older") && counter < 5)
            {
                if(counter > 0)
                    params.remove(params.size() - 1);
                String lastDate = postsOlder.getJSONObject(postsOlder.length()).getJSONObject("post").getString("date");
                params.add(new BasicNameValuePair("from_older", lastDate));
                reply = new JSONObject(NetworkStuff.makeGetRequest("search", params));
                postsOlder = reply.getJSONArray("posts_older");

                for(int i = 0; i < postsOlder.length(); i++)
                {
                    String newsgroup = postsOlder.getJSONObject(i).getJSONObject("post").getString("newsgroup");
                    String postNum = postsOlder.getJSONObject(i).getJSONObject("post").getInt("number") + "";
                    String threadNum = "";
                    if(FileStuff.fileExists(newsgroup + "/" + postNum))
                    {
                        JSONObject post = FileStuff.readJSONObject(newsgroup + "/" + postNum);

                        if(post.getJSONObject("post").has("thread_parent") &&
                                post.getJSONObject("post").getJSONObject("thread_parent")
                                        .getString("newsgroup").equals(newsgroup))
                            threadNum = post.getJSONObject("post").getJSONObject("thread_parent").getInt("number") + "";
                        else
                            threadNum = postNum;

                        String[] holder = { newsgroup, postNum, threadNum };
                        toReturn.add(holder);
                    }
                }
                counter++;
            }
            return toReturn;
        } catch (JSONException e) {
            Log.e("Hi", "Error parsing json for getListOfUnreadPosts");
            Log.e("Hi", "Error " + e.toString());
        }
        return new ArrayList<String[]>();
    }

    private void writeThreadMetadata(String newsgroup, String threadNum, JSONObject threadMetadata)
    {
        String path = newsgroup + "/threadmetadata/" + threadNum;
        FileStuff.writeJSONObject(path, threadMetadata);
    }
    private Thread getAndWritePostObjects(final String newsgroup, final JSONObject postMetadata, final boolean firstLevel)
    {
        try {
            postLock.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Thread t = new Thread() {
            public void run()
            {
                try
                {
                    String path = newsgroup + "/" +
                            postMetadata.getJSONObject("post").getString("number");

                    ArrayList<NameValuePair> nvp = new ArrayList<NameValuePair>();
                    nvp.add(new BasicNameValuePair("html_body","hi there!"));

                    String postText = NetworkStuff.makeGetRequest(
                            newsgroup + "/" + postMetadata.getJSONObject("post").getString("number"), nvp);
                    JSONObject post = new JSONObject(postText);

                    if(!post.getJSONObject("post").getString("unread_class").equals("null"))
                    {
                        int postNum = post.getJSONObject("post").getInt("number");
                        int threadNum = postNum;
                        if(post.getJSONObject("post").has("thread_parent") &&
                                post.getJSONObject("post").getJSONObject("thread_parent").getString("newsgroup").equals(newsgroup))
                            threadNum = post.getJSONObject("post").getJSONObject("thread_parent").getInt("number");
                        UnreadTools.addToUnreadList(newsgroup, threadNum, postNum);
                    }

                    FileStuff.writeJSONObject(path, post);

                    postLock.release();

                    JSONArray children = postMetadata.getJSONArray("children");
                    ArrayList<Thread> childThreads = new ArrayList<Thread>();
                    for(int i = 0; i < children.length(); i++)
                    {
                        childThreads.add(getAndWritePostObjects(newsgroup, children.getJSONObject(i), false));
                    }
                    for(Thread t : childThreads)
                        try {
                            t.join();
                        } catch (InterruptedException e) {
                            Log.e("Hi", "Error joining on a thread in DatabaseBuilder");
                        }
                } catch (JSONException e) {
                    Log.e("Hi", "JSON error with writing post");
                    Log.e("Hi", "JSON error: " + e.toString());
                    for(StackTraceElement ste : e.getStackTrace())
                        Log.e("Hi", ste.toString());
                }
                finally {
                    try
                    {
                        if(caller != null && firstLevel)
                        {
                            barUpdaterLock.acquire();
                            ProgressBar temp = caller.loadingBars.get(newsgroup);
                            temp.setProgress((int) (temp.getProgress() + barRatio));
                            barUpdaterLock.release();
                        }
                    } catch (InterruptedException e) {
                        Log.e("Hi", "InterruptedException in getAndWritePost thing");
                    }
                }
            }
        };
        t.start();
        return t;
    }
    private JSONArray getNumInNewsgroup(String newsgroupName, int numToFetch)
    {
        try {
            int numFetched = 0;
            JSONArray postsOlder = new JSONArray();
            JSONObject result = null;
            while(numFetched < numToFetch && (result == null || result.getBoolean("more_older")))
            {
                ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
                if(numToFetch - numFetched < 20)
                    params.add(new BasicNameValuePair("limit", numToFetch - numFetched + ""));
                else
                    params.add(new BasicNameValuePair("limit", "20"));
                if(numFetched > 0)
                    params.add(new BasicNameValuePair("from_older",
                            postsOlder.getJSONObject(postsOlder.length() - 1).getJSONObject("post").getString("date")));
                result = new JSONObject(NetworkStuff.makeGetRequest(newsgroupName + "/index", params));
                numFetched += 20;

                for(int i = 0; i < result.getJSONArray("posts_older").length(); i++)
                {
                    postsOlder.put(result.getJSONArray("posts_older").getJSONObject(i));
                }
            }
            return postsOlder;
        } catch (JSONException e) {
            Log.e("Hi", "JSON error with getNumInNewsgroup " + newsgroupName);
            Log.e("Hi", "JSON error: " + e.toString());
        }
        return null;
    }
    private JSONObject populateNewsgroupsFile()
    {
        String newsgroupsString = NetworkStuff.makeGetRequest("newsgroups");
        JSONObject returnValue = null;

        try {
            JSONObject newsgroupsJSON = new JSONObject(newsgroupsString);
            FileStuff.writeJSONObject("newsgroups", newsgroupsJSON);

            returnValue = new JSONObject(newsgroupsString);
        } catch (JSONException e) {
            Log.e("Hi", "JSON error with populating newsgroups file");
            Log.e("Hi", "JSON error: " + e.toString());
        }
        return returnValue;
    }
    private void populateUserFile()
    {
        String userString = NetworkStuff.makeGetRequest("user");
        try {
            JSONObject userJSON = new JSONObject(userString);
            FileStuff.writeJSONObject("user", userJSON);
        } catch (JSONException e) {
            Log.e("Hi", "Error parsing json for user");
            Log.e("Hi", "Error " + e.toString());
        }

    }

    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }
}
