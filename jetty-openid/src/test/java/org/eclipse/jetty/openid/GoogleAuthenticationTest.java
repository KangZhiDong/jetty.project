//
//  ========================================================================
//  Copyright (c) 1995-2019 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.openid;

import java.io.IOException;
import java.security.Principal;
import java.util.Map;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.MimeTypes;
import org.eclipse.jetty.security.Authenticator;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.authentication.GoogleAuthenticator;
import org.eclipse.jetty.security.google.GoogleLoginService;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.security.Constraint;
import org.junit.jupiter.api.Test;

public class GoogleAuthenticationTest
{
    private static final Logger LOG = Log.getLogger(GoogleAuthenticationTest.class);

    public static class LoginPage extends HttpServlet
    {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException
        {
            response.getWriter().println("<p>you logged in  <a href=\"/\">Home</a></p>");
        }
    }

    public static class LogoutPage extends HttpServlet
    {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException
        {
            request.getSession().invalidate();
            response.sendRedirect("/");
        }
    }

    public static class HomePage extends HttpServlet
    {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException
        {
            response.setContentType(MimeTypes.Type.TEXT_HTML.asString());
            response.getWriter().println("<h1>Home Page</h1>");

            Principal userPrincipal = request.getUserPrincipal();
            if (userPrincipal != null)
            {
                Map<String, String> userInfo = (Map)request.getSession().getAttribute(GoogleAuthenticator.__USER_INFO);
                response.getWriter().println("<p>Welcome: " + userInfo.get("name") + "</p>");
                response.getWriter().println("<a href=\"/profile\">Profile</a><br>");
                response.getWriter().println("<a href=\"/logout\">Logout</a><br>");
            }
            else
            {
                response.getWriter().println("<p>Please Login  <a href=\"/login\">Login</a></p>");
            }
        }
    }

    public static class ProfilePage extends HttpServlet
    {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException
        {
            response.setContentType(MimeTypes.Type.TEXT_HTML.asString());
            Map<String, String> userInfo = (Map)request.getSession().getAttribute(GoogleAuthenticator.__USER_INFO);

            response.getWriter().println("<!-- Add icon library -->\n" +
                "<div class=\"card\">\n" +
                "  <img src=\""+userInfo.get("picture")+"\" style=\"width:30%\">\n" +
                "  <h1>"+ userInfo.get("name") +"</h1>\n" +
                "  <p class=\"title\">"+userInfo.get("email")+"</p>\n" +
                "  <p>UserId: " + userInfo.get("sub") +"</p>\n" +
                "</div>");

            response.getWriter().println("<a href=\"/\">Home</a><br>");
            response.getWriter().println("<a href=\"/logout\">Logout</a><br>");
        }
    }

    public static class ErrorPage extends HttpServlet
    {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException
        {
            response.setContentType(MimeTypes.Type.TEXT_HTML.asString());
            response.getWriter().println("<h1>error: not authorized</h1>");
            response.getWriter().println("<p>" + request.getUserPrincipal() + "</p>");
        }
    }

    public static final String clientId = "1051168419525-5nl60mkugb77p9j194mrh287p1e0ahfi.apps.googleusercontent.com";
    public static final String clientSecret = "XT_MIsSv_aUCGollauCaJY8S";
    public static final String redirectUri = "http://localhost:8080/j_security_check";

    @Test
    public void runAuthenticationDemo() throws Exception
    {
        Server server = new Server(8080);
        ServletContextHandler context = new ServletContextHandler(server, "/", ServletContextHandler.SESSIONS | ServletContextHandler.SECURITY);

        // Add servlets
        context.addServlet(ProfilePage.class, "/profile");
        context.addServlet(LoginPage.class, "/login");
        context.addServlet(LogoutPage.class, "/logout");
        context.addServlet(HomePage.class, "/*");
        context.addServlet(ErrorPage.class, "/error");

        // configure security constraints
        Constraint constraint = new Constraint();
        constraint.setName(Constraint.__GOOGLE_AUTH);
        constraint.setRoles(new String[]{"user","admin","moderator"});
        constraint.setAuthenticate(true);

        // constraint mappings
        ConstraintMapping profileMapping = new ConstraintMapping();
        profileMapping.setConstraint(constraint);
        profileMapping.setPathSpec("/profile");
        ConstraintMapping loginMapping = new ConstraintMapping();
        loginMapping.setConstraint(constraint);
        loginMapping.setPathSpec("/login");

        // security handler
        ConstraintSecurityHandler securityHandler = new ConstraintSecurityHandler();
        securityHandler.addConstraintMapping(profileMapping);
        securityHandler.addConstraintMapping(loginMapping);

        // configure loginservice with user store
        GoogleLoginService loginService = new GoogleLoginService(clientId, clientSecret, redirectUri);
        securityHandler.setLoginService(loginService);

        Authenticator authenticator = new GoogleAuthenticator(clientId, redirectUri, "/error");
        securityHandler.setAuthenticator(authenticator);
        context.setSecurityHandler(securityHandler);

        server.start();
        server.join();
    }
}
