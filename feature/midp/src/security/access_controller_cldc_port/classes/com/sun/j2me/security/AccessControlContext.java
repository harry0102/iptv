/*
 * Copyright  1990-2009 Sun Microsystems, Inc. All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License version
 * 2 only, as published by the Free Software Foundation.
 * 
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License version 2 for more details (a copy is
 * included at /legal/license.txt).
 * 
 * You should have received a copy of the GNU General Public License
 * version 2 along with this work; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA
 * 
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, CA 95054 or visit www.sun.com if you need additional
 * information or have any questions.
 */

package com.sun.j2me.security;

/** 
 * The AccessControlContext interface is used in the case where the
 * permission of an application not running in the current isolate needs
 * to be checked, for example the when an installer has to register
 * connection for a  MIDlet with the push registry.
 * <p>
 * This API does not specify the names used to identify permissions, the
 * policy behind the API should use the standard names in JSR specifications
 * to identify permissions. Manufacturer API names should be specified in the
 * documentation for that API so the policy maker can accomidate them.
 */
public interface AccessControlContext {
    /** 
     * Determines whether the access request indicated by the
     * specified permission should be allowed or denied, based on
     * the security policy currently in effect. 
     * This method quietly returns if the access request
     * is permitted, or throws a suitable SecurityException otherwise. 
     * May block to ask the user a question.
     * <p>
     * If the permission check failed because an InterruptedException was
     * thrown, this method will throw a InterruptedSecurityException.
     *
     * @param name name of the requested permission
     * 
     * @exception SecurityException if the specified permission
     * is not permitted, based on the current security policy
     */
    void checkPermission(String name) throws SecurityException;

    /**
     * Check for permission and throw an exception if not allowed.
     * May block to ask the user a question.
     * <p>
     * If the permission check failed because an InterruptedException was
     * thrown, this method will throw a InterruptedSecurityException.
     *
     * @param name name of the requested permission
     *
     * @param resource string to insert into the question, can be null if
     *        no %2 in the question
     *
     * @param name name of the requested permission
     * 
     * @exception SecurityException if the specified permission
     * is not permitted, based on the current security policy
     */
    void checkPermission(String name, String resource)
        throws SecurityException;

    /**
     * Checks for permission and throw an exception if not allowed.
     * May block to ask the user a question.
     * <p>
     * If the permission check failed because an InterruptedException was
     * thrown, this method will throw a InterruptedSecurityException.
     *
     * @param permission ID of the permission to check for,
     *      the ID must be from
     *      {@link com.sun.midp.security.Permissions}
     * @param resource string to insert into the question, can be null if
     *        no %2 in the question
     * @param extraValue string to insert into the question,
     *        can be null if no %3 in the question
     *
     * @param name name of the requested permission
     * 
     * @exception SecurityException if the specified permission
     * is not permitted, based on the current security policy
     */
    void checkPermission(String name, String resource, String extraValue)
        throws SecurityException;
}
