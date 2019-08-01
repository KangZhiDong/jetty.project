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

package org.eclipse.jetty.websocket.javax.client;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import javax.websocket.ClientEndpoint;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.Extension;
import javax.websocket.Session;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.util.annotation.ManagedObject;
import org.eclipse.jetty.websocket.core.WebSocketComponents;
import org.eclipse.jetty.websocket.core.client.WebSocketCoreClient;
import org.eclipse.jetty.websocket.javax.common.ConfiguredEndpoint;
import org.eclipse.jetty.websocket.javax.common.InvalidWebSocketException;
import org.eclipse.jetty.websocket.javax.common.JavaxWebSocketContainer;
import org.eclipse.jetty.websocket.javax.common.JavaxWebSocketExtensionConfig;
import org.eclipse.jetty.websocket.javax.common.JavaxWebSocketFrameHandler;
import org.eclipse.jetty.websocket.javax.common.JavaxWebSocketFrameHandlerFactory;

/**
 * Container for Client use of the javax.websocket API.
 * <p>
 * This should be specific to a JVM if run in a standalone mode. or specific to a WebAppContext if running on the Jetty server.
 */
@ManagedObject("JSR356 Client Container")
public class JavaxWebSocketClientContainer extends JavaxWebSocketContainer implements javax.websocket.WebSocketContainer
{
    protected WebSocketCoreClient coreClient;
    protected Supplier<WebSocketCoreClient> coreClientFactory;
    private final JavaxWebSocketClientFrameHandlerFactory frameHandlerFactory;

    public JavaxWebSocketClientContainer()
    {
        this(new WebSocketComponents());
    }

    public JavaxWebSocketClientContainer(WebSocketComponents components)
    {
        this(components, () ->
        {
            WebSocketCoreClient coreClient = new WebSocketCoreClient(components);
            coreClient.getHttpClient().setName("Javax-WebSocketClient@" + Integer.toHexString(coreClient.getHttpClient().hashCode()));
            return coreClient;
        });
    }

    public JavaxWebSocketClientContainer(WebSocketComponents components, Supplier<WebSocketCoreClient> coreClientFactory)
    {
        super(components);
        this.coreClientFactory = coreClientFactory;
        this.frameHandlerFactory = new JavaxWebSocketClientFrameHandlerFactory(this);
    }

    protected HttpClient getHttpClient()
    {
        return getWebSocketCoreClient().getHttpClient();
    }

    protected WebSocketCoreClient getWebSocketCoreClient()
    {
        if (coreClient == null)
        {
            coreClient = coreClientFactory.get();
            addManaged(coreClient);
        }

        return coreClient;
    }

    /**
     * Connect to remote websocket endpoint
     *
     * @param upgradeRequest the upgrade request information
     * @return the future for the session, available on success of connect
     */
    private CompletableFuture<Session> connect(JavaxClientUpgradeRequest upgradeRequest)
    {
        upgradeRequest.setConfiguration(defaultCustomizer);
        CompletableFuture<Session> futureSession = new CompletableFuture<>();

        try
        {
            WebSocketCoreClient coreClient = getWebSocketCoreClient();
            coreClient.connect(upgradeRequest).whenComplete((coreSession, error) ->
            {
                if (error != null)
                {
                    futureSession.completeExceptionally(error);
                    return;
                }

                JavaxWebSocketFrameHandler frameHandler = (JavaxWebSocketFrameHandler)upgradeRequest.getFrameHandler();
                futureSession.complete(frameHandler.getSession());
            });
        }
        catch (Exception e)
        {
            futureSession.completeExceptionally(e);
        }

        return futureSession;
    }

    private Session connect(ConfiguredEndpoint configuredEndpoint, URI destURI) throws IOException
    {
        Objects.requireNonNull(configuredEndpoint, "WebSocket configured endpoint cannot be null");
        Objects.requireNonNull(destURI, "Destination URI cannot be null");

        JavaxClientUpgradeRequest upgradeRequest = new JavaxClientUpgradeRequest(this, getWebSocketCoreClient(), destURI, configuredEndpoint);

        EndpointConfig config = configuredEndpoint.getConfig();
        if (config instanceof ClientEndpointConfig)
        {
            ClientEndpointConfig clientEndpointConfig = (ClientEndpointConfig)config;

            JsrUpgradeListener jsrUpgradeListener = new JsrUpgradeListener(clientEndpointConfig.getConfigurator());
            upgradeRequest.addListener(jsrUpgradeListener);

            for (Extension ext : clientEndpointConfig.getExtensions())
            {
                upgradeRequest.addExtensions(new JavaxWebSocketExtensionConfig(ext));
            }

            if (clientEndpointConfig.getPreferredSubprotocols().size() > 0)
                upgradeRequest.setSubProtocols(clientEndpointConfig.getPreferredSubprotocols());
        }

        long timeout = getWebSocketCoreClient().getHttpClient().getConnectTimeout();
        try
        {
            Future<Session> sessionFuture = connect(upgradeRequest);
            if (timeout > 0)
                return sessionFuture.get(timeout + 1000, TimeUnit.MILLISECONDS);
            return sessionFuture.get();
        }
        catch (ExecutionException e)
        {
            var cause = e.getCause();
            if (cause instanceof RuntimeException)
                throw (RuntimeException)cause;
            if (cause instanceof IOException)
                throw (IOException)cause;
            throw new IOException(cause);
        }
        catch (TimeoutException e)
        {
            throw new IOException("Connection future timeout " + timeout + " ms for " + destURI, e);
        }
        catch (Throwable e)
        {
            throw new IOException("Unable to connect to " + destURI, e);
        }
    }

    @Override
    public Session connectToServer(final Class<? extends Endpoint> endpointClass, final ClientEndpointConfig config, URI path) throws IOException
    {
        ClientEndpointConfig clientEndpointConfig = config;
        if (clientEndpointConfig == null)
        {
            clientEndpointConfig = new EmptyClientEndpointConfig();
        }
        ConfiguredEndpoint instance = newConfiguredEndpoint(endpointClass, clientEndpointConfig);
        return connect(instance, path);
    }

    @Override
    public Session connectToServer(final Class<?> annotatedEndpointClass, final URI path) throws IOException
    {
        ConfiguredEndpoint instance = newConfiguredEndpoint(annotatedEndpointClass, new EmptyClientEndpointConfig());
        return connect(instance, path);
    }

    @Override
    public Session connectToServer(final Endpoint endpoint, final ClientEndpointConfig config, final URI path) throws DeploymentException, IOException
    {
        ClientEndpointConfig clientEndpointConfig = config;
        if (clientEndpointConfig == null)
        {
            clientEndpointConfig = new EmptyClientEndpointConfig();
        }
        ConfiguredEndpoint instance = newConfiguredEndpoint(endpoint, clientEndpointConfig);
        return connect(instance, path);
    }

    @Override
    public Session connectToServer(Object endpoint, URI path) throws DeploymentException, IOException
    {
        ConfiguredEndpoint instance = newConfiguredEndpoint(endpoint, new EmptyClientEndpointConfig());
        return connect(instance, path);
    }

    @Override
    public JavaxWebSocketFrameHandlerFactory getFrameHandlerFactory()
    {
        return frameHandlerFactory;
    }

    @Override
    public Executor getExecutor()
    {
        return getHttpClient().getExecutor();
    }

    private ConfiguredEndpoint newConfiguredEndpoint(Class<?> endpointClass, EndpointConfig config)
    {
        try
        {
            return newConfiguredEndpoint(endpointClass.getConstructor().newInstance(), config);
        }
        catch (Throwable e)
        {
            throw new InvalidWebSocketException("Unable to instantiate websocket: " + endpointClass.getName());
        }
    }

    public ConfiguredEndpoint newConfiguredEndpoint(Object endpoint, EndpointConfig providedConfig) throws DeploymentException
    {
        EndpointConfig config = providedConfig;

        if (config == null)
        {
            config = newEmptyConfig(endpoint);
        }

        config = readAnnotatedConfig(endpoint, config);

        return new ConfiguredEndpoint(endpoint, config);
    }

    protected EndpointConfig newEmptyConfig(Object endpoint)
    {
        return new EmptyClientEndpointConfig();
    }

    protected EndpointConfig readAnnotatedConfig(Object endpoint, EndpointConfig config) throws DeploymentException
    {
        ClientEndpoint anno = endpoint.getClass().getAnnotation(ClientEndpoint.class);
        if (anno != null)
        {
            // Overwrite Config from Annotation
            // TODO: should we merge with provided config?
            return new AnnotatedClientEndpointConfig(anno);
        }
        return config;
    }
}