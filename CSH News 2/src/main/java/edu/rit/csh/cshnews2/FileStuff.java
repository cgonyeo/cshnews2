package edu.rit.csh.cshnews2;

import android.os.Environment;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.Semaphore;

/**
 * Created by derek on 12/23/13.
 */
public class FileStuff {
    static String rootFolder = "";
    static Semaphore writeLock = new Semaphore(1, true);
    static boolean initHasRun = false;

    public static void init()
    {
        if(!initHasRun)
        {
            initHasRun = true;
            rootFolder = Environment.getExternalStoragePublicDirectory("") + "/news";
            if(!fileExists(rootFolder))
                makeFolder(rootFolder);
        }
    }

    //Makes the folder at path if it doesn't already exist
    public static void makeFolder(String path)
    {
        try
        {
           writeLock.acquire();
        }
        catch(InterruptedException e)
        {
            Log.e("Hi", "Error getting writeLock in makeFolder");
        }

        File folder = new File(rootFolder, path);
        if(!folder.exists())
            folder.mkdirs();

        writeLock.release();
    }

    public static boolean fileExists(String path)
    {
        try
        {
            writeLock.acquire();
        }
        catch(InterruptedException e)
        {
            Log.e("Hi", "Error getting writeLock in makeFolder");
        }

        File file = new File(rootFolder, path);
        boolean exists = file.exists();

        writeLock.release();

        return exists;
    }

    //Writes content to path. If path already exists, it is overwritten.
    public static void writeFile(String path, String content)
    {
        try
        {
        writeLock.acquire();
        }
        catch(InterruptedException e)
        {
            Log.e("Hi", "Error getting writeLock in writeFile");
        }

        Log.d("Hi", "Writing to " + path);
        try
        {
            File file = new File(rootFolder, path);
            file.createNewFile();

            BufferedWriter bw =
                    new BufferedWriter(new FileWriter(file.getAbsoluteFile()));
            bw.write(content);
            bw.close();
        } catch (IOException e) {
            Log.e("Hi", "IO error with writing file");
            Log.e("Hi", "IO error: " + e.toString());
        }
        writeLock.release();
    }

    //Returns the String contained in the file at path
    public static String readFile(String path)
    {
        try
        {
            writeLock.acquire();
        }
        catch(InterruptedException e)
        {
            Log.e("Hi", "Error getting writeLock in readFile");
        }

        String returnValue = "";
        try {
            File file = new File(rootFolder, path);
            FileInputStream fis = new FileInputStream(file);
            byte[] data = new byte[(int)file.length()];
            fis.read(data);
            fis.close();
            returnValue = new String(data, "UTF-8");
        } catch (FileNotFoundException e) {
            Log.e("Hi", "Error opening file " + path);
            Log.e("Hi", "Error: " + e.toString());
        } catch (IOException e) {
            Log.e("Hi", "Error reading file " + path);
            Log.e("Hi", "Error: " + e.toString());
        }

        writeLock.release();
        return returnValue;
    }

    public static JSONObject readJSONObject(String path)
    {
        try {
            return new JSONObject(readFile(path));
        } catch (JSONException e) {
            Log.e("Hi", "Error parsing json for readJSONObject");
            Log.e("Hi", "Error " + e.toString());
        }
        return null;
    }

    public static JSONArray readJSONArray(String path)
    {
        try {
            return new JSONArray(readFile(path));
        } catch (JSONException e) {
            Log.e("Hi", "Error parsing json for readJSONArray");
            Log.e("Hi", "Error " + e.toString());
        }
        return null;
    }

    public static void writeJSONObject(String path, JSONObject obj)
    {
        try {
            writeFile(path, obj.toString(4));
        } catch (JSONException e) {
            Log.e("Hi", "Error parsing json for writeJSONObject");
            Log.e("Hi", "Error " + e.toString());
        }
    }

    public static void writeJSONArray(String path, JSONArray arr)
    {
        try {
            writeFile(path, arr.toString(4));
        } catch (JSONException e) {
            Log.e("Hi", "Error parsing json for writeJSONArray");
            Log.e("Hi", "Error " + e.toString());
        }
    }

    public static File[] getFilesInFolder(String path)
    {
        File folder = new File(rootFolder, path);
        return folder.listFiles();
    }
}
