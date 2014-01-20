package edu.rit.csh.cshnews2;

import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Typeface;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

/**
 * Created by derek on 1/7/14.
 */
public class NewsgroupsSpinnerAdapter extends BaseAdapter implements SpinnerAdapter {
    Context context;
    JSONArray newsgroups;
    JSONArray recentActivity;

    public NewsgroupsSpinnerAdapter(Context context, JSONArray newsgroups, JSONArray recentActivity)
    {
        this.context = context;
        this.newsgroups = newsgroups;
        this.recentActivity = recentActivity;
    }

    @Override
    public int getCount() {
        if(newsgroups == null)
            return 0;
        return newsgroups.length() + 1;
    }

    @Override
    public Object getItem(int position) {
        if(position == 0)
            return "Recent Activity";
        position--;
        try {
            return newsgroups.getJSONObject(position);
        } catch (JSONException e) {
            Log.d("Hi", "Error parsing json in NewsgroupsSpinnerAdapter.getItem");
        }
        return null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View view, ViewGroup parent) {
        TextView text = new TextView(context);
        text.setPadding(7, 7, 7, 7);
        text.setTextSize(18);
        try {
            if(position == 0)
            {
                String name = "Recent Activity";
                text.setText(name);
            }
            else
            {
                position--;

                    String name = newsgroups.getJSONObject(position).getString("name");
                    int unread_count = newsgroups.getJSONObject(position).getInt("unread_count");
                    if(unread_count > 0)
                    {
                        name += " (" + unread_count + ")";
                        text.setTypeface(null, Typeface.BOLD);
                    }
                    text.setText(name);

            }
        } catch (JSONException e) {
            Log.d("Hi", "Error parsing json in NewsgroupsSpinnerAdapter.getItem");
            Log.d("Hi", "Error " + e.toString());
        }
        return text;
    }

    public void setJSONArray(JSONArray newsgroups)
    {
        this.newsgroups = newsgroups;
    }
    public void setRecentActivity(JSONArray recentActivity)
    {
        this.recentActivity = recentActivity;
    }
}
