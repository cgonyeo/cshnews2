package edu.rit.csh.cshnews2;

import android.app.Activity;
import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.os.Build;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

public class LoginActivity extends Activity implements SeekBar.OnSeekBarChangeListener{
    int loginState = 0;
    String apiKey = "";
    int desiredThreadCount = 10;
    JSONArray newsgroups;
    public HashMap<String, ProgressBar> loadingBars;

    ProgressDialog pleaseWait;

    View welcomeScreen;
    View apiKeyScreen;
    View threadCountScreen;
    View threadFetchingScreen;

    LinearLayout barContainer;

    Button beginButton;
    Button nextButton1;
    Button nextButton2;

    EditText apiKeyField;
    SeekBar threadCountSlider;
    TextView threadCountSliderText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        if(settings.getAll().containsKey("UPDATE_COMPLETE") && settings.getBoolean("UPDATE_COMPLETE", true))
        {
            Intent i = new Intent(this, NewsgroupActivity.class);
            startActivity(i);
            finish();
        }

        welcomeScreen = findViewById(R.id.welcome_screen);
        apiKeyScreen = findViewById(R.id.api_key_screen);
        threadCountScreen = findViewById(R.id.thread_count_screen);
        threadFetchingScreen = findViewById(R.id.thread_fetching_screen);

        barContainer = (LinearLayout) findViewById(R.id.bar_container);

        beginButton = (Button) findViewById(R.id.begin_button);
        nextButton1 = (Button) findViewById(R.id.next_button1);
        nextButton2 = (Button) findViewById(R.id.next_button2);

        apiKeyField = (EditText) findViewById(R.id.api_key_field);
        threadCountSlider = (SeekBar) findViewById(R.id.thread_count_slider);
        threadCountSliderText = (TextView) findViewById(R.id.thread_count_slider_text);

        apiKeyField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        threadCountSlider.setOnSeekBarChangeListener(this);

        pleaseWait = new ProgressDialog(this);
        pleaseWait.setTitle("Please wait");
        pleaseWait.setMessage("Checking that your API key is valid");
        pleaseWait.setIndeterminate(true);
        pleaseWait.setCancelable(false);

        loadingBars = new HashMap<String, ProgressBar>();

        beginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(NetworkStuff.isNetworkAvailable(LoginActivity.this))
                {
                    welcomeScreen.setVisibility(View.GONE);
                    apiKeyScreen.setVisibility(View.VISIBLE);
                    loginState++;
                }
                else
                {
                    new AlertDialog.Builder(LoginActivity.this)
                            .setTitle("No Internet Detected")
                            .setMessage("You'll need an active internet connection for this part")
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                }
                            })
                            .show();
                }
            }
        });
        nextButton1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (NetworkStuff.isNetworkAvailable(LoginActivity.this)) {
                    apiKey = apiKeyField.getText().toString();
                    new apiValidator(apiKey).execute();
                } else {
                    new AlertDialog.Builder(LoginActivity.this)
                            .setTitle("No Internet Detected")
                            .setMessage("You'll need an active internet connection for this part")
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                }
                            })
                            .show();
                }
            }
        });
        nextButton2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (NetworkStuff.isNetworkAvailable(LoginActivity.this)) {
                    threadCountScreen.setVisibility(View.GONE);
                    threadFetchingScreen.setVisibility(View.VISIBLE);
                    setUpThreadFetchingScreen();
                    new DatabaseBuilder(LoginActivity.this, apiKey, desiredThreadCount).start();
                } else {
                    new AlertDialog.Builder(LoginActivity.this)
                            .setTitle("No Internet Detected")
                            .setMessage("You'll need an active internet connection for this part")
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                }
                            })
                            .show();
                }
            }
        });
    }

    private void setUpThreadFetchingScreen()
    {
        try
        {
            LayoutInflater infalInflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            for(int i = 0; i < newsgroups.length(); i++)
            {
                View rootView = infalInflater.inflate(R.layout.newsgroup_bar, null);
                TextView name = (TextView) rootView.findViewById(R.id.newsgroup_name);
                ProgressBar newsgroupBar = (ProgressBar) rootView.findViewById(R.id.newsgroup_bar);
                String nameText = newsgroups.getJSONObject(i).getString("name");
                name.setText(nameText);
                barContainer.addView(rootView);

                loadingBars.put(nameText, newsgroupBar);
            }
        } catch (JSONException e) {
            Log.e("Hi", "Error parsing json for setUpThreadFetchingScreen");
            Log.e("Hi", "error " + e.toString());
        }
    }

    public void UpdateFinished()
    {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = settings.edit();
        editor.putBoolean("UPDATE_COMPLETE", true);
        editor.putString("apiKeyInput", apiKey);
        editor.commit();

        Intent i = new Intent(this, NewsgroupActivity.class);
        startActivity(i);
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.login, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        threadCountSliderText.setText("Number of threads: " + (progress + 5));
        desiredThreadCount = progress + 5;
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }

    private class apiValidator extends AsyncTask
    {
        String apiKey;
        boolean succeeded = false;
        boolean netwokFailure = false;
        public apiValidator(String apiKey)
        {
            this.apiKey = apiKey;
        }
        @Override
        protected void onPreExecute()
        {
            pleaseWait.show();
        }
        @Override
        protected Object doInBackground(Object[] params) {
            NetworkStuff.init(apiKey, null);
            try {
                String response = NetworkStuff.makeGetRequest("newsgroups");
                if(response == null) {
                    netwokFailure = true;
                    return null;
                }
                JSONObject newsgroupsObject = new JSONObject(response);
                succeeded = !newsgroupsObject.has("error");
                if(succeeded)
                    newsgroups = newsgroupsObject.getJSONArray("newsgroups");
            } catch (JSONException e) {
                Log.e("Hi", "Error parsing json for apiValidator");
                Log.e("Hi", "Error " + e.toString());
            }

            return null;
        }

        @Override
        protected void onPostExecute(Object params)
        {
            pleaseWait.hide();
            if(netwokFailure)
            {
                new AlertDialog.Builder(LoginActivity.this)
                        .setTitle("Internet Connection Lost")
                        .setMessage("The internet connection was lost while checking the API key")
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        })
                        .show();
            }
            else if (succeeded)
            {
                apiKeyScreen.setVisibility(View.GONE);
                threadCountScreen.setVisibility(View.VISIBLE);
                loginState++;
            }
            else
            {
                new AlertDialog.Builder(LoginActivity.this)
                        .setTitle("Invalid API Key")
                        .setMessage("Sorry, your API key is invalid. Please try entering it again.")
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        })
                        .show();
            }
        }
    }
}
