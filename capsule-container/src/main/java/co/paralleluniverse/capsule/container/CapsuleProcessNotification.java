/*
 * Capsule
 * Copyright (c) 2014, Parallel Universe Software Co. All rights reserved.
 * 
 * This program and the accompanying materials are licensed under the terms 
 * of the Eclipse Public License v1.0, available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package co.paralleluniverse.capsule.container;

import javax.management.Notification;

/**
 *
 * @author pron
 */
public class CapsuleProcessNotification extends Notification {
    protected final String processId;

    public CapsuleProcessNotification(String type, Object source, long sequenceNumber, String processId) {
        super(type, source, sequenceNumber);
        this.processId = processId;
    }

    public CapsuleProcessNotification(String type, Object source, long sequenceNumber, String processId, String message) {
        super(type, source, sequenceNumber, message);
        this.processId = processId;
    }

    public CapsuleProcessNotification(String type, Object source, long sequenceNumber, long timeStamp, String processId) {
        super(type, source, sequenceNumber, timeStamp);
        this.processId = processId;
    }

    public CapsuleProcessNotification(String type, Object source, long sequenceNumber, long timeStamp, String processId, String message) {
        super(type, source, sequenceNumber, timeStamp, message);
        this.processId = processId;
    }

    public final String getProcessId() {
        return processId;
    }

    @Override
    public String toString() {
        return super.toString() + "[processId=" + processId + "]";
    }
}
