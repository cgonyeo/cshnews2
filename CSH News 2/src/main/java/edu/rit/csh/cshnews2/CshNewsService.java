package edu.rit.csh.cshnews2;

import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.IBinder;
import android.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.conn.HttpHostConnectException;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
//import java.net.HttpURLConnection;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLPeerUnverifiedException;


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
        //TODO for communication return IBinder implementation
        return null;
    }

    class PostFetcher extends AsyncTask
    {
        @Override
        protected Object doInBackground(Object[] params) {
            String urlParameters = "api_key=419ec537c23c42df";
            URL url;
            HttpsURLConnection connection = null;
            Log.d("Hi", "Async task started");
            try {
                Log.d("Hi", "0");
                //Create connection
                url = new URL("https", "webnews.csh.rit.edu", 443, "user");
                Log.d("Hi", "Url host: " + url.getHost());
                Log.d("Hi", "Url port: " + url.getPort());
                Log.d("Hi", "Url protocol: " + url.getProtocol());
                Log.d("Hi", "Url path: " + url.getPath());
                connection = (HttpsURLConnection)url.openConnection();
                Log.d("Hi", "1");
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Content-Type",
                        "application/x-www-form-urlencoded");
                connection.setRequestProperty("User-Agent","Curl");
                connection.setRequestProperty("Accept","application/json");

                //connection.setRequestProperty("Content-Length", "" +
                //        Integer.toString(urlParameters.getBytes().length));
                //connection.setRequestProperty("Content-Language", "en-US");
                Log.d("Hi", "2");

                connection.setUseCaches(false);
                connection.setDoInput(true);
                connection.setDoOutput(true);
                Log.d("Hi", "2.5");

                //Send request
                DataOutputStream wr = new DataOutputStream (
                        connection.getOutputStream ());
                wr.writeBytes(urlParameters);
                wr.flush();
                wr.close();
                Log.d("Hi", "3 " + connection.getResponseCode());

                //Get Response
                InputStream is = connection.getInputStream();
                Log.d("Hi", "3.5");
                BufferedReader rd = new BufferedReader(new InputStreamReader(is));
                Log.d("Hi", "4");
                String line;
                StringBuffer response = new StringBuffer();
                while((line = rd.readLine()) != null) {
                    response.append(line);
                    response.append('\r');
                }
                Log.d("Hi", "5");
                rd.close();
                Log.d("Hi!", response.toString());
                return null;

            } catch (Exception e) {
                Log.d("Hi!", "Error: " + e.toString());
                return null;

            } finally {

                if(connection != null) {
                    connection.disconnect();
                }
            }
        }
    }
}
