package edu.rit.csh.cshnews2;

import android.util.Log;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;

/**
 * Created by derek on 12/23/13.
 */
public class UnreadTools {
    static Semaphore unreadListLock = new Semaphore(1, true);

    //Starting at currentNewsgroup, iterates through the newsgroups looking for
    //threads with unread posts in them. Returns the first one it finds, and
    //returns null if none found.
    public static JSONObject getNextUnread(String currentNewsgroup)
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
    public static boolean threadHasUnread(JSONObject threadMetadata, String selectedNewsgroup)
    {
        try {
            boolean hasUnread = false;
            JSONArray children = threadMetadata.getJSONArray("children");
            for(int i= 0; i < children.length(); i++)
                hasUnread |= threadHasUnread(children.getJSONObject(i), selectedNewsgroup);
            String unread_class = FileStuff.readJSONObject(selectedNewsgroup + "/" + threadMetadata.getJSONObject("post")
                    .getString("number")).getJSONObject("post").getString("unread_class");
            hasUnread |= !unread_class.equals("null");
            return hasUnread;
        } catch (JSONException e) {
            Log.e("Hi", "Error parsing json for threadHasUnread");
            Log.e("Hi", "Error " + e.toString());
        }
        return false;
    }

    public static void changeReadStatusOfPost(final String newsgroup, final String postNum, final boolean newValue)
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

                    JSONObject post = FileStuff.readJSONObject(newsgroup + "/" + postNum);
                    post.getJSONObject("post").put("unread_class", (newValue ? "null" : "manual"));
                    FileStuff.writeJSONObject(newsgroup + "/" + postNum, post);
                } catch (JSONException e) {
                    Log.e("Hi", "Error parsing JSON for changeReadStatusOfPost");
                    Log.e("Hi", "Error " + e.toString());
                }
            }
        }.start();
    }

    public static void addToUnreadList(String newsgroup, int threadNum, int postnum)
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

    public static void removeFromUnreadList(String newsgroup, int postnum)
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
}
