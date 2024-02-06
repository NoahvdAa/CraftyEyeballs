package me.noahvdaa.craftyeyeballs.util;

import java.net.InetSocketAddress;

public class HappyEyeballRunnable implements Runnable {

    private final Runnable originalRunnable;
    private final InetSocketAddress address;

    public HappyEyeballRunnable(Runnable originalRunnable, InetSocketAddress address) {
        this.originalRunnable = originalRunnable;
        this.address = address;
    }

    @Override
    public void run() {
        this.originalRunnable.run();
    }

    public InetSocketAddress address() {
        return this.address;
    }

}
