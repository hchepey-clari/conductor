/*
 * Copyright 2022 Conductor Authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.embedded.jetty.JettyWebServer;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

/** Test to verify that Jetty is being used as the embedded server instead of Tomcat. */
@SpringBootTest
public class ServerTypeTest {

    @Autowired private ServletWebServerApplicationContext applicationContext;

    @Test
    public void testJettyIsUsedInsteadOfTomcat() {
        // Get the embedded web server from the application context
        WebServer webServer = applicationContext.getWebServer();

        // Assert that the web server is a JettyWebServer
        assertTrue(
                webServer instanceof JettyWebServer,
                "Expected embedded server to be JettyWebServer but was: "
                        + webServer.getClass().getName());

        // Assert that the web server is NOT a TomcatWebServer
        assertFalse(
                webServer instanceof TomcatWebServer,
                "Embedded server should not be TomcatWebServer");

        // Log the server type for clarity
        System.out.println("Embedded server type: " + webServer.getClass().getName());
    }
}
