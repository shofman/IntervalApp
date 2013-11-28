package com.hofman.musicplayer;

import com.hofman.musicplayer.MusicRetriever.Item;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.net.Uri;

public class MusicRetriever {

	public MusicRetriever(ContentResolver contentResolver) {
		// TODO Auto-generated constructor stub
	}
	

	public Item getRandomItem() {
		// TODO Auto-generated method stub
		return null;
	}

	public static class Item {
		long id;
		String artist;
		String title;
		String album;
		long duration;
		
		public Item(long id, String artist, String title, String album, long duration) {
			this.id = id;
			this.artist = artist;
			this.title = title;
			this.album = album;
			this.duration = duration;
		}
		
		public long getId() {
			return id;
		}
		
		public String getArtist() {
			return artist;
		}
		
		public String getTitle() {
			return title;
		}
		
		public String getAlbum() {
			return album;
		}
		
		public long getDuration() {
			return duration;
		}
		
		public Uri getURI() {
			return ContentUris.withAppendedId(
					android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
		}
	}

}
