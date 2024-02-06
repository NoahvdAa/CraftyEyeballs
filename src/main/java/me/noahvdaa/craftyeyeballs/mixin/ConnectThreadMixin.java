package me.noahvdaa.craftyeyeballs.mixin;

import com.mojang.datafixers.util.Pair;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ConnectTimeoutException;
import me.noahvdaa.craftyeyeballs.CraftyEyeballs;
import me.noahvdaa.craftyeyeballs.resolver.CraftyEyeballsResolver;
import me.noahvdaa.craftyeyeballs.resolver.ResolvedServer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ClientLoginNetworkHandler;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.resource.server.ServerResourcePackManager;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Mixin(targets = "net/minecraft/client/gui/screen/multiplayer/ConnectScreen$1")
public class ConnectThreadMixin {

    @Final
    @Shadow
    ServerAddress field_33737;

    @Final
    @Shadow
    MinecraftClient field_33738;

    @Final
    @Shadow
    ServerInfo field_40415;

    @Final
    @Shadow
    ConnectScreen field_2416;

    // here be dragons!
    // The code below is a modified copy of Mojang's connection thread implementation.

    /**
     * @author NoahvdAa
     * @reason CraftyEyeballs hijacks the entire connection thread because the changes it makes are too extensive to be done with redirects and injections.
     */
    @Overwrite
    public void run() {
        InetSocketAddress inetSocketAddress = null;

        try {
            if (field_2416.connectingCancelled) return;

            ResolvedServer resolved = CraftyEyeballsResolver.resolve(field_33737);
            List<InetSocketAddress> candidates = resolved.candidates().stream()
                    .map((c) -> new InetSocketAddress(c, resolved.port())).toList();
            if (field_2416.connectingCancelled) return;

            if (candidates.size() == 0) {
                field_33738.execute(
                        () -> field_33738.setScreen(
                                new DisconnectedScreen(field_2416.parent, field_2416.failureErrorMessage, ConnectScreen.BLOCKED_HOST_TEXT)
                        )
                );
                return;
            }

            CompletableFuture<Pair<InetSocketAddress, ClientConnection>> ccFuture = new CompletableFuture<>();
            List<Future<?>> futures = new ArrayList<>();
            synchronized (field_2416) {
                for (InetSocketAddress candidate : candidates) {
                    if (field_2416.connectingCancelled) return;

                    CraftyEyeballs.LOGGER.info("Attempting to connect to " + candidate + "...");

                    ClientConnection cc = new ClientConnection(NetworkSide.CLIENTBOUND);
                    cc.resetPacketSizeLog(field_33738.getDebugHud().getPacketSizeLog());
                    ChannelFuture promise = ClientConnection.connect(candidate, field_33738.options.shouldUseNativeTransport(), cc);

                    CompletableFuture<Void> sleepFuture = CompletableFuture.runAsync(() -> {
                        try {
                            Thread.sleep(CraftyEyeballs.NEXT_CANDIDATE_DELAY);
                        } catch (InterruptedException ignored) {
                        }
                    });
                    promise.addListener((e) -> {
                        if (e.cause() != null) { // if the connection already failed we might aswell skip waiting
                            sleepFuture.complete(null);
                            return;
                        }

                        synchronized (futures) {
                            if (futures.size() == 0) return;
                            CraftyEyeballs.LOGGER.info("Fastest to respond was " + candidate + "!");
                            for (Future<?> future : futures) {
                                if (future == promise) continue;
                                // cancel any other pending connections and sleep tasks
                                future.cancel(true);
                            }
                            futures.clear();
                            ccFuture.complete(Pair.of(candidate, cc));
                        }
                    });
                    futures.add(promise);
                    futures.add(sleepFuture);

                    try {
                        sleepFuture.join();
                    } catch (CancellationException ignored) {
                        break;
                    }
                }
            }
            Pair<InetSocketAddress, ClientConnection> winner;
            try {
                winner = ccFuture.get((candidates.size() * CraftyEyeballs.NEXT_CANDIDATE_DELAY) + CraftyEyeballs.TIMEOUT_DELAY, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                if (field_2416.connectingCancelled) return;
                throw new ConnectTimeoutException("connection timed out: no candidates responded in time");
            } finally {
                // clean up old futures
                for (Future<?> future : futures) {
                    future.cancel(true);
                }
            }
            inetSocketAddress = winner.getFirst();
            ClientConnection clientConnection = winner.getSecond();

            synchronized (field_2416) {
                if (field_2416.connectingCancelled) {
                    clientConnection.disconnect(ConnectScreen.ABORTED_TEXT);
                    return;
                }

                field_2416.connection = clientConnection;
                field_33738.getServerResourcePackProvider()
                        .init(clientConnection, field_40415 != null ? toAcceptanceStatus(field_40415.getResourcePackPolicy()) : ServerResourcePackManager.AcceptanceStatus.PENDING);
            }

            field_2416.connection
                    .connect(
                            inetSocketAddress.getHostName(),
                            inetSocketAddress.getPort(),
                            new ClientLoginNetworkHandler(
                                    field_2416.connection, field_33738, field_40415, field_2416.parent, false, null, field_2416::setStatus
                            )
                    );
            field_2416.connection.send(new LoginHelloC2SPacket(field_33738.getSession().getUsername(), field_33738.getSession().getUuidOrNull()));
        } catch (Exception var9) {
            if (field_2416.connectingCancelled) return;

            Throwable var5 = var9.getCause();
            Exception exception3;
            if (var5 instanceof Exception exception2) {
                exception3 = exception2;
            } else {
                exception3 = var9;
            }

            CraftyEyeballs.LOGGER.error("Couldn't connect to server", var9);
            String string = inetSocketAddress == null
                    ? exception3.getMessage()
                    : exception3.getMessage()
                    .replaceAll(inetSocketAddress.getHostName() + ":" + inetSocketAddress.getPort(), "")
                    .replaceAll(inetSocketAddress.toString(), "");
            field_33738.execute(
                    () -> field_33738.setScreen(
                            new DisconnectedScreen(
                                    field_2416.parent,
                                    field_2416.failureErrorMessage,
                                    Text.translatable("disconnect.genericReason", new Object[]{string})
                            )
                    )
            );
        }
    }

    @Shadow
    private static ServerResourcePackManager.AcceptanceStatus toAcceptanceStatus(ServerInfo.ResourcePackPolicy policy) {
        return null;
    }

}
