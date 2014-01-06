package edu.rit.csh.cshnews2;

import android.app.ActionBar;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.widget.SlidingPaneLayout;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class NewsgroupActivity extends Activity {
    private SlidingPaneLayout mSlidingLayout;
    private ListView mList;
    //private TextView mContent;
    private LinearLayout mPosts;
    boolean mBound = false;
    CshNewsService mService;
    JSONArray threadMetadatas;

    String selectedNewsgroup = "csh.test";

    private ActionBarHelper mActionBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.fragment_newsgroup);


        mSlidingLayout = (SlidingPaneLayout) findViewById(R.id.sliding_pane_layout);
        mList = (ListView) findViewById(R.id.left_pane);
        //mContent = (TextView) findViewById(R.id.content_text);
        mPosts = (LinearLayout) findViewById(R.id.posts);

        mSlidingLayout.setPanelSlideListener(new SliderListener());
        mSlidingLayout.openPane();

        String[] loadingText = {"Loading..."};
        mList.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1,
                loadingText));
        mList.setOnItemClickListener(new ListItemClickListener());

        mActionBar = createActionBarHelper();
        mActionBar.init();

        mSlidingLayout.getViewTreeObserver().addOnGlobalLayoutListener(new FirstLayoutListener());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        /*
         * The action bar up action should open the slider if it is currently closed,
         * as the left pane contains content one level up in the navigation hierarchy.
         */
        if (item.getItemId() == android.R.id.home && !mSlidingLayout.isOpen()) {
            mSlidingLayout.openPane();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent i = new Intent(this, CshNewsService.class);
        i.putExtra("action", "startService");
        startService(i);
        // Bind to LocalService
        Intent intent = new Intent(this, CshNewsService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onStop() {
        super.onDestroy();
        if(mBound)
        {
            unbindService(mConnection);
            mBound = false;
        }
    }

    //Changes the detail view to show the information from the selected thread
    private void onThreadSelected(int threadSelected)
    {
        mPosts.removeAllViews();

        try {
            JSONObject postjson = threadMetadatas.getJSONObject(threadSelected);
            addPostView(postjson);
        } catch (JSONException e) {
            Log.d("Hi", "Error parsing json for onThreadSelected");
            Log.d("Hi", "Error " + e.toString());
        }
    }

    private void addPostView(JSONObject postjsonmetadata) throws JSONException
    {
        JSONObject post = mService.getPost(selectedNewsgroup, postjsonmetadata.getJSONObject("post").getString("number"));
        LinearLayout postView = buildPostView(post.getJSONObject("post"));
        mPosts.addView(postView);
        JSONArray children = postjsonmetadata.getJSONArray("children");
        for(int i = 0; i < children.length(); i++)
            addPostView(children.getJSONObject(i));
    }

    //Returns a linearlayout containing the information given in post
    private LinearLayout buildPostView(JSONObject post)
    {
        LinearLayout rootView = new LinearLayout(this);
        RelativeLayout nameAndTimeHolder = new RelativeLayout(this);
        TextView nameView = new TextView(this);
        TextView timeView = new TextView(this);
        RelativeLayout subjectAndMenuHolder = new RelativeLayout(this);
        TextView subjectView = new TextView(this);
        Button menuButton = new Button(this);
        ImageView dividerView = new ImageView(this);
        TextView bodyView = new TextView(this);

        try {
            nameView.setText(post.getString("author_name"));
            timeView.setText(post.getString("date"));
            subjectView.setText(post.getString("subject"));

            String bodyString = post.getString("body");
            bodyString = bodyString.replace("\n", "<br/>");
            bodyView.setText(Html.fromHtml(bodyString, null, null));
            bodyView.setMovementMethod(LinkMovementMethod.getInstance());
        } catch (JSONException e) {
            Log.d("Hi", "Error parsing json for buildPostView");
            Log.d("Hi", "Error " + e.toString());
        }

        LinearLayout.LayoutParams rootViewParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rootView.setLayoutParams(rootViewParams);
        rootView.setOrientation(LinearLayout.VERTICAL);
        rootView.setPadding(10,10,10,10);

        RelativeLayout.LayoutParams relativeParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT,
                RelativeLayout.LayoutParams.WRAP_CONTENT);
        nameAndTimeHolder.setLayoutParams(relativeParams);
        nameAndTimeHolder.addView(nameView);
        nameAndTimeHolder.addView(timeView);
        RelativeLayout.LayoutParams params1 = (RelativeLayout.LayoutParams)nameView.getLayoutParams();
        params1.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        RelativeLayout.LayoutParams params2 = (RelativeLayout.LayoutParams)timeView.getLayoutParams();
        params2.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        rootView.addView(nameAndTimeHolder);

        subjectAndMenuHolder.setLayoutParams(relativeParams);
        subjectAndMenuHolder.addView(subjectView);
        RelativeLayout.LayoutParams params3 = (RelativeLayout.LayoutParams)subjectView.getLayoutParams();
        params3.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        rootView.addView(subjectAndMenuHolder);

        rootView.addView(bodyView);

        return rootView;
    }


    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            CshNewsService.LocalBinder binder = (CshNewsService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;

            threadMetadatas = mService.getThreadsForNewsgroup(selectedNewsgroup);
            if(threadMetadatas != null)
            {
                ArrayList<String> threads = new ArrayList<String>();
                for(int i = 0; i < threadMetadatas.length(); i++)
                    try {
                        threads.add(threadMetadatas.getJSONObject(i).getJSONObject("post").getString("subject"));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                mList.setAdapter(new ArrayAdapter<String>(NewsgroupActivity.this, android.R.layout.simple_list_item_1,
                        threads));
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    /**
     * This list item click listener implements very simple view switching by changing
     * the primary content text. The slider is closed when a selection is made to fully
     * reveal the content.
     */
    private class ListItemClickListener implements ListView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            //mContent.setText(Shakespeare.DIALOGUE[position]);
            onThreadSelected(position);
            //mActionBar.setTitle(Shakespeare.TITLES[position]);
            mSlidingLayout.closePane();
        }
    }

    /**
     * This panel slide listener updates the action bar accordingly for each panel state.
     */
    private class SliderListener extends SlidingPaneLayout.SimplePanelSlideListener {
        @Override
        public void onPanelOpened(View panel) {
            mActionBar.onPanelOpened();
        }

        @Override
        public void onPanelClosed(View panel) {
            mActionBar.onPanelClosed();
        }
    }

    /**
     * This global layout listener is used to fire an event after first layout occurs
     * and then it is removed. This gives us a chance to configure parts of the UI
     * that adapt based on available space after they have had the opportunity to measure
     * and layout.
     */
    private class FirstLayoutListener implements ViewTreeObserver.OnGlobalLayoutListener {
        @Override
        public void onGlobalLayout() {
            mActionBar.onFirstLayout();
            mSlidingLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
        }
    }

    /**
     * Create a compatible helper that will manipulate the action bar if available.
     */
    private ActionBarHelper createActionBarHelper() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            return new ActionBarHelperICS();
        } else {
            return new ActionBarHelper();
        }
    }

    /**
     * Stub action bar helper; this does nothing.
     */
    private class ActionBarHelper {
        public void init() {}
        public void onPanelClosed() {}
        public void onPanelOpened() {}
        public void onFirstLayout() {}
        public void setTitle(CharSequence title) {}
    }

    /**
     * Action bar helper for use on ICS and newer devices.
     */
    private class ActionBarHelperICS extends ActionBarHelper {
        private final ActionBar mActionBar;
        private CharSequence mDrawerTitle;
        private CharSequence mTitle;

        ActionBarHelperICS() {
            mActionBar = getActionBar();
        }

        @Override
        public void init() {
            mActionBar.setDisplayHomeAsUpEnabled(true);
            mActionBar.setHomeButtonEnabled(true);
            mTitle = mDrawerTitle = getTitle();
        }

        @Override
        public void onPanelClosed() {
            super.onPanelClosed();
            mActionBar.setDisplayHomeAsUpEnabled(true);
            mActionBar.setHomeButtonEnabled(true);
            mActionBar.setTitle(mTitle);
        }

        @Override
        public void onPanelOpened() {
            super.onPanelOpened();
            mActionBar.setHomeButtonEnabled(false);
            mActionBar.setDisplayHomeAsUpEnabled(false);
            mActionBar.setTitle(mDrawerTitle);
        }

        @Override
        public void onFirstLayout() {
            if (mSlidingLayout.isSlideable() && !mSlidingLayout.isOpen()) {
                onPanelClosed();
            } else {
                onPanelOpened();
            }
        }

        @Override
        public void setTitle(CharSequence title) {
            mTitle = title;
        }
    }

}