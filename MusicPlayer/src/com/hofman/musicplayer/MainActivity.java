package com.hofman.musicplayer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class MainActivity extends Activity implements OnClickListener {
	
	Button mPlayButton;
	Button mStopButton;
	Button mSkipButton;
	Button mRewindButton;
	Button mPauseButton;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		mPlayButton = (Button) findViewById(R.id.playbutton);
		mStopButton = (Button) findViewById(R.id.stopbutton);
		mSkipButton = (Button) findViewById(R.id.skipbutton);
		mRewindButton = (Button) findViewById(R.id.rewindbutton);
		mPauseButton = (Button) findViewById(R.id.pausebutton);
		
        mPlayButton.setOnClickListener(this);
        mPauseButton.setOnClickListener(this);
        mSkipButton.setOnClickListener(this);
        mRewindButton.setOnClickListener(this);
        mStopButton.setOnClickListener(this);
        
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public void onClick(View target) {
		// Send the correct intent to the MusicService, according to the button that was clicked
		if (target == mPlayButton)
			startService(new Intent(MusicService.ACTION_PLAY));
		else if (target == mPauseButton)
			startService(new Intent(MusicService.ACTION_PAUSE));
		else if (target == mSkipButton)
			startService(new Intent(MusicService.ACTION_SKIP));
		else if (target == mRewindButton)
			startService(new Intent(MusicService.ACTION_REWIND));
		else if (target == mStopButton)
			startService(new Intent(MusicService.ACTION_STOP));
	}
	
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch(keyCode) {
		case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
		case KeyEvent.KEYCODE_HEADSETHOOK:
			startService(new Intent(MusicService.ACTION_TOGGLE_PLAYBACK));
		}
        return super.onKeyDown(keyCode, event);
	}

}
