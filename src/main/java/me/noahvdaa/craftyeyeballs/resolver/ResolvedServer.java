package me.noahvdaa.craftyeyeballs.resolver;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class ResolvedServer {

    private final Inet4Address address4;
    private final Inet6Address address6;
    private final int port;

    public ResolvedServer(@Nullable Inet4Address address4, @Nullable Inet6Address address6, int port) {
        this.address4 = address4;
        this.address6 = address6;
        this.port = port;
    }

    @Nullable
    public Inet4Address address4() {
        return address4;
    }

    @Nullable
    public Inet6Address address6() {
        return address6;
    }

    public int port() {
        return port;
    }

    @NotNull
    public List<InetAddress> candidates() {
        List<InetAddress> addresses = new ArrayList<>();

        if (this.address6 != null) addresses.add(this.address6);
        if (this.address4 != null) addresses.add(this.address4);

        return addresses;
    }

}
