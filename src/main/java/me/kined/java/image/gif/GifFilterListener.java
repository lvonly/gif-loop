package me.kined.java.image.gif;

public interface GifFilterListener {
    default int onLoopCount(int loopCount) {
        return loopCount;
    }

    default int onDelay(int delay) {
        return delay;
    }
}
