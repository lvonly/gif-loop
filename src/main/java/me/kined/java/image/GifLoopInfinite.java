package me.kined.java.image;

import me.kined.java.image.gif.GifFilter;
import me.kined.java.image.gif.GifFilterListener;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;

public class GifLoopInfinite {
    private static final String SUFFIX = "-c";
    public static void main(String[] args) throws Exception {
        GifFilterListener infiniteLoopListener = new GifFilterListener() {
            @Override
            public int onLoopCount(int loopCount) {
                return 0;
            }
        };

        File currentDir = new File(".");
        for (File file : currentDir.listFiles()) {
            if (file.isFile()) {
                String filePath = file.getPath();
                if (filePath.toLowerCase().endsWith(".gif")) {
                    int extensionIndex = filePath.lastIndexOf('.');
                    String newFilename = filePath.substring(0, extensionIndex)
                            + SUFFIX + filePath.substring(extensionIndex);
                    System.out.println(file.getPath() + " => " + newFilename);
                    try (FileOutputStream os = new FileOutputStream(newFilename);
                         FileInputStream is = new FileInputStream(file)) {
                        GifFilter.newBuilder()
                                .setListener(infiniteLoopListener)
                                .setOutputStream(os)
                                .build(is)
                                .filter();
                    } catch (Exception e) {
                        System.err.println("failed to filter gif file: " + e.toString());
                        Files.deleteIfExists(new File(newFilename).toPath());
                    }
                }
            }
        }
    }
}
