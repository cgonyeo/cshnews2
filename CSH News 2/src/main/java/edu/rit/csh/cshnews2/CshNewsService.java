package edu.rit.csh.cshnews2;

import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
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
//import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Scanner;


/**
 * Created by derek on 12/23/13.
 */
public class CshNewsService extends Service {
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("Hi", "Starting service...");
        new PostFetcher().execute();
        return Service.START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    class PostFetcher extends AsyncTask
    {
        String apiKey = "0000000000000000";
        int returnStatus = 0;
        @Override
        protected Object doInBackground(Object[] parameters) {
            if(isExternalStorageWritable())
            {
                File userFile = new File(
                        Environment.getExternalStoragePublicDirectory(""), "news/user.txt");
                if(!userFile.exists())
                {
                    populateUserFile();
                }

                try {
                    JSONArray newsgroups = populateNewsgroupsFile().getJSONArray("newsgroups");
                    for(int i = 0; i < newsgroups.length(); i++)
                    {
                        JSONObject newsgroup = newsgroups.getJSONObject(i);

                        makeFolder("news/" + newsgroup.getString("name"));
                        makeFolder("news/" + newsgroup.getString("name") + "/threadmetadata");

                        File newsgroupFolder = new File(
                            Environment.getExternalStoragePublicDirectory(""), 
                            "news/" + newsgroup.getString("name"));
                            
                        //We have yet to fetch any posts
                        if(newsgroupFolder.list().length == 1)
                        {
                            JSONObject newsgroupJson = getNewsgroup(newsgroup.getString("name"), null);
                            JSONArray threads = newsgroupJson.getJSONArray("posts_older");
                            for(int j = 0; j < threads.length(); j++)
                            {
                                String threadNum = threads.getJSONObject(j).getJSONObject("post").getString("number");
                                writeThreadMetadata(newsgroup.getString("name"), threadNum, threads.getJSONObject(j));
                                makeFolder("news/" + newsgroup.getString("name") + "/" + threadNum);
                                getAndWritePostObjects(newsgroup.getString("name"),
                                        threadNum,
                                        threads.getJSONObject(j));
                            }
                        }
                        //Get all posts that have occurred since the latest post we have
                        else
                        {
                            Log.d("Hi", "User info: " + readFile("news/user.txt"));
                        }
                    }
                } catch (JSONException e) {
                    Log.d("Hi", "JSON error with newsgroups");
                    Log.d("Hi", "JSON error: " + e.toString());
                }
            }

            return null;
        }
        
        //File manipulation methods
        private void makeFolder(String path)
        {
            File folder = new File(
                    Environment.getExternalStoragePublicDirectory(""), path);
            if(!folder.exists())
                folder.mkdirs();
        }
        private void writeFile(String path, String content)
        {
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
        }
        private String readFile(String path)
        {
            String returnValue = "";
            Scanner reader = null;
            try {
                reader = new Scanner(new FileInputStream(
                    Environment.getExternalStoragePublicDirectory("") + "/" + path));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            while(reader.hasNext())
                returnValue += reader.nextLine();
            return returnValue;
        }

        private String getDateOfLastPost()
        {
            try {
                JSONArray newsgroups = 
                    new JSONObject(readFile("news/newsgroups.txt"))
                        .getJSONArray("newsgroups");
                String latest = "";
                for(int i = 0; i < newsgroups.length(); i++)
                {
                    String newsgroupName = newsgroups.getJSONObject(i).getString("name");
                    File newsgroupMetadataFolder = new File(
                        Environment.getExternalStoragePublicDirectory(""), 
                        "news/" + newsgroupName + "/threadmetadata");
                    for(String filename : newsgroupMetadataFolder.list())
                    {
                        JSONObject threadMetadata = new JSONObject(
                            readFile("news/" + newsgroupName + "/threadmetadata/" + filename));
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
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
        private void getAndWritePostObjects(String newsgroup, String threadNum, JSONObject postMetadata)
        {
            try {
                String path = "news/" + newsgroup + "/" + threadNum + "/" +
                        postMetadata.getJSONObject("post").getString("number");

                ArrayList<NameValuePair> nvp = new ArrayList<NameValuePair>();
                nvp.add(new BasicNameValuePair("html_body","hi there!"));

                String postText = makeGetRequest(newsgroup + "/" + postMetadata.getJSONObject("post").getString("number"), apiKey, nvp);
                JSONObject post = new JSONObject(postText);

                writeFile(path, post.toString(4));

                JSONArray children = postMetadata.getJSONArray("children");
                for(int i = 0; i < children.length(); i++)
                {
                    getAndWritePostObjects(newsgroup, threadNum, children.getJSONObject(i));
                }
            } catch (JSONException e) {
                Log.d("Hi", "JSON error with writing post");
                Log.d("Hi", "JSON error: " + e.toString());
            }
        }
        private JSONObject getNewsgroup(String newsgroupName, String since)
        {
            ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
            params.add(new BasicNameValuePair("limit", "5"));
            if(since != null)
            {
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
        private JSONObject populateNewsgroupsFile()
        {
            String newsgroupsString = makeGetRequest("newsgroups", apiKey);
            writeFile("news/newsgroups.txt", newsgroupsString);

            JSONObject returnValue = null;
            try {
                returnValue = new JSONObject(newsgroupsString);
            } catch (JSONException e) {
                Log.d("Hi", "JSON error with populating newsgroups file");
                Log.d("Hi", "JSON error: " + e.toString());
            }
            return returnValue;
        }
        private void populateUserFile()
        {
            File userFile = new File(Environment.getExternalStoragePublicDirectory(""), "news/user.txt");
            String userString = makeGetRequest("user", apiKey);

            makeFolder("news");
            writeFile("news/user.txt", userString);
        }
        private String makeGetRequest(String page, String apiKey)
        {
            ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
            return makeGetRequest(page, apiKey, params);
        }
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

                Log.d("Hi", "Status: " + response.getStatusLine().getStatusCode());

                // Get the response
                BufferedReader rd = new BufferedReader
                        (new InputStreamReader(response.getEntity().getContent()));

                String line = "";
                String result = "";
                while ((line = rd.readLine()) != null) {
                    result += line;
                }
                returnStatus = response.getStatusLine().getStatusCode();
                return result;
            } catch (IOException e) {
                if(response != null)
                    returnStatus = response.getStatusLine().getStatusCode();
                Log.d("Hi", "IOException on the get request to " + page);
                return response.getStatusLine().getStatusCode() + "Error!";
            } catch (URISyntaxException e) {
                if(response != null)
                    returnStatus = response.getStatusLine().getStatusCode();
                Log.d("Hi", "URISyntaxException on the get request to " + page);
                return "Error!";
            }
        }

        /* Checks if external storage is available for read and write */
        public boolean isExternalStorageWritable() {
            String state = Environment.getExternalStorageState();
            return Environment.MEDIA_MOUNTED.equals(state);
        }
    }
}
