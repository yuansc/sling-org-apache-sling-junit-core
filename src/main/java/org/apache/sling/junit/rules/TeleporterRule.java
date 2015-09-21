/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.junit.rules;

import org.apache.sling.junit.Activator;
import org.junit.rules.ExternalResource;

/** JUnit Rule used to teleport a server-side test to a Sling instance
 *  to execute it there. See the launchpad/integration-tests module for
 *  usage examples (coming soon). 
 *  A concrete TeleporterRule class is selected to match the different required 
 *  behaviors of the server-side and client-side variants of this rule.
 *  The junit.core module only contains the server-side code, to minimize
 *  its dependencies, and the client-side part is in the sling testing.teleporter
 *  module.  
 */
public abstract class TeleporterRule extends ExternalResource {
    protected Class<?> classUnderTest;

    /** Name of the implementation class to use when running on the client side */ 
    public static final String CLIENT_CLASS = "org.apache.sling.testing.teleporter.client.ClientSideTeleporter";
    
    /** Class name pattern for Customizers */ 
    public static final String CUSTOMIZER_PATTERN = "org.apache.sling.junit.teleporter.customizers.<NAME>Customizer";
    
    /** Customizer is used client-side to setup the server URL and other parameters */
    public static interface Customizer {
        void customize(TeleporterRule t, String options);
    }

    /** Meant to be instantiated via {@link #forClass} */
    protected TeleporterRule() {
    }
    
    protected void setClassUnderTest(Class<?> c) {
        this.classUnderTest = c;
    }

    /** True if running on the server-side. */
    public static boolean isServerSide() {
        return Activator.getBundleContext() != null;
    }
    
    /** Build a TeleporterRule for the given class, with no client setup options */
    public static TeleporterRule forClass(Class <?> classUnderTest) {
        return forClass(classUnderTest, null);
    }
    
    /** Build a TeleporterRule for the given class, with optional clientSetupOptions.
     * 
     *  @param clientSetupOptions If supplied, the part of that string before the first colon
     *  is used as the class name of a Customizer (or shorthand for that if it contains no dots). 
     *  The rest of the string is then passed to the Customizer so that it can be used to define 
     *  options (which server to run the test on, etc) 
     */
    public static TeleporterRule forClass(Class <?> classUnderTest, String clientSetupOptions) {
        TeleporterRule result = null;
        
        if(isServerSide()) {
            result = new ServerSideTeleporter();
            result.setClassUnderTest(classUnderTest);
        } else {
            // Client-side. Instantiate the class dynamically to 
            // avoid bringing its dependencies into this module when
            // it's running on the server side
            try {
                result = createInstance(TeleporterRule.class, CLIENT_CLASS);
            } catch(Exception e) {
                throw new RuntimeException("Unable to instantiate Teleporter client " + CLIENT_CLASS, e);
            }
            
            result.setClassUnderTest(classUnderTest);
            
            if(clientSetupOptions != null && !clientSetupOptions.isEmpty()) {
                String customizerClassName = clientSetupOptions;
                String customizerOptions = "";
                final int firstColon = clientSetupOptions.indexOf(":");
                if(firstColon > 0) {
                    customizerClassName = clientSetupOptions.substring(0, firstColon);
                    customizerOptions = clientSetupOptions.substring(firstColon + 1);
                }
                // If a short name is used, transform it using our pattern. Simplifies referring
                // to these customizers in test code, without having to make the customizer
                // classes accessible to this bundle
                if(!customizerClassName.contains(".")) {
                    customizerClassName = CUSTOMIZER_PATTERN.replace("<NAME>", customizerClassName);
                }
                createInstance(Customizer.class, customizerClassName).customize(result, customizerOptions);
            }
        }
        
        return result;
    }
    
    @SuppressWarnings("unchecked")
    protected static <T> T createInstance(Class<T> objectClass, String className) {
        try {
            return (T)(TeleporterRule.class.getClassLoader().loadClass(className).newInstance());
        } catch(Exception e) {
            throw new RuntimeException("Unable to instantiate " + className, e);
        }
    }
    
    /** If running on the server side, get an OSGi service */
    public final <T> T getService (Class<T> serviceClass) {
        return getService(serviceClass, null);
    }
    
    /** If running on the server side, get an OSGi service specified by an LDAP service filter */
    public <T> T getService (Class<T> serviceClass, String ldapFilter) {
        throw new UnsupportedOperationException("This TeleporterRule does not implement getService()");
    }
}
