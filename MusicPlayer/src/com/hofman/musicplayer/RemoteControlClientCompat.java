package com.hofman.musicplayer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import android.app.PendingIntent;
import android.graphics.Bitmap;
import android.os.Looper;
import android.util.Log;
@SuppressWarnings({"rawtypes", "unchecked"})
public class RemoteControlClientCompat {
	private static final String TAG = "RemoteControlCompat";
	
	private static Class sRemoteControlClientClass;
	
	//RCC short for RemoteControlClient
	private static Method sRCCEditMetadataMethod;
	private static Method sRCCSetPlayStateMethhod;
	private static Method sRCCSetTransportControlFlags;
	
	private static boolean sHasRemoteControlAPIs = false;
	
	static {
		try {
			ClassLoader classLoader = RemoteControlClientCompat.class.getClassLoader();
			sRemoteControlClientClass = getActualRemoteControlClientClass(classLoader);
			
			for (Field field : RemoteControlClientCompat.class.getFields()) {
				try {
					Field realField = sRemoteControlClientClass.getField(field.getName());
					Object realValue = realField.get(null);
					field.set(null, realValue);
				} catch(NoSuchFieldException e) {
					Log.w(TAG, "Could not get real field: " + field.getName());
				} catch(IllegalAccessException e) {
					Log.w(TAG, "Error trying to pull field value for: "+field.getName() + " " + e.getMessage());
				} catch(IllegalArgumentException e) {
					Log.w(TAG, "Error trying to pull field value for: " + field.getName() + " " +e.getMessage());
				}
			}
			
			//get required public methods on RemoteControlClientCompat
			sRCCEditMetadataMethod = sRemoteControlClientClass.getMethod("editMetadata",
                    boolean.class);
			sRCCSetPlayStateMethhod = sRemoteControlClientClass.getMethod("setPlaybackState", 
					int.class);
			sRCCSetTransportControlFlags = sRemoteControlClientClass.getMethod("" +
					"setTransportControlFlags", int.class);		
			sHasRemoteControlAPIs = true;
		} catch(NoSuchMethodException e) {
			
		} catch(IllegalArgumentException e) {
			
		} catch (SecurityException e) {
			
		} catch (ClassNotFoundException e) {
			
		}
	}
	
	public RemoteControlClientCompat(PendingIntent broadcast) {
		if (!sHasRemoteControlAPIs) {
			return;
		}
		try {
			mActualRemoteControlClient = 
					sRemoteControlClientClass.getConstructor(PendingIntent.class)
						.newInstance(broadcast);
		} catch (Exception e) {
            throw new RuntimeException(e);
        }
	}
	
	public RemoteControlClientCompat(PendingIntent pendingIntent, Looper looper) {
		if (!sHasRemoteControlAPIs) {
			return;
		}
		try {
			mActualRemoteControlClient =
                    sRemoteControlClientClass.getConstructor(PendingIntent.class, Looper.class)
                            .newInstance(pendingIntent, looper);
        } catch (Exception e) {
            Log.e(TAG, "Error creating new instance of " + sRemoteControlClientClass.getName(), e);
        }
	}

	public static Class getActualRemoteControlClientClass(
			ClassLoader classLoader) throws ClassNotFoundException {
		return classLoader.loadClass("android.media.RemoteControlClient");
	}
	
	private Object mActualRemoteControlClient;

	public class MetadataEditorCompat {
		private Method mPutStringMethod;
		private Method mPutBitmapMethod;
		private Method mPutLongMethod;
		private Method mClearMethod;
		private Method mApplyMethod;
		
		private Object mActualMetadataEditor;
		
		public final static int METADATA_KEY_ARTWORK = 100;
		
		//Metadata key for content artwork
		private MetadataEditorCompat(Object actualMetadataEditor) {
			if (sHasRemoteControlAPIs && actualMetadataEditor == null) {
				throw new IllegalArgumentException("Remote Control API's exist, shouldn't be null");
			}
			if (sHasRemoteControlAPIs) {
				Class metadataEditorClass = actualMetadataEditor.getClass();
				
				try {
					mPutStringMethod = metadataEditorClass.getMethod("putString",
							int.class, String.class);
					mPutBitmapMethod = metadataEditorClass.getMethod("putBitmap", 
							int.class, Bitmap.class);
					mPutLongMethod = metadataEditorClass.getMethod("putLong", 
							int.class, long.class);
					mClearMethod = metadataEditorClass.getMethod("clear", new Class[]{});
					mApplyMethod = metadataEditorClass.getMethod("apply", new Class[]{});
				} catch(Exception e) {
					throw new RuntimeException(e.getMessage(), e);
				}
			}
			mActualMetadataEditor = actualMetadataEditor;
		}
		
		//Textual information
		public MetadataEditorCompat putString(int key, String value) {
			if (sHasRemoteControlAPIs) {
				try {
					mPutStringMethod.invoke(mActualMetadataEditor, key, value);
				} catch (Exception e) {
					throw new RuntimeException(e.getMessage(), e);
				}
			}
			return this;
		}
		//Set album artwork to be displayed
        public MetadataEditorCompat putBitmap(int key, Bitmap bitmap) {
            if (sHasRemoteControlAPIs) {
                try {
                    mPutBitmapMethod.invoke(mActualMetadataEditor, key, bitmap);
                } catch (Exception e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }
            return this;
        }
        //Adds numerical info to be displayed
        public MetadataEditorCompat putLong(int key, long value) {
            if (sHasRemoteControlAPIs) {
                try {
                    mPutLongMethod.invoke(mActualMetadataEditor, key, value);
                } catch (Exception e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }
            return this;
        }

	}
	public void setPlaybackState(int playstateStopped) {
		
	}

	public void setTransportControlFlags(int i) {
		
	}

	public Object editMetadata(boolean b) {
		return null;
	}

}
