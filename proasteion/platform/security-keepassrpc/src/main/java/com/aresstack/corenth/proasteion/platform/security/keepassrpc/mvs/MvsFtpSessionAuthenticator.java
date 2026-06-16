package com.aresstack.corenth.proasteion.platform.security.keepassrpc.mvs;

import com.aresstack.corenth.proasteion.emporion.holkas.ftp.FtpClientSession;

import java.io.IOException;

/**
 * Trusted SPI that turns vault material plus a route into an authenticated session.
 */
public interface MvsFtpSessionAuthenticator {

    FtpClientSession authenticate(MvsFtpSessionAuthenticationRequest request) throws IOException;
}
