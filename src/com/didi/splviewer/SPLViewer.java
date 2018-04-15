package com.didi.splviewer;

import java.awt.Button;
import java.awt.Color;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextField;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.IOException;
import java.io.PipedInputStream;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

public final class SPLViewer implements SPLModule {

    /**
     * SETTINGS
     */
    private int UPDATES_PER_SECOND = 50; //refresh rate of the viewer, in pixels
    private int X_ZOOM_LEVEL = 40; //horizontal pixels per second (= horizontal pixels per #samples=SAMPLE_RATE)
    private double Y_ZOOM_LEVEL = 1.9;  //multiplier for vertical zoom


    private static Logger logger = (Logger) LoggerFactory.getLogger(SPLViewer.class);

    static {
        logger.setLevel(Level.WARN);
    }

    private Container viewerContainer;
    private int width, height;
    private double cursor_x = -1;
    private PipedInputStream pullBuffer;
    private boolean EXIT_FLAG = false;

    private int SAMPLES_PER_UPDATE = AudioCapture.SAMPLE_RATE / UPDATES_PER_SECOND;

    @Override
    public void run() {
        byte[] frameSamples = new byte[AudioCapture.SAMPLE_RATE];
        long captureBeginTime = System.currentTimeMillis();

        while (!EXIT_FLAG) {
            try {
                Graphics g = viewerContainer.getGraphics();
                g.setColor(Color.WHITE);


                logger.warn("There are {} available", pullBuffer.available());

                int count = pullBuffer.read(frameSamples);
                if (count > 0) {
                    int updateIndex = 0;
                    double processingTimeUpToNow;
                    double remainingTimeForRestOfFrame;
                    long begin = System.nanoTime();


                    for (int i = 0; i < count; i++) {
                        //synchronized (g) {
                        printSample(g, frameSamples[i]);
                        //}


                        if ((i + 1) % SAMPLES_PER_UPDATE == 0) {
                            processingTimeUpToNow = (System.nanoTime() - begin) / 1000000.0;
                            remainingTimeForRestOfFrame = 1000 - processingTimeUpToNow;
                            if (remainingTimeForRestOfFrame >= 0) {
                                double newSleeptimePerRemainingUpdate = remainingTimeForRestOfFrame / (UPDATES_PER_SECOND - updateIndex + 1);
                                Thread.sleep((long) newSleeptimePerRemainingUpdate);
                            } else {
                                logger.warn("Out of TIME at loop " + updateIndex + " time=" + (-remainingTimeForRestOfFrame) + " frame processing time up to now=" + processingTimeUpToNow);
                            }

                            updateIndex++;
                        }
                    }

                    g.drawString(String.valueOf((System.currentTimeMillis() - captureBeginTime) / 1000), (int) cursor_x, (height / 2) + 200);

                    //logger.debug("Processing finished at: " + (System.nanoTime() - begin) / 1000000);

                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }

        try {
            pullBuffer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public SPLViewer shutdown() {
        EXIT_FLAG = true;
        return this;
    }

    private void printSample(Graphics g, double value) {
        if (cursor_x >= width) {
            cursor_x = 0;
            g.clearRect(0, 0, width, height);
        }
        int y1 = height / 2 + (int) (value * Y_ZOOM_LEVEL);
        g.drawLine((int) cursor_x, y1, (int) cursor_x, y1);
        //cursor_x += 1;
        cursor_x += (float) X_ZOOM_LEVEL / AudioCapture.SAMPLE_RATE;
    }


    public SPLViewer(final PipedInputStream pullBuffer) {
        this.pullBuffer = pullBuffer;
        viewerContainer = new Container();
        viewerContainer.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(final ComponentEvent e) {
                width = viewerContainer.getWidth();
                height = viewerContainer.getHeight();
                cursor_x = 0;
            }
        });
    }

    @Override
    public Container getView() {
        return viewerContainer;
    }

    @Override
    public Panel getOptionsPanel() {
        Panel menuPanel = new Panel();
        TextField refreshRate = new TextField("  " + UPDATES_PER_SECOND);
        TextField horizontalZoom = new TextField("  " + X_ZOOM_LEVEL);
        TextField verticalZoom = new TextField(String.valueOf(Y_ZOOM_LEVEL));
        menuPanel.add(new Label("Viewer refresh rate (Hz):"));
        menuPanel.add(refreshRate);
        menuPanel.add(new Label("   Horizontal zoom:"));
        menuPanel.add(horizontalZoom);
        menuPanel.add(new Label("   Vertical zoom:"));
        menuPanel.add(verticalZoom);

        Button button = new Button("Yes!");
        button.addActionListener(e -> {
            synchronized (viewerContainer.getGraphics()) {
                UPDATES_PER_SECOND = Integer.parseInt(refreshRate.getText().trim());
                SAMPLES_PER_UPDATE = AudioCapture.SAMPLE_RATE / UPDATES_PER_SECOND;
                X_ZOOM_LEVEL = Integer.parseInt(horizontalZoom.getText().trim());
                Y_ZOOM_LEVEL = Double.parseDouble(verticalZoom.getText().trim());
                viewerContainer.getGraphics().clearRect(0, 0, viewerContainer.getWidth(), viewerContainer.getHeight());
                cursor_x = 0;
            }
        });
        menuPanel.add(button);
        menuPanel.setBackground(Color.YELLOW);

        return menuPanel;
    }
}