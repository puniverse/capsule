/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package co.paralleluniverse.capsule.container;

/**
 *
 * @author pron
 */
public class CapsuleProcessKilled extends CapsuleProcessNotification {
    public static final String CAPSULE_PROCESS_KILLED = "capsule.death";

    private final int exitValue;

    public CapsuleProcessKilled(Object source, long sequenceNumber, String processId, int exitValue) {
        super(CAPSULE_PROCESS_KILLED, source, sequenceNumber, processId);
        this.exitValue = exitValue;
    }

    public CapsuleProcessKilled(Object source, long sequenceNumber, String processId, int exitValue, String message) {
        super(CAPSULE_PROCESS_KILLED, source, sequenceNumber, processId, message);
        this.exitValue = exitValue;
    }

    public CapsuleProcessKilled(Object source, long sequenceNumber, long timeStamp, String processId, int exitValue) {
        super(CAPSULE_PROCESS_KILLED, source, sequenceNumber, timeStamp, processId);
        this.exitValue = exitValue;
    }

    public CapsuleProcessKilled(Object source, long sequenceNumber, long timeStamp, String processId, int exitValue, String message) {
        super(CAPSULE_PROCESS_KILLED, source, sequenceNumber, timeStamp, processId, message);
        this.exitValue = exitValue;
    }

    public final int getExitValue() {
        return exitValue;
    }

    @Override
    public String toString() {
        return super.toString() + "[exitValue=" + exitValue + "]";
    }
}
