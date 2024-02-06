package me.noahvdaa.craftyeyeballs.resolver;

import net.minecraft.client.network.Address;
import net.minecraft.client.network.BlockListChecker;
import net.minecraft.client.network.RedirectResolver;
import net.minecraft.client.network.ServerAddress;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Optional;

public final class CraftyEyeballsResolver {

    private CraftyEyeballsResolver() {
    }

    private final static BlockListChecker BLOCK_LIST_CHECKER = BlockListChecker.create();
    private final static RedirectResolver REDIRECT_RESOLVER = RedirectResolver.createSrv();

    public static ResolvedServer resolve(ServerAddress in) {
        InetAddress[] addresses = getAllByName(in.getAddress());
        boolean allAddressesAllowed = Arrays.stream(addresses).allMatch((a) -> BLOCK_LIST_CHECKER.isAllowed(Address.create(new InetSocketAddress(a, in.getPort()))));

        if ((addresses.length == 0 || allAddressesAllowed) && BLOCK_LIST_CHECKER.isAllowed(in)) {
            Optional<ServerAddress> optional = REDIRECT_RESOLVER.lookupRedirect(in);
            if (optional.isPresent()) {
                InetAddress[] srvAddresses = getAllByName(optional.get().getAddress());
                boolean allSrvAddressesAllowed = Arrays.stream(srvAddresses).allMatch((a) -> BLOCK_LIST_CHECKER.isAllowed(Address.create(new InetSocketAddress(a, in.getPort()))));
                if (allSrvAddressesAllowed) {
                    addresses = srvAddresses;
                } else {
                    // blocked, return nothing.
                    return new ResolvedServer(null, null, in.getPort());
                }
            }

            Inet4Address v4 = (Inet4Address) Arrays.stream(addresses).filter(a -> a instanceof Inet4Address).findAny().orElse(null);
            Inet6Address v6 = (Inet6Address) Arrays.stream(addresses).filter(a -> !(a instanceof Inet4Address)).findAny().orElse(null);

            return new ResolvedServer(v4, v6, in.getPort());
        } else {
            // blocked or no addresses, return nothing.
            return new ResolvedServer(null, null, in.getPort());
        }
    }

    private static InetAddress[] getAllByName(String host) {
        try {
            return InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            return new InetAddress[0];
        }
    }

}
