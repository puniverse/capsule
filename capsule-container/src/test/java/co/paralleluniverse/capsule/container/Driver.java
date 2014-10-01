/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. and Contributors. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package co.paralleluniverse.capsule.container;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;


public class Driver {
    private static final Path CAPSULES = Paths.get("XXXXXXXXX"); // the name of a library containing capsules

    public static void main(String[] args) throws Exception {
        System.setProperty("capsule.log", "verbose");
        Path cache = CAPSULES.resolve("cache");
        CapsuleContainer container = new CascadingCapsuleContainer(cache) {
            @Override
            protected void onProcessDeath(String id, CapsuleContainer.ProcessInfo pi, int exitValue) {
                System.out.println("DEATH: " + id);
            }

            @Override
            protected void onProcessLaunch(String id, CapsuleContainer.ProcessInfo pi) {
                System.out.println("LAUNCH: " + id);
            }
        };

        int i = 0;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(CAPSULES)) {
            for (Path f : ds) {
                if (!Files.isDirectory(f) && f.getFileName().toString().endsWith(".jar")) {
                    System.out.println("Launching " + f);
                    String id = container.launchCapsule(f, null, Arrays.asList("hi", Integer.toString(++i)));
                }
            }
        }

        Thread.sleep(Long.MAX_VALUE);
    }
}
