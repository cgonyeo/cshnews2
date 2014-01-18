package edu.rit.csh.cshnews2;

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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;

/**
 * Created by derek on 12/23/13.
 */
public class NetworkStuff {
    public static int returnStatus;
    private static String apiKey;
    private static boolean initHasRun = false;

    public static void init(String api_key)
    {
        if(!initHasRun)
        {
            initHasRun = true;
            apiKey = api_key;
        }
    }
    //Makes a get request to page with no additional parameters
    public static String makeGetRequest(String page)
    {
        ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
        return makeGetRequest(page, params);
    }
    //Makes a get request to page with the specified additional parameters
    public static String makeGetRequest(String page, ArrayList<NameValuePair> params)
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
            rd.close();
            returnStatus = response.getStatusLine().getStatusCode();
            return sb.toString();
        } catch (IOException e) {
            if(response != null && response.getStatusLine() != null)
                returnStatus = response.getStatusLine().getStatusCode();
            Log.e("Hi", "IOException on the get request to " + page);
            params.remove(params.size() - 1);
            params.remove(params.size() - 1);
            return makeGetRequest(page, params);
        } catch (URISyntaxException e) {
            if(response != null && response.getStatusLine() != null)
                returnStatus = response.getStatusLine().getStatusCode();
            Log.e("Hi", "URISyntaxException on the get request to " + page);
            return null;
        }
    }

    //Makes a get request to page with the specified additional parameters
    public static String makePutRequest(String page, ArrayList<NameValuePair> params)
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
            rd.close();
            returnStatus = response.getStatusLine().getStatusCode();
            return sb.toString();
        } catch (IOException e) {
            if(response != null && response.getStatusLine() != null)
                returnStatus = response.getStatusLine().getStatusCode();
            Log.e("Hi", "IOException on the get request to " + page);
            return "Error!";
        } catch (URISyntaxException e) {
            if(response != null && response.getStatusLine() != null)
                returnStatus = response.getStatusLine().getStatusCode();
            Log.e("Hi", "URISyntaxException on the get request to " + page);
            return "Error!";
        }
    }
}
