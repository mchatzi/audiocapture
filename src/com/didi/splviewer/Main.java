package com.didi.splviewer;


import java.awt.Button;
import java.awt.Color;
import java.awt.Container;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Label;
import java.awt.Panel;
import java.awt.TextField;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

interface SPLModule extends Runnable {
    default Container getView() {
        return null;
    }

    default Container getOptionsPanel() {
        return null;
    }

    default SPLModule begin() {
        Thread splModuleThread = new Thread(this);
        splModuleThread.setPriority(Thread.MAX_PRIORITY);
        splModuleThread.start();
        return this;
    }

    SPLModule shutdown();
}


public class Main {

    private static Logger logger = (Logger) LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws LineUnavailableException, IOException {

        //Audio capture reads in bytes, splViewer prints them out.
        //For communicating, setup a pipeline between the 2 threads (alternatively use a concurrent queue)
        final PipedOutputStream pushBuffer = new PipedOutputStream();
        final PipedInputStream pullBuffer = new PipedInputStream(pushBuffer, AudioCapture.SAMPLE_RATE);


        AudioCapture audioCapture = new AudioCapture(pushBuffer);
        SPLViewer splViewer = new SPLViewer(pullBuffer);


        final Frame mainFrame = new Frame();

        /* ***********   Window mode  ************** */
        int frameWidth = 1200;
        int frameHeight = 600;
        mainFrame.setSize(frameWidth, frameHeight);

        mainFrame.addWindowListener(
            new WindowAdapter() {
                public void windowClosing(WindowEvent e) {
                    mainFrame.dispose();
                    System.exit(0);
                }
            });

        /* ***********   Fullscreen mode  ************** */
        /*frame.setUndecorated(true);
        GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().setFullScreenWindow(frame);
        frame.addMouseListener(new MouseAdapter() { //->exit on click
            public void mouseClicked(MouseEvent e) {
                frame.dispose();
                System.exit(0);
            }
        });*/


        mainFrame.add(splViewer.getView());
        mainFrame.setBackground(Color.BLUE);
        mainFrame.setVisible(true);


        Frame menuFrame = new Frame();
        menuFrame.setBounds(100, frameHeight + 30, 680, 40);
        menuFrame.setUndecorated(true);

        Container splViewerOptions = splViewer.getOptionsPanel();


        Button exitButton = new Button("Quit");
        exitButton.addActionListener(e -> {
            audioCapture.shutdown();
            splViewer.shutdown();
            System.exit(0);
        });


        splViewerOptions.add(exitButton);


        splViewerOptions.setBounds(0, mainFrame.getY() - 1, frameWidth, 40);
        menuFrame.add(splViewerOptions);
        menuFrame.setVisible(true);


        splViewer.begin();
        audioCapture.begin();


    }
}


class AudioCapture implements SPLModule {

    /**
     * SETTINGS
     */
    public static final int SAMPLE_RATE = 44100;

    public static final int SAMPLE_SIZE_IN_BITS = 8;
    public static final boolean SIGNED = true;
    public static final boolean BIG_ENDIAN = true;

    private static Logger logger = (Logger) LoggerFactory.getLogger(AudioCapture.class);
    private final PipedOutputStream pushBuffer;
    private boolean EXIT_FLAG = false;

    public AudioCapture(PipedOutputStream pushBuffer) {
        this.pushBuffer = pushBuffer;
    }

    @Override
    public void run() {
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

        final AudioFormat format = new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE_IN_BITS, 1, SIGNED, BIG_ENDIAN);
        final TargetDataLine line;
        try {
            line = (TargetDataLine) mixer.getLine(
                new DataLine.Info(TargetDataLine.class, format));
            line.open(format);
            line.start();

            int bufferSize = (int) format.getSampleRate(); //* format.getFrameSize();
            byte buffer[] = new byte[bufferSize];

            //Main loop
            while (!EXIT_FLAG) {
                int count = line.read(buffer, 0, buffer.length);
                if (count > 0) {
                    pushBuffer.write(buffer);
                }
            }

            pushBuffer.close();

        } catch (LineUnavailableException | IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public AudioCapture shutdown() {
        EXIT_FLAG = true;
        return this;
    }
}


class SPLViewer implements SPLModule {

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
    PipedInputStream pullBuffer;
    private boolean EXIT_FLAG = false;

    private int SAMPLES_PER_UPDATE = AudioCapture.SAMPLE_RATE / UPDATES_PER_SECOND;

    @Override
    public void run() {
        byte[] frameSamples = new byte[AudioCapture.SAMPLE_RATE];
        while (!EXIT_FLAG) {
            try {
                Graphics g = viewerContainer.getGraphics();
                g.setColor(Color.WHITE);

                int count = pullBuffer.read(frameSamples);
                logger.warn("Ive read {} samplees", count);
                if (count > 0) {
                    int updateIndex = 0;
                    double processingTimeUpToNow;
                    double remainingTimeForRestOfFrame;
                    long begin = System.nanoTime();

                    for (int i = 0; i < count; i++) {
                        synchronized (g) {
                            printSample(g, frameSamples[i]);
                        }
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

                    g.drawString("e", (int) cursor_x, (height / 2) + 200);

                    logger.debug("Processing finished at: " + (System.nanoTime() - begin) / 1000000);

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
