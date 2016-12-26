package com.nodeart.raixur.recorder;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.UUID;

public class CordovaRecorder extends CordovaPlugin {

    private static final int RECORDING_DEVICE = MediaRecorder.AudioSource.MIC;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private AudioRecorder recorder;


    private String outputPath;
    private int duration;

    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
        if(action.equals("record")) {
            initRecord(args);
            record(callbackContext);
            return true;
        }
        else if(action.equals("startRecord")) {
            initRecord(args);
            startRecord(callbackContext);
            return true;
        }
        else if(action.equals("stopRecord")) {
            stopRecord(callbackContext);
            return true;
        }
        return false;
    }

    private void record(final CallbackContext callbackContext) {
        recorder.start();

        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                recorder.release();
                callbackContext.success(outputPath);
            }
        }, duration);
    }

    private void startRecord(CallbackContext callbackContext) {
        recorder.start();
        callbackContext.success(recorder.getState().toString());
    }

    private void stopRecord(CallbackContext callbackContext) {
        recorder.stop();
        callbackContext.success(outputPath);
    }

    private int getDeviceSampleRate() {
        for (int rate : new int[] {44100, 22050, 16000, 11025, 8000}) {
            int bufferSize = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_CONFIGURATION_DEFAULT, AudioFormat.ENCODING_PCM_16BIT);
            if ( bufferSize > 0) {
                return rate;
	    }
        }
        return 0;
    }

    private void initRecord(JSONArray args) throws JSONException {
        Context context = cordova.getActivity().getApplicationContext();
        String outputFile;

        if (args.length() >= 1) {
            outputFile = args.getString(0);
        } else {
            outputFile = UUID.randomUUID().toString();
        }

        if (args.length() >= 2) {
            duration = Integer.parseInt(args.getString(1));
        } else {
            duration = 0;
        }

        outputPath = context.getExternalCacheDir().getAbsoluteFile() + "/" + outputFile + ".wav";

	

        recorder = new AudioRecorder(RECORDING_DEVICE, getDeviceSampleRate(),
                    CHANNEL_CONFIG, AUDIO_ENCODING);
        recorder.setOutputFile(outputPath);
        recorder.prepare();
    }
}
