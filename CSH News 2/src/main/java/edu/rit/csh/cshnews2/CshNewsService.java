package edu.rit.csh.cshnews2;

import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;


/**
 * Created by derek on 12/23/13.
 */
public class CshNewsService extends Service {
    int returnStatus = 0;
    String apiKey = "0c0f86ead3223876";
    Semaphore updateLock = new Semaphore(1, true);
    Semaphore writeLock = new Semaphore(1, true);
    boolean isStarted = false;

    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("Hi", "Starting service...");
        if(!isStarted)
        {
            isStarted = true;
            ScheduledExecutorService scheduleTaskExecutor = Executors.newScheduledThreadPool(5);
            scheduleTaskExecutor.scheduleAtFixedRate(new Runnable() {
                public void run() {
                    new PostFetcher(null).execute();
                }
            }, 0, 2, TimeUnit.MINUTES);
        }
        return Service.START_NOT_STICKY;
    }

    public void update(NewsgroupActivity requester)
    {
        new PostFetcher(requester).execute();
    }

    public class JSONComparator implements Comparator<JSONObject> {

        @Override
        public int compare(JSONObject lhs, JSONObject rhs) {
            try {
                return lhs.getJSONObject("post").getString("date")
                        .compareTo(rhs.getJSONObject("post").getString("date"));
            } catch (JSONException e) {
                Log.d("Hi", "Error parsing json for jsoncomparator");
                Log.d("Hi", "Error " + e.toString());
            }
            return 0;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.d("Hi", "Service bound to");
        return mBinder;
    }

    public JSONArray getNewsgroups()
    {
        try {
            return new JSONObject(readFile("news/newsgroups")).getJSONArray("newsgroups");
        } catch (JSONException e) {
            Log.d("Hi", "Error parsing json for getnewsgroups");
        }
        return null;
    }

    public JSONObject getPost(String newsgroup, String postNum)
    {
        String filepath = "news/" + newsgroup + "/" + postNum;
        File postFile = new File(
                Environment.getExternalStoragePublicDirectory(""), filepath);
        if(!postFile.exists())
            return null;
        String post = readFile(filepath);
        try {
            return new JSONObject(post);
        } catch (JSONException e) {
            Log.d("Hi", "Error parsing json for getPost");
            Log.d("Hi", "Error " + e.toString());
        }
        return null;
    }

    public JSONArray getThreadsForNewsgroup(String newsgroup)
    {
        File threadMetadataFolder = new File(
                Environment.getExternalStoragePublicDirectory(""), "news/" + newsgroup + "/threadmetadata");
        if(threadMetadataFolder.exists())
        {
            JSONComparator compare = new JSONComparator();
            try {
                ArrayList<JSONObject> threads = new ArrayList<JSONObject>();
                File[] threaddatas = threadMetadataFolder.listFiles();
                for(File f : threaddatas)
                {
                    String threadMetadata = readFile("news/" + newsgroup + "/threadmetadata/" + f.getName());
                    JSONObject jo =  new JSONObject(threadMetadata);
                    threads.add(jo);
                }
                Collections.sort(threads, compare);
                Collections.reverse(threads);
                return new JSONArray(threads);
            } catch (JSONException e) {
                Log.d("Hi", "Error parsing json for getting threads for newgroups");
                Log.d("Hi", "Error " + e.toString());
            }
        }
        return null;

    }

    public void changeReadStatusOfPost(final String newsgroup, final String postNum, final boolean newValue)
    {
        new Thread(){
            public void run()
            {
                try {
                    ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
                    params.add(new BasicNameValuePair("number",postNum));
                    params.add(new BasicNameValuePair("newsgroup",newsgroup));
                    if(!newValue)
                        params.add(new BasicNameValuePair("mark_unread","hi"));
                    makePutRequest("mark_read", apiKey, params);

                    if(returnStatus == 200)
                    {
                        JSONObject post = new JSONObject(readFile("news/" + newsgroup + "/" + postNum));
                        post.getJSONObject("post").put("unread_class", (newValue ? "null" : "manual"));
                        writeFile("news/" + newsgroup + "/" + postNum, post.toString(4));
                    }
                } catch (JSONException e) {
                    Log.d("Hi", "Error parsing JSON for changeReadStatusOfPost");
                    Log.d("Hi", "Error " + e.toString());
                }
            }
        }.start();
    }

    //Makes the folder at path if it doesn't already exist
    private void makeFolder(String path)
    {
        try
        {
        writeLock.acquire();
        }
        catch(InterruptedException e)
        {
            Log.d("Hi", "Error getting writeLock in makeFolder");
        }

        File folder = new File(
                Environment.getExternalStoragePublicDirectory(""), path);
        if(!folder.exists())
            folder.mkdirs();

        writeLock.release();
    }

    //Writes content to path. If path already exists, it is overwritten.
    private void writeFile(String path, String content)
    {
        try
        {
        writeLock.acquire();
        }
        catch(InterruptedException e)
        {
            Log.d("Hi", "Error getting writeLock in writeFile");
        }

        Log.d("Hi", "Writing to " + path);
        try
        {
            File file = new File(
                    Environment.getExternalStoragePublicDirectory(""), path);
            file.createNewFile();

            BufferedWriter bw =
                    new BufferedWriter(new FileWriter(file.getAbsoluteFile()));
            bw.write(content);
            bw.close();
        } catch (IOException e) {
            Log.d("Hi", "IO error with writing file");
            Log.d("Hi", "IO error: " + e.toString());
        }
        writeLock.release();
    }

    //Returns the String contained in the file at path
    private String readFile(String path)
    {
        try
        {
        writeLock.acquire();
        }
        catch(InterruptedException e)
        {
            Log.d("Hi", "Error getting writeLock in readFile");
        }

        String returnValue = "";
        try {
            File file = new File(
                    Environment.getExternalStoragePublicDirectory("") + "/" + path);
            FileInputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int)file.length()];
            fis.read(data);
            fis.close();
            returnValue = new String(data, "UTF-8");
        } catch (FileNotFoundException e) {
            Log.d("Hi", "Error opening file " + path);
            Log.d("Hi", "Error: " + e.toString());
        } catch (IOException e) {
            Log.d("Hi", "Error reading file " + path);
            Log.d("Hi", "Error: " + e.toString());
        }

        writeLock.release();
        return returnValue;
    }

    //Makes a get request to page with no additional parameters
    private String makeGetRequest(String page, String apiKey)
    {
        ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
        return makeGetRequest(page, apiKey, params);
    }
    //Makes a get request to page with the specified additional parameters
    private String makeGetRequest(String page, String apiKey, ArrayList<NameValuePair> params)
    {
        params.add(new BasicNameValuePair("api_key", apiKey));
        params.add(new BasicNameValuePair("api_agent", "Android_Webnews"));

        HttpClient client = new DefaultHttpClient();
        HttpResponse response = null;
        try {

            URI target = URIUtils.createURI("https", "webnews.csh.rit.edu", -1, "/" + page,
                    URLEncodedUtils.format(params, "UTF-8"), null);
            HttpGet request = new HttpGet(target);
            request.addHeader("Accept", "application/json");
            response = client.execute(request);

            if(response.getStatusLine().getStatusCode() != 200)
                Log.d("Hi", "Status: " + response.getStatusLine().getStatusCode());

            // Get the response
            BufferedReader rd = new BufferedReader
                    (new InputStreamReader(response.getEntity().getContent()));

            StringBuilder sb = new StringBuilder();
            String line = "";
            while ((line = rd.readLine()) != null) {
                sb.append(line);
            }
            returnStatus = response.getStatusLine().getStatusCode();
            return sb.toString();
        } catch (IOException e) {
            if(response != null && response.getStatusLine() != null)
                returnStatus = response.getStatusLine().getStatusCode();
            Log.d("Hi", "IOException on the get request to " + page);
            return "Error!";
        } catch (URISyntaxException e) {
            if(response != null && response.getStatusLine() != null)
                returnStatus = response.getStatusLine().getStatusCode();
            Log.d("Hi", "URISyntaxException on the get request to " + page);
            return "Error!";
        }
    }

    //Makes a get request to page with the specified additional parameters
    private String makePutRequest(String page, String apiKey, ArrayList<NameValuePair> params)
    {
        params.add(new BasicNameValuePair("api_key", apiKey));
        params.add(new BasicNameValuePair("api_agent", "Android_Webnews"));

        HttpClient client = new DefaultHttpClient();
        HttpResponse response = null;
        try {

            URI target = URIUtils.createURI("https", "webnews.csh.rit.edu", -1, "/" + page,
                    URLEncodedUtils.format(params, "UTF-8"), null);
            HttpPut request = new HttpPut(target);
            request.addHeader("Accept", "application/json");
            response = client.execute(request);

            if(response.getStatusLine().getStatusCode() != 200)
                Log.d("Hi", "Status: " + response.getStatusLine().getStatusCode());

            // Get the response
            BufferedReader rd = new BufferedReader
                    (new InputStreamReader(response.getEntity().getContent()));

            StringBuilder sb = new StringBuilder();
            String line = "";
            while ((line = rd.readLine()) != null) {
                sb.append(line);
            }
            returnStatus = response.getStatusLine().getStatusCode();
            return sb.toString();
        } catch (IOException e) {
            if(response != null && response.getStatusLine() != null)
                returnStatus = response.getStatusLine().getStatusCode();
            Log.d("Hi", "IOException on the get request to " + page);
            return "Error!";
        } catch (URISyntaxException e) {
            if(response != null && response.getStatusLine() != null)
                returnStatus = response.getStatusLine().getStatusCode();
            Log.d("Hi", "URISyntaxException on the get request to " + page);
            return "Error!";
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


    //Fetches posts. If a newsgroup has no posts yet,
    //fetches the five most recent threads and their posts,
    //otherwise fetches all posts that have occurred since
    //the last known post
    class PostFetcher extends AsyncTask
    {
        NewsgroupActivity requester;
        public PostFetcher(NewsgroupActivity requester)
        {
            this.requester = requester;
        }
        @Override
        protected Object doInBackground(Object[] parameters) {
            try {
                updateLock.acquire();
            } catch (InterruptedException e) {
                Log.e("Hi", "Interrupted acquiring updateLock in PostFetcher!");
            }
            //Don't do anything if we can't write to the filesystem
            if(isExternalStorageWritable())
            {
                //File to contain information about the user
                File userFile = new File(
                        Environment.getExternalStoragePublicDirectory(""), "news/user");
                if(!userFile.exists())
                {
                    populateUserFile();
                }

                try {
                    //Fetch newsgroups.txt, save it, and give us a list of newsgroups
                    JSONArray newsgroups = populateNewsgroupsFile().getJSONArray("newsgroups");
                    //For every newsgroup, make the folders (does nothing if folders exist),
                    //And start fetching
                    for(int i = 0; i < newsgroups.length(); i++)
                    {
                        JSONObject newsgroup = newsgroups.getJSONObject(i);

                        makeFolder("news/" + newsgroup.getString("name"));
                        makeFolder("news/" + newsgroup.getString("name") + "/threadmetadata");

                        File newsgroupFolder = new File(
                            Environment.getExternalStoragePublicDirectory(""), 
                            "news/" + newsgroup.getString("name"));
                            
                        if(newsgroupFolder.list().length == 1)
                        {
                            //We have no posts for the newsgroup yet
                            //Get the json for the newsgroup
                            JSONObject newsgroupJson = getNewsgroup(newsgroup.getString("name"), null);
                            //Get the threads out of the returned json
                            JSONArray threads = newsgroupJson.getJSONArray("posts_older");
                            for(int j = 0; j < threads.length(); j++)
                            {
                                String threadNum = threads.getJSONObject(j).getJSONObject("post").getString("number");
                                writeThreadMetadata(newsgroup.getString("name"), threadNum, threads.getJSONObject(j));
                                getAndWritePostObjects(newsgroup.getString("name"), threads.getJSONObject(j));
                            }
                        }
                        //Get all posts that have occurred since the latest post we have
                        else
                        {
                            String lastPostDate = getDateOfLastPostFromNewsgroup(newsgroup.getString("name"));
                            JSONObject newsgroupJson = getNewsgroup(newsgroup.getString("name"), lastPostDate);
                            JSONArray posts = newsgroupJson.getJSONArray("posts_newer");
                            Log.d("Hi", newsgroup.getString("name") + " has " + posts.length() + " new posts since " + lastPostDate);
                            for(int j = posts.length() - 1; j >= 0; j--)
                            {
                                Log.d("Hi", posts.getJSONObject(j).toString(4));
                                String newsgroupName = newsgroup.getString("name");
                                String postNum = posts.getJSONObject(j).getJSONObject("post").getString("number");
                                ArrayList<NameValuePair> nvp = new ArrayList<NameValuePair>();
                                nvp.add(new BasicNameValuePair("html_body", "fsfsef"));
                                String postDetailString = makeGetRequest(newsgroupName + "/" + postNum, apiKey, nvp);
                                insertPostObjects(posts.getJSONObject(j), new JSONObject(postDetailString));
                            }
                        }
                    }
                } catch (JSONException e) {
                    Log.d("Hi", "JSON error with newsgroups");
                    Log.d("Hi", "JSON error: " + e.toString());
                } catch (NullPointerException e) {
                    Log.d("Hi", "NullPointerException, I guess we don't have network");
                    Log.d("Hi", "Error: " + e.toString());
                }
            }
            updateLock.release();
            return null;
        }

        @Override
        protected void onPostExecute(Object thing) {
            if(requester != null)
            {
                requester.updateFinished();
            }
        }



        private String getDateOfLastPostFromNewsgroup(String newsgroupName)
        {
            try {
                String latest = "";
                File newsgroupMetadataFolder = new File(
                    Environment.getExternalStoragePublicDirectory(""),
                    "news/" + newsgroupName + "/threadmetadata");
                for(String filename : newsgroupMetadataFolder.list())
                {
                    JSONObject threadMetadata = new JSONObject(
                        readFile("news/" + newsgroupName + "/threadmetadata/" + filename));
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
            } catch (JSONException e) {
                Log.d("Hi", "Error parsing JSON for getting latest date");
                Log.d("Hi", "Error: " + e.toString());
            }
            return null;
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
                Log.d("Hi", "Error parsing JSON for dates");
                Log.d("Hi", "Error: " + e.toString());
            }
        }

        private void writeThreadMetadata(String newsgroup, String threadNum, JSONObject threadMetadata)
        {
            try {
                String path = "news/" + newsgroup + "/threadmetadata/" + threadNum;
                writeFile(path, threadMetadata.toString(4));
            } catch (JSONException e) {
                Log.d("Hi", "JSON error with writing thread metadata");
                Log.d("Hi", "JSON error: " + e.toString());
            }
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

                    if(!post.getJSONObject("post").isNull("thread_parent"))
                    {
                        threadNum = post.getJSONObject("post").getJSONObject("thread_parent").getString("number");
                        threadParentNewsgroup = post.getJSONObject("post").getJSONObject("thread_parent").getString("newsgroup");
                    }
                    else
                    {
                        threadNum = postNum;
                    }
                    writeFile("news/" + newsgroup + "/" + postNum, post.toString(4));

                    if(newsgroup.equals(parentNewsgroup))
                    {

                        JSONObject threadMetadata = new JSONObject(
                                readFile("news/" + newsgroup + "/threadmetadata/" + threadNum));

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
                            writeFile("news/" + newsgroup + "/threadmetadata/" + threadNum, threadMetadata.toString(4));
                        }
                    }
                    else
                        writeFile("news/" + newsgroup + "/threadmetadata/" + postNum, postMetadata.toString(4));
                }
                else
                {
                    writeFile("news/" + newsgroup + "/" + postNum, post.toString(4));

                    postMetadata.put("children", new JSONArray());
                    writeFile("news/" + newsgroup + "/threadmetadata/" + postNum, postMetadata.toString(4));
                }


            } catch (JSONException e) {
                Log.d("Hi", "Error parsing JSON for insert post");
                Log.d("Hi", "Error: " + e.toString());
            }
        }
        private void getAndWritePostObjects(final String newsgroup, final JSONObject postMetadata)
        {
            new Thread() {
                public void run() {
                    try {

                        JSONArray children = postMetadata.getJSONArray("children");
                        for(int i = 0; i < children.length(); i++)
                        {
                            getAndWritePostObjects(newsgroup, children.getJSONObject(i));
                        }

                        String path = "news/" + newsgroup + "/" +
                                postMetadata.getJSONObject("post").getString("number");

                        ArrayList<NameValuePair> nvp = new ArrayList<NameValuePair>();
                        nvp.add(new BasicNameValuePair("html_body","hi there!"));

                        String postText = makeGetRequest(newsgroup + "/" + postMetadata.getJSONObject("post").getString("number"), apiKey, nvp);
                        JSONObject post = new JSONObject(postText);

                        writeFile(path, post.toString(4));
                    } catch (JSONException e) {
                        Log.d("Hi", "JSON error with writing post");
                        Log.d("Hi", "JSON error: " + e.toString());
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
            String result = makeGetRequest(newsgroupName + "/index", apiKey, params);
            try {
                JSONObject returnValue = new JSONObject(result);
                return returnValue;
            } catch (JSONException e) {
                Log.d("Hi", "JSON error with getting newsgroup " + newsgroupName);
                Log.d("Hi", "result: " + result);
                Log.d("Hi", "JSON error: " + e.toString());
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
                Log.d("Hi", "Error with JSON finding post " + num);
                Log.d("Hi", "Error: " + e.toString());
            }
            return null;
        }
        private JSONObject populateNewsgroupsFile()
        {
            String newsgroupsString = makeGetRequest("newsgroups", apiKey);
            JSONObject returnValue = null;

            try {
                JSONObject newsgroupsJSON = new JSONObject(newsgroupsString);
                writeFile("news/newsgroups", newsgroupsJSON.toString(4));

                returnValue = new JSONObject(newsgroupsString);
            } catch (JSONException e) {
                Log.d("Hi", "JSON error with populating newsgroups file");
                Log.d("Hi", "JSON error: " + e.toString());
            }
            return returnValue;
        }
        private void populateUserFile()
        {
            String userString = makeGetRequest("user", apiKey);
            try {
                JSONObject userJSON = new JSONObject(userString);
                makeFolder("news");
                writeFile("news/user", userJSON.toString(4));
            } catch (JSONException e) {
                Log.d("Hi", "Error parsing json for user");
                Log.d("Hi", "Error " + e.toString());
            }

        }

        /* Checks if external storage is available for read and write */
        public boolean isExternalStorageWritable() {
            String state = Environment.getExternalStorageState();
            return Environment.MEDIA_MOUNTED.equals(state);
        }
    }
}
