package org.owl.flux.util;

import com.velocitypowered.api.proxy.InboundConnection;
import java.net.InetSocketAddress;
import java.util.regex.Pattern;

public final class NetworkUtil {
    private static final Pattern IPV4_PATTERN = Pattern.compile("^\\d{1,3}(\\.\\d{1,3}){3}$");

    private NetworkUtil() {
    }

    public static String extractIp(InboundConnection connection) {
        InetSocketAddress address = connection.getRemoteAddress();
        if (address.getAddress() != null) {
            return address.getAddress().getHostAddress();
        }
        return address.getHostString();
    }

    public static boolean isIpLiteral(String input) {
        return input != null && IPV4_PATTERN.matcher(input).matches();
    }
}
