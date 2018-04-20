package com.didi.splviewer;

import java.awt.Button;
import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Label;
import java.awt.Panel;
import java.util.concurrent.LinkedBlockingQueue;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;

public final class AudioCapture implements SPLModule {

    private static int SAMPLE_RATE = 44100;

    static int getSampleRate() {
        return SAMPLE_RATE;
    }

    private static int SAMPLE_SIZE_IN_BITS = 16;

    public static int getSampleSizeInBits() {
        return SAMPLE_SIZE_IN_BITS;
    }

    public static final int NUMBER_OF_CHANNELS = 1;

    private static final boolean SIGNED = true;

    public static boolean isSIGNED() {
        return SIGNED;
    }

    public static boolean BIG_ENDIAN = true;

    public static boolean isBigEndian() {
        return BIG_ENDIAN;
    }

    private static Logger logger = (Logger) LoggerFactory.getLogger(AudioCapture.class);
    private final LinkedBlockingQueue<Long> pushBuffer;
    private boolean STOP_CAPTURE_FLAG = false;

    public AudioCapture(LinkedBlockingQueue<Long> pushBuffer) {
        this.pushBuffer = pushBuffer;
    }

    @Override
    public void run() {
        STOP_CAPTURE_FLAG = false;

        Mixer mixer = getMixer();
        final AudioFormat format = new AudioFormat(getSampleRate(), getSampleSizeInBits(), NUMBER_OF_CHANNELS, SIGNED, isBigEndian());
        final TargetDataLine line;
        try {
            line = (TargetDataLine) mixer.getLine(
                new DataLine.Info(TargetDataLine.class, format));
            line.open(format);
            line.start();

            int bufferSize = (int) format.getSampleRate();

            int frameSize = format.getFrameSize();
            byte buffer[] = new byte[bufferSize * frameSize];

            //Main loop
            while (!STOP_CAPTURE_FLAG) {
                int[] counts = new int[frameSize];
                for (int i = 0; i < frameSize; i++) {
                    counts[i] = line.read(buffer, i * bufferSize, bufferSize);
                }

                int checkCount = counts[0];
                for (int count : counts) {
                    if (count != checkCount) {
                        logger.error("Big/low-endian pair didn't have equal number of samples");
                        throw new RuntimeException("Big/low-endian pair didn't have equal number of samples");
                    }
                }
                pushSamples(frameSize, buffer);
            }

            line.close();
            mixer.close();

        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
    }

    private void pushSamples(final int frameSize, final byte[] buffer) {
        for (int i = 0; i < buffer.length; i += frameSize) {
            long value = 0;
            if (frameSize == 1) {
                value = buffer[i];
            } else if (frameSize == 2) {
                value = isBigEndian() ?
                    //TODO, do we subtract 1 before 1s complement or after???? Makes small difference to the actual value.    ~(((buffer[i] << 8) | buffer[i + 1]) - 1)  VERSUS ~((buffer[i] << 8) | buffer[i + 1]) - 1
                    ~(((buffer[i] << 8) | buffer[i + 1]) - 1) :
                    ~(((buffer[i + 1] << 8) | buffer[i]) - 1);
            } else if (frameSize == 3) {
                value = isBigEndian() ?
                    ~(((buffer[i] << 16) | (buffer[i + 1] << 8) | buffer[i + 2]) - 1) :
                    ~(((buffer[i + 2] << 16) | (buffer[i + 1] << 8) | buffer[i]) - 1);
            } else if (frameSize == 4) {
                value = isBigEndian() ?
                    ~(((buffer[i] << 24) | (buffer[i + 1] << 16) | (buffer[i + 2] << 8) | buffer[i + 3]) - 1) :
                    ~(((buffer[i + 3] << 24) | (buffer[i + 2] << 16) | (buffer[i + 1] << 8) | buffer[i]) - 1);
            }
            pushBuffer.offer(value);
        }

    }

    @Override
    public AudioCapture shutdown() {
        STOP_CAPTURE_FLAG = true;
        return this;
    }

    @Override
    public Panel getOptionsPanel() {
        Panel menuPanel = new Panel();

        Choice sampleRatesChoice = new Choice();
        sampleRatesChoice.add("8000");
        sampleRatesChoice.add("44100");
        sampleRatesChoice.add("48000");
        sampleRatesChoice.add("96000");
        sampleRatesChoice.select(String.valueOf(getSampleRate()));

        Choice sampleSizeInBitsChoice = new Choice();
        sampleSizeInBitsChoice.add("8");
        sampleSizeInBitsChoice.add("16");
        sampleSizeInBitsChoice.add("24");
        //sampleSizeInBits.add("32");
        sampleSizeInBitsChoice.select(String.valueOf(getSampleSizeInBits()));

        Choice numberOfChannelsChoice = new Choice();
        numberOfChannelsChoice.add(String.valueOf(NUMBER_OF_CHANNELS));
        numberOfChannelsChoice.setEnabled(false);

        Checkbox signedCheckbox = new Checkbox("Signed");
        signedCheckbox.setState(SIGNED);
        signedCheckbox.setEnabled(false);

        Checkbox bigEndianCheckbox = new Checkbox("Big-endian");
        bigEndianCheckbox.setState(isBigEndian());


        menuPanel.add(new Label("Sample rate:"));
        menuPanel.add(sampleRatesChoice);

        menuPanel.add(new Label("  Sample size:"));
        menuPanel.add(sampleSizeInBitsChoice);

        menuPanel.add(new Label("  Channels:"));
        menuPanel.add(numberOfChannelsChoice);

        menuPanel.add(signedCheckbox);
        menuPanel.add(bigEndianCheckbox);

        final Button captureButton = new Button("Capture!");
        Button stopCaptureButton = new Button("Stop!");
        stopCaptureButton.setEnabled(false);

        captureButton.addActionListener(e -> {
            AudioCapture.SAMPLE_RATE = Integer.parseInt(sampleRatesChoice.getItem(sampleRatesChoice.getSelectedIndex()));
            sampleRatesChoice.setEnabled(false);
            AudioCapture.SAMPLE_SIZE_IN_BITS = Integer.parseInt(sampleSizeInBitsChoice.getItem(sampleSizeInBitsChoice.getSelectedIndex()));
            sampleSizeInBitsChoice.setEnabled(false);
            AudioCapture.BIG_ENDIAN = bigEndianCheckbox.getState();
            bigEndianCheckbox.setEnabled(false);
            captureButton.setEnabled(false);
            stopCaptureButton.setEnabled(true);
            new Thread(() -> {
                try {
                    Color initialBackground = captureButton.getBackground();
                    while (!STOP_CAPTURE_FLAG) {
                        captureButton.setBackground(new Color(29, 76, 122));
                        Thread.sleep(400);
                        captureButton.setBackground(initialBackground);
                        Thread.sleep(400);
                    }
                    captureButton.setBackground(initialBackground);
                } catch (InterruptedException e1) {
                    e1.printStackTrace();
                }
            }).start();

            AudioCapture.this.begin();
        });
        menuPanel.add(captureButton);

        stopCaptureButton.addActionListener(e -> {
            AudioCapture.this.STOP_CAPTURE_FLAG = true;
            sampleRatesChoice.setEnabled(true);
            sampleSizeInBitsChoice.setEnabled(true);
            //numberOfChannels.setEnabled(true);
            //signed.setEnabled(true);
            bigEndianCheckbox.setEnabled(true);
            captureButton.setEnabled(true);
            stopCaptureButton.setEnabled(false);
        });
        menuPanel.add(stopCaptureButton);

        menuPanel.setBackground(Color.YELLOW);
        return menuPanel;
    }

    private Mixer getMixer() {
        Mixer mixer = null;
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            logger.info(info.toString());
            if (info.getName().equals("Built-in Microphone")) {
                mixer = AudioSystem.getMixer(info);
                break;
            }
        }
        if (mixer == null) {
            logger.error("No mixer by that name found");
            System.exit(-1);
        }
        return mixer;
    }
}