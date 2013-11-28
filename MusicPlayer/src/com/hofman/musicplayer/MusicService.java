package com.hofman.musicplayer;

import java.io.IOException;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.media.RemoteControlClient;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.widget.Toast;

public class MusicService extends Service implements OnCompletionListener, OnPreparedListener,
				OnErrorListener {
	
	//Tag for debug messages
	final static String TAG = "MusicPlayer";
	
	//Intent actions for use to handle - convenience (Android manifest does the heavy work)
	public static final String ACTION_TOGGLE_PLAYBACK = "com.hofman.musicplayer.action.TOGGLE_PLAYBACK";
	public static final String ACTION_PLAY = "com.hofman.musicplayer.action.PLAY";
	public static final String ACTION_PAUSE = "com.hofman.musicplayer.action.PAUSE";
	public static final String ACTION_STOP = "com.hofman.musicplayer.action.STOP";
	public static final String ACTION_SKIP = "com.hofman.musicplayer.action.SKIP";
	public static final String ACTION_REWIND = "com.hofman.musicplayer.action.REWIND";

	//If we lose audio focus, but allow ducking (decrease volume on loss of focus temporarily)
	public static final float DUCK_VOLUME = 0.1f;
	
	//Media Player
	MediaPlayer mPlayer = null;
	
	//AudioFocusHelper to deal with audio focus loss (if available. Else always null)
	AudioFocusHelper mAudioFocusHelper = null;
	
	//State of service
	enum State {
		Retrieving,			//MediaRetriever is retrieving
		Stopped,			//Media Player Stopped, not ready to play
		Preparing,			//Media Player is preparing
		Playing,			//Playback active, media player ready (still in this state if no audio focus)
		Paused				//Playback paused, media player ready
	};
	
	State mState = State.Retrieving;
	
	//If state is retrieving, indicate whether to play immediately or not
	boolean mStartPlayingAfterRetrieve = false;
	
	//TODO: Remove
	//if mStartPlayingAfterRetrieve, indicate URL to play, or random if null
	Uri mWhatToPlayAfterRetrieve = null;
	
	enum PauseReason {
		UserRequest,		//Paused by user
		FocusLoss			//Paused by loss of audio focus
	};
	
	//Boolean for why we paused
	PauseReason mPauseReason = PauseReason.UserRequest;
	
	enum AudioFocus {
		NoFocusNoDuck, 		//No audio focus, no ducking allowed
		NoFocusCanDuck,		//No audio focus, ducking allowed
		Focus				//We have full audio focus
	};
	
	AudioFocus mAudioFocus = AudioFocus.NoFocusNoDuck;
	
	//Title of current song
	String mSongTitle = "";
	
	//TODO: Remove
	//Whether the song we are playing is streaming
	boolean mIsStreaming = false;
	
	//Wifi lock that we hold when streaming, to prevent device from shutting off Wifi
	WifiLock mWifiLock;
	
	//ID for notification (onSCreen alert)
	final int NOTIFICATION_ID = 1;
	
	//Music Retriever, scans for media and provides titles and URIs
	MusicRetriever mRetriever;
	
	//RemoteControl Client object, used for remote control APIS available in SDK level >= 14
	RemoteControlClientCompat mRemoteControlClientCompat;
	
	//Dummy alumb art we pass to remote control
	Bitmap mDummyAlbumArt;
	
	//Component name of MusicintentReciever, used with media button and remote control
	ComponentName mMediaButtonReceiverComponent;
	
	AudioManager mAudioManager;
	NotificationManager mNotificationManager;
	
	Notification mNotification = null;
	
	void createMediaPlayerIfNeeded() {
		if (mPlayer == null) {
			mPlayer = new MediaPlayer();
			
			//Media player needs wake-lock while playing. CPU goes to sleep otherwise and stops playback
			//Permissions needed in manifest
			mPlayer.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
			
			//Notify while mediaplayer is preparing, and when done playing
			mPlayer.setOnPreparedListener(this);
			mPlayer.setOnCompletionListener(this);
		} else
			mPlayer.reset();
	}
	
	@Override
	public void onCreate() {
		Log.i(TAG, "Debug: Creating service");
		
		//Create wifi lock (does not acquire it)
		mWifiLock = ((WifiManager) getSystemService(Context.WIFI_SERVICE))
				.createWifiLock(WifiManager.WIFI_MODE_FULL, "mylock");
		
		mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
		
		//Create the retriever and start an asynchronous task to prepare it
		mRetriever = new MusicRetriever(getContentResolver());
		new PrepareMusicRetrieverTask(mRetriever,this).execute();
		
		//Create audio focus if available
		if (android.os.Build.VERSION.SDK_INT >= 8)
			mAudioFocusHelper = new AudioFocusHelper(getApplicationContext(), this);
		else
			mAudioFocus = AudioFocus.Focus; //Always focused
		
		mDummyAlbumArt = BitmapFactory.decodeResource(getResources(), R.drawable.dummy_album_art);
		
		mMediaButtonReceiverComponent = new ComponentName(this, MusicIntentReceiver.class);
	}
	
	//Called when receiving an intent (via StartService). React appropriately
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		String action = intent.getAction();
		if (action.equals(ACTION_TOGGLE_PLAYBACK)) processTogglePlaybackRequest();
		else if (action.equals(ACTION_PLAY)) processPlayRequest();
		else if (action.equals(ACTION_PAUSE)) processPauseRequest();
		else if (action.equals(ACTION_SKIP)) processSkipRequest();
		else if (action.equals(ACTION_STOP)) processStopRequest();
		else if (action.equals(ACTION_REWIND)) processRewindRequest();
		
		return START_NOT_STICKY; //Hover over START_NOT_STICKY to understand - 
								 //basically, if killed, take it out of commission until it is called again 
	}
	
	
	void processRewindRequest() {
		if (mState == State.Playing || mState == State.Paused)
			mPlayer.seekTo(0);
	}

	void processStopRequest() {
		processStopRequest(false);
	}

	void processStopRequest(boolean force) {
		if (mState == State.Playing || mState == State.Paused || force) {
			mState = State.Stopped;
			
			//Release resources
			relaxResources(true);
			giveUpAudioFocus();
			
			//Tell remote controls that playback is paused
			if (mRemoteControlClientCompat != null) {
				mRemoteControlClientCompat.setPlaybackState(RemoteControlClient.PLAYSTATE_STOPPED);
			}
			//Stop service
			stopSelf();
		}
	}

	private void giveUpAudioFocus() {
		if (mAudioFocus == AudioFocus.Focus && mAudioFocusHelper != null && mAudioFocusHelper.abandonFocus())
			mAudioFocus = AudioFocus.NoFocusNoDuck;
	}

	void processSkipRequest() {
		if (mState == State.Playing || mState == State.Paused) {
			tryToGetAudioFocus();
			playNextSong(null);
		}
	}

	void processPauseRequest() {
		if (mState == State.Retrieving) {
			//Wait until we've finished retrieving, and clear flag
			mStartPlayingAfterRetrieve = false;
			return;
		}
		
		if (mState == State.Playing) {
			mState = mState.Paused;
			mPlayer.pause();
			relaxResources(false); //while paused, retain MediaPlayer 
		}
		
		//Tell remote controls that playback state is paused
		if (mRemoteControlClientCompat != null) {
			mRemoteControlClientCompat.setPlaybackState(RemoteControlClient.PLAYSTATE_PAUSED);
		}
		
	}

	private void relaxResources(boolean releaseMediaPlayer) {
		//Stop as a foreground Service
		stopForeground(true);
		
		//Stop and release media player, if available
		if (releaseMediaPlayer && mPlayer != null) {
			mPlayer.reset();
			mPlayer.release();
			mPlayer = null;
		}
		
		//Release wifilock, if held
		//TODO: remove
		if (mWifiLock.isHeld()) mWifiLock.release();
		
	}

	void processPlayRequest() {
		if (mState == State.Retrieving) {
			//If retrieving, set flag to play once ready
			mWhatToPlayAfterRetrieve = null; //Random song
			mStartPlayingAfterRetrieve = true;
			return;
		}
		
		tryToGetAudioFocus();
		
		//Actually play song
		if(mState == State.Stopped) {
			playNextSong(null);
		} 
		
		else if(mState == State.Paused) {
			//If paused, continue playback
			mState = State.Playing;
			setUpAsForeground(mSongTitle + " (playing)");
			configAndStartMediaPlayer();
		}
		
		//Tell any remote controls that our playback is playing
		if (mRemoteControlClientCompat != null) 
			mRemoteControlClientCompat.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING);
		
	}

	//Configures media player and starts/restarts it, based on focus state. 
	//Assumes that mPlayer is not null - check if used
	void configAndStartMediaPlayer() {
		//No focus and no ducking means pausing. State doesn't change
		if (mAudioFocus == AudioFocus.NoFocusNoDuck) {
			if (mPlayer.isPlaying()) mPlayer.pause();
			return;
		}
		
		else if (mAudioFocus == AudioFocus.NoFocusCanDuck)
			mPlayer.setVolume(DUCK_VOLUME, DUCK_VOLUME);
		else 
			mPlayer.setVolume(1.0f, 1.0f);
		
		if(!mPlayer.isPlaying()) mPlayer.start();
	}
	
	void playNextSong(String manualUrl) {
			mState = State.Stopped;
			relaxResources(false); //Release everything except mediaPlayer
			
			try {
				MusicRetriever.Item playingItem = null;
				//TODO: Remove
				if (manualUrl != null) {
					createMediaPlayerIfNeeded();
					mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
					mPlayer.setDataSource(manualUrl);
					mIsStreaming = manualUrl.startsWith("http:") || manualUrl.startsWith("https:");
					
					playingItem = new MusicRetriever.Item(0, null, manualUrl, null, 0);
				} 
				else {
					mIsStreaming = false;
					playingItem = mRetriever.getRandomItem();
					if (playingItem == null) {
						Toast.makeText(this, "No music available.", Toast.LENGTH_LONG).show();
						processStopRequest(true); //Stop everything
						return;
					}
					
					//Set the source of media player to a content Uri
					createMediaPlayerIfNeeded();
					mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
					mPlayer.setDataSource(getApplicationContext(), playingItem.getURI());
				}
				
				mSongTitle = playingItem.getTitle();
				mState = State.Preparing;
				setUpAsForeground(mSongTitle + " (loading)");
				
				//Use media buttons APIS to register for media button events
				MediaButtonHelper.registerMediaButtonEventReceiverCompat(
						mAudioManager, mMediaButtonReceiverComponent);
				
				//Use remote control APIs (if available) to set playback state
				if (mRemoteControlClientCompat == null) {
					Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
					intent.setComponent(mMediaButtonReceiverComponent);
					mRemoteControlClientCompat = new RemoteControlClientCompat(
							PendingIntent.getBroadcast(this, 0, intent, 0));
					RemoteControlHelper.registerRemoteControlClient(
							mAudioManager, mRemoteControlClientCompat);
				}
				
				mRemoteControlClientCompat.setPlaybackState(RemoteControlClient.PLAYSTATE_PLAYING);
				
				mRemoteControlClientCompat.setTransportControlFlags(
						RemoteControlClient.FLAG_KEY_MEDIA_PLAY |
						RemoteControlClient.FLAG_KEY_MEDIA_PAUSE |
						RemoteControlClient.FLAG_KEY_MEDIA_NEXT |
						RemoteControlClient.FLAG_KEY_MEDIA_STOP);
				
				//Update remote controls
				mRemoteControlClientCompat.editMetadata(true)
						.putString(MediaMetadataRetriever.METADATA_KEY_ARTIST, playingItem.getArtist())
						.putString(MediaMetadataRetriever.METADATA_KEY_ALBUM, playingItem.getAlbum())
						.putString(MediaMetadataRetriever.METADATA_KEY_TITLE, playingItem.getTitle())
						.putLong(MediaMetadataRetriever.METADATA_KEY_DURATION, playingItem.getDuration())
						.putBitmap(RemoteControlClientCompat.MetadataEditorCompat.METADATA_KEY_ARTWORK, mDummyAlbumArt)
						.apply();
				
				//Prepares mediaPlayer in the background. 
				//Calls listener when ready
				mPlayer.prepareAsync();
				
				if (mIsStreaming) mWifiLock.acquire();
				else if (mWifiLock.isHeld()) mWifiLock.release();
			}
			catch(IOException ex) {
				Log.e("MusicService", "IOException playing next song: " +ex.getMessage());
				ex.printStackTrace();
			}
	}

	private void tryToGetAudioFocus() {
		if (mAudioFocus != AudioFocus.Focus && mAudioFocusHelper != null && mAudioFocusHelper.requestFocus())
			mAudioFocus = AudioFocus.Focus;
	}

	void processTogglePlaybackRequest() {
		if (mState == State.Paused || mState == State.Stopped) 
			processPlayRequest();
		else
			processPauseRequest();
		
	}

	@Override
	public void onCompletion(MediaPlayer player) {
		playNextSong(null); //Finished song, play next one
	}

	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}

	@Override
	public void onPrepared(MediaPlayer player) {
		//Media player is done playing
		mState = State.Playing;
		updateNotification(mSongTitle + " (playing)");
		configAndStartMediaPlayer();
	}

	void updateNotification(String text) {
		if (android.os.Build.VERSION.SDK_INT > 11) 
			mNotification = buildNotification(text);
		else 
			mNotification = buildOlderNotification(text);
		
		mNotificationManager.notify(NOTIFICATION_ID, mNotification);
	}
	
	@SuppressWarnings("deprecation")
	@TargetApi(11)
	Notification buildOlderNotification(String text) {
		PendingIntent pi = PendingIntent.getActivity(getApplicationContext(), 0,
				new Intent(getApplicationContext(), MainActivity.class), 
				PendingIntent.FLAG_UPDATE_CURRENT);
		return new Notification.Builder(getApplicationContext())
		.setContentTitle("MusicPlayer")
		.setContentIntent(pi)
		.setContentText(text)
		.getNotification();
	}
	
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	Notification buildNotification(String text) {
		PendingIntent pi = PendingIntent.getActivity(getApplicationContext(), 0,
				new Intent(getApplicationContext(), MainActivity.class), 
				PendingIntent.FLAG_UPDATE_CURRENT);
		return new Notification.Builder(getApplicationContext())
		.setContentTitle("MusicPlayer")
		.setContentIntent(pi)
		.setContentText(text)
		.build();
	}

	@Override
	public boolean onError(MediaPlayer mp, int what, int extra) {
		Toast.makeText(getApplicationContext(), "Media Player Error - Reseting",
				Toast.LENGTH_SHORT).show();
		Log.e(TAG, "Error: what= " + String.valueOf(what) + ", extra= " + String.valueOf(extra));
		
		mState = State.Stopped;
		relaxResources(true);
		giveUpAudioFocus();
		return true;
	}
	
	@TargetApi(16)
	void setUpAsForeground(String text) {
		PendingIntent pi = PendingIntent.getActivity(getBaseContext(), 0, 
				new Intent(getApplicationContext(), MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
		mNotification = new Notification.Builder(getApplicationContext())
		.setContentTitle("MusicPlayer")
		.setContentIntent(pi)
		.setContentText(text)
		.build();
		mNotification.tickerText = text;
		mNotification.icon = R.drawable.ic_launcher;
		mNotification.flags |= Notification.FLAG_ONGOING_EVENT;
		startForeground(NOTIFICATION_ID, mNotification);
	}
	
	public void onGainedAudioFocus() {
		Toast.makeText(getApplicationContext(), "Gained Audio Focus", Toast.LENGTH_SHORT).show();
		mAudioFocus = AudioFocus.Focus;
		//restart media with new focus settings
		if (mState == State.Playing)
			configAndStartMediaPlayer();
	}
	
	public void onLostAudioFocus(boolean canDuck) {
		Toast.makeText(getApplicationContext(), "Lost audio focus"+ (canDuck ? "can duck" :
	            "no duck"), Toast.LENGTH_SHORT).show();
        mAudioFocus = canDuck ? AudioFocus.NoFocusCanDuck : AudioFocus.NoFocusNoDuck;
        //start/restart/pause media player
        if (mPlayer != null && mPlayer.isPlaying())
        	configAndStartMediaPlayer();
	}
	
	public void onMusicRetrieverPrepared() {
		mState = State.Stopped;
		
		if(mStartPlayingAfterRetrieve) {
			tryToGetAudioFocus();
			playNextSong(mWhatToPlayAfterRetrieve == null ?
                    null : mWhatToPlayAfterRetrieve.toString());
		}
	}
	
	@Override
	public void onDestroy() {
		//Service is done, release everything
		mState = State.Stopped;
		relaxResources(true);
		giveUpAudioFocus();
	}



}
