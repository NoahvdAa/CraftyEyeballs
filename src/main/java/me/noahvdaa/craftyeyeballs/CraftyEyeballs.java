package me.noahvdaa.craftyeyeballs;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CraftyEyeballs implements ClientModInitializer {

    public static final long NEXT_CANDIDATE_DELAY = 250L;
    public static final long TIMEOUT_DELAY = 30_000L;
    public static final Logger LOGGER = LoggerFactory.getLogger("craftyeyeballs");

    @Override
    public void onInitializeClient() {
    }

}