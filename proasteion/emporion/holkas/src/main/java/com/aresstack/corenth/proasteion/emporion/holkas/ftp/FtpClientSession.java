package com.aresstack.corenth.proasteion.emporion.holkas.ftp;

import com.aresstack.corenth.proasteion.emporion.holkas.ResourceReadMode;
import com.aresstack.corenth.proasteion.emporion.holkas.mvs.MvsLocation;
import com.aresstack.corenth.proasteion.platform.network.NetworkRoutePlan;

import java.io.IOException;
import java.util.List;

/**
 * Minimal session abstraction used by the Holkas MVS connector.
 */
public interface FtpClientSession extends AutoCloseable {

    byte[] readBytes(MvsLocation location, ResourceReadMode readMode,
                     NetworkRoutePlan routePlan) throws IOException;

    List<String> listNames(MvsLocation location, NetworkRoutePlan routePlan) throws IOException;

    @Override
    void close();
}
