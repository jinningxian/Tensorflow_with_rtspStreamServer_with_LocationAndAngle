/*
 * Copyright (C) 2011-2015 GUIGUI Simon, fyhertz@gmail.com
 *
 * This file is part of libstreaming (https://github.com/fyhertz/libstreaming)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.majorkernelpanic.streaming.video;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.widget.Toast;

import com.ncs.rtspstream.App;
import com.ncs.rtspstream.MainActivity;
import com.ubtechinc.cruzr.sdk.navigation.NavigationApi;

import net.majorkernelpanic.streaming.MediaStream;
import net.majorkernelpanic.streaming.SessionBuilder;
import net.majorkernelpanic.streaming.Stream;
import net.majorkernelpanic.streaming.exceptions.CameraInUseException;
import net.majorkernelpanic.streaming.exceptions.ConfNotSupportedException;
import net.majorkernelpanic.streaming.exceptions.InvalidSurfaceException;
import net.majorkernelpanic.streaming.gl.SurfaceView;
import net.majorkernelpanic.streaming.hw.EncoderDebugger;
import net.majorkernelpanic.streaming.hw.NV21Convertor;
import net.majorkernelpanic.streaming.rtp.MediaCodecInputStream;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import detection.BitmapHandler;
import detection.MqttHelper;
import detection.customview.OverlayView;
import detection.tflite.Classifier;
import detection.tflite.TFLiteObjectDetectionAPIModel;

import static com.ncs.rtspstream.App.displayPosition;
import static com.ncs.rtspstream.App.robotWorkStatus;


/**
 * Don't use this class directly.
 */
public abstract class VideoStream extends MediaStream {

	protected final static String TAG = "VideoStream";

	protected VideoQuality mRequestedQuality = VideoQuality.DEFAULT_VIDEO_QUALITY.clone();
	protected VideoQuality mQuality = mRequestedQuality.clone(); 
	protected SurfaceHolder.Callback mSurfaceHolderCallback = null;
	protected SurfaceView mSurfaceView = null;
	protected SharedPreferences mSettings = null;
	protected int mVideoEncoder, mCameraId = 0;
	protected int mRequestedOrientation = 0, mOrientation = 0;
	protected Camera mCamera;
	protected Thread mCameraThread;
	protected Looper mCameraLooper;

	protected boolean mCameraOpenedManually = true;
	protected boolean mFlashEnabled = false;
	protected boolean mSurfaceReady = false;
	protected boolean mUnlocked = false;
	protected boolean mPreviewStarted = false;
	protected boolean mUpdated = false;
	
	protected String mMimeType;
	protected String mEncoderName;
	protected int mEncoderColorFormat;
	protected int mCameraImageFormat;
	protected int mMaxFps = 0;
	public String FILE_PATH = Environment.getExternalStorageDirectory() + "/movie.mkv";

	public static Camera realCamera;

	@Override
	public void setStreamingMethod(byte mode) {
		super.setStreamingMethod(mode);
	}

	/**
	 * Don't use this class directly.
	 * Uses CAMERA_FACING_BACK by default.
	 */
	public VideoStream() {
		this(CameraInfo.CAMERA_FACING_BACK);
	}	

	/** 
	 * Don't use this class directly
	 * @param camera Can be either CameraInfo.CAMERA_FACING_BACK or CameraInfo.CAMERA_FACING_FRONT
	 */
	@SuppressLint("InlinedApi")
	public VideoStream(int camera) {
		super();
		setCamera(camera);
	}

	/**
	 * another class for videoStream, trying to pass camera object inside
	 */

	public VideoStream(Camera camera){
		super();
	    this.mCamera = camera;
    }

	/**
	 * Sets the camera that will be used to capture video.
	 * You can call this method at any time and changes will take effect next time you start the stream.
	 * @param camera Can be either CameraInfo.CAMERA_FACING_BACK or CameraInfo.CAMERA_FACING_FRONT
	 */
	public void setCamera(int camera) {
		CameraInfo cameraInfo = new CameraInfo();
		int numberOfCameras = Camera.getNumberOfCameras();
		for (int i=0;i<numberOfCameras;i++) {
			Camera.getCameraInfo(i, cameraInfo);
			if (cameraInfo.facing == camera) {
				mCameraId = i;
				break;
			}
		}
	}

	/**	Switch between the front facing and the back facing camera of the phone. 
	 * If {@link #startPreview()} has been called, the preview will be  briefly interrupted. 
	 * If {@link #start()} has been called, the stream will be  briefly interrupted.
	 * You should not call this method from the main thread if you are already streaming. 
	 * @throws IOException 
	 * @throws RuntimeException 
	 **/
	public void switchCamera() throws RuntimeException, IOException {
		if (Camera.getNumberOfCameras() == 1) throw new IllegalStateException("Phone only has one camera !");
		boolean streaming = mStreaming;
		boolean previewing = mCamera!=null && mCameraOpenedManually; 
		mCameraId = (mCameraId == CameraInfo.CAMERA_FACING_BACK) ? CameraInfo.CAMERA_FACING_FRONT : CameraInfo.CAMERA_FACING_BACK; 
		setCamera(mCameraId);
		stopPreview();
		mFlashEnabled = false;
		if (previewing) startPreview();
		if (streaming) start(); 
	}

	/**
	 * Returns the id of the camera currently selected. 
	 * Can be either {@link CameraInfo#CAMERA_FACING_BACK} or 
	 * {@link CameraInfo#CAMERA_FACING_FRONT}.
	 */
	public int getCamera() {
		return mCameraId;
	}

	/**
	 * Sets a Surface to show a preview of recorded media (video). 
	 * You can call this method at any time and changes will take effect next time you call {@link #start()}.
	 */
	public synchronized void setSurfaceView(SurfaceView view) {
		mSurfaceView = view;
		if (mSurfaceHolderCallback != null && mSurfaceView != null && mSurfaceView.getHolder() != null) {
			mSurfaceView.getHolder().removeCallback(mSurfaceHolderCallback);
		}
		if (mSurfaceView != null && mSurfaceView.getHolder() != null) {
			mSurfaceHolderCallback = new Callback() {
				@Override
				public void surfaceDestroyed(SurfaceHolder holder) {
					mSurfaceReady = false;
					stopPreview();
					Log.d(TAG,"Surface destroyed !");
					setSurfaceView(mSurfaceView); // added to allow toggling of visibility of surface
				}
				@Override
				public void surfaceCreated(SurfaceHolder holder) {
					mSurfaceReady = true;
				}
				@Override
				public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
					Log.d(TAG,"Surface Changed !");
				}
			};
			mSurfaceView.getHolder().addCallback(mSurfaceHolderCallback);
			mSurfaceReady = true;
		}
	}

	/** Turns the LED on or off if phone has one. */
	public synchronized void setFlashState(boolean state) {
		// If the camera has already been opened, we apply the change immediately
		if (mCamera != null) {

			if (mStreaming && mMode == MODE_MEDIARECORDER_API) {
				lockCamera();
			}

			Parameters parameters = mCamera.getParameters();

			// We test if the phone has a flash
			if (parameters.getFlashMode()==null) {
				// The phone has no flash or the choosen camera can not toggle the flash
				throw new RuntimeException("Can't turn the flash on !");
			} else {
				parameters.setFlashMode(state?Parameters.FLASH_MODE_TORCH:Parameters.FLASH_MODE_OFF);
				try {
					mCamera.setParameters(parameters);
					mFlashEnabled = state;
				} catch (RuntimeException e) {
					mFlashEnabled = false;
					throw new RuntimeException("Can't turn the flash on !");
				} finally {
					if (mStreaming && mMode == MODE_MEDIARECORDER_API) {
						unlockCamera();
					}
				}
			}
		} else {
			mFlashEnabled = state;
		}
	}

	/** 
	 * Toggles the LED of the phone if it has one.
	 * You can get the current state of the flash with {@link VideoStream#getFlashState()}.
	 */
	public synchronized void toggleFlash() {
		setFlashState(!mFlashEnabled);
	}

	/** Indicates whether or not the flash of the phone is on. */
	public boolean getFlashState() {
		return mFlashEnabled;
	}

	/** 
	 * Sets the orientation of the preview.
	 * @param orientation The orientation of the preview
	 */
	public void setPreviewOrientation(int orientation) {
		mRequestedOrientation = orientation;
		mUpdated = false;
	}
	
	/** 
	 * Sets the configuration of the stream. You can call this method at any time 
	 * and changes will take effect next time you call {@link #configure()}.
	 * @param videoQuality Quality of the stream
	 */
	public void setVideoQuality(VideoQuality videoQuality) {
		if (!mRequestedQuality.equals(videoQuality)) {
			mRequestedQuality = videoQuality.clone();
			mUpdated = false;
		}
	}

	/** 
	 * Returns the quality of the stream.  
	 */
	public VideoQuality getVideoQuality() {
		return mRequestedQuality;
	}

	/**
	 * Some data (SPS and PPS params) needs to be stored when {@link #getSessionDescription()} is called 
	 * @param prefs The SharedPreferences that will be used to save SPS and PPS parameters
	 */
	public void setPreferences(SharedPreferences prefs) {
		mSettings = prefs;
	}

	/**
	 * Configures the stream. You need to call this before calling {@link #getSessionDescription()} 
	 * to apply your configuration of the stream.
	 */
	public synchronized void configure() throws IllegalStateException, IOException {
		super.configure();
		mOrientation = mRequestedOrientation;
	}	
	
	/**
	 * Starts the stream.
	 * This will also open the camera and display the preview 
	 * if {@link #startPreview()} has not already been called.
	 */
	public synchronized void start() throws IllegalStateException, IOException {
		if (!mPreviewStarted) mCameraOpenedManually = false;
		super.start();
		Log.d(TAG,"Stream configuration: FPS: "+mQuality.framerate+" Width: "+mQuality.resX+" Height: "+mQuality.resY);
	}

	/** Stops the stream. */
	public synchronized void stop() {
		if (mCamera != null) {
			if (mMode == MODE_MEDIACODEC_API && !MainActivity.cameraRelease) {
				mCamera.setPreviewCallbackWithBuffer(null);
			}
			if (mMode == MODE_MEDIACODEC_API_2) {
				((SurfaceView)mSurfaceView).removeMediaCodecSurface();
			}
			super.stop();
			// We need to restart the preview
			if (!mCameraOpenedManually) {
				destroyCamera();
			} else {
				try {
					startPreview();
				} catch (RuntimeException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public synchronized void startPreview() 
			throws CameraInUseException, 
			InvalidSurfaceException, 
			RuntimeException {
		
		mCameraOpenedManually = true;
		if (!mPreviewStarted) {
			createCamera();
			updateCamera();
		}
	}

	/**
	 * Stops the preview.
	 */
	public synchronized void stopPreview() {
		mCameraOpenedManually = false;
		stop();
	}

	/**
	 * Video encoding is done by a MediaRecorder.
	 */
	protected void encodeWithMediaRecorder() throws IOException, ConfNotSupportedException {

		Log.d(TAG,"Video encoded using the MediaRecorder API");

		// We need a local socket to forward data output by the camera to the packetizer
		createSockets();

		// Reopens the camera if needed
		destroyCamera();

		// added this to "fix" the crash
		try {
			Thread.sleep(500);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		createCamera();

		// The camera must be unlocked before the MediaRecorder can use it
		unlockCamera();

		// initialize file
		//File fileLocation = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)+ "/video.mp4");

		try {
			mMediaRecorder = new MediaRecorder();
			mMediaRecorder.setCamera(mCamera);
			mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
			mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
			mMediaRecorder.setVideoEncoder(mVideoEncoder);
			mMediaRecorder.setPreviewDisplay(mSurfaceView.getHolder().getSurface());
			mMediaRecorder.setVideoSize(mRequestedQuality.resX,mRequestedQuality.resY);
			mMediaRecorder.setVideoFrameRate(mRequestedQuality.framerate);
			//mMediaRecorder.setOutputFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES) + "/sense.mp4");

			// The bandwidth actually consumed is often above what was requested
			mMediaRecorder.setVideoEncodingBitRate((int)(mRequestedQuality.bitrate*0.8));

			// We write the output of the camera in a local socket instead of a file !
			// This one little trick makes streaming feasible quiet simply: data from the camera
			// can then be manipulated at the other end of the socket
			FileDescriptor fd = null;
			if (sPipeApi == PIPE_API_PFD) {
				fd = mParcelWrite.getFileDescriptor();
			} else  {
				fd = mSender.getFileDescriptor();
			}
			mMediaRecorder.setOutputFile(fd);

			mMediaRecorder.prepare();
			mMediaRecorder.start();

			/*MediaRecorder record2File = new MediaRecorder();
			record2File.setCamera(mCamera);
			record2File.setVideoSource(MediaRecorder.VideoSource.CAMERA);
			record2File.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
			record2File.setVideoEncoder(mVideoEncoder);
			record2File.setPreviewDisplay(mSurfaceView.getHolder().getSurface());
			record2File.setVideoSize(mRequestedQuality.resX,mRequestedQuality.resY);
			record2File.setVideoFrameRate(mRequestedQuality.framerate);
			record2File.setVideoEncodingBitRate((int)(mRequestedQuality.bitrate*0.8));
			record2File.setOutputFile(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)+ "/video.mp4");
			record2File.setMaxDuration(50000); //50 seconds
			record2File.setMaxFileSize(5000000); // approximately 5MB

			record2File.prepare();
			record2File.start();
*/
		} catch (Exception e) {
			throw new ConfNotSupportedException(e.getMessage());
		}

		InputStream is = null;

		if (sPipeApi == PIPE_API_PFD) {
			is = new ParcelFileDescriptor.AutoCloseInputStream(mParcelRead);
		} else  {
			is = mReceiver.getInputStream();
		}

		// This will skip the MPEG4 header if this step fails we can't stream anything :(
		try {
			byte buffer[] = new byte[4];
			// Skip all atoms preceding mdat atom
			while (!Thread.interrupted()) {
				while (is.read() != 'm');
				is.read(buffer,0,3);
				if (buffer[0] == 'd' && buffer[1] == 'a' && buffer[2] == 't') break;
			}
		} catch (IOException e) {
			Log.e(TAG,"Couldn't skip mp4 header :/");
			stop();
			throw e;
		}

		// The packetizer encapsulates the bit stream in an RTP stream and send it over the network
		mPacketizer.setInputStream(is);
		mPacketizer.start();

		mStreaming = true;
		//encodeWithMediaCodecMethod1();

	}

	public String getRecordPath()
	{
		File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
				Environment.DIRECTORY_DCIM), "RecordsMediaStreamer");

		if (! mediaStorageDir.exists()){
			if (!(mediaStorageDir.mkdirs() || mediaStorageDir.isDirectory())){
				Log.e(TAG, "<=getRecordPath() failed to create directory path="+mediaStorageDir.getPath());
				return "";
			}
		}
		return mediaStorageDir.getPath();
	}


	/**
	 * Video encoding is done by a MediaCodec.
	 */
	protected void encodeWithMediaCodec() throws RuntimeException, IOException {

		// also record so I put things here kthxbye
		final EncoderDebugger debugger = EncoderDebugger.debug(mSettings, mQuality.resX, mQuality.resY);
//		MediaMuxer mMuxer = new MediaMuxer(FILE_PATH, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

//		MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/mp4v-es", mQuality.resX, mQuality.resY);
//		mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mQuality.bitrate);
//		mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mQuality.framerate);
//		mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,debugger.getEncoderColorFormat());
//		mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
//		videoTrack = mMuxer.addTrack(mediaFormat);
//		mMuxer.start();


		if (mMode == MODE_MEDIACODEC_API_2) {
			// Uses the method MediaCodec.createInputSurface to feed the encoder
			encodeWithMediaCodecMethod2();
		} else {
			// Uses dequeueInputBuffer to feed the encoder
			encodeWithMediaCodecMethod1();
		}
	}

	private enum DetectorMode {
		TF_OD_API;
	}
//	public String FILE_PATH = Environment.getExternalStorageDirectory() + "/movie_new.mkv";
//	public MediaMuxer mMuxer;
//	public MediaCodec.BufferInfo videoInfo = new MediaCodec.BufferInfo();
//	public int videoTrack;
//	public boolean endOfStream = false;
	public Runnable imageConverter;
	public static Bitmap image;
	public Classifier detector;

	// Configuration values for the prepackaged SSD model.
	private static final int TF_OD_API_INPUT_SIZE = 300;
	private static final boolean TF_OD_API_IS_QUANTIZED = true;
	private static final String TF_OD_API_MODEL_FILE = "detect.tflite";
	private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/labelmap.txt";
	private static final DetectorMode MODE = DetectorMode.TF_OD_API;
	//private static final DetectorMode MODE = DetectorMode.TF_OD_API;
	// Minimum detection confidence to track a detection.
	private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;
	private static final boolean MAINTAIN_ASPECT = false;
	private static final Size DESIRED_PREVIEW_SIZE = new Size(640, 480);
	private static final boolean SAVE_PREVIEW_BITMAP = false;
	private static final float TEXT_SIZE_DIP = 10;

	private int cropSize;
	private AssetManager assetManager;
	private int previewHeight;
	private int previewWidth;
	private int[] rgbBytes;

	private boolean readyForNextImage = false;

	// --------------------- Timeout class ---------------------------------------
//	Runnable r;
//	Handler timeOutHandler;
//	public boolean runnable = false;
//
//
//	private void timeOut(){
//		timeOutHandler = new Handler();
//		r = new Runnable() {
//			@Override
//			public void run() {
//				// TODO Auto-generated method stub
//				MainActivity.faceDetected = false;
//				runnable = false;
//				stopHandler();
//				//processImage();
//			}
//		};
//	}
//	public void stopHandler(){
//		timeOutHandler.removeCallbacks(r);
//	}
//	public void startHandler() {
//		timeOutHandler.postDelayed(r, 1 * 1000);
//	}
	//----------------------------------------------------------------------------------

	/**
	 * Video encoding is done by a MediaCodec.
	 */
	@SuppressLint("NewApi")
	protected void encodeWithMediaCodecMethod1() throws RuntimeException, IOException {

		Log.d(TAG,"Video encoded using the MediaCodec API with a buffer");

		// Updates the parameters of the camera if needed
		// createCamera();
		createCamera(mCamera);
		updateCamera();

		// Estimates the frame rate of the camera
		measureFramerate();

		// Starts the preview if needed
		if (!mPreviewStarted) {
			try {
				mCamera.startPreview();
				mPreviewStarted = true;
				/*muxer= new MediaMuxer(FILE_PATH,
						MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);*/
			} catch (RuntimeException e) {
				destroyCamera();
				throw e;
			}
		}

		final EncoderDebugger debugger = EncoderDebugger.debug(mSettings, mQuality.resX, mQuality.resY);
		final NV21Convertor convertor = debugger.getNV21Convertor();

		//Log.i(TAG, "Media Codec hihi");
		mMediaCodec = MediaCodec.createByCodecName(debugger.getEncoderName());
		MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", mQuality.resX, mQuality.resY);
		mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mQuality.bitrate);
		mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mQuality.framerate);	
		mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,debugger.getEncoderColorFormat());
		mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
		mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		mMediaCodec.start();

		//Log.i(TAG, "Saving to" + FILE_PATH);
		/*Log.i(TAG, mMediaCodec.getOutputFormat().toString());
		videoTrack = muxer.addTrack(mediaFormat);
		muxer.start();
*/
		assetManager = SessionBuilder.getInstance().getContext().getAssets();
		detector =
				TFLiteObjectDetectionAPIModel.create(
						assetManager,
						TF_OD_API_MODEL_FILE,
						TF_OD_API_LABELS_FILE,
						TF_OD_API_INPUT_SIZE,
						TF_OD_API_IS_QUANTIZED);
		cropSize = TF_OD_API_INPUT_SIZE;


		Camera.PreviewCallback callback = new Camera.PreviewCallback() {
			long now = System.nanoTime()/1000, oldnow = now, i=0;
			ByteBuffer[] inputBuffers = mMediaCodec.getInputBuffers();
			//OutputStream outputStream = new FileOutputStream(FILE_PATH);
			// change to media muxer


			@Override
			synchronized public void onPreviewFrame(final byte[] data, Camera camera) {
//				imageConverter =
//						new Runnable() {
//							@Override
//							public void run() {
//								ImageUtils.convertYUV420SPToARGB8888(
//										data,
//										previewWidth,
//										previewHeight,
//										rgbBytes);
//								image = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
//								image.setPixels(rgbBytes, 0, previewWidth, 0, 0, previewWidth, previewHeight);
//							}
//						};

				oldnow = now;
				now = System.nanoTime()/1000;
/*				if (i++>3) {
					i = 0;
					//Log.d(TAG,"Mea	sured: "+1000000L/(now-oldnow)+" fps.");
				}*/
				try {
						int bufferIndex = mMediaCodec.dequeueInputBuffer(500000);
						/*// after dequeueing, then save image, get the input buffer from mediacodec while supposedly doesn't affect the input itself within mediacodec
						ByteBuffer imageBuffer = ByteBuffer.wrap(data);
						// try to see if Bitmap is created
						Bitmap image = BitmapFactory.decodeByteArray(data, 0, data.length);*/
//						Camera.Size previewSize = camera.getParameters().getPreviewSize();
//						previewHeight = previewSize.height;
//						previewWidth = previewSize.width;
//						rgbBytes = new int[previewWidth * previewHeight];
//						imageConverter.run();
////						image = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
//						//image.setPixels(rgbBytes, 0, previewWidth, 0,0,previewWidth,previewHeight);
//						croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888);
//					if (MainActivity.faceDetected == false){
//						processImage();
//						//ImageUtils.saveBitmap(image);
//
//						/*try {
//							//File file = new File(Environment.getExternalStorageDirectory() + "/testImage.jpeg");
//							//if (file.exists()) file.delete();
//							FileOutputStream fileOutputStream = new FileOutputStream(Environment.getExternalStorageDirectory() + "/testImage.jpeg");
//							//fileOutputStream.write(data);
//							//fileOutputStream.close();
//							//image.compress(Bitmap.CompressFormat.JPEG, 100, fileOutputStream);
//							Log.i(TAG, "Save Successful!");
//						} catch (Exception e) {
//							e.printStackTrace();
//						}finally {
//							MainActivity.save = false;
//						}*/
//
//					} else {
//						if (runnable == false)
//						{
//							runnable = true;
//							timeOut();
//							startHandler();
//						}
//					}
					if (MainActivity.cameraRelease == true){
						Log.i(TAG, "Camera has been released!");
						//MainActivity.mSurfaceView.setVisibility(View.VISIBLE);
						return;
					}
					runDetection(data, camera);

					if (bufferIndex>=0) {
						inputBuffers[bufferIndex].clear();
						if (data == null) Log.e(TAG,"Symptom of the \"Callback buffer was to small\" problem...");
						else
							try{convertor.convert(data, inputBuffers[bufferIndex]);}
							catch (Exception e){
								Log.i(TAG, "Buffer has been freed. Nothing to convert...");
								return;
							}
						mMediaCodec.queueInputBuffer(bufferIndex, 0, inputBuffers[bufferIndex].position(), now, 0);
					} else {
						Log.e(TAG,"No buffer available !");
					}
				} finally {
					mCamera.addCallbackBuffer(data);
				}
			}
		};

		for (int i=0;i<10;i++) mCamera.addCallbackBuffer(new byte[convertor.getBufferSize()]);
		mCamera.setPreviewCallbackWithBuffer(callback);

		// The packetizer encapsulates the bit stream in an RTP stream and send it over the network
		mPacketizer.setInputStream(new MediaCodecInputStream(mMediaCodec));
		mPacketizer.start();

		// record to file
		// writeToFile(FILE_PATH);

		mStreaming = true;

	}

	private long timestamp = 0, lastProcessingTimeMs;
	OverlayView trackingOverlay;
	private boolean computingDetection = false;
	public Bitmap croppedBitmap, cropCopyBitmap;
	private Matrix frameToCropTransform;
	private Matrix cropToFrameTransform;
	public MqttHelper mqttHelper = App.mqttHelper;
	boolean ans = false;

	private void runDetection(byte[] data, Camera camera){
		imageConverter =
				new Runnable() {
					@Override
					public void run() {
						ImageUtils.convertYUV420SPToARGB8888(
								data,
								previewWidth,
								previewHeight,
								rgbBytes);
						image = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
						image.setPixels(rgbBytes, 0, previewWidth, 0, 0, previewWidth, previewHeight);
					}
				};
		Camera.Size previewSize = camera.getParameters().getPreviewSize();
		previewHeight = previewSize.height;
		previewWidth = previewSize.width;
		rgbBytes = new int[previewWidth * previewHeight];
		imageConverter.run();
		croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888);
		processImage();
		//if (MainActivity.faceDetected == false){
		//	processImage();
//		} else {
//			if (runnable == false){
//				runnable = true;
//				timeOut();
//				startHandler();
//			}
//		}
	}

	protected void processImage() {
		++timestamp;
		final long currTimestamp = timestamp;
		//trackingOverlay.postInvalidate();

		frameToCropTransform =
				ImageUtils.getTransformationMatrix(
						previewWidth, previewHeight,
						cropSize, cropSize,
						0, MAINTAIN_ASPECT);
		cropToFrameTransform = new Matrix();
		frameToCropTransform.invert(cropToFrameTransform);

		// No mutex needed as this method is not reentrant.
		if (computingDetection) {
			//readyForNextImage(); // just add callback buffer and toggle an isprocessingframe
			readyForNextImage = false;
			return;
		}
		computingDetection = true;
		Log.i(TAG, "Preparing image " + currTimestamp + " for detection in bg thread.");

		image.setPixels(rgbBytes, 0, previewWidth, 0, 0, previewWidth, previewHeight);

		//readyForNextImage(); // just add callback buffer and toggle an isprocessingframe

		final Canvas canvas = new Canvas(croppedBitmap);
		canvas.drawBitmap(image, frameToCropTransform, null);
		// For examining the actual TF input.
		if (SAVE_PREVIEW_BITMAP) {
			ImageUtils.saveBitmap(croppedBitmap);
		}
		if (detectInBackground != null){
			detectInBackground.run();
		}

	}

	public Runnable detectInBackground = new Thread() {
		@Override
		public void run() {
			if (mCamera != null) {
				Log.i(TAG,"Running detection on image " + System.nanoTime()/1000);
				final long startTime = SystemClock.uptimeMillis();
				final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);
				lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;

				cropCopyBitmap = Bitmap.createBitmap(croppedBitmap);
				final Canvas canvas = new Canvas(cropCopyBitmap);
				final Paint paint = new Paint();
				paint.setColor(Color.RED);
				paint.setStyle(Paint.Style.STROKE);
				paint.setStrokeWidth(2.0f);

				float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
				switch (MODE) {
					case TF_OD_API:
						minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
						break;
				}

				final List<Classifier.Recognition> mappedRecognitions =
						new LinkedList<Classifier.Recognition>();

				for (final Classifier.Recognition result : results) {
					final RectF location = result.getLocation();
					if (result.getTitle().equals("person") && location != null && result.getConfidence() >= minimumConfidence) {
						//App.receivedNotification = false;
						ans = false;
						canvas.drawRect(location, paint);
						cropToFrameTransform.mapRect(location);
						result.setLocation(location);
						mappedRecognitions.add(result);
						if(displayPosition!=null) {
//
//							Point nPoint = new Point(displayPosition[0],displayPosition[1],(displayPosition[2]+180));
//							ans = PointDetectAction(nPoint);
//
//							Log.d(" RESULT0 ", "\nRESULT SHOW"+"\nCurrent Detection->" +
//									"\n X: " + displayPosition[0]+" Y: " + displayPosition[1] +
//									"\n X: " + currentDetectPoint.x+" Y: " + currentDetectPoint.y +
//									"\nUPDATEs Detection: " + ans);
//
//							if(ans){
//								//objectIn9Sectors(location);
//								MainActivity.faceDetected = true;
//								robotWorkStatus = 1;
//								//App.receivedNotification  = false;
//								Log.d(" RESULT1 ","X: " + displayPosition[0]+" Y: " + displayPosition[1]);
//							}
							checkDistance = new Runnable() {
								@Override
								public void run() {

									Point nPoint = new Point(displayPosition[0],displayPosition[1],(displayPosition[2]+180));
									ans = PointDetectAction(nPoint);

									Log.d(" RESULT0 ", "\nRESULT SHOW"+"\nCurrent Detection->" +
											"\n X: " + displayPosition[0]+" Y: " + displayPosition[1] +
											"\n X: " + currentDetectPoint.x+" Y: " + currentDetectPoint.y +
											"\nUPDATEs Detection: " + ans);

									if(ans){
										//objectIn9Sectors(location);
										MainActivity.faceDetected = true;
										robotWorkStatus = 1;
										//App.receivedNotification  = false;
										Log.d(" RESULT1 ","X: " + displayPosition[0]+" Y: " + displayPosition[1]);
									}
								}
							};
							executorService.submit(checkDistance);

						}

					}
				}

				if (MainActivity.faceDetected == true){//CameraActivity.faceDetected == true) {
					DateFormat dateFormat = new SimpleDateFormat("yyyymmdd_hhmmss");
					Date date = new Date();
					BitmapHandler bitmapHandler = new BitmapHandler(cropCopyBitmap, dateFormat.format(date) + ".jpg", 1L);
					Log.i("FileFormat", dateFormat.format(date) + ".jpg");
					try {
						bitmapHandler.save();
//						bitmapHandler.uploadFile("192.168.21.236", "robotmanager", 9300, "robotmanager", "sdcard/" + bitmapHandler.getFilename(), "/home/godzilla/mount/web/html/sftp/NCS");
//                bitmapHandler.uploadFile("192.168.21.194","sftpuser", 22,"q1w2e3r4","sdcard/" + bitmapHandler.getFilename(),"/data/sftpuser/upload");
						bitmapHandler.uploadFile("172.18.4.35", "robotmanager", 9300, "robotmanager", "sdcard/" + bitmapHandler.getFilename(), "/home/godzilla/mount/web/html/sftp/NCS");
						if (mqttHelper.isMqttConnected() && ans) {
							Log.i(TAG, "detected something");
							NavigationApi.get().stopNavigationService();
							mqttHelper.publishRbNotification("Human Detected", bitmapHandler.getFilename(), "5c899c07-7b0a-4f1c-810e-f4bb419e1547");
						}else{
							Log.w(TAG, "Error, mqtt not connected!");
						}

					} catch (Exception e) {
						e.printStackTrace();
					}
				}

				computingDetection = false;

				//tracker.trackResults(mappedRecognitions, currTimestamp);
				//trackingOverlay.postInvalidate();

							/*computingDetection = false;
							((Activity)SessionBuilder.getInstance().getContext()).runOnUiThread(
									new Runnable() {
										@Override
										public void run() {
											*//*screenshot.setImageBitmap(cropCopyBitmap);

											showFrameInfo(previewWidth + "x" + previewHeight);
											showCropInfo(cropCopyBitmap.getWidth() + "x" + cropCopyBitmap.getHeight());
											showInference(lastProcessingTimeMs + "ms");*//*
										}
									});*/
/*
							// ----------------received notification------------------
							if (receivedNotification == true) {
								JSONObject jsonObject;
								try {
									jsonObject = new JSONObject(payload.toString());
									String toastMessage = jsonObject.getString("status").equals("acknowledged") ? "Notification Acknowledged!" : payload;
									Log.i("DectectorActivity", toastMessage);
									Toast.makeText(DetectorActivity.this, toastMessage, Toast.LENGTH_LONG).show();
								} catch (Exception e) {
									e.printStackTrace();
								}
								//Toast.makeText(DetectorActivity.this, payload, Toast.LENGTH_LONG).show();
								receivedNotification = false;
							}*/
			}

		}
	};

	/**
	 * Video encoding is done by a MediaCodec.
	 * But here we will use the buffer-to-surface method
	 */
	@SuppressLint({ "InlinedApi", "NewApi" })	
	protected void encodeWithMediaCodecMethod2() throws RuntimeException, IOException {

		Log.d(TAG,"Video encoded using the MediaCodec API with a surface");

		// Updates the parameters of the camera if needed
		createCamera();
		updateCamera();

		// Estimates the frame rate of the camera
		measureFramerate();

		EncoderDebugger debugger = EncoderDebugger.debug(mSettings, mQuality.resX, mQuality.resY);

		mMediaCodec = MediaCodec.createByCodecName(debugger.getEncoderName());
		MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", mQuality.resX, mQuality.resY);
		mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mQuality.bitrate);
		mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mQuality.framerate);	
		mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
		mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
		mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
		Surface surface = mMediaCodec.createInputSurface();
		((SurfaceView)mSurfaceView).addMediaCodecSurface(surface);
		mMediaCodec.start();

		// The packetizer encapsulates the bit stream in an RTP stream and send it over the network
		mPacketizer.setInputStream(new MediaCodecInputStream(mMediaCodec));
		mPacketizer.start();

		mStreaming = true;

	}

	/**
	 * Returns a description of the stream using SDP. 
	 * This method can only be called after {@link Stream#configure()}.
	 * @throws IllegalStateException Thrown when {@link Stream#configure()} wa not called.
	 */	
	public abstract String getSessionDescription() throws IllegalStateException;

	/**
	 * Opens the camera in a new Looper thread so that the preview callback is not called from the main thread
	 * If an exception is thrown in this Looper thread, we bring it back into the main thread.
	 * @throws RuntimeException Might happen if another app is already using the camera.
	 */
	private void openCamera() throws RuntimeException {
		final Semaphore lock = new Semaphore(0);
		final RuntimeException[] exception = new RuntimeException[1];
		mCameraThread = new Thread(new Runnable() {
			@Override
			public void run() {
				Looper.prepare();
				mCameraLooper = Looper.myLooper();
				try {
					mCamera = Camera.open(mCameraId);
				} catch (RuntimeException e) {
					exception[0] = e;
				} finally {
					lock.release();
					Looper.loop();
				}
			}
		});
		mCameraThread.start();
		lock.acquireUninterruptibly();
		if (exception[0] != null) throw new CameraInUseException(exception[0].getMessage());
	}

	protected synchronized  void openCamera(Camera camera)throws RuntimeException{
		final Camera cam = camera;
		mCameraThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Looper.prepare();
                mCameraLooper = Looper.myLooper();
                try {
                    //mCamera = Camera.open(mCameraId);
                    realCamera = cam;
                    //mCamera = realCamera;
                } catch (RuntimeException e) {
                    e.printStackTrace();
                } finally {
                    Looper.loop();
                }
        }
        });

	}

	protected synchronized void createCamera() throws RuntimeException {
		if (mSurfaceView == null)
			throw new InvalidSurfaceException("Invalid surface !");
		if (mSurfaceView.getHolder() == null || !mSurfaceReady)
			throw new InvalidSurfaceException("Invalid surface !");

		if (mCamera == null) {
			openCamera();
			mUpdated = false;
			mUnlocked = false;
			mCamera.setErrorCallback(new Camera.ErrorCallback() {
				@Override
				public void onError(int error, Camera camera) {
					// On some phones when trying to use the camera facing front the media server will die
					// Whether or not this callback may be called really depends on the phone
					if (error == Camera.CAMERA_ERROR_SERVER_DIED) {
						// In this case the application must release the camera and instantiate a new one
						Log.e(TAG,"Media server died !");
						// We don't know in what thread we are so stop needs to be synchronized
						mCameraOpenedManually = false;
						stop();
					} else {
						Log.e(TAG,"Error unknown with the camera: "+error);
					}	
				}
			});

			try {

				// If the phone has a flash, we turn it on/off according to mFlashEnabled
				// setRecordingHint(true) is a very nice optimization if you plane to only use the Camera for recording
				Parameters parameters = mCamera.getParameters();
				if (parameters.getFlashMode()!=null) {
					parameters.setFlashMode(mFlashEnabled?Parameters.FLASH_MODE_TORCH:Parameters.FLASH_MODE_OFF);
				}
				parameters.setRecordingHint(true);
				mCamera.setParameters(parameters);
				mCamera.setDisplayOrientation(mOrientation);

				try {
					if (mMode == MODE_MEDIACODEC_API_2) {
						mSurfaceView.startGLThread();
						mCamera.setPreviewTexture(mSurfaceView.getSurfaceTexture());
					} else {
						mCamera.setPreviewDisplay(mSurfaceView.getHolder());

					}
				} catch (IOException e) {
					throw new InvalidSurfaceException("Invalid surface !");
				}

			} catch (RuntimeException e) {
				destroyCamera();
				throw e;
			}

		}
	}

	protected synchronized void createCamera(Camera camera) throws RuntimeException {
		if (mSurfaceView == null)
			throw new InvalidSurfaceException("Invalid surface !");
		if (mSurfaceView.getHolder() == null || !mSurfaceReady)
			throw new InvalidSurfaceException("Invalid surface !");

//		if (mCamera != null) {
			openCamera(camera);
			mUpdated = false;
			mUnlocked = false;
			mCamera.setErrorCallback(new Camera.ErrorCallback() {
				@Override
				public void onError(int error, Camera camera) {
					// On some phones when trying to use the camera facing front the media server will die
					// Whether or not this callback may be called really depends on the phone
					if (error == Camera.CAMERA_ERROR_SERVER_DIED) {
						// In this case the application must release the camera and instantiate a new one
						Log.e(TAG,"Media server died !");
						// We don't know in what thread we are so stop needs to be synchronized
						mCameraOpenedManually = false;
						stop();
					} else {
						Log.e(TAG,"Error unknown with the camera: "+error);
					}
				}
			});

			try {

				// If the phone has a flash, we turn it on/off according to mFlashEnabled
				// setRecordingHint(true) is a very nice optimization if you plane to only use the Camera for recording
				Parameters parameters = mCamera.getParameters();
				if (parameters.getFlashMode()!=null) {
					parameters.setFlashMode(mFlashEnabled?Parameters.FLASH_MODE_TORCH:Parameters.FLASH_MODE_OFF);
				}
				parameters.setRecordingHint(true);
				mCamera.setParameters(parameters);
				mCamera.setDisplayOrientation(mOrientation);

				try {
					if (mMode == MODE_MEDIACODEC_API_2) {
						mSurfaceView.startGLThread();
						mCamera.setPreviewTexture(mSurfaceView.getSurfaceTexture());
					} else {
						mCamera.setPreviewDisplay(mSurfaceView.getHolder());

					}
				} catch (IOException e) {
					throw new InvalidSurfaceException("Invalid surface !");
				}

			} catch (RuntimeException e) {
				destroyCamera();
				throw e;
			}

//		}
	}

	protected synchronized void destroyCamera() {
		//MainActivity.surfaceVisibility = false;
		if (mCamera != null && !MainActivity.cameraRelease) {
			if (mStreaming) super.stop();
			lockCamera();
			mCamera.addCallbackBuffer(null);
			mCamera.stopPreview();
			/*try {
				mCamera.release();
			} catch (Exception e) {
				Log.e(TAG,e.getMessage()!=null?e.getMessage():"unknown error");
			}
			mCamera = null;*/
			if (mCameraLooper!= null){
			mCameraLooper.quit();}
			mUnlocked = false;
			mPreviewStarted = false;
		}	
	}

	protected synchronized void updateCamera() throws RuntimeException {
		
		// The camera is already correctly configured
		if (mUpdated) return;
		
		if (mPreviewStarted) {
			mPreviewStarted = false;
			mCamera.stopPreview();
		}

		Parameters parameters = mCamera.getParameters();
		mQuality = VideoQuality.determineClosestSupportedResolution(parameters, mQuality);
		int[] max = VideoQuality.determineMaximumSupportedFramerate(parameters);
		
		double ratio = (double)mQuality.resX/(double)mQuality.resY;
		mSurfaceView.requestAspectRatio(ratio);
		
		parameters.setPreviewFormat(mCameraImageFormat);
		parameters.setPreviewSize(mQuality.resX, mQuality.resY);
		parameters.setPreviewFpsRange(max[0], max[1]);

		try {
			mCamera.setParameters(parameters);
			mCamera.setDisplayOrientation(mOrientation);
			mCamera.startPreview();


			mPreviewStarted = true;
			mUpdated = true;
		} catch (RuntimeException e) {
			destroyCamera();
			throw e;
		}
	}

	protected void lockCamera() {
		if (mUnlocked) {
			Log.d(TAG,"Locking camera");
			try {
				mCamera.reconnect();
			} catch (Exception e) {
				Log.e(TAG,e.getMessage());
			}
			mUnlocked = false;
		}
	}

	protected void unlockCamera() {
		if (!mUnlocked) {
			Log.d(TAG,"Unlocking camera");
			try {	
				mCamera.unlock();
			} catch (Exception e) {
				Log.e(TAG,e.getMessage());
			}
			mUnlocked = true;
		}
	}


	/**
	 * Computes the average frame rate at which the preview callback is called.
	 * We will then use this average frame rate with the MediaCodec.  
	 * Blocks the thread in which this function is called.
	 */
	private void measureFramerate() {
		final Semaphore lock = new Semaphore(0);

		final Camera.PreviewCallback callback = new Camera.PreviewCallback() {
			int i = 0, t = 0;
			long now, oldnow, count = 0;
			@Override
			public void onPreviewFrame(byte[] data, Camera camera) {
				i++;
				now = System.nanoTime()/1000;
				if (i>3) {
					t += now - oldnow;
					count++;
				}
				if (i>20) {
					mQuality.framerate = (int) (1000000/(t/count)+1);
					lock.release();
				}
				oldnow = now;
			}
		};

		mCamera.setPreviewCallback(callback);

		try {
			lock.tryAcquire(2,TimeUnit.SECONDS);
			Log.d(TAG,"Actual framerate: "+mQuality.framerate);
			if (mSettings != null) {
				Editor editor = mSettings.edit();
				editor.putInt(PREF_PREFIX+"fps"+mRequestedQuality.framerate+","+mCameraImageFormat+","+mRequestedQuality.resX+mRequestedQuality.resY, mQuality.framerate);
				editor.commit();
			}
		} catch (InterruptedException e) {}

		mCamera.setPreviewCallback(null);

	}









	public static int CAMERADISTANCETODETECT = 107; //107 = 5 meters; ensure camera can only view 5 meters
	public static Point currentDetectPoint = null;

	private double distance(Point current, Point checkPoint){
		return Math.sqrt(Math.pow((current.x-checkPoint.x),2)+ Math.pow((current.y-checkPoint.y),2));
	}
	public boolean PointDetectAction(Point p){
		if(currentDetectPoint == null || distance(currentDetectPoint,p)>CAMERADISTANCETODETECT){
			currentDetectPoint = p;
			return true;
		}return false;
	}

	private ExecutorService executorService = Executors.newFixedThreadPool(1);

	private Runnable checkDistance;


//Location with camera view angle range

/*	public static int CAMERAANGLERANGE = 60;
	private int angle(Point current, Point checkPoint){
		Log.d("RESULT3 part8 ", "["+current.x+","+current.y+"]||["+checkPoint.x+","+checkPoint.y+"]");
		double angle = Math.atan2((current.y-checkPoint.y),(current.x-checkPoint.x));
		Log.d(" RESULT3 part5 ", ""+(current.y-checkPoint.y)+","+ (current.x-checkPoint.x));
		double theta = (angle*180 / Math.PI);
		if(theta < 0) theta += 360;



		Log.d(" RESULT3 part6 ", ""+theta);
		Log.d(" RESULT3 part7 ", ""+(int)theta);
		return (int) theta;
	}
	public boolean PointDetectAction(Point p){

		if(currentDetectPoint == null){
			currentDetectPoint = p;
			return true;
		}
		double dis = distance(p,currentDetectPoint);
		if(dis>CAMERADISTANCETODETECT){
			Log.d(" RESULT1 ", "More than detect distance");
			currentDetectPoint = p;
			return true;

		}else if(dis==0){
			if(angleCompred(currentDetectPoint.angle, p.angle)) {
				Log.d(" RESULT1 ", "Same Location");
				currentDetectPoint = p;
				return true;
			}
		}else{
			double mPointX, mPointY;//, mPointDistance;
			mPointX = (currentDetectPoint.x + p.x)/2;
			mPointY = (currentDetectPoint.y + p.y)/2;

			double hPointX, hPointY;
			hPointX = Math.sqrt(Math.pow(CAMERADISTANCETODETECT,2)- Math.pow(p.x-mPointX,2));
			hPointY = Math.sqrt(Math.pow(CAMERADISTANCETODETECT,2)- Math.pow(p.y-mPointY,2));

			Point interPoint1, interPoint2;
			interPoint1 = new Point(mPointX+hPointX,mPointY+hPointY);
			interPoint2 = new Point(mPointX-hPointX,mPointY-hPointY);
			Log.d(" RESULT3 part1 ", "["+interPoint1.x +", " + interPoint1.y+"]||["+interPoint2.x+","+interPoint2.y+"]");
			Log.d(" RESULT3 part2 ", ""+mPointX+","+hPointX +", " + hPointY+","+mPointY);
			double angleBetweenIP1, angleBetweenIP2;
			angleBetweenIP1 = angle(interPoint1, currentDetectPoint);
			angleBetweenIP2 = angle(interPoint2, currentDetectPoint);
			Log.d(" RESULT3 part3 ", ""+angleBetweenIP1 +", " + angleBetweenIP2);

			Log.d(" RESULT3 part4 ", "["+(interPoint1.x)+ ","+(interPoint1.y)+"]||[" +(interPoint2.x)+","+(interPoint2.y)+"]");
			if(angleCompred(currentDetectPoint.angle,angleBetweenIP1) || angleCompred(currentDetectPoint.angle,angleBetweenIP2)){
				currentDetectPoint = p;
				return true;
			}


			if(angleCompred(currentDetectPoint.angle,angle(currentDetectPoint, new Point(mPointX+hPointX,mPointY+hPointY)))
					||angleCompred(currentDetectPoint.angle,angle(currentDetectPoint, new Point(mPointX-hPointX,mPointY-hPointY)))){
				currentDetectPoint = p;
				return true;
			}
			 if(angleCompred(angle(p,currentDetectPoint), currentDetectPoint.angle)){
				 Log.d(" RESULT1 ", "Different Location");
			 	currentDetectPoint = p;
			 	return true;
			 }
		}
		return false;
	}
	public boolean angleCompred(double stock, double newAngle) {
		double result = 0;
		result= ((stock - newAngle)+360) % 360;
		Log.d(" RESULT2 ","RESULT ANGLE "+ stock +" - " + newAngle +" = "+result);
		if(result > 180) return result-180 > CAMERAANGLERANGE;
		return result > CAMERAANGLERANGE;


		}

		if(stock > newAngle) result= stock - newAngle;
		else result = newAngle - stock;
		if(result > 0)
			Log.d(" RESULT2 ","RESULT ANGLE "+ stock +" - " + newAngle +" = "+result);
		if(result > 180) return (360-result) > CAMERAANGLERANGE;
		else return result > CAMERAANGLERANGE;

		stock %= 360; newAngle%=360;
		if(newAngle > 330 && stock < 30){
			return !((newAngle+30)%360 < stock);
		}
		double b = Math.abs (((stock - newAngle)+180)%360 - 180);

		Log.d("Test 10", ""+b);
		return b<CAMERAANGLERANGE;



	//Return 0 = first sector, Return 1= second sector, Return 2 = third sector, Return 3 = all 3 Sector
	protected boolean[] objectInSectorWidth(RectF location){
		boolean[] result = new boolean[3];
		double sectionWidth = 100;
		double startLocation = location.left;
		double endLocation = location.right;
		Log.d(" TEST Section Location ", "["+sectionWidth +","+startLocation +"," + endLocation+"]");
		int startSector = (int) (startLocation/sectionWidth);
		int endSector = (int) (endLocation/sectionWidth);
		if(startSector < 0)startSector=0;
		if(endSector > 2) endSector=2;
		for(int i = startSector; i < endSector+1; i++){
			result[i] = true;
		}
		return result;
	}
	protected boolean[] objectInSectorHeight(RectF location){
		boolean[] result = new boolean[3];
		double sectionHeight = 100;
		double topLocation = location.top;
		double bottomLocation = location.bottom;
		int topSector = (int) (topLocation/sectionHeight);
		int bottomSector = (int)(bottomLocation/sectionHeight);
		if(topSector < 0) topSector = 0;
		if(bottomSector > 2) bottomSector = 2;
		for(int i = topSector; i < bottomSector+1; i++){
			result[i] = true;
		}
		return result;
	}
	//Return the boolean of nine block box location by 2D array
	protected boolean[][] objectIn9Sectors(RectF location){
		boolean[][] area = new boolean[3][3];
		boolean[] width = objectInSectorWidth(location);
		boolean[] height = objectInSectorHeight(location);
		for(int i = 0; i < 3; i++){
			for(int j = 0; j < 3; j++){
				if(height[i] || width[j]){
					area[i][j]=true;
				}
			}
		}
		return area;
	}*/


}
