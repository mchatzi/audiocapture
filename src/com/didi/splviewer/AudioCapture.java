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


    public static final boolean BIG_ENDIAN = true;

    private static Logger logger = (Logger) LoggerFactory.getLogger(AudioCapture.class);
    private final LinkedBlockingQueue<Sample> pushBuffer;
    private boolean EXIT_FLAG = false;
    private boolean STOP_CAPTURE_FLAG = false;

    public AudioCapture(LinkedBlockingQueue<Sample> pushBuffer) {
        this.pushBuffer = pushBuffer;
    }

    @Override
    public void run() {
        STOP_CAPTURE_FLAG = false;

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

        final AudioFormat format = new AudioFormat(getSampleRate(), getSampleSizeInBits(), NUMBER_OF_CHANNELS, SIGNED, BIG_ENDIAN);
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
            while (!EXIT_FLAG && !STOP_CAPTURE_FLAG) {
                int[] counts = new int[frameSize];
                for (int i = 0; i < frameSize; i++) {
                    counts[i] = line.read(buffer, i * bufferSize, bufferSize);
                }

                int checkCount = counts[0];
                for (int count : counts) {
                    if (count != checkCount) {
                        logger.error("Big-endian pair didn't have equal number of samples");

                    }
                }


                //Assuming bytes need to be combined subsequently

                for (int i = 0; i < buffer.length; i += frameSize) {
                    //int sign = buffer[i] < 0 ? -1 : 1;

                    long value = 0;
                    if (frameSize == 1) {
                        value = buffer[i];
                    } else if (frameSize == 2) {
                        //Main.printTabular(i/2, buffer[i], buffer[i+1]);
                        value = (buffer[i] << 8) + buffer[i + 1];
                    } else if (frameSize == 3) {
                        //Main.printTabular(buffer[i], buffer[i + 1], buffer[i + 2]);
                        value = (buffer[i] << 16) + (buffer[i + 1] << 8) + buffer[i + 2];
                    } else if (frameSize == 4) {
                        value = (buffer[i] << 24) + (buffer[i + 1] << 16) + (buffer[i + 2] << 8) + buffer[i + 3];
                    }

                    //long sampleValue = sign * value;


                    pushBuffer.offer(new Sample(value));
                }

            }

            line.close();
            mixer.close();

        } catch (LineUnavailableException /*| IOException*/ e) {
            e.printStackTrace();
        }
    }

    @Override
    public AudioCapture shutdown() {
        EXIT_FLAG = true;
        return this;
    }

    @Override
    public Panel getOptionsPanel() {
        Panel menuPanel = new Panel();

        Choice sampleRates = new Choice();
        sampleRates.add("8000");
        sampleRates.add("44100");
        sampleRates.add("48000");
        sampleRates.add("96000");
        sampleRates.select(String.valueOf(getSampleRate()));

        Choice sampleSizeInBits = new Choice();
        sampleSizeInBits.add("8");
        sampleSizeInBits.add("16");
        sampleSizeInBits.add("24");
        //sampleSizeInBits.add("32");
        sampleSizeInBits.select(String.valueOf(getSampleSizeInBits()));

        Choice numberOfChannels = new Choice();
        numberOfChannels.add(String.valueOf(NUMBER_OF_CHANNELS));
        numberOfChannels.setEnabled(false);

        Checkbox signed = new Checkbox("Signed");
        signed.setState(SIGNED);
        signed.setEnabled(false);

        Checkbox bigEndian = new Checkbox("Big-endian");
        bigEndian.setState(BIG_ENDIAN);
        bigEndian.setEnabled(false);

        menuPanel.add(new Label("Sample rate:"));
        menuPanel.add(sampleRates);

        menuPanel.add(new Label("  Sample size:"));
        menuPanel.add(sampleSizeInBits);

        menuPanel.add(new Label("  Channels:"));
        menuPanel.add(numberOfChannels);

        menuPanel.add(signed);
        menuPanel.add(bigEndian);

        Button captureButton = new Button("Capture!");
        captureButton.addActionListener(e -> {
            AudioCapture.SAMPLE_RATE = Integer.parseInt(sampleRates.getItem(sampleRates.getSelectedIndex()));
            sampleRates.setEnabled(false);
            AudioCapture.SAMPLE_SIZE_IN_BITS = Integer.parseInt(sampleSizeInBits.getItem(sampleSizeInBits.getSelectedIndex()));
            sampleSizeInBits.setEnabled(false);
            AudioCapture.this.begin();
        });
        menuPanel.add(captureButton);

        Button stopCaptureButton = new Button("Stop!");
        stopCaptureButton.addActionListener(e -> {
            AudioCapture.this.STOP_CAPTURE_FLAG = true;
            sampleRates.setEnabled(true);
            sampleSizeInBits.setEnabled(true);
            //numberOfChannels.setEnabled(true);
            //signed.setEnabled(true);
            //bigEndian.setEnabled(true);
        });
        menuPanel.add(stopCaptureButton);

        menuPanel.setBackground(Color.YELLOW);
        return menuPanel;
    }
}