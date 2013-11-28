package com.hofman.musicplayer;

import android.content.Context;
import android.media.AudioManager;;

public class AudioFocusHelper implements AudioManager.OnAudioFocusChangeListener {

	public AudioFocusHelper(Context applicationContext,
			MusicService musicService) {
		// TODO Auto-generated constructor stub
	}

	@Override
	public void onAudioFocusChange(int arg0) {
		// TODO Auto-generated method stub
		
	}

	public boolean abandonFocus() {
		// TODO Auto-generated method stub
		return false;
	}

	public boolean requestFocus() {
		// TODO Auto-generated method stub
		return false;
	}

}
