package com.ncs.rtspstream;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import net.majorkernelpanic.streaming.rtsp.RtspServer;

import java.util.Set;

public class Settings extends AppCompatActivity {

    private EditText port;
    private EditText resolutionX;
    private EditText resolutionY;
    private EditText videoBitrate;
    private EditText framerate;
    private Button applyButton;
    private Button cancelButton;
    private ImageView backButton;

    private String mPort, mResolutionX, mResolutionY, mVideoBitrate, mFramerate;

    private SharedPreferences mSharedPreference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        port = findViewById(R.id.portNumber);
        resolutionX = findViewById(R.id.resolutionW);
        resolutionY = findViewById(R.id.resolutionH);
        videoBitrate = findViewById(R.id.videoBitrate);
        framerate = findViewById(R.id.framerate);

        applyButton = findViewById(R.id.applySettings);
        cancelButton = findViewById(R.id.cancelSettings);
        backButton = findViewById(R.id.backButton);

        applyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getValues();
                updateValues();
                setResult(RESULT_OK);
                Settings.this.finish();
            }
        });
        cancelButton.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setResult(RESULT_CANCELED);
                Settings.this.finish();
            }
        }));
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setResult(RESULT_CANCELED);
                Settings.this.finish();
            }
        });
    }

    @Override
    protected void onStart(){
        super.onStart();
        mSharedPreference = getSharedPreferences("robotConfigFile", MODE_PRIVATE);
        getValues();

        mPort = mSharedPreference.getString(RtspServer.KEY_PORT,"1234");
        mResolutionX = mSharedPreference.getString("ResolutionX", "640");
        mResolutionY = mSharedPreference.getString("ResolutionY", "480");
        mFramerate = mSharedPreference.getString("Framerate", "10");
        mVideoBitrate = mSharedPreference.getString("VideoBitrate", "500000");

        port.setText(mPort);
        resolutionY.setText(mResolutionY);
        resolutionX.setText(mResolutionX);
        framerate.setText(mFramerate);
        videoBitrate.setText(mVideoBitrate);


    }

    private void getValues(){
        if (!(port.getText().equals(""))){
            mPort = port.getText().toString();
        }
        if (!(resolutionY.getText().equals(""))){
            mResolutionY = resolutionY.getText().toString();
        }
        if (!(resolutionX.getText().equals(""))){
            mResolutionX = resolutionX.getText().toString();
        }
        if (!(videoBitrate.getText().equals(""))){
            mVideoBitrate = videoBitrate.getText().toString();
        }
        if (!(framerate.getText().equals(""))){
            mFramerate = framerate.getText().toString();
        }

    }
    private void updateValues(){

        Log.i("UpdatingValues", mPort +","+mResolutionX+","+mResolutionY+","+mVideoBitrate+","+mFramerate);
        SharedPreferences.Editor editor = mSharedPreference.edit();
        editor.putString("ResolutionX", mResolutionX);
        editor.putString("ResolutionY", mResolutionY);
        editor.putString("VideoBitrate", mVideoBitrate);
        editor.putString(RtspServer.KEY_PORT, mPort);
        editor.putString("Framerate", mFramerate);
        editor.commit();
    }
}
