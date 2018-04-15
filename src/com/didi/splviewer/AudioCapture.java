package com.didi.splviewer;

import java.awt.Button;
import java.awt.Checkbox;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Label;
import java.awt.Panel;
import java.io.IOException;
import java.io.PipedOutputStream;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;

public final class AudioCapture implements SPLModule {

    /**
     * SETTINGS
     */
    static final int SAMPLE_RATE = 44100;

    public static final int SAMPLE_SIZE_IN_BITS = 8;
    public static final int NUMBER_OF_CHANNELS = 1;
    public static final boolean SIGNED = true;
    public static final boolean BIG_ENDIAN = true;

    private static Logger logger = (Logger) LoggerFactory.getLogger(AudioCapture.class);
    private final PipedOutputStream pushBuffer;
    private boolean EXIT_FLAG = false;
    private boolean STOP_CAPTURE_FLAG = false;

    public AudioCapture(PipedOutputStream pushBuffer) {
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

        final AudioFormat format = new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE_IN_BITS, NUMBER_OF_CHANNELS, SIGNED, BIG_ENDIAN);
        final TargetDataLine line;
        try {
            line = (TargetDataLine) mixer.getLine(
                new DataLine.Info(TargetDataLine.class, format));
            line.open(format);
            line.start();

            int bufferSize = (int) format.getSampleRate(); //* format.getFrameSize();
            byte buffer[] = new byte[bufferSize];

            //Main loop
            while (!EXIT_FLAG && !STOP_CAPTURE_FLAG) {
                int count = line.read(buffer, 0, buffer.length);
                if (count > 0) {
                    pushBuffer.write(buffer);
                    logger.warn("I pushed {} samples", count);
                }
            }

            line.close();
            mixer.close();

            if(EXIT_FLAG) {
                pushBuffer.close();
            }
        } catch (LineUnavailableException | IOException e) {
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
        sampleRates.add(String.valueOf(SAMPLE_RATE));

        Choice sampleSizeInBits = new Choice();
        sampleSizeInBits.add(SAMPLE_SIZE_IN_BITS + "bit");

        Choice numberOfChannels = new Choice();
        numberOfChannels.add(String.valueOf(NUMBER_OF_CHANNELS));

        Checkbox signed = new Checkbox("Signed");
        signed.setState(SIGNED);

        Checkbox bigEndian = new Checkbox("Big-endian");
        signed.setState(BIG_ENDIAN);

        menuPanel.add(new Label("Sample rate:"));
        menuPanel.add(sampleRates);

        menuPanel.add(new Label("  Sample size:"));
        menuPanel.add(sampleSizeInBits);

        menuPanel.add(new Label("  Channels:"));
        menuPanel.add(numberOfChannels);

        menuPanel.add(signed);
        menuPanel.add(bigEndian);

        /*Button button = new Button("Yes!");
        button.addActionListener(e -> {
            UPDATES_PER_SECOND = Integer.parseInt(sampleRate.getText().trim());
            SAMPLES_PER_UPDATE = AudioCapture.SAMPLE_RATE / UPDATES_PER_SECOND;
            X_ZOOM_LEVEL = Integer.parseInt(sampleSizeInBits.getText().trim());
            Y_ZOOM_LEVEL = Double.parseDouble(signed.getText().trim());
            viewerContainer.getGraphics().clearRect(0, 0, viewerContainer.getWidth(), viewerContainer.getHeight());
            cursor_x = 0;
        });
        menuPanel.add(button);*/

        Button captureButton = new Button("Capture!");
        captureButton.addActionListener(e -> AudioCapture.this.begin());
        menuPanel.add(captureButton);

        Button stopCaptureButton = new Button("Stop!");
        stopCaptureButton.addActionListener(e -> AudioCapture.this.STOP_CAPTURE_FLAG = true);
        menuPanel.add(stopCaptureButton);

        menuPanel.setBackground(Color.YELLOW);
        return menuPanel;
    }
}