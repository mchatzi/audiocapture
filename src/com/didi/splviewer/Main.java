package com.didi.splviewer;

import java.awt.Button;
import java.awt.Color;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;


public final class Main {

    private static Logger logger = (Logger) LoggerFactory.getLogger(Main.class);

    private final AudioCapture audioCapture;
    private final SPLViewer splViewer;
    private final Frame mainFrame;
    private final Frame viewerControlFrame;
    private final Frame audioCaptureControlFrame;
    private final Frame applicationControlFrame;

    public static void main(String[] args) throws IOException {
        new Main();
    }

    private Main() throws IOException {

        LinkedBlockingQueue<Long> queue = new LinkedBlockingQueue<>();

        audioCapture = new AudioCapture(queue);
        splViewer = new SPLViewer(queue);

        //Simple GUI
        mainFrame = new Frame();
        mainFrame.setSize(1200, 600);
        mainFrame.setBackground(new Color(29, 76, 122));
        mainFrame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                Main.this.shutdown();
            }
        });

        //Fullscreen mode
        /*mainFrame.setUndecorated(true);
        GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().setFullScreenWindow(mainFrame);
        mainFrame.addMouseListener(new MouseAdapter() { //->exit on click
            public void mouseClicked(MouseEvent e) {
                Main.this.shutdown();
            }
        });*/

        mainFrame.add(splViewer.getView());


        //Viewer control frame
        viewerControlFrame = new Frame();
        viewerControlFrame.setBounds(100, mainFrame.getHeight() + 30, 880, 40);
        viewerControlFrame.setUndecorated(true);
        viewerControlFrame.add(splViewer.getOptionsPanel());

        //Audio capture control frame
        audioCaptureControlFrame = new Frame();
        audioCaptureControlFrame.setBounds(100, mainFrame.getHeight() + 75, 880, 40);
        audioCaptureControlFrame.setUndecorated(true);
        audioCaptureControlFrame.add(audioCapture.getOptionsPanel());

        //Application options
        applicationControlFrame = new Frame();
        applicationControlFrame.setBounds(100, mainFrame.getHeight() + 120, 880, 40);
        applicationControlFrame.setUndecorated(true);
        Panel applicationOptionsPanel = new Panel();
        applicationOptionsPanel.setBackground(Color.YELLOW);
        Button exitButton = new Button("Quit");
        exitButton.addActionListener(e -> Main.this.shutdown());
        applicationOptionsPanel.add(exitButton);
        applicationControlFrame.add(applicationOptionsPanel);

        mainFrame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(final ComponentEvent e) {
                viewerControlFrame.setBounds(100, mainFrame.getHeight() + 30, 780, 40);
                audioCaptureControlFrame.setBounds(100, mainFrame.getHeight() + 75, 880, 40);
                applicationControlFrame.setBounds(100, mainFrame.getHeight() + 120, 780, 40);
            }
        });


        mainFrame.setVisible(true);
        viewerControlFrame.setVisible(true);
        audioCaptureControlFrame.setVisible(true);
        applicationControlFrame.setVisible(true);

        splViewer.begin();
    }


    private void shutdown() {
        audioCapture.shutdown();
        splViewer.shutdown();
        mainFrame.dispose();
        viewerControlFrame.dispose();
        audioCaptureControlFrame.dispose();
        applicationControlFrame.dispose();
        System.exit(0);
    }

    public static void printTabular(final Object... values) {
        int spaces = 22;
        StringBuilder sb = new StringBuilder("   ");
        for (Object value : values) {
            String stringValue = String.valueOf(value);
            sb.append(stringValue);
            for (int i = 0; i < spaces - stringValue.length(); i++) {
                sb.append(" ");
            }
        }
        System.out.println(sb.toString());
    }

}






