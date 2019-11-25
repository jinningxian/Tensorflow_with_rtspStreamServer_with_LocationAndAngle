package com.ncs.rtspstream.Utils;

import java.io.Closeable;
import java.io.IOException;

/**
 * Description: IOUtils <br/>
 * Date: 2017/10/12 <br/>
 *
 * @author seven.hu@ubtrobot.com
 */

public class IOUtils {

    private IOUtils() {
        throw new AssertionError();
    }

    /**
     * Close closable object and wrap {@link IOException} with {@link RuntimeException}
     * @param closeable closeable object
     */
    public static void close(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                throw new RuntimeException("IOException occurred. ", e);
            }
        }
    }

    /**
     * Close closable and hide possible {@link IOException}
     * @param closeable closeable object
     */
    public static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                // Ignored
            }
        }
    }
}
