package com.aresstack.corenth.proasteion.emporion.holkas.ftp;

import com.aresstack.corenth.proasteion.emporion.holkas.ResourceReadMode;
import com.aresstack.corenth.proasteion.emporion.holkas.mvs.MvsLocation;

import java.io.IOException;
import java.util.List;

/**
 * Minimal session abstraction used by the Holkas MVS connector.
 */
public interface FtpClientSession extends AutoCloseable {

    byte[] readBytes(MvsLocation location, ResourceReadMode readMode) throws IOException;

    List<String> listNames(MvsLocation location) throws IOException;

    @Override
    void close();
}
