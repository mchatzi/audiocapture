package com.didi.splviewer;

import java.awt.Container;

public interface SPLModule extends Runnable {
    default Container getView() {
        return new Container();
    }

    default Container getOptionsPanel() {
        return new Container();
    }

    default SPLModule begin() {
        Thread splModuleThread = new Thread(this);
        splModuleThread.setPriority(Thread.MAX_PRIORITY);
        splModuleThread.start();
        return this;
    }

    SPLModule shutdown();
}
