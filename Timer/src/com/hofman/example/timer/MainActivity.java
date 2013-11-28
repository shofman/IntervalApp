package com.hofman.example.timer;

import java.util.Timer;
import java.util.TimerTask;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.Vibrator;
import android.view.Menu;
import android.view.View;
import android.widget.NumberPicker;
import android.widget.TextView;

@TargetApi(Build.VERSION_CODES.HONEYCOMB)
public class MainActivity extends Activity {
	private int time = 5;
	private TimerTask tt;
	private boolean run = true;
	Timer t;
	
	//Pickers
	NumberPicker hourPick;
	NumberPicker minPick;
	NumberPicker secPick;
	
	//Vibrator
	Vibrator vibe;
	PowerManager.WakeLock mWakeLock;

	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		//Make into fragment next
		hourPick = (NumberPicker) findViewById(R.id.hourPicker);
		minPick = (NumberPicker) findViewById(R.id.minPicker);
		secPick = (NumberPicker) findViewById(R.id.secPicker);
		hourPick.setMaxValue(100);
		hourPick.setMinValue(0);
		minPick.setMaxValue(59);
		minPick.setMinValue(0);
		secPick.setMaxValue(59);
		secPick.setMinValue(0);
		minPick.setValue(25);
		
		//Declare new timer
		t = new Timer();
		tt = createTimerTask();
		t.scheduleAtFixedRate(tt, 0, 1000);
		
		//Setup vibrator for when timer is finished
		vibe = (Vibrator) getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);
		PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Keep Timer Running");
		mWakeLock.acquire();
		mWakeLock.release();
	}
	
	private TimerTask createTimerTask() {
		return new TimerTask() {
			public void run() {
				runOnUiThread(new Runnable() {
					public void run() {
						if(run) {
							TextView tv = (TextView) findViewById(R.id.timer);
							tv.setText(convertSecondsToMinuteString(time));
							time--;
							if (time < 0 ) {
								time = 0;
								run = false;
								if (vibe.hasVibrator()) vibe.vibrate(1000);
							}
						}
					}
				});
			}
		};
	}
	
	private String convertSecondsToMinuteString(int sec) {
		boolean hourAdd = false;
		int hours = 0;
		int min = sec / 60;
		if (min >= 60) {
			hours = min/60;
			min %= 60;
			hourAdd = true;
		}
		sec %= 60;
		
		String output = "";
		if (hourAdd) {
			output += hours + ":";
		}
		String secString = "", minString = "";
		if(sec < 10) secString = "0";
		if(min < 10) minString = "0";
		output += minString + min + ":" + secString + sec;
		return output;
	}
	
	public void sendMessage(View view) {
		Intent intent = new Intent(getApplicationContext(), SetTimerDialogFrag.class);
		startActivity(intent);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	public void showNumberPickers(View view) {
		time = hourPick.getValue()*3600 + minPick.getValue() * 60 + secPick.getValue();
		t.cancel();
		tt.cancel();
		t = new Timer();
		tt = createTimerTask();
		t.scheduleAtFixedRate(tt, 0, 1000);
		//sendMessage(getCurrentFocus());
	}
	@Override
	protected void onPause() {
		run = false;
		super.onPause();
	}
	@Override
	protected void onResume() {
		run = true;
		super.onResume();
	}
	@Override
	protected void onStop() {
		t.cancel();
		tt.cancel();
		super.onStop();
	}

}
