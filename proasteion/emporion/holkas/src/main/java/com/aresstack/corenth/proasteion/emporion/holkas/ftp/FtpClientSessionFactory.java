package com.aresstack.corenth.proasteion.emporion.holkas.ftp;

import java.io.IOException;

/**
 * Opens routed MVS client sessions for an authenticated access handle.
 */
public interface FtpClientSessionFactory extends AutoCloseable {

    FtpClientSession open(FtpSessionOpenRequest request) throws IOException;

    @Override
    void close();
}
