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

import java.io.Serializable;
import java.security.Principal;

public class GoogleUserPrincipal implements Principal, Serializable
{
    private static final long serialVersionUID = -6226920753748399662L;
    private final GoogleCredentials _credentials;

    public GoogleUserPrincipal(GoogleCredentials credentials)
    {
        _credentials = credentials;
    }

    public GoogleCredentials getCredentials()
    {
        return _credentials;
    }

    @Override
    public String getName()
    {
        return _credentials.getUserId();
    }

    @Override
    public String toString()
    {
        return _credentials.getUserId();
    }
}