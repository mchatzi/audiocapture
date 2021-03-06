package com.didi.splviewer;

import java.awt.Button;
import java.awt.Choice;
import java.awt.Color;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextField;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.LinkedList;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//import ch.qos.logback.classic.Level;
//import ch.qos.logback.classic.Logger; //TODO check if relevant

public final class SPLViewer implements SPLModule {

    private enum DisplayType {Linear, Log}

    /**
     * SETTINGS
     */
    private int UPDATES_PER_SECOND = 50; //refresh rate of the viewer, in pixels
    private int X_ZOOM_LEVEL = 40; //horizontal pixels per second (= horizontal pixels per #samples=SAMPLE_RATE)
    private double Y_ZOOM_LEVEL = 1.0;  //multiplier for vertical zoom
    private DisplayType DISPLAY_TYPE = DisplayType.Log;

    private static Logger logger = (Logger) LoggerFactory.getLogger(SPLViewer.class);

    static {
//        logger.setLevel(Level.WARN); //TODO relevant?
    }

    private Container viewerContainer;
    private int width, height;
    private double cursor_x = -1;
    private LinkedBlockingQueue<Long> pullBuffer;
    private boolean EXIT_FLAG = false;
    private final Average updateProcessingTimeAverage = new Average();

    private int SAMPLES_PER_UPDATE = AudioCapture.getSampleRate() / UPDATES_PER_SECOND;

    @Override
    public void run() {
        Graphics g = viewerContainer.getGraphics();
        g.setColor(Color.LIGHT_GRAY);

        long captureBeginTime = System.currentTimeMillis();  //No we don't restart the runnable every time a capture is stopped and started

        while (!EXIT_FLAG) {
            try {
                int updateIndex = 1;
                updateProcessingTimeAverage.reset();
                long frameBeginTime = 0;
                LinkedList<Long> localUpdateSampleBuffer = new LinkedList<>();

                for (int sampleIndex = 1; sampleIndex <= AudioCapture.getSampleRate(); sampleIndex++) {
                    Long sample = pullBuffer.take();

                    //The take() method is bloking so we only want to start timing the frame after we're unblocked, ie samples started coming in
                    if (sampleIndex == 1) {
                        frameBeginTime = System.nanoTime();
                        SAMPLES_PER_UPDATE = AudioCapture.getSampleRate() / UPDATES_PER_SECOND; //Need to recalculate this when we change audio capture settings
                    }

                    if (sample != null) {
                        localUpdateSampleBuffer.add(sample);

                        if (sampleIndex % SAMPLES_PER_UPDATE == 0) {
                            long updateBeginTime = System.nanoTime();

                            //Completely empty the sample buffer
                            for (Long bufferedSample = localUpdateSampleBuffer.poll(); bufferedSample != null; bufferedSample = localUpdateSampleBuffer.poll()) {
                                synchronized (g) {
                                    printSample(g, bufferedSample);
                                }
                            }
                            updateSleepIntervalAndSleep(updateIndex, frameBeginTime, updateBeginTime);
                            updateIndex++;
                        }
                    } else {
                        logger.error("Sample was NULL  - THIS SHOULDN'T HAPPEN, i = {}", sampleIndex);
                    }
                }

                //Show a timeline of seconds since beginning of capture
                synchronized (g) {
                    g.drawString(String.valueOf((System.currentTimeMillis() - captureBeginTime) / 1000), (int) cursor_x, height - 40);
                }

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void updateSleepIntervalAndSleep(final int updateIndex, final long frameBeginTime, final long updateBeginTime) throws InterruptedException {
        int numberOfRemainingUpdates = UPDATES_PER_SECOND - updateIndex;
        if (numberOfRemainingUpdates > 0) {
            long timeNow = System.nanoTime();
            long updateProcessingTime = timeNow - updateBeginTime;

            long averageUpdateProcessingTime = (long) updateProcessingTimeAverage.newAverage(updateProcessingTime);
            //logger.warn("Average: {}, actual {}", averageUpdateProcessingTime, updateProcessingTime);

            long frameProcessingTime = timeNow - frameBeginTime;
            long remainingTimeForRestOfFrame = 1000000000L - frameProcessingTime; //Audio source is giving us samples every 1000ms (1 sec)

            if (remainingTimeForRestOfFrame >= 0L) {
                long newSleeptimePerUpdateInMillis = Math.round(
                    ((remainingTimeForRestOfFrame / (double) numberOfRemainingUpdates) - averageUpdateProcessingTime)
                        / 1000000L);

                if (newSleeptimePerUpdateInMillis > 0 && newSleeptimePerUpdateInMillis >= 1) {
                    Thread.sleep(newSleeptimePerUpdateInMillis);
                }
                if (newSleeptimePerUpdateInMillis < 1) {
                    logger.warn("Please lower refresh rate"); //Well, if it happened that the processing time took too much, it's not the refresh rate's fault
                }
            } else {
                logger.warn("Out of TIME at sample " + (updateIndex * (AudioCapture.getSampleRate() / UPDATES_PER_SECOND)) + " (loop " + updateIndex + ", time=" + (-remainingTimeForRestOfFrame) + ", frame processing time up to now=" + frameProcessingTime);
            }
        }
    }

    @Override
    public SPLViewer shutdown() {
        EXIT_FLAG = true;
        return this;
    }


    private void printSample(Graphics g, long value) {
        if (cursor_x >= width) {
            cursor_x = 0;
            g.clearRect(0, 0, width, height);
        }

        double ratio = (double) value / (1 << (AudioCapture.getSampleSizeInBits() - (AudioCapture.isSIGNED() ? 1 : 0)));
        double maxAmplitude = height / 2;
        int cursorY = (int) maxAmplitude;

        synchronized (DISPLAY_TYPE) {
            if (DISPLAY_TYPE == DisplayType.Log) {
                int sign = ratio > 0 ? 1 : -1;
                double decibelValue9 = Math.pow(10, Math.abs(ratio)) - 1;  //since abs(ratio) is between 0 and 1, we know this power is in [1..10]
                double decibelValue = 10 * (decibelValue9 / 9); //So this moves in [0..10]
                cursorY = (int) (maxAmplitude + sign * (decibelValue * (maxAmplitude / 10)) * Y_ZOOM_LEVEL);

            } else if (DISPLAY_TYPE == DisplayType.Linear) {
                double absoluteAmplitude = ratio * maxAmplitude;
                cursorY = (int) (maxAmplitude + (Math.abs(absoluteAmplitude) >= 1 ? absoluteAmplitude : 0));

            }
        }
        if (cursorY == maxAmplitude) {
            g.setColor(Color.YELLOW);
        }

        g.drawLine((int) cursor_x, cursorY, (int) cursor_x, cursorY);

        if (cursorY == maxAmplitude) {
            g.setColor(Color.LIGHT_GRAY);
        }

        cursor_x += (float) X_ZOOM_LEVEL / AudioCapture.getSampleRate();
    }


    public SPLViewer(final LinkedBlockingQueue<Long> pullBuffer) {
        this.pullBuffer = pullBuffer;
    }

    @Override
    public Container getView() {
        viewerContainer = new Panel();
        viewerContainer.setBackground(new Color(29, 76, 122));
        viewerContainer.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(final ComponentEvent e) {
                width = viewerContainer.getWidth();
                height = viewerContainer.getHeight();
                cursor_x = 0;
            }
        });
        return viewerContainer;
    }

    @Override
    public Panel getOptionsPanel() {
        Panel menuPanel = new Panel();

        TextField refreshRate = new TextField("  " + UPDATES_PER_SECOND);
        TextField horizontalZoom = new TextField("  " + X_ZOOM_LEVEL);
        TextField verticalZoom = new TextField(String.valueOf(Double.valueOf(Y_ZOOM_LEVEL)));

        Choice displayTypeChoice = new Choice();
        displayTypeChoice.add(DisplayType.Linear.name());
        displayTypeChoice.add(DisplayType.Log.name());
        displayTypeChoice.select(DISPLAY_TYPE.name());

        menuPanel.add(new Label("Refresh rate (Hz):"));
        menuPanel.add(refreshRate);
        menuPanel.add(new Label("   Horizontal zoom:"));
        menuPanel.add(horizontalZoom);
        menuPanel.add(new Label("   Vertical zoom:"));
        menuPanel.add(verticalZoom);
        menuPanel.add(new Label("  Display:"));
        menuPanel.add(displayTypeChoice);

        Button button = new Button("Yes!");
        button.addActionListener(e -> {
            synchronized (viewerContainer.getGraphics()) {
                UPDATES_PER_SECOND = Integer.parseInt(refreshRate.getText().trim());
                SAMPLES_PER_UPDATE = AudioCapture.getSampleRate() / UPDATES_PER_SECOND;
                X_ZOOM_LEVEL = Integer.parseInt(horizontalZoom.getText().trim());
                Y_ZOOM_LEVEL = Double.parseDouble(verticalZoom.getText().trim());
                DISPLAY_TYPE = DisplayType.valueOf(displayTypeChoice.getItem(displayTypeChoice.getSelectedIndex()));
                //viewerContainer.getGraphics().clearRect(0, 0, viewerContainer.getWidth(), viewerContainer.getHeight());
                //cursor_x = 0;
            }
        });
        menuPanel.add(button);
        menuPanel.setBackground(Color.YELLOW);

        return menuPanel;
    }
}