package edu.rit.csh.cshnews2;

import android.app.ActionBar;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.widget.SlidingPaneLayout;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import uk.co.senab.actionbarpulltorefresh.library.ActionBarPullToRefresh;
import uk.co.senab.actionbarpulltorefresh.library.listeners.OnRefreshListener;
import uk.co.senab.actionbarpulltorefresh.library.PullToRefreshLayout;


public class NewsgroupActivity extends Activity implements ActionBar.OnNavigationListener, OnRefreshListener {
    private SlidingPaneLayout mSlidingLayout;
    private ListView mList;
    private LinearLayout mPosts;
    boolean mBound = false;
    CshNewsService mService;
    JSONArray threadMetadatas;
    JSONArray newsgroups;
    NewsgroupsSpinnerAdapter newsgroupsSpinner;
    boolean inRecentActivity = true;
    JSONArray recentActivity;

    String selectedNewsgroup = "csh.test";
    String viewNewsgroup = "";
    String threadNum;
    ArrayList<String[]> newsgroupThreadsMetadata;

    private ActionBarHelper mActionBar;
    private PullToRefreshLayout mPullToRefreshLayout;

    BroadcastReceiver updateReceiver;

    boolean loadPost = false;
    int spinnerSelection = -1;
    boolean onResumeWasCalledWithoutOnCreate = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_newsgroup);

        if(savedInstanceState != null)
            spinnerSelection = savedInstanceState.getInt("spinnerSelection");

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

        newsgroupsSpinner = new NewsgroupsSpinnerAdapter(this, new JSONArray(), new JSONArray());

        mActionBar = createActionBarHelper();
        mActionBar.init();

        mSlidingLayout.getViewTreeObserver().addOnGlobalLayoutListener(new FirstLayoutListener());

        // Now find the PullToRefreshLayout to setup
        mPullToRefreshLayout = (PullToRefreshLayout) findViewById(R.id.ptr_layout);

        updateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateFinished();
            }
        };

        // Now setup the PullToRefreshLayout
        ActionBarPullToRefresh.from(this)
                // Mark All Children as pullable
                .allChildrenArePullable()
                        // Set the OnRefreshListener
                .listener(this)
                        // Finally commit the setup to our PullToRefreshLayout
                .setup(mPullToRefreshLayout);

        Intent i = new Intent(this, CshNewsService.class);
        startService(i);
        // Bind to LocalService
        Intent intent = new Intent(this, CshNewsService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        onResumeWasCalledWithoutOnCreate = false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.newsgroup, menu);
        return true;
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
        if(item.getItemId() == R.id.action_settings) {
            Intent i = new Intent(this, SettingsActivity.class);
            startActivity(i);
        }
        if(item.getItemId() == R.id.action_next) {
            JSONObject next = UnreadTools.getNextUnread(selectedNewsgroup);
            if(next != null)
            {
                String newsgroup = "";
                int threadNum = -1;
                try {
                    newsgroup = next.getJSONObject("post").getString("newsgroup");
                    threadNum = next.getJSONObject("post").getInt("number");
                } catch (JSONException e) {
                    Log.d("Hi", "Error parsing json for menu item next");
                    Log.d("Hi", "Error " + e.toString());
                }
                viewNewsgroup = newsgroup;
                mActionBar.setTitle(viewNewsgroup);
                if(mSlidingLayout.isOpen())
                    mSlidingLayout.closePane();
                mPosts.removeAllViews();
                int unreadCounter = addPostView(next, 0);
                changeUnreadCounterInNewsgroup(newsgroup, unreadCounter * -1);

                try {
                    if(unreadCounter != 0 )
                    {
                        for(int i = 0; i < recentActivity.length(); i++)
                        {
                            JSONObject thread_parent = recentActivity.getJSONObject(i).getJSONObject("thread_parent");
                            if(thread_parent.getString("newsgroup").equals(viewNewsgroup) &&
                                    thread_parent.getInt("number") == threadNum)
                            {
                                recentActivity.getJSONObject(i).put("unread_count", 0);
                                mService.saveRecentActivity(recentActivity);
                            }
                        }
                        newsgroupChanged(false);
                    }
                } catch (JSONException e) {
                    Log.e("Hi", "Error parsing json for onThreadSelected while editing recent activity while not in recent activity");
                    Log.d("Hi", "Error " + e.toString());
                }
            }
            else
            {
                Toast.makeText(getApplicationContext(), "No more unread posts",
                        Toast.LENGTH_LONG).show();
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerReceiver(updateReceiver, new IntentFilter(NewsUpdater.UPDATE_FINISHED));
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(mBound)
        {
            mService.deregisterUI();
            unbindService(mConnection);
            mBound = false;
        }
        unregisterReceiver(updateReceiver);
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        if(onResumeWasCalledWithoutOnCreate)
        {
            newsgroups = mService.getNewsgroups();
            newsgroupChanged(false);
        }
        onResumeWasCalledWithoutOnCreate = true;
    }

    @Override
    public void onBackPressed() {
        if(!mSlidingLayout.isOpen())
            mSlidingLayout.openPane();
        else
            super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState (Bundle outState)
    {
        super.onSaveInstanceState(outState);
        if(threadMetadatas != null)
            outState.putString("threadMetadatas", threadMetadatas.toString());
        outState.putString("newsgroups", newsgroups.toString());
        outState.putString("recentActivity", recentActivity.toString());
        outState.putString("selectedNewsgroup", selectedNewsgroup);
        outState.putString("viewNewsgroup", viewNewsgroup);
        outState.putBoolean("inRecentActivity", inRecentActivity);
        if(mActionBar.mActionBar != null)
            outState.putInt("spinnerSelection", mActionBar.mActionBar.getSelectedNavigationIndex());
        if(!mSlidingLayout.isOpen())
            outState.putString("threadNum", threadNum);
    }

    @Override
    protected void onRestoreInstanceState (Bundle savedInstanceState)
    {
        super.onRestoreInstanceState(savedInstanceState);
        try
        {
            if(savedInstanceState.containsKey("threadMetadatas"))
                threadMetadatas = new JSONArray(savedInstanceState.getString("threadMetadatas"));
            newsgroups = new JSONArray(savedInstanceState.getString("newsgroups"));
            recentActivity = new JSONArray(savedInstanceState.getString("recentActivity"));
            selectedNewsgroup = savedInstanceState.getString("selectedNewsgroup");
            viewNewsgroup = savedInstanceState.getString("viewNewsgroup");
            inRecentActivity = savedInstanceState.getBoolean("inRecentActivity");
            spinnerSelection = savedInstanceState.getInt("spinnerSelection");
            if(savedInstanceState.containsKey("threadNum"))
            {
                loadPost = true;
                threadNum = savedInstanceState.getString("threadNum");
            }
        } catch (JSONException e) {
            Log.e("Hi", "Error parsing json in onRestoreInstanceState");
            Log.e("Hi", "Error " + e.toString());
        }
    }

    @Override
    public void onRefreshStarted(View view) {
        mService.update();
    }

    //Changes the detail view to show the information from the selected thread
    private void onThreadSelected(int threadSelected)
    {
        mPosts.removeAllViews();

        if(inRecentActivity)
        {
            try {
                viewNewsgroup = recentActivity.getJSONObject(threadSelected).getJSONObject("thread_parent").getString("newsgroup");
                int postNum = recentActivity.getJSONObject(threadSelected).getJSONObject("thread_parent").getInt("number");
                JSONObject postjson = mService.getThreadMetadata(viewNewsgroup, postNum + "");
                int unreadCounter = addPostView(postjson, 0);

                if(unreadCounter>0)
                {
                    newsgroupThreadsMetadata.get(threadSelected)[3] = "n";
                    ((ThreadsListAdapter)mList.getAdapter()).notifyDataSetChanged();

                    recentActivity.getJSONObject(threadSelected).put("unread_count", 0);
                    mService.saveRecentActivity(recentActivity);

                    changeUnreadCounterInNewsgroup(viewNewsgroup, unreadCounter * -1);
                }
            } catch (JSONException e) {
                Log.d("Hi", "Error parsing json for onThreadSelected while in recent activity");
                Log.d("Hi", "Error " + e.toString());
            }
        }
        else
        {
            viewNewsgroup = selectedNewsgroup;
            int unreadCounter = 0;
            int threadNum = 0;
            try {
                JSONObject postjson = threadMetadatas.getJSONObject(threadSelected);
                unreadCounter = addPostView(postjson, 0);
                threadNum = postjson.getJSONObject("post").getInt("number");
            } catch (JSONException e) {
                Log.d("Hi", "Error parsing json for onThreadSelected while not in recent activity");
                Log.d("Hi", "Error " + e.toString());
            }

            if(unreadCounter>0)
            {
                newsgroupThreadsMetadata.get(threadSelected)[3] = "n";
                ((ThreadsListAdapter)mList.getAdapter()).notifyDataSetChanged();

                changeUnreadCounterInNewsgroup(selectedNewsgroup, unreadCounter * -1);

                try {
                    for(int i = 0; i < recentActivity.length(); i++)
                    {
                        JSONObject thread_parent = recentActivity.getJSONObject(i).getJSONObject("thread_parent");
                        if(thread_parent.getString("newsgroup").equals(selectedNewsgroup) &&
                                thread_parent.getInt("number") == threadNum)
                        {
                            recentActivity.getJSONObject(i).put("unread_count", 0);
                            mService.saveRecentActivity(recentActivity);
                        }
                    }
                } catch (JSONException e) {
                    Log.e("Hi", "Error parsing json for onThreadSelected while editing recent activity while not in recent activity");
                    Log.d("Hi", "Error " + e.toString());
                }
            }
        }
    }

    private void changeUnreadCounterInNewsgroup(String newsgroup, int changeAmount)
    {
        Log.d("Hi", "Changing spinner value for " + newsgroup + " by " + changeAmount);
        for(int i = 0; i < newsgroups.length(); i++)
        {
            try {
                if(newsgroups.getJSONObject(i).getString("name").equals(newsgroup))
                {
                    int count = newsgroups.getJSONObject(i).getInt("unread_count");
                    newsgroups.getJSONObject(i).put("unread_count", count + changeAmount);
                }
            } catch (JSONException e) {
                Log.d("Hi", "Error parsing json to decrement newsgroup unread count");
                Log.d("Hi", "Error " + e.toString());
            }
        }
        newsgroupsSpinner.setJSONArray(newsgroups);
        newsgroupsSpinner.notifyDataSetChanged();
    }

    private int addPostView(JSONObject postjsonmetadata, int depth)
    {
        try {
            String newsgroup = postjsonmetadata.getJSONObject("post").getString("newsgroup");
            int unreadCounter = 0;
            if(depth == 0)
                threadNum = postjsonmetadata.getJSONObject("post").getString("number");
            JSONObject post = mService.getPost(newsgroup, postjsonmetadata.getJSONObject("post").getString("number"));
            if(post.isNull("post"))
                Log.e("Hi", "fuck");
            if(!post.getJSONObject("post").isNull("unread_class") && !post.getJSONObject("post").getString("unread_class").equals("null"))
            {
                unreadCounter++;
                UnreadTools.changeReadStatusOfPost(newsgroup, postjsonmetadata.getJSONObject("post").getString("number"), true);
            }
            View postView = buildPostView(post.getJSONObject("post"),
                    depth, postjsonmetadata.getJSONArray("children").length() == 0 && depth == 0);
            mPosts.addView(postView);
            JSONArray children = postjsonmetadata.getJSONArray("children");
            for(int i = 0; i < children.length(); i++)
                unreadCounter += addPostView(children.getJSONObject(i), depth + 1);
            return unreadCounter;
        }
        catch (Exception e)
        {
            Log.d("Hi", "Error parsing json for addPostView");
            Log.d("Hi", "Error " + e.toString());
            for(StackTraceElement ste : e.getStackTrace())
                Log.d("Hi", "Error " + ste.toString());
        }
        return 0;
    }

    //Returns a linearlayout containing the information given in post
    private View buildPostView(JSONObject post, int depth, boolean onlyOne)
    {
        LayoutInflater infalInflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View rootView = infalInflater.inflate(R.layout.post_item, null);

        View nameAndTimeHolder = rootView.findViewById(R.id.nameAndTimeHolder);
        View subjectAndMenuHolder = rootView.findViewById(R.id.subjectAndMenuHolder);
        final View bodyHolder = rootView.findViewById(R.id.bodyHolder);
        View quotedTextHolder = rootView.findViewById(R.id.quotedTextHolder);

        final Button quotedTextButton = (Button) rootView.findViewById(R.id.quotedTextButton);

        TextView nameView = (TextView) rootView.findViewById(R.id.nameView);
        TextView timeView = (TextView) rootView.findViewById(R.id.timeView);
        TextView subjectView = (TextView) rootView.findViewById(R.id.subjectView);
        TextView bodyView1 = (TextView) rootView.findViewById(R.id.bodyView1);
        final TextView bodyView2 = (TextView) rootView.findViewById(R.id.bodyView2);
        TextView bodyView3 = (TextView) rootView.findViewById(R.id.bodyView3);

        try {
            nameView.setText(post.getString("author_name"));
            String date = post.getString("date");
            timeView.setText(date.substring(11,16)
                    + " " + date.substring(5,7)
                    + "/" + date.substring(8,10)
                    + "/" + date.substring(0,4));
            subjectView.setText(post.getString("subject"));

            String bodyString = post.getString("body");
            bodyString = bodyString.replace("\n", "<br/>");
            if(shouldBeCondensed(bodyString))
            {
                String[] parts = splitQuotedText(bodyString);
                bodyView1.setText(Html.fromHtml(parts[0], null, null));
                bodyView2.setText(Html.fromHtml(parts[1], null, null));
                bodyView3.setText(Html.fromHtml(parts[2], null, null));
                quotedTextHolder.setVisibility(View.VISIBLE);

                quotedTextButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if(bodyView2.getVisibility() == View.VISIBLE)
                        {
                            quotedTextButton.setText("Show Quoted Text");
                            bodyView2.setVisibility(View.GONE);
                        }
                        else
                        {
                            quotedTextButton.setText("Hide Quoted Text");
                            bodyView2.setVisibility(View.VISIBLE);
                        }
                    }
                });
            }
            else
            {
                bodyView1.setText(Html.fromHtml(bodyString, null, null));
            }
            bodyView1.setMovementMethod(LinkMovementMethod.getInstance());
            bodyView2.setMovementMethod(LinkMovementMethod.getInstance());
            bodyView3.setMovementMethod(LinkMovementMethod.getInstance());

            if(!post.getString("unread_class").equals("null"))
            {
                nameView.setTypeface(null, Typeface.BOLD);
                timeView.setTypeface(null, Typeface.BOLD);
                subjectView.setTypeface(null, Typeface.BOLD);
            }
        } catch (JSONException e) {
            Log.d("Hi", "Error parsing json for buildPostView");
            Log.d("Hi", "Error " + e.toString());
        }

        try {
            if(!post.getString("unread_class").equals("null"))
            {
                nameView.setTypeface(null, Typeface.BOLD);
                timeView.setTypeface(null, Typeface.BOLD);
                subjectView.setTypeface(null, Typeface.BOLD);
            }
            else if (!onlyOne)
                bodyHolder.setVisibility(View.GONE);
        } catch (JSONException e) {
            Log.d("Hi", "Error parsing json for buildpostview");
            Log.d("Hi", "Error " + e.toString());
        }

        View.OnClickListener toggleBody = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(bodyHolder.getVisibility() == View.VISIBLE)
                    bodyHolder.setVisibility(View.GONE);
                else
                    bodyHolder.setVisibility(View.VISIBLE);
            }
        };

        nameAndTimeHolder.setOnClickListener(toggleBody);
        subjectAndMenuHolder.setOnClickListener(toggleBody);

        if(depth > 7)
            depth = 7;
        LinearLayout.LayoutParams rootViewParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rootViewParams.setMargins(5 + 20 * depth, 10, 5, 10);
        rootView.setPadding(15, 15, 15, 15);
        rootView.setLayoutParams(rootViewParams);

        return rootView;
    }

    public String[] splitQuotedText(String input)
    {
        int start = input.indexOf("<div class=\"quoted_text\">");
        if(input.contains("<br/><div class=\"quoted_text\">"))
            start = input.indexOf("<br/><div class=\"quoted_text\">");
        int end = input.lastIndexOf("</div>") + 6;
        String[] returnValue = {
            input.substring(0, start),
            input.substring(start, end),
            input.substring(end, input.length())
        };
        return returnValue;
    }

    public boolean shouldBeCondensed(String input)
    {
        return input.contains("<div class=\"quoted_text\">");
    }

    //Updates
    public void newsgroupChanged(boolean stealFocus)
    {
        Log.d("Hi", "We are " + (inRecentActivity ? "in" : "not in") + " recent activity");
        newsgroupsSpinner.setJSONArray(newsgroups);
        newsgroupsSpinner.setRecentActivity(recentActivity);
        newsgroupsSpinner.notifyDataSetChanged();
        if(inRecentActivity)
        {
            recentActivity = mService.getRecentActivity();
            if(recentActivity != null)
            {
                newsgroupThreadsMetadata = new ArrayList<String[]>();
                for(int i = 0; i < recentActivity.length(); i++)
                    try {
                        String[] data = {
                                recentActivity.getJSONObject(i).getJSONObject("thread_parent").getString("author_name"),
                                recentActivity.getJSONObject(i).getJSONObject("thread_parent").getString("date"),
                                recentActivity.getJSONObject(i).getJSONObject("thread_parent").getString("subject"),
                                (recentActivity.getJSONObject(i).getInt("unread_count") == 0 ? "n" : "y")
                        };
                        newsgroupThreadsMetadata.add(data);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                mList.setAdapter(new ThreadsListAdapter(NewsgroupActivity.this, 0, newsgroupThreadsMetadata));
            }
        }
        else
        {
            threadMetadatas = mService.getThreadsForNewsgroup(selectedNewsgroup);
            if(threadMetadatas != null)
            {
                newsgroupThreadsMetadata = new ArrayList<String[]>();
                for(int i = 0; i < threadMetadatas.length(); i++)
                    try {
                        String[] data = {
                                threadMetadatas.getJSONObject(i).getJSONObject("post").getString("author_name"),
                                threadMetadatas.getJSONObject(i).getJSONObject("post").getString("date"),
                                threadMetadatas.getJSONObject(i).getJSONObject("post").getString("subject"),
                                (UnreadTools.threadHasUnread(threadMetadatas.getJSONObject(i), selectedNewsgroup) ? "y" : "n")
                        };
                        newsgroupThreadsMetadata.add(data);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                mList.setAdapter(new ThreadsListAdapter(NewsgroupActivity.this, 0, newsgroupThreadsMetadata));
            }
        }
        if(!mSlidingLayout.isOpen() && stealFocus)
            mSlidingLayout.openPane();
        if(stealFocus)
            mPosts.removeAllViews();
    }

    public void updateFinished()
    {
        mPullToRefreshLayout.setRefreshComplete();
        newsgroups = mService.getNewsgroups();
        Log.d("Hi", "Called from updatefinished");
        newsgroupChanged(false);
    }

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            CshNewsService.LocalBinder binder = (CshNewsService.LocalBinder) service;
            mService = binder.getService();
            mService.registerUI(NewsgroupActivity.this);
            mBound = true;
            newsgroups = mService.getNewsgroups();
            recentActivity = mService.getRecentActivity();
            Log.d("Hi", "Called from onserviceconnected");
            newsgroupChanged(true);

            if(loadPost)
            {
                loadPost = false;
                JSONObject postJsonMetadata = mService.getThreadMetadata(viewNewsgroup, threadNum);
                addPostView(postJsonMetadata, 0);
                mSlidingLayout.closePane();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    @Override
    public boolean onNavigationItemSelected(int itemPosition, long itemId) {
        try {
            threadNum = null;
            if(spinnerSelection != -1)
            {
                Log.d("Hi", "spinnerSelection was set!");
                itemPosition = spinnerSelection;
                spinnerSelection = -1;
            }
            if(itemPosition == 0)
            {
                inRecentActivity = true;
                recentActivity = mService.getRecentActivity();
            }
            else
            {
                itemPosition--;
                selectedNewsgroup = newsgroups.getJSONObject(itemPosition).getString("name");
                inRecentActivity = false;
            }
        } catch (JSONException e) {
            Log.d("Hi", "Error parsing json for onnavigaitonitemselected");
            Log.d("Hi", "Error " + e.toString());
            return false;
        }
        Log.d("Hi", "Called from onNavigationItemSelected");
        newsgroupChanged(true);
        return true;
    }

    /**
     * This list item click listener implements very simple view switching by changing
     * the primary content text. The slider is closed when a selection is made to fully
     * reveal the content.
     */
    private class ListItemClickListener implements ListView.OnItemClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            onThreadSelected(position);
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
        public ActionBar mActionBar;
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
            mActionBar.setListNavigationCallbacks(newsgroupsSpinner, NewsgroupActivity.this);
        }

        @Override
        public void onPanelClosed() {
            super.onPanelClosed();
            mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
            mActionBar.setDisplayShowTitleEnabled(true);
            mActionBar.setDisplayHomeAsUpEnabled(true);
            mActionBar.setHomeButtonEnabled(true);
            mActionBar.setTitle(NewsgroupActivity.this.viewNewsgroup);
        }

        @Override
        public void onPanelOpened() {
            super.onPanelOpened();
            mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);
            mActionBar.setDisplayShowTitleEnabled(false);
            mActionBar.setHomeButtonEnabled(false);
            mActionBar.setDisplayHomeAsUpEnabled(false);
            mActionBar.setTitle(mDrawerTitle);

            if(spinnerSelection != -1)
            {
                mActionBar.setSelectedNavigationItem(spinnerSelection);
                //spinnerSelection = -1;
            }
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