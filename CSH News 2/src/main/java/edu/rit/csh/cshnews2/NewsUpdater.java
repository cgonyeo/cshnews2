package edu.rit.csh.cshnews2;

import android.content.Intent;
import android.os.Environment;
import android.util.Log;

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
class NewsUpdater extends Thread
{
    public static String UPDATE_FINISHED = "UpdateFinished";
    CshNewsService caller;
    String apiKey = "";
    Semaphore postLock = new Semaphore(20, true);

    public NewsUpdater(CshNewsService caller, String apiKey)
    {
        this.caller = caller;
        this.apiKey = apiKey;
    }

    public void run() {
        boolean isntUpdating = caller.updateLock.tryAcquire();
        if(!isntUpdating) { //No point to updating if we're already updating
            Log.d("Hi", "Cancelling another update, one's already running");
            return;
        }

        //Don't do anything if we can't write to the filesystem, or don't have network
        if(isExternalStorageWritable() && NetworkStuff.isNetworkAvailable())
        {
            //File to contain information about the user
            if(!FileStuff.fileExists("user"))
            {
                populateUserFile();
            }

            //File to contain a list of unread posts
            if(!FileStuff.fileExists("unread"))
            {
                FileStuff.writeJSONArray("unread", new JSONArray());
            }

            try {
                //Fetch newsgroups.txt, save it, and give us a list of newsgroups
                final JSONArray newsgroups = populateNewsgroupsFile().getJSONArray("newsgroups");
                //Get recent activity
                getAndWriteRecentActivity();

                ArrayList<Thread> threads = new ArrayList<Thread>();
                //For every newsgroup, make the folders (does nothing if folders exist),
                //And start fetching
                for(int i = 0; i < newsgroups.length(); i++)
                {
                    final JSONObject newsgroup = newsgroups.getJSONObject(i);
                    threads.add( new Thread() {
                        public void run() {
                            try {
                                FileStuff.makeFolder(newsgroup.getString("name"));
                                FileStuff.makeFolder(newsgroup.getString("name") + "/threadmetadata");

                                if(FileStuff.getFilesInFolder(newsgroup.getString("name")).length == 1)
                                {
                                    //We have no posts for the newsgroup yet
                                    //Get the json for the newsgroup
                                    JSONObject newsgroupJson = getNewsgroup(newsgroup.getString("name"), null);
                                    if(newsgroupJson != null)
                                    {
                                        //Get the threads out of the returned json
                                        JSONArray threads = newsgroupJson.getJSONArray("posts_older");
                                        for(int j = 0; j < threads.length(); j++)
                                        {
                                            String threadNum = threads.getJSONObject(j).getJSONObject("post").getString("number");
                                            writeThreadMetadata(newsgroup.getString("name"), threadNum, threads.getJSONObject(j));
                                            getAndWritePostObjects(newsgroup.getString("name"), threads.getJSONObject(j));
                                        }
                                    }
                                }
                                //Get all posts that have occurred since the latest post we have
                                else
                                {
                                    String lastPostDate = getDateOfLastPostFromNewsgroup(newsgroup.getString("name"));
                                    JSONObject newsgroupJson = getNewsgroup(newsgroup.getString("name"), lastPostDate);
                                    if(newsgroupJson != null)
                                    {
                                        JSONArray posts = newsgroupJson.getJSONArray("posts_newer");
                                        Log.d("Hi", newsgroup.getString("name") + " has " + posts.length() + " new posts since " + lastPostDate);
                                        for(int j = posts.length() - 1; j >= 0; j--)
                                        {
                                            String newsgroupName = newsgroup.getString("name");
                                            String postNum = posts.getJSONObject(j).getJSONObject("post").getString("number");
                                            ArrayList<NameValuePair> nvp = new ArrayList<NameValuePair>();
                                            nvp.add(new BasicNameValuePair("html_body", "fsfsef"));
                                            String postDetailString =
                                                    NetworkStuff.makeGetRequest(newsgroupName + "/" + postNum, nvp);
                                            insertPostObjects(posts.getJSONObject(j), new JSONObject(postDetailString));
                                        }
                                    }
                                }
                            } catch (JSONException e) {
                                Log.e("Hi", "Error parsing json in an internal thread in NewsUpdater");
                                Log.e("Hi", "Error " + e.toString());
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

                //Check for items in the new list that we don't have in our list
                for(String[] s : unreadPosts)
                {
                    boolean weHaveAlready = false;
                    for(int i = 0; i < unreadList.length() && !weHaveAlready; i++)
                    {
                        if(unreadList.getJSONObject(i).getString("newsgroup").equals(s[0]) &&
                                unreadList.getJSONObject(i).getInt("number") == Integer.parseInt(s[1]))
                            weHaveAlready = true;
                    }
                    if(!weHaveAlready && FileStuff.fileExists(s[0] + "/" + s[1]))
                    {
                        Log.d("Hi", "Adding " + s[0] + " " + s[1] + " " + s[2] + " to unread list");
                        UnreadTools.addToUnreadList(s[0], Integer.parseInt(s[2]), Integer.parseInt(s[1]));
                        JSONObject post = FileStuff.readJSONObject(s[0] + "/" + s[1]);
                        post.getJSONObject("post").put("unread_class", "manual");
                        FileStuff.writeJSONObject(s[0] + "/" + s[1], post);
                    }
                    Log.d("Hi", "Unread: " + s[0] + " " + s[1]);
                }

                //Check for items in our list that don't exist in the new list
                for(int i = 0; i < unreadList.length(); i++)
                {
                    boolean isStillUnread = false;
                    for(int j = 0; j < unreadPosts.size() && !isStillUnread; i++)
                    {
                        if(unreadList.getJSONObject(i).getString("newsgroup").equals(unreadPosts.get(j)[0]) &&
                                unreadList.getJSONObject(i).getInt("number") == Integer.parseInt(unreadPosts.get(j)[1]))
                            isStillUnread = true;
                    }
                    if(!isStillUnread && FileStuff.fileExists(
                            unreadList.getJSONObject(i).getString("newsgroup") + "/" +
                                    unreadList.getJSONObject(i).getInt("number")))
                    {
                        UnreadTools.removeFromUnreadList(unreadList.getJSONObject(i).getString("newsgroup"),
                                unreadList.getJSONObject(i).getInt("number"));
                        JSONObject post = FileStuff.readJSONObject(
                                unreadList.getJSONObject(i).getString("newsgroup") + "/" +
                                        unreadList.getJSONObject(i).getInt("number"));
                        post.getJSONObject("post").put("unread_class", "null");
                        FileStuff.writeJSONObject(
                                unreadList.getJSONObject(i).getString("newsgroup") + "/" +
                                        unreadList.getJSONObject(i).getInt("number"),
                                post);
                    }
                }

                Log.d("Hi", "Update complete");
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
        caller.updateLock.release();
        sendFinishedBroadcast();
    }

    protected void sendFinishedBroadcast() {
        Intent i = new Intent(UPDATE_FINISHED);
        caller.sendBroadcast(i);
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

    private String getDateOfLastPostFromNewsgroup(String newsgroupName)
    {
        String latest = "";
        File newsgroupMetadataFolder = new File(
            Environment.getExternalStoragePublicDirectory(""),
            "news/" + newsgroupName + "/threadmetadata");
        for(String filename : newsgroupMetadataFolder.list())
        {
            JSONObject threadMetadata = FileStuff.readJSONObject(newsgroupName +
                    "/threadmetadata/" + filename);
            ArrayList<String> dates = new ArrayList<String>();
            getAllDates(threadMetadata, dates);
            for(String date : dates)
            {
                if(latest.compareTo(date) < 0)
                {
                    latest = date;
                }
            }
        }
        return latest;
    }

    private void getAllDates(JSONObject threadMetadata, ArrayList<String> dates)
    {
        try {
            JSONArray children = threadMetadata.getJSONArray("children");
            for(int i = 0; i < children.length(); i++)
            {
                getAllDates(children.getJSONObject(i), dates);
            }
            dates.add(threadMetadata.getJSONObject("post").getString("date"));
        } catch (JSONException e) {
            Log.e("Hi", "Error parsing JSON for dates");
            Log.e("Hi", "Error: " + e.toString());
        }
    }

    private void writeThreadMetadata(String newsgroup, String threadNum, JSONObject threadMetadata)
    {
        String path = newsgroup + "/threadmetadata/" + threadNum;
        FileStuff.writeJSONObject(path, threadMetadata);
    }
    private void insertPostObjects(JSONObject postMetadata, JSONObject post)
    {
        try {
            if(postMetadata.isNull("children"))
                postMetadata.put("children", new JSONArray());
            String postNum = post.getJSONObject("post").getString("number");
            String newsgroup = post.getJSONObject("post").getString("newsgroup");
            boolean hasParent = !post.getJSONObject("post").isNull("parent");

            Log.d("Hi", "post's parent " + (hasParent ? "is not" : "is") + " null");

            String parentNum = "";
            String threadNum = "";
            String parentNewsgroup = "";
            String threadParentNewsgroup = "";


            if(hasParent)
            {
                parentNum = post.getJSONObject("post").getJSONObject("parent").getString("number");
                parentNewsgroup = post.getJSONObject("post").getJSONObject("parent").getString("newsgroup");

                if(post.getJSONObject("post").has("thread_parent"))
                {
                    threadNum = post.getJSONObject("post").getJSONObject("thread_parent").getString("number");
                    threadParentNewsgroup = post.getJSONObject("post").getJSONObject("thread_parent").getString("newsgroup");
                }
                else
                {
                    threadNum = postNum;
                }
                FileStuff.writeJSONObject(newsgroup + "/" + postNum, post);


                if(!post.getJSONObject("post").getString("unread_class").equals("null"))
                {
                    UnreadTools.addToUnreadList(newsgroup, Integer.parseInt(threadNum), Integer.parseInt(postNum));
                }

                if(newsgroup.equals(parentNewsgroup))
                {


                    JSONObject threadMetadata = FileStuff.readJSONObject(newsgroup + "/threadmetadata/" + threadNum);

                    JSONObject parentMetadata = findPost(threadMetadata, parentNum);

                    boolean alreadyExists = false;
                    JSONArray children = parentMetadata.getJSONArray("children");
                    for(int i = 0; i < parentMetadata.getJSONArray("children").length(); i++)
                    {
                        alreadyExists |= children
                                .getJSONObject(i).getJSONObject("post")
                                .getString("number") == postNum;
                    }
                    if(!alreadyExists)
                    {
                        children.put(children.length(), postMetadata);
                        FileStuff.writeJSONObject(newsgroup + "/threadmetadata/" + threadNum, threadMetadata);
                    }
                }
                else
                    FileStuff.writeJSONObject(newsgroup + "/threadmetadata/" + postNum, postMetadata);
            }
            else
            {
                if(!post.getJSONObject("post").getString("unread_class").equals("null"))
                {
                    UnreadTools.addToUnreadList(newsgroup, Integer.parseInt(postNum), Integer.parseInt(postNum));
                }
                FileStuff.writeJSONObject(newsgroup + "/" + postNum, post);

                postMetadata.put("children", new JSONArray());
                FileStuff.writeJSONObject(newsgroup + "/threadmetadata/" + postNum, postMetadata);
            }


        } catch (JSONException e) {
            Log.e("Hi", "Error parsing JSON for insert post");
            Log.e("Hi", "Error: " + e.toString());
        }
    }
    private void getAndWritePostObjects(final String newsgroup, final JSONObject postMetadata)
    {
        try {
            postLock.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        new Thread() {
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
                    for(int i = 0; i < children.length(); i++)
                    {
                        getAndWritePostObjects(newsgroup, children.getJSONObject(i));
                    }
                } catch (JSONException e) {
                    Log.e("Hi", "JSON error with writing post");
                    Log.e("Hi", "JSON error: " + e.toString());
                    for(StackTraceElement ste : e.getStackTrace())
                        Log.e("Hi", ste.toString());
                }
            }
        }.start();
    }
    private JSONObject getNewsgroup(String newsgroupName, String since)
    {
        ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("limit", "15"));
        if(since != null)
        {
            params.add(new BasicNameValuePair("thread_mode","flat"));
            params.add(new BasicNameValuePair("from_newer", since));
        }
        String result = NetworkStuff.makeGetRequest(newsgroupName + "/index", params);
        try {
            JSONObject returnValue = new JSONObject(result);
            return returnValue;
        } catch (JSONException e) {
            Log.e("Hi", "JSON error with getting newsgroup " + newsgroupName);
            Log.e("Hi", "result: " + result);
            Log.e("Hi", "JSON error: " + e.toString());
        }
        return null;
    }
    private JSONObject findPost(JSONObject threadMetadata, String num)
    {
        try {
            if(threadMetadata.getJSONObject("post").getString("number").equals(num))
                return threadMetadata;
            for(int i = 0; i < threadMetadata.getJSONArray("children").length(); i++)
            {
                JSONObject value = findPost(threadMetadata.getJSONArray("children").getJSONObject(i), num);
                if(value != null)
                    return value;
            }
        } catch (JSONException e) {
            Log.e("Hi", "Error with JSON finding post " + num);
            Log.e("Hi", "Error: " + e.toString());
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
