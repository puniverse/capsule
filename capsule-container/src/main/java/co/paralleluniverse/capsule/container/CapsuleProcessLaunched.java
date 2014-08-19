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
public class CapsuleProcessLaunched extends CapsuleProcessNotification {
    public static final String CAPSULE_PROCESS_LAUNCHED = "capsule.launch";


    public CapsuleProcessLaunched(Object source, long sequenceNumber, String processId) {
        super(CAPSULE_PROCESS_LAUNCHED, source, sequenceNumber, processId);
    }

    public CapsuleProcessLaunched(Object source, long sequenceNumber, String processId, String message) {
        super(CAPSULE_PROCESS_LAUNCHED, source, sequenceNumber, processId, message);
    }

    public CapsuleProcessLaunched(Object source, long sequenceNumber, long timeStamp, String processId) {
        super(CAPSULE_PROCESS_LAUNCHED, source, sequenceNumber, timeStamp, processId);
    }

    public CapsuleProcessLaunched(Object source, long sequenceNumber, long timeStamp, String processId, String message) {
        super(CAPSULE_PROCESS_LAUNCHED, source, sequenceNumber, timeStamp, processId, message);
    }
}
