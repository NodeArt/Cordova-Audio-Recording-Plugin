package com.nodeart.raixur.recorder;

import android.media.AudioFormat;
import android.media.AudioRecord;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

public class AudioRecorder
{
    public enum State {INITIALIZING, READY, RECORDING, ERROR, STOPPED}

    // The interval in which the recorded samples are output to the file
    private static final int TIMER_INTERVAL = 120;

    private AudioRecord recorder = null;

    private State state;

    private String filePath;

    private RandomAccessFile fileWriter;
    private FileChannel		 fileChannel;

    private short   channels; 
    private int     sampleRate;
    private short   bitsPerSample;
    private int     bufferSize;
    private int     audioSource;
    private int     audioFormat;

    // Number of frames written to file on each output
    private int framePeriod;

    // Buffer for output
    private ByteBuffer buffer;

    // Number of bytes written to file after start of the recording
    private int payloadSize;

    /**
     * Returns the state of the recorder in a AudioRecorder.State typed object.
     *
     * @return recorder state
     */
    public State getState() {
        return state;
    }

    /**
     * Method used for recording.
     */
    private AudioRecord.OnRecordPositionUpdateListener updateListener = new AudioRecord.OnRecordPositionUpdateListener()
    {
        @Override
        public void onPeriodicNotification(AudioRecord recorder) {
            this.recorder.read(buffer, buffer.capacity());
            try {
                buffer.rewind();
                fileChannel.write(buffer);
                payloadSize += buffer.capacity();
            } catch (IOException e) {
                stop();
            }
        }

        @Override
        public void onMarkerReached(AudioRecord recorder) {  }
    };


    public AudioRecorder(int audioSource, int sampleRate, int channelConfig,
                                  int audioFormat)
    {
        try {
            if (audioFormat == AudioFormat.ENCODING_PCM_16BIT) {
                bitsPerSample = 16;
            } else {
                bitsPerSample = 8;
            }

            if (channelConfig == AudioFormat.CHANNEL_IN_MONO) {
                channels = 1;
            } else {
                channels = 2;
            }

            audioSource = audioSource;
            sampleRate   = sampleRate;
            audioFormat = audioFormat;

            framePeriod = sampleRate * TIMER_INTERVAL / 1000;
            bufferSize = framePeriod * 2 * bitsPerSample * channels / 8;

            if (bufferSize < AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)) {
                bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
                framePeriod = bufferSize / ( 2 * bitsPerSample * channels / 8 );
            }

            recorder = new AudioRecord(audioSource, sampleRate,
                    channelConfig, audioFormat, bufferSize);

            if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                throw new Exception("AudioRecord initialization failed");
            }

            recorder.setRecordPositionUpdateListener(updateListener);
            recorder.setPositionNotificationPeriod(framePeriod);

            filePath = null;
            state = State.INITIALIZING;

        } catch (Exception e) {
            state = State.ERROR;
        }
    }

    /**
     * Sets output file path, call directly after construction/reset.
     *
     * @param outputFile file path
     */
    public void setOutputFile(String outputFile) {
        if (state == State.INITIALIZING) {
            filePath = outputFile;
        }
    }

    /**
     * Prepares the recorder for recording, in case the recorder is not in the INITIALIZING state and the file path was not set
     * the recorder is set to the ERROR state, which makes a reconstruction necessary.
     * the header of the wave file is written.
     * In case of an exception, the state is changed to ERROR
     */
    public void prepare() {
        try {
            if (state == State.INITIALIZING) {
                if ((recorder.getState() == AudioRecord.STATE_INITIALIZED) & (filePath != null)) {
                    fileWriter = new RandomAccessFile(filePath, "rw");
                    fileChannel= fileWriter.getChannel();

                    fileWriter.setLength(0); // Set file length to 0, to prevent unexpected behavior in case the file already existed
                    fileWriter.writeBytes("RIFF");
                    fileWriter.writeInt(0); // Temporary size
                    fileWriter.writeBytes("WAVE");
                    fileWriter.writeBytes("fmt ");
                    fileWriter.writeInt(Integer.reverseBytes(16)); // Sub-chunk size, 16 for PCM
                    fileWriter.writeShort(Short.reverseBytes((short) 1)); // AudioFormat, 1 for PCM
                    fileWriter.writeShort(Short.reverseBytes(channels));// Number of channels, 1 for mono, 2 for stereo
                    fileWriter.writeInt(Integer.reverseBytes(sampleRate)); // Sample rate
                    fileWriter.writeInt(Integer.reverseBytes(sampleRate*bitsPerSample*channels/8)); // Byte rate, SampleRate*NumberOfChannels*BitsPerSample/8
                    fileWriter.writeShort(Short.reverseBytes((short)(channels*bitsPerSample/8))); // Block align, NumberOfChannels*BitsPerSample/8
                    fileWriter.writeShort(Short.reverseBytes(bitsPerSample)); // Bits per sample

                    fileWriter.writeBytes("data");
                    fileWriter.writeInt(0); // Data chunk size not known yet, write 0

                    buffer = ByteBuffer.allocateDirect(framePeriod*bitsPerSample/8*channels);
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                    buffer.rewind();

                    state = State.READY;
                }
                else {
                    state = State.ERROR;
                }
            }
            else {
                release();
                state = State.ERROR;
            }
        }
        catch(Exception e)
        {
            state = State.ERROR;
        }
    }

    /**
     *  Releases the resources associated with this class, 
     *  and removes the unnecessary files, when necessary
     */
    public void release() {
        if (state == State.RECORDING) {
            stop();
        }
        else if (state == State.READY) {
            try {
                fileWriter.close();
            } catch (IOException ignored) { }

            (new File(filePath)).delete();
        }

        if (recorder != null) {
            recorder.release();
        }
    }

    /**
     * Resets the recorder to the INITIALIZING state, as if it was just created.
     * In case the class was in RECORDING state, the recording is stopped.
     */
    public void reset() {
        try {
            if (state != State.ERROR) {
                release();
                filePath = null;

                recorder = new AudioRecord(audioSource, sampleRate, channels+1, audioFormat, bufferSize);

                state = State.INITIALIZING;
            }
        }
        catch (Exception e) {
            state = State.ERROR;
        }
    }

    /**
     * Starts the recording, and sets the state to RECORDING.
     * Call after prepare().
     */
    public void start() {
        if (state == State.READY) {
            payloadSize = 0;
            recorder.startRecording();
            recorder.read(buffer, buffer.capacity());
            buffer.rewind();

            state = State.RECORDING;
        }
        else {
            state = State.ERROR;
        }
    }

    /**
     *  Stops the recording, and sets the state to STOPPED and finalizes the wave file.
     *  In case of further usage, a reset is needed.
     */
    public void stop() {
        if (state == State.RECORDING) {
            recorder.stop();

            try {
                fileWriter.seek(4); // Skips RIFF header
                fileWriter.writeInt(Integer.reverseBytes(36+payloadSize));

                fileWriter.seek(40); // Skips to the subchunk2Size
                fileWriter.writeInt(Integer.reverseBytes(payloadSize));

                fileWriter.close();
            } catch(IOException e) {
                state = State.ERROR;
            }

            state = State.STOPPED;
        }
        else {
            state = State.ERROR;
        }
    }
}